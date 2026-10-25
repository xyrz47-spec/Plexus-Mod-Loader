plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that unpacks a jar it carries and adds it with Fabric Loader's internal FabricLauncher, as CustomSkinLoader does."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}

// The jar the mod unpacks: its own classes, carried as a plain resource (not a Fabric nested jar).
val extraLib = sourceSets.create("extra")
val extraJar = tasks.register<Jar>("extraJar") {
    archiveFileName = "extra-lib.jar"
    destinationDirectory = layout.buildDirectory.dir("extra")
    from(extraLib.output)
}
tasks.jar {
    from(extraJar) { into("launcher") }
}
