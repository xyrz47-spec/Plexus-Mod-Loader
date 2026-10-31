plugins {
    id("plexus.java-conventions")
    id("plexus.minecraft-compile")
}

description = "Fabric API modules implemented on NeoForge 26.2's hooks (ADR-0031): run in place of the original modules next to NeoForge."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.sponge.mixin) { isTransitive = false }
    compileOnly(libs.mixinextras.common) { isTransitive = false }
    compileOnly(variantOf(libs.neoforge.universal) { classifier("universal") }) { isTransitive = false }
    compileOnly(libs.fabric.api.base) { isTransitive = false }
    compileOnly(libs.fabric.resource.loader.v1) { isTransitive = false }
    compileOnly(libs.jspecify)
    compileOnly(libs.jetbrains.annotations)
    // NeoForge's event bus and FML (ModList, ModContainer) and their Dist, for the modules that use NeoForge's events.
    compileOnly(libs.neoforge.bus) { isTransitive = false }
    compileOnly(libs.neoforge.fml.loader) { isTransitive = false }
    compileOnly(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
}

// The code calls what NeoForge's patch adds to the game (IBlockExtension's methods on Block, for example) and the
// client's classes (item tooltips): it compiles against the client jar NeoForge's own installer patched, which holds
// the common classes too, ahead of the vanilla jar. The installation is kept in Gradle's cache and reused while the
// installer is the same (network on first use).
val neoForgeRelease = libs.versions.neoforge.get()
val installNeoForgeClient =
    tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.NeoForgeClientInstallTask>("installNeoForgeClient") {
        description = "Installs NeoForge $neoForgeRelease's client with its own installer, for the patched game classes."
        neoForgeVersion = neoForgeRelease
        // The installer Plexus Mod Loader itself checks for 26.2 (plexus-bridge-forge, neoforge-releases.properties).
        installerSha1 = "97205829a020fc9389f504a77f590b54157c8dde"
        javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
        clientDirectory.set(
            layout.dir(provider { gradle.gradleUserHomeDir.resolve("caches/plexus-neoforge/$neoForgeRelease/client") }),
        )
    }
val patchedGame = files(installNeoForgeClient.map {
    it.clientDirectory.file(
        "libraries/net/neoforged/minecraft-client-patched/$neoForgeRelease/minecraft-client-patched-$neoForgeRelease.jar",
    )
}).builtBy(installNeoForgeClient)

// The game jar references JetBrains annotations that are not on the class path; javac warns about every use (classfile
// lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
    // The adapted Fabric API code calls game methods the game marks deprecated and casts generics unchecked, as Fabric
    // API does (it builds without -Werror); its code is kept as it is.
    options.compilerArgs.add("-Xlint:-deprecation")
    options.compilerArgs.add("-Xlint:-unchecked")
}

// What Plexus Mod Loader's Fabric bridge carries (HostModules in plexus-runtime-impl): each jar under the host's key, and an
// index entry <host key>.<mod id> for it. The host key is HostModules.hostKey of the host's name ("NeoForge 26.2.0.93").
val hostKey = "neoforge-$neoForgeRelease"

/**
 * One host module: its mod ID, the source set (src/<sourceSet>), the version its jar carries, whether the Fabric bridge
 * carries it (a module that is not shipped is only compiled), whether it replaces the original even when the original
 * fits NeoForge's patch (one that loads there but does not do what it does under Fabric Loader), and the source sets of
 * the host modules it depends on (listed before it).
 */
data class HostModule(
    val modId: String,
    val sourceSet: String,
    val version: String,
    val shipped: Boolean = true,
    val always: Boolean = false,
    val dependsOn: List<String> = emptyList(),
)

// Fabric API 0.161.0+26.2's module versions these jars stand in for (ADR-0031: same public API as that version).
val hostModules = listOf(
    HostModule("fabric-block-api-v1", "fabricBlockApiV1", "3.1.0+plexus.neoforge.26.2"),
    HostModule("fabric-item-api-v1", "fabricItemApiV1", "14.5.0+plexus.neoforge.26.2"),
    HostModule("fabric-registry-sync-v0", "fabricRegistrySyncV0", "7.1.1+plexus.neoforge.26.2"),
    // The original loads next to NeoForge, but its attribute modifications run before Fabric mods add them, into a map
    // NeoForge's own attributes hide (fabric-object-builder test mod: zombie max health 20.0, not 42.0).
    HostModule(
        "fabric-object-builder-api-v1",
        "fabricObjectBuilderApiV1",
        "24.1.1+plexus.neoforge.26.2",
        always = true,
        dependsOn = listOf("fabricRegistrySyncV0"),
    ),
)

val hostModuleJars = hostModules.map { module ->
    val sourceSet = sourceSets.create(module.sourceSet)
    configurations.named(sourceSet.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
    // The patched game with the module's class tweaker applied, as Fabric's build tools compile a mod (Loom).
    val tweaked = tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.ClassTweakedJarTask>(
        "${module.sourceSet}CompileGame",
    ) {
        description = "The patched game with ${module.modId}'s class tweaker applied, for compiling it."
        inputJar.fileProvider(provider { patchedGame.singleFile })
        dependsOn(installNeoForgeClient)
        classTweakers.from(fileTree("src/${module.sourceSet}/resources") { include("*.classtweaker") })
        outputJar = layout.buildDirectory.file("compile-game/${module.sourceSet}.jar")
    }
    sourceSet.compileClasspath = files(tweaked.flatMap { it.outputJar }) + sourceSet.compileClasspath
    for (dependency in module.dependsOn) {
        sourceSet.compileClasspath += sourceSets.getByName(dependency).output
    }
    module to tasks.register<Jar>("${module.sourceSet}Jar") {
        description = "Packs Plexus Mod Loader's ${module.modId} for NeoForge $neoForgeRelease."
        archiveBaseName = module.modId
        archiveVersion = module.version
        from(sourceSet.output)
    }
}

val hostModuleIndex = tasks.register<WriteProperties>("hostModuleIndex") {
    destinationFile = layout.buildDirectory.file("host-module-index/META-INF/plexusmodloader/host-modules.properties")
    for ((module, jar) in hostModuleJars.filter { it.first.shipped }) {
        property(
            "$hostKey.${module.modId}",
            jar.flatMap { it.archiveFileName.map { name -> "META-INF/plexusmodloader/host-modules/$hostKey/$name" } },
        )
        if (module.always) {
            property("$hostKey.${module.modId}.always", "true")
        }
    }
}
val hostModuleResources = tasks.register<Sync>("hostModuleResources") {
    into(layout.buildDirectory.dir("host-module-resources"))
    for ((module, jar) in hostModuleJars) {
        if (module.shipped) {
            from(jar) { into("META-INF/plexusmodloader/host-modules/$hostKey") }
        } else {
            dependsOn(jar)
        }
    }
    from(hostModuleIndex.flatMap { it.destinationFile }) { into("META-INF/plexusmodloader") }
}
configurations.consumable("hostModuleElements") {
    outgoing.artifact(hostModuleResources)
}
tasks.assemble {
    dependsOn(hostModuleResources)
}
