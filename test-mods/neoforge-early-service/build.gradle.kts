plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "NeoForge mod laid out as Sodium's NeoForge build: an FML library jar with an early service, the mod itself nested in it."

dependencies {
    // NeoForge's API comes from NeoForge's own files at run time (ADR-0026); the jar holds only the mod.
    compileOnly(variantOf(libs.neoforge.universal) { classifier("universal") }) { isTransitive = false }
    compileOnly(libs.neoforge.fml.loader) { isTransitive = false }
    compileOnly(libs.neoforge.bus) { isTransitive = false }
    compileOnly(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
}

// The early service (an FML GraphicsBootstrapper), compiled apart from the mod: it goes into the outer jar.
val service = sourceSets.create("service")
dependencies {
    "serviceCompileOnly"(libs.neoforge.fml.loader) { isTransitive = false }
}

// The mod itself, nested in the outer jar as NeoForge's JarJar does (META-INF/jarjar/ and its metadata.json).
val modJar = tasks.register<Jar>("modJar") {
    archiveFileName = "com.example.neoforge-early-service-mod.jar"
    destinationDirectory = layout.buildDirectory.dir("nested")
    from(sourceSets.main.get().output)
}
// The jar players install: FMLModType LIBRARY, the early service, the same neoforge.mods.toml as the nested mod (as
// Sodium's has), and the nested mod jar.
tasks.jar {
    manifest {
        attributes("FMLModType" to "LIBRARY", "Automatic-Module-Name" to "neoforge_early_service_service")
    }
    exclude("com/example/neoforgeearly/NeoForgeEarlyService*.class")
    from(service.output)
    from(modJar) { into("META-INF/jarjar") }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
