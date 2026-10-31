import java.util.Properties
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

// Version and coordinates come from the main build so the published plugin matches the runtime.
val plexusProperties = Properties().apply { file("../gradle.properties").inputStream().use(::load) }
group = plexusProperties.getProperty("plexus.group")
version = plexusProperties.getProperty("plexus.version")

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
        "plexus" to project.version.toString(),
        "java" to libs.versions.java.get(),
        "slf4j" to libs.versions.slf4j.get(),
    )
    inputs.properties(values)
    filesMatching("**/plexus-plugin.properties") {
        expand(values)
    }
    // The supported Minecraft versions, for mod projects (ADR-0011).
    from("../gradle/minecraft-versions.properties") {
        into("dev/plexusmodloader/devtools/gradle/minecraft")
    }
}

gradlePlugin {
    plugins {
        create("plexusJavaConventions") {
            id = "plexus.java-conventions"
            implementationClass = "dev.plexusmodloader.devtools.gradle.PlexusJavaConventionsPlugin"
        }
        create("plexusMinecraftDev") {
            id = "plexus.minecraft-dev"
            implementationClass = "dev.plexusmodloader.devtools.gradle.minecraft.MinecraftDevPlugin"
        }
        create("plexusMinecraftCompile") {
            id = "plexus.minecraft-compile"
            implementationClass = "dev.plexusmodloader.devtools.gradle.minecraft.MinecraftCompilePlugin"
        }
        create("plexusMinecraftVersions") {
            id = "plexus.minecraft-versions"
            implementationClass = "dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersionsPlugin"
        }
        create("plexusSdkRepository") {
            id = "plexus.sdk-repository"
            implementationClass = "dev.plexusmodloader.devtools.gradle.release.SdkRepositoryPlugin"
        }
        create("plexusModTemplate") {
            id = "plexus.mod-template"
            implementationClass = "dev.plexusmodloader.devtools.gradle.mod.ModTemplatePlugin"
        }
        create("plexusMod") {
            id = "dev.plexusmodloader.mod"
            implementationClass = "dev.plexusmodloader.devtools.gradle.minecraft.PlexusModPlugin"
            displayName = "Plexus Mod Loader mod development"
            description = "Builds, validates, and runs Plexus mods against Minecraft through Plexus Mod Loader, and generates and checks their resource files."
        }
        // The plugin ID before the rename (ADR-0008), so existing mod projects keep building with new versions.
        create("plexusModLegacy") {
            id = "dev.polymodloader.mod"
            implementationClass = "dev.plexusmodloader.devtools.gradle.minecraft.PlexusModPlugin"
            displayName = "Plexus Mod Loader mod development (legacy ID)"
            description = "Former ID of dev.plexusmodloader.mod; applies the same plugin."
        }
    }
}

publishing {
    publications.withType<MavenPublication>().matching { it.name == "pluginMaven" }.configureEach {
        artifactId = "plexus-gradle-plugin"
    }
}

// The public SDK repository (ADR-0008, `./gradlew publishSdk` in the main build runs `:publishSdk` here):
// the plugin jar without sources, and the markers of dev.plexusmodloader.mod and its legacy ID
// dev.polymodloader.mod only; the other plugin IDs are for this repository. publishToMavenLocal still installs everything, sources included.
val sdkRepository = rootDir.resolve("..").toPath()
    .resolve(providers.gradleProperty("plexusSdkRepo").getOrElse(".plexus/sdk-maven")).normalize().toFile()
val sdkComponent = serviceOf<SoftwareComponentFactory>().adhoc("plexusSdk")
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
            name = "plexusSdk"
            url = uri(sdkRepository)
        }
    }
    publications.create<MavenPublication>("sdk") {
        from(sdkComponent)
        artifactId = "plexus-gradle-plugin"
    }
}
val sdkPublications = setOf("sdk", "plexusModPluginMarkerMaven", "plexusModLegacyPluginMarkerMaven")
tasks.withType<PublishToMavenRepository>().configureEach {
    val toSdk = name.endsWith("ToPlexusSdkRepository")
    enabled = toSdk == sdkPublications.any { name.startsWith("publish${it.replaceFirstChar(Char::uppercase)}Publication") }
}
tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = !name.startsWith("publishSdkPublication")
}
tasks.register("publishSdk") {
    group = "publishing"
    description = "Publishes the dev.plexusmodloader.mod plugin (and its legacy ID), without sources, into the SDK repository folder."
    dependsOn(
        "publishSdkPublicationToPlexusSdkRepository",
        "publishPlexusModPluginMarkerMavenPublicationToPlexusSdkRepository",
        "publishPlexusModLegacyPluginMarkerMavenPublicationToPlexusSdkRepository",
    )
}
