plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Fabric mod that uses real Fabric API events and data (block breaking, resource conditions, crafting remainders) on the game test server."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}

// Stand-ins for the Fabric API classes the mod uses; never packaged. Fabric API itself comes from the mod corpus at run
// time, as from a player's mods folder. The stubs see the game classes too.
val stubs = sourceSets.create("stubs")
configurations.named(stubs.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}
sourceSets.main {
    compileClasspath += stubs.output
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
