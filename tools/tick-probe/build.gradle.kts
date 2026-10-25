// A Java agent that times one tick method, for the tickBenchmark task (design doc §14.4):
//   java -javaagent:tick-probe.jar=out=<file>,target=<owner>.<method><descriptor> ...
// It is attached the same way to vanilla and to Poly Mod Loader runs, so both pay the same cost.
// A measurement tool only; not published and not part of Poly Mod Loader.
plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "dev.polymodloader.tools.tickprobe.TickProbeAgent")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
