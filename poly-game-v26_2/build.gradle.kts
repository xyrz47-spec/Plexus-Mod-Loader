plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Game layer for Minecraft 26.2: implements the Native API with game classes; loaded by the game class loader."

dependencies {
    implementation(project(":poly-version-adapter-api"))
    // ForeignPayloads: a host loader's view of its own custom payloads (ADR-0026 40).
    implementation(project(":poly-bridge-api"))
    implementation(libs.slf4j.api)
    // BuiltInRulesTest plans and applies the adapter's rules to the real game classes on the test class
    // path, so client-only rules are checked without starting a client.
    testImplementation(project(":poly-version-adapter-v26_2"))
    testImplementation(project(":poly-transform-engine"))
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.util)
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}

// Game classes log to logs/ under the working directory; keep that out of the source tree.
tasks.withType<Test>().configureEach {
    val testRun = layout.buildDirectory.dir("test-run")
    doFirst { testRun.get().asFile.mkdirs() }
    workingDir = testRun.get().asFile
}

// BuiltInRulesTest also lists the built-in rules NeoForge's patch turns off, given the game jars NeoForge's installer
// patched (-PneoForgePatchedClient=<minecraft-client-patched-<version>.jar>, -PneoForgePatchedServer=<...server...>).
tasks.test {
    for (name in listOf("neoForgePatchedClient", "neoForgePatchedServer")) {
        providers.gradleProperty(name).orNull?.let { systemProperty("poly.$name", it) }
    }
}
