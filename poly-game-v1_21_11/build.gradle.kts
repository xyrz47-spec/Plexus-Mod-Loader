plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Game layer for Minecraft 1.21.11: implements the Native API with game classes; loaded by the game class loader."

dependencies {
    implementation(project(":poly-version-adapter-api"))
    implementation(libs.slf4j.api)
    // BuiltInRulesTest plans and applies the adapter's rules to the real game classes on the test class
    // path, so client-only rules are checked without starting a client.
    testImplementation(project(":poly-version-adapter-v1_21_11"))
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
