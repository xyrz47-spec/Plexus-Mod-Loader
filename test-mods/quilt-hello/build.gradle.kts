plugins {
    id("poly.java-conventions")
}

description = "Quilt mod with only quilt.mod.json that uses Quilt Loader's API, Mixin, access wideners and nested jars."

dependencies {
    // Fabric Loader first: Quilt Loader's jar carries its own copies of Fabric Loader's classes.
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.quilt.loader) { isTransitive = false }
    // Mixin comes from Poly Mod Loader's class path at run time, as from Quilt Loader's.
    compileOnly(libs.sponge.mixin) { isTransitive = false }
    compileOnly(libs.slf4j.api)
    compileOnly(project(":test-mods:quilt-nested-lib"))
}

// Jar-in-jar, as Quilt's Loom does it: the nested mods' jars go into META-INF/jars/.
val nestedJars = configurations.create("nestedJars") {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
dependencies {
    nestedJars(project(":test-mods:fabric-nested-lib"))
    nestedJars(project(":test-mods:quilt-nested-lib"))
    nestedJars(project(":test-mods:quilt-nested-unused"))
}
tasks.jar {
    from(nestedJars) {
        into("META-INF/jars")
        rename { name -> name.replace("-${project.version}", "") }
    }
}
