// Generates the API reference pages of the docs site from the public API's Javadoc:
//   ./gradlew apiReference   ->   site/docs/api-reference/<package>.md (English, the site's default)
//                                 site/i18n/ko/docusaurus-plugin-content-docs/current/api-reference/ (Korean)
// The Javadoc text is English in both; only the surrounding words differ.
// A build tool only; not published and not part of Poly Mod Loader.
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

// The modules mods compile against.
val apiModules = listOf("poly-runtime-api", "poly-system-api", "poly-event-api", "poly-game-api", "poly-lifecycle-api")

tasks.register<JavaExec>("apiReference") {
    group = "documentation"
    description = "Writes site/docs/api-reference/ from the public API's Javadoc."
    val site = rootProject.layout.projectDirectory.dir("site")
    val localeOutputs = mapOf(
        "en" to site.dir("docs/api-reference"),
        "ko" to site.dir("i18n/ko/docusaurus-plugin-content-docs/current/api-reference"),
    )
    val sources = apiModules.map { rootProject.layout.projectDirectory.dir("$it/src/main/java") }
    inputs.files(sources)
    localeOutputs.values.forEach { outputs.dir(it) }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.polymodloader.tools.apireference.ApiReferenceMain"
    args(localeOutputs.map { (locale, dir) -> "$locale=${dir.asFile.path}" } + sources.map { it.asFile.path })
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

// The release that added each API element (ApiSinceMain): reads the API modules at every release tag (v*) and writes
// api-since.json, which apiReference shows as "Since". Run it after tagging a release, then apiReference.
val apiSinceFile = layout.projectDirectory.file("api-since.json")
tasks.register<JavaExec>("apiSince") {
    group = "documentation"
    description = "Writes tools/api-reference/api-since.json: the release that added each public API element."
    outputs.file(apiSinceFile)
    outputs.upToDateWhen { false }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.polymodloader.tools.apireference.ApiSinceMain"
    args(listOf(rootProject.layout.projectDirectory.asFile.path, apiSinceFile.asFile.path) + apiModules)
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}
tasks.named<JavaExec>("apiReference") {
    inputs.file(apiSinceFile).optional()
    systemProperty("poly.apiReference.since", apiSinceFile.asFile.path)
}
