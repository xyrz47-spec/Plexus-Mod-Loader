plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that ships its libraries as nested jars (jar-in-jar)."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
    compileOnly(project(":test-mods:fabric-nested-lib"))
}

// Jar-in-jar, as Fabric Loom does it: the nested mods' jars go into META-INF/jars/.
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
    nestedJars(project(":test-mods:fabric-nested-mixin"))
}
tasks.jar {
    from(nestedJars) {
        into("META-INF/jars")
        rename { name -> name.replace("-${project.version}", "") }
    }
}
