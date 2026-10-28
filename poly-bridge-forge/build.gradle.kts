plugins {
    id("poly.java-conventions")
}

description = "NeoForge and MinecraftForge bridges: mod metadata analysis and diagnosis; MinecraftForge through FML."

dependencies {
    implementation(project(":poly-bridge-api"))
    implementation(libs.tomlj)
    implementation(libs.xz)
    implementation(libs.gson)
    // At run time ForgeModRequirements uses the copy in Forge's own libraries.
    testImplementation(libs.maven.artifact)
    testImplementation(libs.night.config.core)
    // ForgeTitleBrandingTest plans its rule against Forge's patched client classes.
    testImplementation(project(":poly-transform-engine"))
    testImplementation(libs.asm)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.util)
}

// Poly Mod Loader's implementation of ModLauncher's launch for FML (ADR-0021). These classes run inside the BOOT layer's
// cpw.mods.modlauncher module, defined by the game class loader, so they are compiled separately against ModLauncher's
// API and packed as META-INF/polymodloader/forge-host.jar, never loaded from this jar's own class path.
val forgeHost = sourceSets.create("forgeHost")
dependencies {
    "forgeHostCompileOnly"(sourceSets.main.get().output)
    "forgeHostCompileOnly"(project(":poly-bridge-api"))
    "forgeHostCompileOnly"(libs.forge.modlauncher) { isTransitive = false }
    "forgeHostCompileOnly"(libs.forge.securemodules) { isTransitive = false }
    "forgeHostCompileOnly"(libs.forge.fmlloader) { isTransitive = false }
    "forgeHostCompileOnly"(libs.forge.forgespi) { isTransitive = false }
    "forgeHostCompileOnly"(libs.jopt.simple)
    "forgeHostCompileOnly"(libs.asm)
    "forgeHostCompileOnly"(libs.asm.tree)
    "forgeHostCompileOnly"(libs.maven.artifact)
    // ForeignModFileInfoTest checks the forgespi views of other loaders' mods (ADR-0025) against forgespi itself.
    testImplementation(forgeHost.output)
    testImplementation(libs.forge.forgespi) { isTransitive = false }
    testImplementation(libs.forge.securemodules) { isTransitive = false }
}
val forgeHostJar = tasks.register<Jar>("forgeHostJar") {
    description = "Packs Poly Mod Loader's ModLauncher implementation for FML."
    archiveFileName = "forge-host.jar"
    destinationDirectory = layout.buildDirectory.dir("forge-host")
    from(forgeHost.output)
}
// Poly Mod Loader's host for NeoForge's FML 11 (ADR-0026). Its classes run inside the layer of NeoForge's loader
// libraries, defined by the game class loader, so they are compiled separately against FML's loader and packed as
// META-INF/polymodloader/neoforge-host.jar, never loaded from this jar's own class path.
val neoForgeHost = sourceSets.create("neoForgeHost")
dependencies {
    "neoForgeHostCompileOnly"(sourceSets.main.get().output)
    "neoForgeHostCompileOnly"(project(":poly-bridge-api"))
    "neoForgeHostCompileOnly"(libs.neoforge.fml.loader) { isTransitive = false }
    "neoForgeHostCompileOnly"(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
    "neoForgeHostCompileOnly"(libs.maven.artifact)
    // NeoForgeLoaderPatchTest checks the patch against FML's own FMLLoader, and the patched class against the verifier.
    testImplementation(libs.neoforge.fml.loader) { isTransitive = false }
    testImplementation(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
    // NeoForgeSharedMixinPatchTest verifies FML's patched MixinFacade, whose other methods use Mixin's types.
    testImplementation(libs.sponge.mixin) { isTransitive = false }
    // FmlSharedMixinPlaceholderTest sorts the host's placeholder processors with FML's own topological sort (Guava).
    testImplementation(neoForgeHost.output)
    testImplementation(libs.guava)
}
// NeoForgeSharedMixinPatchTest also checks the patch against FML 12 (NeoForge 26.3), whose classes cannot share the test
// class path with FML 11's; the test reads them from this jar (poly.fml12Loader).
val neoForgeFml12 = configurations.create("neoForgeFml12") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    neoForgeFml12(libs.neoforge.fml12.loader)
}
tasks.test {
    val fml12 = neoForgeFml12.incoming.files
    inputs.files(fml12).withPathSensitivity(PathSensitivity.NAME_ONLY).withPropertyName("neoForgeFml12")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dpoly.fml12Loader=" + fml12.singleFile.absolutePath)
    })
}
val neoForgeHostJar = tasks.register<Jar>("neoForgeHostJar") {
    description = "Packs Poly Mod Loader's host for NeoForge's FML."
    archiveFileName = "neoforge-host.jar"
    destinationDirectory = layout.buildDirectory.dir("neoforge-host")
    from(neoForgeHost.output)
}
tasks.jar {
    from(forgeHostJar) { into("META-INF/polymodloader") }
    from(neoForgeHostJar) { into("META-INF/polymodloader") }
}

// ForgePatchSetTest compares Poly Mod Loader's patching with Forge's installer output when given the installer jar, the
// vanilla server classes (server-<mc>-unpacked.jar) and Forge's patched server jar (forge-<version>-server.jar).
// NeoForgePatchBundleTest does the same with NeoForge's installer, the vanilla client jar or server classes and the jar
// its installer made (minecraft-client-patched-<version>.jar, minecraft-server-patched-<version>.jar; neoForgeSide).
// ForgeTitleBrandingTest checks its rule against Forge's patched client (forge-<version>-client.jar from a Forge client
// run's .poly/forge/, or forgeClientInstaller + forgeVanillaClient to patch it) and Forge's universal jar.
// NeoForgeTitleBrandingTest does the same with the client jar NeoForge's installer patched (neoForgePatchedClient) and
// NeoForge's universal jar (neoForgeUniversal).
tasks.test {
    for (name in listOf(
        "forgeInstaller", "forgeVanillaServer", "forgePatchedServer",
        "forgePatchedClient", "forgeClientInstaller", "forgeVanillaClient", "forgeUniversal",
        "neoForgeInstaller", "neoForgeVanillaJar", "neoForgePatchedJar", "neoForgeSide",
        "neoForgePatchedClient", "neoForgeUniversal",
    )) {
        providers.gradleProperty(name).orNull?.let { systemProperty("poly.$name", it) }
    }
}
