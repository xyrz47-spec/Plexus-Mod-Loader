pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") { name = "FabricMC" } }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" } }
            filter { includeGroup("net.minecraftforge") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.neoforged.net/releases/") { name = "NeoForged" } }
            filter { includeGroupAndSubgroups("net.neoforged") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.quiltmc.org/repository/release/") { name = "QuiltMC" } }
            filter { includeGroup("org.quiltmc") }
        }
    }
}

rootProject.name = "plexus"

// One version adapter and one game layer per supported Minecraft version (ADR-0011).
val minecraftVersions = java.util.Properties().apply {
    file("gradle/minecraft-versions.properties").inputStream().use(::load)
}
// Experimental versions (ADR-0019) have modules too; the build compiles and tests them, but they are not supported.
val versionsWithModules = listOf("supported", "experimental").flatMap { key ->
    minecraftVersions.getProperty(key, "").split(",").map(String::trim).filter(String::isNotEmpty)
}
for (version in versionsWithModules) {
    val id = version.replace('.', '_').replace('-', '_')
    for (module in listOf("plexus-version-adapter-v$id", "plexus-game-v$id")) {
        require(file(module).isDirectory) {
            "Minecraft $version is listed in gradle/minecraft-versions.properties but $module/ is missing; " +
                "create it with ./gradlew newMinecraftVersion -Pfrom=<version> -Pto=$version"
        }
        include(module)
    }
}

// The public source repository leaves out examples/, test-mods/ and tools/ (.github/scripts/export-public-repo.py).
// Without a folder its projects are not included: the loader, the installer and the unit tests still build, and the
// game test and benchmark tasks that need them do not run.
fun includeIfPresent(vararg paths: String) {
    include(*paths.filter { file(it.substringBefore(':')).isDirectory }.toTypedArray())
}

includeIfPresent(
    "plexus-runtime-api",
    "plexus-lifecycle-api",
    "plexus-event-api",
    "plexus-game-api",
    "plexus-system-api",
    "plexus-system-registry",
    "plexus-mod-metadata",
    "plexus-transform-api",
    "plexus-transform-engine",
    "plexus-version-adapter-api",
    "plexus-early-window",
    "plexus-runtime-impl",
    "plexus-bridge-api",
    "plexus-bridge-fabric",
    "plexus-bridge-forge",
    // Fabric API modules implemented on NeoForge 26.2 (ADR-0031).
    "plexus-fabric-api-neoforge-v26_2",
    "plexus-fabric-api-neoforge-v26_3",
    "plexus-bootstrap",
    "plexus-installer",
    "examples:minimal-native-mod",
    "examples:custom-system-mod",
    "examples:transform-demo-mod",
    "examples:native-showcase-mod",
    "test-mods:fabric-entrypoint-only",
    "test-mods:neoforge-blocked",
    "test-mods:transform-conflict",
    // Cross-loader dependency and robustness scenarios (compatibilityMatrix).
    "test-mods:native-library",
    "test-mods:native-addon",
    "test-mods:fabric-library",
    "test-mods:fabric-addon",
    "test-mods:fabric-wrong-version",
    "test-mods:fabric-chain",
    "test-mods:fabric-stale",
    "test-mods:fabric-loader-user",
    "test-mods:fabric-malformed",
    "test-mods:fml-library",
    "test-mods:forge-hello",
    "test-mods:neoforge-hello",
    "test-mods:neoforge-mixin",
    "test-mods:fabric-registry",
    "test-mods:multi-loader-fallback",
    "test-mods:fabric-twin",
    "test-mods:neoforge-twin",
    "test-mods:fabric-twin-user",
    "test-mods:fabric-client-bootstrap-probe",
    "test-mods:fabric-network-probe",
    "test-mods:neoforge-network-probe",
    "test-mods:neoforge-gametest-harness",
    "test-mods:neoforge-unmet",
    "test-mods:neoforge-client-session",
    "test-mods:neoforge-early-service",
    "test-mods:neoforge-environment-probe",
    "test-mods:forge-at",
    "test-mods:forge-mixin",
    // Jar-in-jar scenario (nestedJars).
    "test-mods:fabric-bundle",
    "test-mods:fabric-nested-lib",
    "test-mods:fabric-nested-deep",
    "test-mods:fabric-nested-mixin",
    // Fabric Loader API scenario (fabricLoaderApi).
    "test-mods:fabric-api-user",
    "test-mods:fabric-launcher-user",
    "test-mods:fabric-adapter-provider",
    "test-mods:fabric-adapter-user",
    // Fabric class tweaker scenario (fabricClassTweaker).
    "test-mods:fabric-class-tweaker",
    "test-mods:fabric-class-tweaker-stale",
    // Mixin scenario (fabricMixin, ADR-0020).
    "test-mods:fabric-mixin",
    "test-mods:fabric-mixin-broken",
    "test-mods:fabric-client-session",
    // Quilt mods (ADR-0027 Q3: quiltOnly, quiltFabricPlexus, quiltBlocked).
    "test-mods:quilt-hello",
    "test-mods:quilt-nested-lib",
    "test-mods:quilt-nested-unused",
    "test-mods:fabric-quilt-dual",
    "test-mods:quilt-qsl-resource",
    "test-mods:quilt-blocked",
    // Harness glue for headless corpus runs on Mojang's game test server (compatibilityCorpus).
    "test-mods:fabric-gametest-harness",
    // Applies every installed mod's mixins for one side and stops before the game (mixinAuditQuilt, diagnosis).
    "test-mods:fabric-mixin-audit",
    // Fabric API features whose mixins meet members MinecraftForge's patch added or changed (corpusFabricForge).
    "test-mods:fabric-host-patch-probe",
    // Real Fabric API's events and data under MinecraftForge's patch (forgeFabricApiGameTest, ADR-0021 G8).
    "test-mods:fabric-api-events",
    "test-mods:fabric-dynamic-registry",
    "test-mods:fabric-object-builder",
    "test-mods:fabric-block-item",
    "tools:api-reference",
    "tools:tick-probe",
)
