plugins {
    id("poly.java-conventions")
}

description = "Quilt mods Poly Mod Loader leaves out or runs only in part: a loader plugin, a quilt.mod.json Quilt Loader refuses, a client-only mod, a mod with only QSL's init entrypoint."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}

// The project's own jar is the mod with only QSL's init entrypoint (quilt_init_only, loaded, POLY-COMPAT-303).
tasks.jar {
    archiveBaseName.set("quilt-init-only")
}

// Three more mod jars, each only a quilt.mod.json from src/jars/<name>/; they are artifacts of the same variant, so a
// project dependency on this project gets all four jars.
for (name in listOf("plugin", "malformed", "client-only")) {
    val jar = tasks.register<Jar>("${name.replace("-", "")}Jar") {
        archiveBaseName.set("quilt-$name")
        from(layout.projectDirectory.dir("src/jars/$name"))
    }
    artifacts.add("runtimeElements", jar)
}
