import java.util.Properties
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

// Version and coordinates come from the main build so the published plugin matches the runtime.
val polyProperties = Properties().apply { file("../gradle.properties").inputStream().use(::load) }
group = polyProperties.getProperty("poly.group")
version = polyProperties.getProperty("poly.version")

java {
    withSourcesJar()
}

dependencies {
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.gson)
    // remapMinecraft: remaps obfuscated versions to Mojang names (ADR-0019 point 4).
    implementation(libs.asm.commons)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val values = mapOf(
        "poly" to project.version.toString(),
        "java" to libs.versions.java.get(),
        "slf4j" to libs.versions.slf4j.get(),
    )
    inputs.properties(values)
    filesMatching("**/poly-plugin.properties") {
        expand(values)
    }
    // The supported Minecraft versions, for mod projects (ADR-0011).
    from("../gradle/minecraft-versions.properties") {
        into("dev/polymodloader/devtools/gradle/minecraft")
    }
}

gradlePlugin {
    plugins {
        create("polyJavaConventions") {
            id = "poly.java-conventions"
            implementationClass = "dev.polymodloader.devtools.gradle.PolyJavaConventionsPlugin"
        }
        create("polyMinecraftDev") {
            id = "poly.minecraft-dev"
            implementationClass = "dev.polymodloader.devtools.gradle.minecraft.MinecraftDevPlugin"
        }
        create("polyMinecraftCompile") {
            id = "poly.minecraft-compile"
            implementationClass = "dev.polymodloader.devtools.gradle.minecraft.MinecraftCompilePlugin"
        }
        create("polyMinecraftVersions") {
            id = "poly.minecraft-versions"
            implementationClass = "dev.polymodloader.devtools.gradle.minecraft.MinecraftVersionsPlugin"
        }
        create("polySdkRepository") {
            id = "poly.sdk-repository"
            implementationClass = "dev.polymodloader.devtools.gradle.release.SdkRepositoryPlugin"
        }
        create("polyModTemplate") {
            id = "poly.mod-template"
            implementationClass = "dev.polymodloader.devtools.gradle.mod.ModTemplatePlugin"
        }
        create("polyMod") {
            id = "dev.polymodloader.mod"
            implementationClass = "dev.polymodloader.devtools.gradle.minecraft.PolyModPlugin"
            displayName = "Poly Mod Loader mod development"
            description = "Builds, validates, and runs Poly Mods against Minecraft through Poly Mod Loader, and generates and checks their resource files."
        }
    }
}

publishing {
    publications.withType<MavenPublication>().matching { it.name == "pluginMaven" }.configureEach {
        artifactId = "poly-gradle-plugin"
    }
}

// The public SDK repository (ADR-0008, `./gradlew publishSdk` in the main build runs `:publishSdk` here):
// the plugin jar without sources, and the marker of dev.polymodloader.mod only; the other plugin IDs are for
// this repository. publishToMavenLocal still installs everything, sources included.
val sdkRepository = rootDir.resolve("..").toPath()
    .resolve(providers.gradleProperty("polySdkRepo").getOrElse(".poly/sdk-maven")).normalize().toFile()
val sdkComponent = serviceOf<SoftwareComponentFactory>().adhoc("polySdk")
components.add(sdkComponent)
// Only the jar variants; the class and resource folder variants are not publishable.
for ((configuration, scope) in listOf("apiElements" to "compile", "runtimeElements" to "runtime")) {
    sdkComponent.addVariantsFromConfiguration(configurations[configuration]) {
        if (configurationVariant.name == configuration) mapToMavenScope(scope) else skip()
    }
}
publishing {
    repositories {
        maven {
            name = "polySdk"
            url = uri(sdkRepository)
        }
    }
    publications.create<MavenPublication>("sdk") {
        from(sdkComponent)
        artifactId = "poly-gradle-plugin"
    }
}
val sdkPublications = setOf("sdk", "polyModPluginMarkerMaven")
tasks.withType<PublishToMavenRepository>().configureEach {
    val toSdk = name.endsWith("ToPolySdkRepository")
    enabled = toSdk == sdkPublications.any { name.startsWith("publish${it.replaceFirstChar(Char::uppercase)}Publication") }
}
tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = !name.startsWith("publishSdkPublication")
}
tasks.register("publishSdk") {
    group = "publishing"
    description = "Publishes the dev.polymodloader.mod plugin, without sources, into the SDK repository folder."
    dependsOn("publishSdkPublicationToPolySdkRepository", "publishPolyModPluginMarkerMavenPublicationToPolySdkRepository")
}
