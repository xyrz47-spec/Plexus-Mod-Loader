plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Fabric build of a mod that neoforge-twin builds for NeoForge under another ID (twin-mod, twin_mod) with the same shared class."

sourceSets.main {
    // The class both builds have, as multi-loader mods share their common code.
    java.srcDir("src/shared/java")
}

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
