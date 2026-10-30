// Shared configuration lives in build-logic convention plugins (poly.java-conventions, poly.minecraft-dev).
// The root project lists the supported Minecraft versions and adds new ones (ADR-0011), starts new
// mod projects from templates/poly-mod-template (newPolyMod), publishes the mod SDK for the website's
// Maven repository (publishSdk, ADR-0008), and builds the test mods for the version a build runs (below).
plugins {
    id("poly.minecraft-versions")
    id("poly.mod-template")
    id("poly.sdk-repository")
}

// The test mods and the transform example are written for Minecraft 26.2 and name it in their metadata
// (fabric.mod.json "~26.2", poly.mod.json "26.2" for exact transform targets, quilt.mod.json "26.2.x"). They are built for the
// version this build runs (-PpolyMinecraft, ADR-0011), so the verification tasks check every supported
// version with the same mods; the game members they use were checked with javap in each one. With the
// default version nothing changes.
val runMinecraft = dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.of(project).selected(project)
val writtenFor = Regex("""(?<![\d.])26\.2(?![\d.])""")
// The examples that use only the Native API declare "26.*". A run of an experimental version (ADR-0019, such as
// 1.21.11) adds that version to their range in the built jar, so the same mods check its game layer; the
// sources and the published examples keep "26.*", and supported versions build them unchanged.
val runsExperimental = dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.of(project).isExperimental(runMinecraft)
val apiOnlyExamples = setOf(":examples:minimal-native-mod", ":examples:custom-system-mod", ":examples:native-showcase-mod")
subprojects {
    if (runsExperimental && path in apiOnlyExamples) {
        plugins.withId("java") {
            tasks.named<ProcessResources>("processResources") {
                inputs.property("polyMinecraft", runMinecraft)
                filesMatching("poly.mod.json") {
                    filter { line -> line.replace("\"26.*\"", "\"26.*\", \"$runMinecraft\"") }
                }
            }
        }
    }
    if (path.startsWith(":test-mods:") || path == ":examples:transform-demo-mod") {
        plugins.withId("java") {
            tasks.named<ProcessResources>("processResources") {
                inputs.property("polyMinecraft", runMinecraft)
                filesMatching(listOf("fabric.mod.json", "poly.mod.json")) {
                    filter { line -> line.replace(writtenFor, runMinecraft) }
                }
                // quilt.mod.json names the version Quilt's way, "26.2.x" (26.2 and its hotfixes); a run of a version
                // outside that range names exactly that version.
                if (runMinecraft != "26.2" && !runMinecraft.startsWith("26.2.")) {
                    filesMatching("quilt.mod.json") {
                        filter { line -> line.replace("\"26.2.x\"", "\"=$runMinecraft\"") }
                    }
                }
            }
        }
    }
}
