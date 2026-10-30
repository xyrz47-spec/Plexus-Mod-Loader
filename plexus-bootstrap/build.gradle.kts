import java.util.concurrent.Callable
import java.util.zip.GZIPInputStream

plugins {
    id("poly.java-conventions")
    id("poly.minecraft-dev")
}

description = "Poly Mod Loader launcher entry point and Minecraft development run tasks."

// Jar names of the example and test mods in expected log lines.
val testModVersion = project.version.toString()

// Runs and checks use one Minecraft version: the default, or -PpolyMinecraft=<version> (ADR-0011).
val minecraft = extra["polyMinecraftVersion"] as String
val minecraftVersions = dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.of(project)
// A hotfix (hotfixes.<version>) runs on the modules, adapter and game layer of the version it is listed under.
val minecraftModules = minecraftVersions.modulesVersion(minecraft)
val gameLayer = dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.gameLayerClass(minecraftModules)
// The line showing that a stopping server saved every world. The game logged one up to 26.2; 26.3 logs none, so
// there the check reads Poly Mod Loader's line from the end of MinecraftServer.stopServer (LifecycleHooks).
val worldsSavedLine =
    if (minecraftModules == "26.1" || minecraftModules == "26.2" || minecraftModules.startsWith("1.")) {
        "ThreadedAnvilChunkStorage: All dimensions are saved"
    } else {
        "[Poly Mod Loader] Server stopped after saving and closing its worlds"
    }

dependencies {
    implementation(project(":plexus-runtime-impl"))
    // The launcher carries every supported version's adapter and picks one by the game jar's version.
    for (version in minecraftVersions.supported) {
        runtimeOnly(project(":" + dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.adapterModule(version)))
    }
    // An experimental version (ADR-0019) only when this build runs it, so the installer never carries it.
    if (minecraftVersions.isExperimental(minecraftModules)) {
        runtimeOnly(project(":" + dev.plexusmodloader.devtools.gradle.minecraft.MinecraftVersions.adapterModule(minecraftModules)))
    }
    runtimeOnly(project(":plexus-bridge-fabric"))
    runtimeOnly(project(":plexus-bridge-forge"))
    implementation(libs.slf4j.api)

    // runClient's mods; the public source has no examples/ or test-mods/ (settings.gradle.kts).
    for (path in listOf(
        ":examples:minimal-native-mod",
        ":examples:custom-system-mod",
        ":examples:transform-demo-mod",
        ":examples:native-showcase-mod",
        ":test-mods:fabric-entrypoint-only",
    )) {
        if (findProject(path) != null) {
            polyDevMod(project(path))
        }
    }
}

// Poly Mod Loader's own Java agent (ADR-0024): the launcher profile starts it with -javaagent:<this jar>. It only keeps
// the Instrumentation, to put Forge's file systems on the JVM class path when Forge mods are present.
tasks.jar {
    manifest {
        attributes("Premain-Class" to "dev.plexusmodloader.bootstrap.SystemClassPathAgent")
    }
}

// The tick probe agent that tickBenchmark attaches to both vanilla and Poly Mod Loader runs.
val tickProbe = configurations.create("tickProbe") {
    isCanBeConsumed = false
    isTransitive = false
}
// The public source has no tools/ (settings.gradle.kts); the benchmarks then have no probe agent to attach.
if (findProject(":tools:tick-probe") != null) {
    dependencies {
        tickProbe(project(":tools:tick-probe"))
    }
}
tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.TickBenchmarkTask>("tickBenchmark") {
    probeAgent.from(tickProbe)
    // -PtickBenchmarkRuns=<n> and -PtickBenchmarkTicks=<n> shorten or lengthen a measurement;
    // -PtickBenchmarkHeap=<size> (such as 2g) gives both variants the same fixed heap.
    providers.gradleProperty("tickBenchmarkRuns").orNull?.let { runs = it.toInt() }
    providers.gradleProperty("tickBenchmarkTicks").orNull?.let { holdTicks = it.toInt() }
    providers.gradleProperty("tickBenchmarkHeap").orNull?.let { heapSize = it }
}
// Profiling, for every tick benchmark: -PtickBenchmarkJfrSettings=<default|profile|file.jfc> sets the recording's
// settings, and -PtickBenchmarkTickEvents=true makes the tick probe commit each tick as a JFR event. Every variant of a
// run gets the same setting; the JSON then says it was a profiling run.
tasks.withType<dev.plexusmodloader.devtools.gradle.minecraft.TickBenchmarkTask>().configureEach {
    providers.gradleProperty("tickBenchmarkJfrSettings").orNull?.let { jfrSettings = it }
    providers.gradleProperty("tickBenchmarkTickEvents").orNull?.let { tickEvents = it.toBoolean() }
}
tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.FrameBenchmarkTask>("frameBenchmark") {
    probeAgent.from(tickProbe)
    // -PframeBenchmarkRuns=<n> and -PframeBenchmarkSeconds=<n> shorten or lengthen a measurement;
    // -PframeBenchmarkCooldown=<seconds> sets the pause between two client windows (default 20).
    providers.gradleProperty("frameBenchmarkRuns").orNull?.let { runs = it.toInt() }
    providers.gradleProperty("frameBenchmarkSeconds").orNull?.let { measureSeconds = it.toInt() }
    providers.gradleProperty("frameBenchmarkCooldown").orNull?.let { cooldownSeconds = it.toInt() }
}

tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.VerifiedGameRunTask>("serverBootSmokeTest") {
    expectedOutput.addAll(
        "[Poly Mod Loader] Phase BOOTSTRAP",
        "[Poly Mod Loader] Minecraft $minecraft (DEDICATED_SERVER) via adapter minecraft-$minecraftModules",
        "[Poly Mod Loader] Phase DISCOVERY: 5 mod file(s)",
        "[Poly Mod Loader] Fabric mod fabric_hello 1.0.0 (fabric-entrypoint-only-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
        "[fabric_hello] Fabric main entrypoint ran (loaded by poly-game)",
        "[Poly Mod Loader] Loaded mod custom_system_mod 0.1.0",
        "[Poly Mod Loader] Loaded mod minimal_native_mod 0.1.0",
        "[Poly Mod Loader] Phase CONFLICT_RESOLUTION: 1 system(s), 1 replacement(s)",
        "[Poly Mod Loader] System plexus:game_brand: com.example.poly.brand.ExampleBrandProvider from custom_system_mod",
        "[Poly Mod Loader] Startup report:",
        "[Poly Mod Loader] Transformation plan: 52 rule(s) accepted, 0 rejected (2 from mods)",
        "[Poly Mod Loader] Access widenings: 1 accepted, 0 rejected",
        "[Poly Mod Loader] Native declarations: 7 block(s), 10 item(s), 3 entity type(s), 1 effect(s), 2 game rule(s), 1 sound(s), 1 command(s), 1 channel(s), 62 game test(s)",
        "[Poly Mod Loader] Game layer $gameLayer started",
        "[minimal_native_mod] Hello from minimal_native_mod 0.1.0 on DEDICATED_SERVER",
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.server.Main",
        "You need to agree to the EULA in order to run the server",
        "[Poly Mod Loader] Phase SHUTDOWN: 2 task(s)",
        "[minimal_native_mod] Shutdown task ran",
    )
}

tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.VerifiedGameRunTask>("worldCreateSaveTest") {
    expectedOutput.addAll(
        "[Poly Mod Loader] Minecraft $minecraft (DEDICATED_SERVER) via adapter minecraft-$minecraftModules, launch target game_test_server",
        "[Poly Mod Loader] Loaded mod minimal_native_mod 0.1.0",
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.gametest.Main",
        "[Poly Mod Loader] Access widenings: 1 accepted, 0 rejected",
        // A Fabric mod is installed (fabric_hello), so call redirects come after the Fabric stage (ADR-0020).
        "[Poly Mod Loader] Transformed net/minecraft/server/MinecraftServer with [poly:server_brand, poly:server_tick_events, poly:server_running, poly:server_stopping_event, poly:world_unloaded, poly:server_stopped, poly:worldgen_features, poly:world_loaded, access widening by transform_demo_mod]",
        "[Poly Mod Loader] Transformed net/minecraft/server/MinecraftServer with [transform_demo_mod:tick_start_nanos]",
        "[Poly Mod Loader] System plexus:game_brand: com.example.poly.brand.ExampleBrandProvider from custom_system_mod",
        "[Poly Mod Loader] Transformed net/minecraft/world/level/storage/LevelStorageSource with [transform_demo_mod:storage_format_name]",
        "[transform_demo_mod] tickServer start time comes from the mod hook; MinecraftServer.tickCount is public: true",
        "[minimal_native_mod] Server started (dedicated=false, tick=0, players=0)",
        "[native_showcase] Overworld ready, load count 1",
        "All 1 required tests passed",
        "[minimal_native_mod] Server stopping after",
        worldsSavedLine,
        "[minimal_native_mod] Shutdown task ran",
    )
    expectedFiles.addAll(".poly/startup-report.json", ".poly/transformation-report.json")
    if (minecraft.startsWith("1.")) {
        // 1.x worlds (an experimental version, ADR-0019) keep the world generation settings in level.dat and the
        // overworld's region files at the world's top level.
        expectedFiles.addAll("universe/gametestworld/level.dat", "universe/gametestworld/region")
    } else {
        expectedFiles.addAll(
            "universe/gametestworld/data/minecraft/world_gen_settings.dat",
            "universe/gametestworld/dimensions/minecraft/overworld/region",
        )
    }
}

tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.ClientWorldReloadTask>("clientWorldReloadTest") {
    // The showcase leaves the world after its demo, so leaving is checked too.
    systemProperties.add("native_showcase.leaveAfterDemo=true")
    // Poly Mod Loader's early loading window (no host loader here): opened, drawn, closed when the game's window shows.
    systemProperties.add("poly.earlyWindow.snapshots=.poly")
    expectedOutput.addAll(
        "[Poly Mod Loader] Early loading window opened",
        "[Poly Mod Loader] Early loading window closed",
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.client.main.Main",
        "[minimal_native_mod] Client entrypoint called",
        "[Poly Mod Loader] plexus:game_brand reported 'poly/example' to Minecraft (CLIENT)",
        "[fabric_hello] Fabric client entrypoint ran",
        "[minimal_native_mod] Server started (dedicated=false",
        "PolyDev joined the game",
        "[Poly Mod Loader] Added 1 mod key binding(s) to the controls",
        "[Poly Mod Loader] Registered 7 block(s) and 10 item(s) from mods",
        "[Poly Mod Loader] Answered configuration exchange native_showcase:handshake",
        "[native_showcase] Handshake with PolyDev: showcase 0.1.0",
        "[Poly Mod Loader] Configuration exchange native_showcase:handshake with PolyDev: answered, accepted",
        "[native_showcase] Player joined: PolyDev",
        "[native_showcase] Client received greeting: Welcome, PolyDev",
        "[native_showcase] Server received 'thanks' from PolyDev",
        "[Poly Mod Loader] Drawing 1 mod HUD layer(s)",
        "[native_showcase] HUD layer drew on a",
        "[native_showcase] Overworld ready, load count 2",
        "[Poly Mod Loader] Added 4 mod entity renderer(s)",
        "[native_showcase] Spawned a golem friend next to PolyDev",
        "[native_showcase] Pocket dimension loaded: true",
        "[native_showcase] Terraces at 0 65 0: native_showcase:glow_block in native_showcase:glow_meadow",
        "[native_showcase] Waves terrain follows the density function: true, surface minecraft:grass_block",
        "[native_showcase] Screen opened at",
        "[native_showcase] Screen rendered",
        "[native_showcase] Screen closed, name field was 'PolyDev'",
        "[native_showcase] Config file present: true",
        "[native_showcase] Grinder menu opened: true",
        "[native_showcase] PolyDev rides the golem: true",
        "[native_showcase] Client sees the player riding native_showcase:golem_friend",
        "[Poly Mod Loader] Added 1 mod particle provider(s)",
        "[Poly Mod Loader] Added 1 mod block entity renderer(s)",
        "[native_showcase] Dynamic texture uploaded: native_showcase:dynamic/noise",
        "[native_showcase] Field of view event received",
        "[Poly Mod Loader] Drew block entity renderer of native_showcase:grinder",
        "[Poly Mod Loader] World painter native_showcase:compass drew 3 shape(s) and 2 quad batch(es)",
        "[Poly Mod Loader] Built mod shader pipeline native_showcase:glow (native_showcase:core/glow)",
        "[Poly Mod Loader] Built mod shader pipeline native_showcase:pulse (native_showcase:core/pulse) with 1 texture(s) and 2 uniform(s)",
        "[Poly Mod Loader] Uploaded ModUniforms of mod shader pipeline native_showcase:pulse",
        "[Poly Mod Loader] Bound ModUniforms of mod shader pipeline native_showcase:pulse (2 field(s), 32 bytes)",
        "[native_showcase] Grinder draws lamp state native_showcase:lamp[color=blue, lit=true]",
        "[Poly Mod Loader] Drew mod mob model native_showcase:wisp",
        "[Poly Mod Loader] Played mod mob animation wave of native_showcase:wisp",
        "[Poly Mod Loader] Created mod particle native_showcase:sparkle",
        "[native_showcase] Camera perspective: THIRD_PERSON_BACK",
        "[native_showcase] Post effect sepia loaded: true, active: native_showcase:sepia",
        "[native_showcase] Post effect cleared: true",
        "[native_showcase] Loaded 2 showcase message(s)",
        "[native_showcase] Loaded 1 client tip(s)",
        "[native_showcase] Client joined a world as PolyDev",
        "[native_showcase] Client sees visits:",
        "[native_showcase] Client sees world info: Welcome to the showcase",
        "[native_showcase] Client left the world",
        "[native_showcase] Loaded 1 banner file(s)",
        "[native_showcase] Client sees golem mood: friendly",
        "[Poly Mod Loader] Opened mod menu native_showcase:grinder with 2 slot(s)",
        "[native_showcase] Closing the grinder menu (screen open: true)",
        // Leaving the world opens the title screen, which shows the mod counts per loader above the version text
        // (options.txt names no language, so English).
        "[Poly Mod Loader] Loaded mods per loader: 4 Poly, 1 Fabric, 0 Quilt, 0 Forge",
        "[Poly Mod Loader] Title screen: 2 line(s) of mod counts drawn above the version text: Poly mods: 4 | Fabric mods: 1",
    )
}

// End-user installation: the installer jar installs a launcher profile into a fresh launcher folder, and the
// profile is started the way the Minecraft Launcher starts it, with mods in mods/ (opens a window).
val installerJarFiles = configurations.create("installerJar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    installerJarFiles(project(mapOf("path" to ":plexus-installer", "configuration" to "installerJarElements")))
}
// One installer launch scenario: every launcher profile task wires the installer jar, the client launch spec, the JDK
// and the saved world of worldCreateSaveTest the same way; only the mods, the world name, the run folder and the
// expected lines differ.
fun launcherProfile(name: String, profileDirectory: String, configure: dev.plexusmodloader.devtools.gradle.minecraft.LauncherProfileLaunchTask.() -> Unit) {
    tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.LauncherProfileLaunchTask>(name) {
        group = "verification"
        val worldCreateSave = tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.VerifiedGameRunTask>("worldCreateSaveTest")
        dependsOn(worldCreateSave)
        installerJar.set(layout.file(installerJarFiles.elements.map { it.single().asFile }))
        launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>("prepareMinecraftClient")
            .flatMap { it.launchSpecFile })
        javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
        sourceWorld.set(worldCreateSave.flatMap { it.runDirectory.dir("universe/gametestworld") })
        runDirectory.set(layout.buildDirectory.dir(profileDirectory))
        configure()
    }
}

launcherProfile("launcherProfileTest", "poly/launcher-profile-$minecraft/minecraft") {
    description = "Installs Poly Mod Loader with the installer jar and starts the profile like the Minecraft Launcher (opens a window)."
    // fabric-mixin: the installed profile must carry Mixin and MixinExtras, which only Fabric mods need (ADR-0020).
    // quilt-hello: a Quilt mod gets Quilt Loader's API, its Mixin and access wideners from the installed profile too
    // (Quilt full support Q8).
    modJars.from(modJars(
        ":examples:minimal-native-mod", ":examples:native-showcase-mod", ":test-mods:fabric-entrypoint-only",
        ":test-mods:fabric-mixin", ":test-mods:quilt-hello"))
    worldName.set("poly_profile")
    timeoutSeconds.set(180)
    expectedOutput.addAll(
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.client.main.Main",
        "[minimal_native_mod] Client entrypoint called",
        "[fabric_hello] Fabric client entrypoint ran",
        "[fabric_mixin] client createTitle @Inject ran",
        "[fabric_mixin] getGameInstance() is this client: true",
        "[quilt_hello] client entrypoint ran",
        "[quilt_hello] entrypoint order: pre_launch -> preLaunch -> main",
        "[quilt_hello] MinecraftQuiltLoader.getEnvironmentType() = CLIENT, QuiltLoader.getRawGameVersion() = $minecraft",
        "[quilt_hello] access wideners: SharedConstants.prefixDebugFlagName public = true, Mth.SIN public = true",
        "[quilt_hello] nested quilt_nested_lib is ready",
        // The integrated server's tick, once the world is open.
        "[quilt_hello] Quilt mod's Mixin: tickServer @Inject ran",
        "[Poly Mod Loader] Configuration exchange native_showcase:handshake with PolyDev: answered, accepted",
        "[native_showcase] HUD layer drew on a",
        "PolyDev joined the game",
    )
    // Without Forge mods nothing of Forge is downloaded (ADR-0024).
    absentPaths.add(".poly/forge")
}

// Mixed-loader matrix (design doc §15.4) and regression scenarios, each on a fresh game test server.
fun modJars(vararg projects: String): FileCollection {
    // The public source has no examples/ or test-mods/ (settings.gradle.kts): a task that needs them fails when it runs,
    // not while the build is configured.
    val missing = projects.filter { findProject(it) == null }
    if (missing.isNotEmpty()) {
        return files(provider<List<File>> {
            throw GradleException("This task needs the test mods ${missing.joinToString()}, which are not in this source" +
                " (examples/ and test-mods/ are left out of the public repository)")
        })
    }
    return configurations.detachedConfiguration(*projects.map { dependencies.project(mapOf("path" to it)) }.toTypedArray())
        .apply {
            isTransitive = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
        }
}

val scenarios = extensions.getByType<dev.plexusmodloader.devtools.gradle.minecraft.GameTestScenarios>()
val native = ":examples:minimal-native-mod"
val fabric = ":test-mods:fabric-entrypoint-only"
val neoforge = ":test-mods:neoforge-blocked"
val nativeHello = "[minimal_native_mod] Hello from minimal_native_mod 0.1.0 on DEDICATED_SERVER"
val fabricHello = "[fabric_hello] Fabric main entrypoint ran (loaded by poly-game)"
val nativeLib = ":test-mods:native-library"
val fabricLib = ":test-mods:fabric-library"
val gameTestHarness = ":test-mods:fabric-gametest-harness"
// A file in the mods folder that is not a readable jar, like a cut-off download.
val corruptJar = tasks.register("corruptTestJar") {
    val jar = layout.buildDirectory.file("poly/test-jars/corrupt-download.jar")
    outputs.file(jar)
    doLast { jar.get().asFile.apply { parentFile.mkdirs(); writeText("this download was cut off") } }
}
val neoforgeBlocked ="[Poly Mod Loader] NeoForge mod blocked_example 1.0.0 (neoforge-blocked-$testModVersion.jar): BLOCKED - NOT loaded"
// What must not show in the Quilt scenarios: entrypoints nothing may run (QSL's init, the client entrypoint on a server,
// the quilt.mod.json side of a jar that also has fabric.mod.json, a nested if_required mod nobody requires), a bridge
// that threw, and crashes.
val quiltUnexpected = listOf(
    "QSL init entrypoint ran",
    "[quilt_hello] client entrypoint ran",
    "[fabric_quilt_dual] Quilt entrypoint ran",
    "[quilt_nested_unused] initialised",
    // The nested mod left out with 307 does not make its container "work only in part".
    "PLEXUS-COMPAT-124",
    // quilt_hello's own nested library is not a QSL module (308 once said so).
    "the QSL module 'quilt_nested_lib'",
    "PLEXUS-COMPAT-002",
    "Exception in thread",
    "Caused by: ",
    "---- Minecraft Crash Report ----",
)
// What quilt_hello (test-mods/quilt-hello) reports wherever it runs: its assessment, its nested mods, the order of its
// entrypoints, Quilt Loader's API, its Mixin, its access wideners and Quilt's annotation stripping on the server.
val quiltHelloLines = listOf(
    "[Poly Mod Loader] Quilt mod quilt_hello 1.0.0 (quilt-hello-$testModVersion.jar): EXPERIMENTAL - loaded through the Quilt bridge",
    "PLEXUS-COMPAT-303 [limited] entrypoints [init]: QSL runs these entrypoints",
    "PLEXUS-COMPAT-304 [limited] entrypoint 'quilt_hello:custom': not run by Poly Mod Loader; other mods can get it through QuiltLoader.getEntrypoints and FabricLoader.getEntrypoints",
    "PLEXUS-COMPAT-131 [limited] mixins: 1 Mixin configuration(s), 1 for DEDICATED_SERVER (quilt_hello.mixins.json)",
    "PLEXUS-COMPAT-129 [limited] class tweaker: quilt_hello.accesswidener (accessWidener, official): 1 line(s)",
    "PLEXUS-COMPAT-129 [limited] class tweaker: quilt_hello_extra.accesswidener (accessWidener, official): 1 line(s)",
    "[Poly Mod Loader] Fabric mod fabric_nested_lib 2.0.0 (fabric-nested-lib.jar): EXPERIMENTAL - loaded through the Fabric bridge",
    "PLEXUS-COMPAT-120 [limited] nested jar: inside quilt-hello-$testModVersion.jar as META-INF/jars/fabric-nested-lib.jar",
    "[Poly Mod Loader] Quilt mod quilt_nested_lib 1.0.0 (quilt-nested-lib.jar): EXPERIMENTAL - loaded through the Quilt bridge",
    "PLEXUS-COMPAT-120 [limited] nested jar: inside quilt-hello-$testModVersion.jar as META-INF/jars/quilt-nested-lib.jar",
    "[Poly Mod Loader] Quilt mod quilt_nested_unused 1.0.0 (quilt-nested-unused.jar): BLOCKED - NOT loaded",
    "PLEXUS-COMPAT-307 [blocking] load type: it is nested in quilt-hello-$testModVersion.jar and loads only when a loaded mod requires it",
    "[Poly Mod Loader] Mixin: 1 configuration(s) from 0 Fabric mod(s) and 1 Quilt mod(s) for DEDICATED_SERVER",
    "[quilt_hello] Quilt pre_launch ran for quilt_hello 1.0.0",
    "[quilt_hello] Fabric preLaunch ran",
    "[quilt_nested_lib] initialised; source path has 2 part(s), the last is /META-INF/jars/quilt-nested-lib.jar",
    "[fabric_nested_lib] initialised, deep library is two levels down",
    "[quilt_hello] entrypoint order: pre_launch -> preLaunch -> main",
    "[quilt_hello] MinecraftQuiltLoader.getEnvironmentType() = SERVER, QuiltLoader.getRawGameVersion() = $minecraft",
    "[quilt_hello] sees fabric_nested_lib: NORMAL_FABRIC, group loader.fabric, version 2.0.0",
    "[quilt_hello] sees minimal_native_mod: OTHER, group loader.poly, version 0.1.0",
    "[quilt_hello] sees minecraft: BUILTIN, group builtin, version $minecraft",
    "[quilt_hello] fabricloader is provided by quilt_loader; quilt_nested_unused is loaded: false",
    "[quilt_hello] own metadata: version 1.0.0, group dev.plexusmodloader.test, name Quilt Hello, licenses [MIT], contributors [PolyDev [Owner], Helper [Tester]], homepage https://example.com/quilt_hello, settings answer = 42",
    "[quilt_hello] provides [quilt_hello_api 1.0.0, quilt_hello_versioned 3.0.0]; isModLoaded(\"quilt_hello_api\") = true, quilt_hello_versioned is from quilt_hello",
    "[quilt_hello] 1 custom entrypoint(s) from quilt_hello: hello through a Quilt custom entrypoint; FabricLoader.getEntrypoints gives the same instance: true",
    "[quilt_hello] the main entrypoint QuiltLoader returns is this instance: true",
    "[quilt_hello] FabricLoader sees quilt_hello as type quilt, version 1.0.0",
    "[quilt_hello] getModContainer(QuiltHello.class) = quilt_hello",
    "[quilt_hello] stripped on SERVER: @ClientOnly method present = false, @DedicatedServerOnly method present = true, @Requires(\"absent_mod\") field present = false, @Requires(\"quilt_loader\") field present = true",
    "[quilt_hello] access wideners: SharedConstants.prefixDebugFlagName public = true, Mth.SIN public = true",
    "[quilt_hello] nested quilt_nested_lib is ready",
    "[quilt_hello] Quilt mod's Mixin: tickServer @Inject ran",
)

// NeoForge through FML 11 on Poly Mod Loader (ADR-0026; on by default, -Dpoly.neoforge=off turns it off). FML reflects
// into java.lang.invoke; Poly Mod Loader's Java agent opens it to FML's modules, as the launcher profile and the server's
// launch jar start it. Without the agent NeoForge mods are left out (PLEXUS-COMPAT-216).
val polyAgentJar = tasks.jar.flatMap { it.archiveFile }
fun dev.plexusmodloader.devtools.gradle.minecraft.VerifiedGameRunTask.runsNeoForge() {
    inputs.file(polyAgentJar).withPropertyName("agentJar").withPathSensitivity(PathSensitivity.NONE)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${polyAgentJar.get().asFile.absolutePath}") })
    // -PpolyTransformDisable=<rule IDs or *> for diagnosing a rule that conflicts with NeoForge's patches.
    providers.gradleProperty("polyTransformDisable").orNull?.let { systemProperty("poly.transform.disable", it) }
    // -PmixinExport: Mixin writes every class it changed to .mixin.out/ in the run folder (diagnosis of the shared Mixin).
    if (providers.gradleProperty("mixinExport").isPresent) {
        systemProperty("mixin.debug.export", "true")
    }
    // -PmixinVerbose: Mixin logs its selection and every class it changes.
    if (providers.gradleProperty("mixinVerbose").isPresent) {
        systemProperty("mixin.debug.verbose", "true")
    }
}

// MinecraftForge through FML on Poly Mod Loader (ADR-0021 G3d, G4): Forge with the forge-hello test mod, and Forge alone. Forge's installer, libraries and patched server jar are prepared once in the run folder's .poly/forge/
// (network on the first run). Only Minecraft versions with a pinned Forge release (forge-releases.properties).
val forgeClassPath = configurations.create("forgeClassPath") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    forgeClassPath(libs.forge.securemodules)
    forgeClassPath(libs.forge.unsafe)
    forgeClassPath(libs.forge.jarjar.filesystems)
    forgeClassPath(libs.forge.roimfs)
}
// SystemClassPathAgentTest (ADR-0024): child JVMs started with the built jar as Java agent, and through a manifest-only
// jar with Launcher-Agent-Class, append these four jars and must then list Forge's file systems.
tasks.test {
    val agentJar = tasks.jar.flatMap { it.archiveFile }
    val forgeJars = forgeClassPath
    inputs.file(agentJar).withPropertyName("agentJar").withPathSensitivity(PathSensitivity.NONE)
    inputs.files(forgeJars).withPropertyName("forgeFileSystemJars").withPathSensitivity(PathSensitivity.NAME_ONLY)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dpoly.agentJar=${agentJar.get().asFile.absolutePath}",
            "-Dpoly.forgeFileSystemJars=${forgeJars.files.joinToString(File.pathSeparator) { it.absolutePath }}",
        )
    })
}
if (minecraft == "26.2") {
    // Forge and a Forge mod on the game test server, which needs no EULA: FML loads Forge and forge_hello
    // (ServerModLoader), the mod's constructor and setup listener run and its item is registered, a world is created,
    // Mojang's always_pass test runs and the world is saved.
    scenarios.gameTest("forgeGameTest", modJars(":test-mods:forge-hello", ":test-mods:forge-at", ":test-mods:forge-mixin", native)) {
        classpath(forgeClassPath)
        description = "Runs MinecraftForge and a Forge mod through Poly Mod Loader on the game test server: Forge's mod loading, the mod's events and item, a world, a GameTest, saving."
        systemProperty("poly.forge", "always")
        // -PpolyTransformDisable=<rule IDs or *> for diagnosing a rule that conflicts with Forge's patches.
        providers.gradleProperty("polyTransformDisable").orNull?.let { systemProperty("poly.transform.disable", it) }
        expectedOutput.addAll(
            "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 loads 3 Forge mod(s) itself on Poly Mod Loader's game class loader",
            "[Poly Mod Loader] Launching target 'polymodloader_server_gametest'",
            "Forge mod loading, version 65.1.5",
            "[forge_hello] Constructed by FML in module ",
            "[forge_hello] Common setup: item forge_hello:hello_item is registered",
            // The mod's META-INF/accesstransformer.cfg widens a private field and a private method of the game.
            "[forge_at] Mth.SIN has 65536 entries, debug flag MC_DEBUG_ACCESS_TRANSFORMER, SIN final: false",
            // Forge's Mixin applies the configuration the mod's manifest names (MixinConfigs).
            "[forge_mixin] tickServer @Inject ran; getServerModName() = ",
            "+forge_mixin",
            // FML loads the three Forge test mods; the Poly mod is Poly Mod Loader's (title screen counts).
            "[Poly Mod Loader] Loaded mods per loader: 1 Poly, 0 Fabric, 0 Quilt, 3 Forge",
            // Forge sets the server brand itself: Poly Mod Loader's optional rule is off, reported without a warning.
            "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 patched the target of Poly Mod Loader's optional rule poly:server_brand; that rule is off",
            // FML's ModList answers for the Poly mod too (ADR-0025); there is no Fabric mod here.
            "[forge_hello] ModList.isLoaded(\"fabric_lib\") = false",
            "[forge_hello] ModList.getModFileById(\"fabric_lib\") = null",
            "[forge_hello] Poly mod minimal_native_mod: isLoaded = true; getModContainerById(\"fabric_lib\") present = false",
            nativeHello,
        )
        expectedFiles.add("universe/gametestworld/level.dat")
    }
    // NeoForge and a NeoForge mod on the dedicated server (ADR-0026, N3): FML 11's own startup with Poly Mod Loader's game
    // class loader defining its game layer; the mod's constructor and setup listener run and its item is registered,
    // then the server stops at the EULA check. Poly Mod Loader does not accept the EULA.
    scenarios.gameTest("neoForgeServerBoot", modJars(":test-mods:neoforge-hello", native)) {
        description = "Starts NeoForge and a NeoForge mod through Poly Mod Loader on the dedicated server: FML's mod loading, the mod's events and item, up to the EULA check."
        launchTarget = "dedicated_server"
        gameArgumentOverrides.set(listOf<String>())
        runsNeoForge()
        expectedOutput.set(listOf(
            "[Poly Mod Loader] NeoForge 26.2.0.93 loads 1 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
            "FML's FMLLoader: [create: buildTransformingLoader -> PolyFml.buildTransformingLoader, runDiscovery: new ModsFolderLocator() -> PolyFml.modsLocator()] (ADR-0026)",
            "module(s) defined by Poly Mod Loader's game class loader",
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge 26.2.0.93 starts the game",
            "You need to agree to the EULA in order to run the server",
        ))
    }
    // NeoForge, a NeoForge mod and Poly Mods on Mojang's game test server (ADR-0026, N3/N6): FML constructs the mod and
    // registers its item, a world is created, Mojang's always_pass test runs and the world is saved. In production
    // NeoForge ticks game tests only in development (GameTestHooks.isGametestEnabled), under NeoForge's own launcher
    // too; the test harness mod ticks them (NEOFORGE_FULL_SUPPORT.md "N6").
    scenarios.gameTest(
        "neoForgeGameTest",
        modJars(":test-mods:neoforge-hello", ":test-mods:neoforge-gametest-harness", ":test-mods:neoforge-unmet", native, nativeLib),
    ) {
        description = "Runs NeoForge and a NeoForge mod through Poly Mod Loader on the game test server: FML's mod loading, the mod's events and item, a world, a GameTest, saving."
        runsNeoForge()
        expectedOutput.addAll(
            "[Poly Mod Loader] NeoForge 26.2.0.93 loads 3 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
            // A mod FML 11 would refuse is left out before FML starts, so the others still run.
            "[Poly Mod Loader] PLEXUS-COMPAT-217 NeoForge mod neoforge_unmet (neoforge-unmet-$testModVersion.jar) is NOT loaded: it requires neoforge [27,), but 26.2.0.93 is installed; it requires not_installed_lib [1,), which is not installed",
            "[Poly Mod Loader] NeoForge 26.2.0.93: 2 of 3 NeoForge mod file(s) meet their requirements; 1 left out (PLEXUS-COMPAT-217)",
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge 26.2.0.93 starts the game",
            "[neoforge_hello] Constructed by FML in module neoforge_hello (neoforge_hello)",
            "[neoforge_hello] Common setup: item neoforge_hello:hello_item is registered",
            "[neoforge_hello] ModList.isLoaded(\"neoforge\") = true",
            // FML's ModList answers for the Poly Mods too (ADR-0025); a mod container stays FML's own.
            "[Poly Mod Loader] FML's ModList [getModFileById, isLoaded] also answers for 2 Poly Mod(s) and other loaders' mod(s) (ADR-0025)",
            "[neoforge_hello] Poly Mod native_lib: isLoaded = true; getModFileById = Native Library 2.1.0 (mod native_lib); getModContainerById present = false",
            "[neoforge_hello] ModList.isLoaded(\"not_installed_lib\") = false; getModFileById = null",
            // No Fabric or Quilt mods run: as under NeoForge, Fabric Loader's API is not there for mods that look.
            "[neoforge_hello] Fabric Loader API visible: false",
            "[neoforge_hello] own jar contents: ",
            // FML runs the two NeoForge mods that meet their requirements; Poly Mod Loader the two Poly Mods.
            "[Poly Mod Loader] Loaded mods per loader: 2 Poly, 0 Fabric, 0 Quilt, 0 Forge, 2 NeoForge",
            "[neoforge_gametest_harness] NeoForge runs in production and does not tick game tests; the harness ticks them",
            // A Poly Mod sees the mods FML runs (GameHost.mods(), ADR-0025).
            "[native_lib] sees neoforge_hello: neoforge 1.0.0",
            nativeHello,
            "All 1 required tests passed",
        )
        expectedFiles.add("universe/gametestworld/level.dat")
    }
    // NeoForge mods, Fabric mods and Poly Mods in one game (ADR-0026 30, 31): one Mixin (Fabric's) serves NeoForge's and
    // Fabric's mods, FML does not start its own, Fabric mods register from onInitialize with NeoForge's registries open,
    // and each loader's mod list answers for the others' mods.
    scenarios.gameTest(
        "neoForgeFabricGameTest",
        modJars(
            ":test-mods:neoforge-hello", ":test-mods:neoforge-mixin", ":test-mods:neoforge-gametest-harness",
            native, nativeLib, fabric, ":test-mods:fabric-mixin", ":test-mods:fabric-api-user", fabricLib,
            ":test-mods:fabric-registry", ":test-mods:multi-loader-fallback",
            ":test-mods:fabric-twin", ":test-mods:neoforge-twin", ":test-mods:fabric-twin-user",
        ),
    ) {
        description = "Runs NeoForge mods, Fabric mods and Poly Mods together through Poly Mod Loader on the game test server: one Mixin serves NeoForge's and Fabric's mods (ADR-0026 30)."
        runsNeoForge()
        expectedOutput.addAll(
            "NeoForge mod file(s) and 7 Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
            "[Poly Mod Loader] NeoForge 26.2.0.93 loads 5 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
            "FML's Mixin: [createClassProcessorSet: MixinFacade.getClassProcessor() -> PolyFml.mixinClassProcessor, loadPlugins: its plugin files through PolyFml.withoutForeignFabricLoader] (ADR-0026 30)",
            "is left out of the game layer: NeoForge's mods use the MixinExtras of the Mixin that Fabric mods share (ADR-0026 30)",
            "Mixin configuration(s) of NeoForge's mods and libraries for the Mixin Fabric mods share; FML's own Mixin does not start (ADR-0026 30)",
            "share Fabric's Mixin",
            "[Poly Mod Loader] Loaded mods per loader: 2 Poly, 7 Fabric, 0 Quilt, 0 Forge, 4 NeoForge",
            "[neoforge_hello] Constructed by FML in module neoforge_hello (neoforge_hello)",
            "[neoforge_hello] Common setup: item neoforge_hello:hello_item is registered",
            // With Fabric mods in the game, Fabric Loader's API is there, as under Sinytra Connector.
            "[neoforge_hello] Fabric Loader API visible: true",
            "[neoforge_hello] Poly Mod native_lib: isLoaded = true; getModFileById = Native Library 2.1.0 (mod native_lib); getModContainerById present = false",
            // Both loaders' Mixin configurations change the same NeoForge-patched methods of MinecraftServer; the
            // NeoForge mod's configuration that needs a missing mod is not applied, as under FML.
            "[neoforge_mixin] tickServer @Inject ran; getServerModName() = neoforge+fabric_mixin; MixinExtras @ModifyReturnValue: getServerVersion() = 26.2+neoforge_mixin",
            "[fabric_mixin] tickServer @Inject ran; getServerModName() = neoforge+fabric_mixin",
            "Mixin config neoforge_mixin.absent.mixins.json from jar(",
            "[fabric_mixin] @ModifyArg on a mod class: sum = 32; MixinExtras @WrapOperation: product = 200",
            // A Fabric mod registers an item from onInitialize; NeoForge's registries are open for it, and the item is
            // still there after NeoForge's own registration and freezing.
            "[fabric_registry] onInitialize registered item fabric_registry:fabric_item (raw id assigned: true)",
            "[fabric_registry] first server tick: item fabric_registry:fabric_item in the registry: true",
            // A jar built for both whose NeoForge side FML would refuse runs by its Fabric metadata (ADR-0026 35).
            "PLEXUS-COMPAT-217 NeoForge mod multi_loader_fallback (multi-loader-fallback-$testModVersion.jar) is NOT loaded: it requires neoforge [99,)",
            "does not run multi-loader-fallback-$testModVersion.jar, which is built for FABRIC too; the FABRIC bridge runs it",
            "[multi_loader_fallback] Fabric side initialised: NeoForge refused the NeoForge side",
            // The same mod built for both loaders under IDs each loader spells its way (Cloth Config: cloth-config,
            // cloth_config): NeoForge's build runs, the Fabric build is left out, and NeoForge's build meets the Fabric
            // mods that need it (ADR-0026 37). Both builds' initialisation would register the same configuration.
            "PLEXUS-COMPAT-139 Fabric mod twin-mod (fabric-twin-$testModVersion.jar) is NOT loaded: NeoForge ",
            "loads its own build of it as twin_mod (the same ID as NeoForge spells it), and the two builds cannot run in one game",
            "mods that need twin-mod or twin-mod-api use NeoForge ",
            "[twin_mod] config example registered by the NeoForge build",
            "[twin_mod] config twin_user registered by the Fabric twin user's build",
            "[twin_user] isModLoaded(\"twin-mod\") = true; isModLoaded(\"twin-mod-api\") = true",
            "[native_lib] sees fabric_lib: fabric 1.2.0",
            "[native_lib] sees neoforge_hello: neoforge 1.0.0",
            nativeHello,
            fabricHello,
            "All 1 required tests passed",
        )
        unexpectedOutput.addAll("PLEXUS-COMPAT-216", "fml.modloadingissue.mixin", "registered by the Fabric build", "already registered", "optional integration: com.example.twin.TwinConfig")
        expectedFiles.add("universe/gametestworld/level.dat")
    }
    scenarios.gameTest("forgeFabricGameTest",modJars(":test-mods:forge-hello", ":test-mods:forge-at", ":test-mods:forge-mixin", native, fabric, ":test-mods:fabric-mixin", ":test-mods:fabric-api-user", ":test-mods:fabric-library", ":test-mods:native-library")) {
        classpath(forgeClassPath)
        description = "Runs Forge mods, Fabric mods and Poly mods together through Poly Mod Loader on the game test server: one Mixin serves Forge and Fabric mods (ADR-0021 G8)."
        expectedOutput.addAll(
            "Forge mod(s) and 4 Fabric mod(s) run together; one Mixin serves both",
            "[Poly Mod Loader] Loaded mods per loader: 2 Poly, 4 Fabric, 0 Quilt, 3 Forge",
            "[Poly Mod Loader] Mixin: 1 configuration(s) from 1 MinecraftForge mod(s) share Fabric's Mixin",
            "Forge mod loading, version 65.1.5",
            "[forge_hello] Common setup: item forge_hello:hello_item is registered",
            "[forge_at] Mth.SIN has 65536 entries, debug flag MC_DEBUG_ACCESS_TRANSFORMER, SIN final: false",
            // Both loaders' Mixin configurations change the same method of MinecraftServer (Forge-patched).
            "[forge_mixin] tickServer @Inject ran; getServerModName() = forge+fabric_mixin",
            "[fabric_mixin] tickServer @Inject ran; getServerModName() = forge+fabric_mixin",
            "[fabric_mixin] @ModifyArg on a mod class: sum = 32; MixinExtras @WrapOperation: product = 200",
            "[fabric_mixin] extend-enum: Difficulty.POLY_MIXIN_TEST is an enum constant: true",
            "[fabric_api_user] Poly Mod native_lib 2.1.0 is loaded: true",
            // Each loader's mod list answers for the other loaders' mods (ADR-0025): FML's ModList for Fabric and
            // Poly mods (a provided ID too, but no ModContainer), Fabric Loader for Forge mods (no resource roots: Forge
            // serves their resources).
            "[forge_hello] ModList.isLoaded(\"fabric_lib\") = true",
            "[forge_hello] ModList.getModFileById(\"fabric_lib\") = Fabric Library 1.2.0 (mod fabric_lib)",
            "[forge_hello] ModList.getModFileById(\"fabric_lib_alias\") = mod fabric_lib",
            "[forge_hello] Poly mod minimal_native_mod: isLoaded = true; getModContainerById(\"fabric_lib\") present = false",
            "[fabric_api_user] Forge mod forge_hello is loaded: true (minecraftforge 1.0.0, 0 root path(s))",
            // And a Poly Mod's ModContext.mods() for both.
            "[native_lib] sees fabric_lib: fabric 1.2.0",
            "[native_lib] sees forge_hello: minecraftforge 1.0.0",
            nativeHello,
            fabricHello,
            "All 1 required tests passed",
        )
        expectedFiles.add("universe/gametestworld/level.dat")
    }
    scenarios.gameTest("forgeServerBoot", files()) {
        classpath(forgeClassPath)
        description = "Starts MinecraftForge alone through Poly Mod Loader: FML's boot, plugin and game layers, the server up to the EULA check."
        launchTarget = "dedicated_server"
        gameArgumentOverrides.set(listOf<String>())
        systemProperty("poly.forge", "always")
        expectedOutput.set(listOf(
            "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 loads 0 Forge mod(s) itself on Poly Mod Loader's game class loader",
            "[Poly Mod Loader] FML's game layer:",
            "[Poly Mod Loader] Phase GAME_LAUNCH: MinecraftForge 26.2-65.1.5 starts the game",
            "You need to agree to the EULA in order to run the server",
        ))
    }
}

// MinecraftForge alone on every supported version (ADR-0021): Forge's own mod loading, a world, a game test, saving. The
// Forge test mods above are built against the 26.2 release.
scenarios.gameTest("forgeWorldTest", files()) {
    classpath(forgeClassPath)
    description = "Runs MinecraftForge alone through Poly Mod Loader on the game test server of the selected Minecraft version."
    systemProperty("poly.forge", "always")
    expectedOutput.addAll(
        "[Poly Mod Loader] FML's game layer:",
        "Forge mod loading, version ",
        "All 1 required tests passed",
        worldsSavedLine,
    )
}

// NeoForge on every supported version (ADR-0026, N10): NeoForge's own mod loading with only the game test harness (built
// against 26.2's NeoForge, but asking for NeoForge 26.1 or newer), a world, a game test, saving.
scenarios.gameTest("neoForgeWorldTest", modJars(":test-mods:neoforge-gametest-harness")) {
    description = "Runs NeoForge with the game test harness through Poly Mod Loader on the game test server of the selected Minecraft version."
    runsNeoForge()
    expectedOutput.addAll(
        "NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
        "[Poly Mod Loader] FML ",
        "[neoforge_gametest_harness] NeoForge runs in production and does not tick game tests; the harness ticks them",
        "All 1 required tests passed",
        worldsSavedLine,
    )
}

val matrix = listOf(
    // A NeoForge mod laid out as Sodium's NeoForge build (test-mods/neoforge-early-service): the jar players install is
    // an FML library with an early service, and the mod is nested in it. FML loads the outer jar as an early service
    // and the mod from its JarJar, so the mod is constructed and mods that need it find it (corpusNeoForgeClient once
    // found Sodium missing for Iris, Sodium Extra and Reese's Sodium Options).
    scenarios.gameTest(
        "neoForgeEarlyService",
        modJars(":test-mods:neoforge-early-service", ":test-mods:neoforge-gametest-harness"),
    ) {
        description = "Runs a NeoForge mod nested in an early service jar, as Sodium ships, through Poly Mod Loader on the game test server."
        runsNeoForge()
        expectedOutput.addAll(
            "Found 1 early service jars",
            "[neoforge_early_service] Constructed by FML from the jar nested in the early service jar",
            "All 1 required tests passed",
        )
        if (minecraftModules == "26.1" || minecraftModules == "26.2") {
            // FML 11 runs graphics bootstrap plugins before it decides not to open the early window on a server.
            expectedOutput.addAll(
                "Running graphics bootstrap plugin neoforge_early_service",
                "[neoforge_early_service] early service ran before mod discovery",
            )
        } else {
            // FML 12 (NeoForge for 26.3) returns before running them when it is headless, as on this server.
            expectedOutput.add("Not loading early display in headless mode.")
            unexpectedOutput.add("Running graphics bootstrap plugin")
        }
        unexpectedOutput.addAll("Attempted to select a dependency jar for JarJar which was passed in as source")
    },
    scenarios.gameTest("matrixNativeOnly", modJars(native)) { expectedOutput.add(nativeHello) },
    scenarios.gameTest("matrixFabricOnly", modJars(fabric)) { expectedOutput.add(fabricHello) },
    // NeoForge runs by default (ADR-0026), but needs Poly Mod Loader's Java agent, which these scenarios start without:
    // the NeoForge mod is left out (216) and diagnosed as before.
    scenarios.gameTest("matrixNeoForgeOnly", modJars(neoforge)) {
        expectedOutput.addAll(
            "PLEXUS-COMPAT-216 NeoForge cannot run here (java.io.IOException: NeoForge's FML needs java.lang.invoke opened to it",
            neoforgeBlocked,
        )
    },
    scenarios.gameTest("matrixNativeFabric", modJars(native, fabric)) { expectedOutput.addAll(nativeHello, fabricHello) },
    // NeoForge mods run together with Fabric mods (ADR-0026 30, neoForgeFabricGameTest), but this scenario starts
    // without Poly Mod Loader's Java agent: the NeoForge mod is left out with the reason (216), the rest runs.
    scenarios.gameTest("matrixNativeFabricNeoForge", modJars(native, fabric, neoforge)) {
        expectedOutput.addAll(
            nativeHello,
            fabricHello,
            "PLEXUS-COMPAT-216 NeoForge cannot run here (java.io.IOException: NeoForge's FML needs java.lang.invoke opened to it",
            neoforgeBlocked,
        )
    },
    // Mods of different loaders that need each other: all load, and each runs after what it needs.
    scenarios.gameTest(
        "dependencyCrossLoader",
        modJars(native, nativeLib, ":test-mods:native-addon", fabricLib, ":test-mods:fabric-addon"),
    ) {
        expectedOutput.addAll(
            "[Poly Mod Loader] Fabric mod fabric_lib 1.2.0 (fabric-library-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "[Poly Mod Loader] Fabric mod fabric_addon 0.3.0 (fabric-addon-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "[Poly Mod Loader] Foreign mods: 2 loaded, 0 not loaded",
            "[Poly Mod Loader] Loaded mod native_lib 2.1.0",
            "[Poly Mod Loader] Loaded mod native_addon 1.0.0",
            "[fabric_addon] fabric_lib says hello-from-fabric-lib, native_lib says 42",
            // Poly Mods initialise before the game starts, Fabric mods where Fabric Loader does (ADR-0020): the Poly
            // Mod sees the Fabric library's classes, not yet its initialisation.
            "[native_addon] fabric_lib says not ready",
            // LifecycleEvents.MODS_INITIALIZED comes after the Fabric mods' initialisers (ADR-0022).
            "[native_addon] after every mod initialised, fabric_lib says hello-from-fabric-lib",
            "[Poly Mod Loader] Loaded mods per loader: 3 Poly, 2 Fabric, 0 Quilt, 0 Forge",
            // A Poly Mod sees the Fabric mods in the same game (ModContext.mods()).
            "[native_lib] sees fabric_lib: fabric 1.2.0",
            "[native_lib] sees forge_hello: not loaded",
            nativeHello,
        )
    },
    // Mods that cannot run, including ones whose dependencies cannot run: each is reported and skipped, and
    // the game starts with the rest.
    scenarios.gameTest(
        "dependencyBlockedChains",
        modJars(
            native,
            fabricLib,
            neoforge,
            ":test-mods:fabric-wrong-version",
            ":test-mods:fabric-chain",
            ":test-mods:fabric-stale",
            ":test-mods:fabric-loader-user",
            ":test-mods:fabric-malformed",
            ":test-mods:fml-library",
        ) + files(corruptJar),
    ) {
        expectedOutput.addAll(
            nativeHello,
            "[fabric_lib] initialised",
            "[Poly Mod Loader] Fabric mod fabric_old 1.0.0 (fabric-wrong-version-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-114 [blocking] dependencies: requires Minecraft ~1.21.4, but $minecraft is running",
            "[Poly Mod Loader] Fabric mod fabric_chain 1.0.0 (fabric-chain-$testModVersion.jar): BLOCKED - NOT loaded",
            "requires 'fabric_old' *, which is installed but not loaded: Fabric mod fabric_old 1.0.0",
            "requires 'blocked_example' *, which is installed but not loaded: NeoForge mod blocked_example 1.0.0",
            "requires 'fabric-api' *, which is not installed",
            "[Poly Mod Loader] Fabric mod fabric_stale 1.0.0 (fabric-stale-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-112 [blocking] link check: 1 Minecraft class(es) this game version does not have (the mod was built for another version): net.minecraft.world.level.block.RemovedBlock",
            "PLEXUS-COMPAT-112 [blocking] link check: 1 Minecraft method(s) or field(s) this game version does not have (the mod was built for another version): net.minecraft.server.MinecraftServer#removedInThisVersion()I",
            "[Poly Mod Loader] Fabric mod fabric_loader_user 1.0.0 (fabric-loader-user-$testModVersion.jar): BLOCKED - NOT loaded",
            "class(es) of the loader's runtime that Poly Mod Loader does not provide: net.fabricmc.loader.impl.launch.knot.Knot",
            "[Poly Mod Loader] Fabric mod fabric_malformed ? (fabric-malformed-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-205 fml-library-$testModVersion.jar is a NeoForge/Forge library (FMLModType GAMELIBRARY)",
            "PLEXUS-DISCOVERY-003 corrupt-download.jar cannot be read as a jar",
            "[Poly Mod Loader] Foreign mods: 1 loaded, 6 not loaded",
        )
    },
    // Jar-in-jar: the bundle's nested mods (two levels deep) load as mods of their own, before the bundle;
    // the nested mod whose Mixin configuration needs a newer Mixin is blocked alone.
    scenarios.gameTest("nestedJars", modJars(native, ":test-mods:fabric-bundle")) {
        expectedOutput.addAll(
            nativeHello,
            "[Poly Mod Loader] Fabric mod fabric_bundle 1.0.0 (fabric-bundle-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "[Poly Mod Loader] Fabric mod fabric_nested_lib 2.0.0 (fabric-nested-lib.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-120 [limited] nested jar: inside fabric-bundle-$testModVersion.jar as META-INF/jars/fabric-nested-lib.jar",
            "[Poly Mod Loader] Fabric mod fabric_nested_deep 3.0.0 (fabric-nested-deep.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-120 [limited] nested jar: inside fabric-nested-lib.jar as META-INF/jars/fabric-nested-deep.jar",
            "[Poly Mod Loader] Fabric mod fabric_nested_mixin 1.0.0 (fabric-nested-mixin.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-124 [limited] nested jars: 1 of its 2 nested mods are not loaded, so it works only in part: fabric_nested_mixin [PLEXUS-COMPAT-132]",
            "[Poly Mod Loader] Foreign mods: 3 loaded, 1 not loaded",
            "[fabric_nested_lib] initialised, deep library is two levels down",
            "[fabric_bundle] nested library is ready",
        )
    },
    // Fabric Loader's runtime API: FabricLoader.getInstance() answers from the mods Poly Mod Loader loaded, and a
    // language adapter declared by one mod creates another mod's entrypoint.
    scenarios.gameTest(
        "fabricLoaderApi",
        modJars(
            native,
            nativeLib,
            fabricLib,
            ":test-mods:fabric-api-user",
            ":test-mods:fabric-adapter-provider",
            ":test-mods:fabric-adapter-user",
            ":test-mods:fabric-launcher-user",
        ),
    ) {
        expectedOutput.addAll(
            "[Poly Mod Loader] Foreign mods: 5 loaded, 0 not loaded",
            "[fabric_api_user] environment SERVER, Minecraft $minecraft",
            "[fabric_api_user] Poly Mod native_lib 2.1.0 is loaded: true",
            "[fabric_api_user] custom value answer = 42",
            "[fabric_api_user] 1 custom entrypoint(s): hello through a custom entrypoint",
            "[fabric_api_user] the main entrypoint FabricLoader returns is this instance: true",
            "[fabric_api_user] object share delivered fabric_api_user:answer = 42",
            "[fabric_api_user] config directory exists: true",
            "[fabric_adapter_user] created by the poly_test language adapter",
            // Fabric Loader's internal launcher (FabricLauncherBase.getLauncher), as CustomSkinLoader uses it.
            "[fabric_launcher_user] extra-lib says hello-from-extra-lib (loaded by poly-game), environment SERVER, development false",
            "[fabric_launcher_user] FabricLoaderImpl game provider: minecraft $minecraft",
        )
    },
    // Fabric class tweakers (ADR-0018): access lines become access widenings of the real game classes, which
    // the mod's entrypoint then uses; lines whose targets 26.2 lacks are left out with a warning, as Fabric Loader
    // applies them to nothing (ADR-0020).
    scenarios.gameTest(
        "fabricClassTweaker",
        modJars(native, ":test-mods:fabric-class-tweaker", ":test-mods:fabric-class-tweaker-stale"),
    ) {
        expectedOutput.addAll(
            nativeHello,
            "[Poly Mod Loader] Fabric mod fabric_class_tweaker 1.0.0 (fabric-class-tweaker-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-129 [limited] class tweaker: fabric_class_tweaker.classtweaker (accessWidener, official): 3 line(s), 1 of them transitive, become 4 access widening(s)",
            "[Poly Mod Loader] Fabric mod fabric_class_tweaker_stale 1.0.0 (fabric-class-tweaker-stale-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-137 [limited] link check: 2 access widening target(s) this game version does not have; not applied, as Fabric Loader applies such lines to nothing: class net/minecraft/world/level/block/RemovedBlock (fabric_class_tweaker_stale.classtweaker:3), field net/minecraft/util/Mth.REMOVED_TABLE:[F (fabric_class_tweaker_stale.classtweaker:2)",
            "[Poly Mod Loader] Foreign mods: 2 loaded, 0 not loaded",
            // The stale field line still makes Mth accessible, as Fabric Loader's implied class access does.
            "[Poly Mod Loader] Access widenings: 5 accepted, 0 rejected (5 from foreign mods' class tweakers)",
            "[fabric_class_tweaker] Mth.SIN has 65536 entries, debug flag MC_DEBUG_CLASS_TWEAKER, SIN final: false",
        )
    },
    // Mixin (ADR-0020): a Fabric mod's mixins change real game classes (also ones Poly Mod Loader transforms itself)
    // and its own classes, with an accessor, an invoker, a configuration plugin and MixinExtras; a mod whose Mixin
    // configurations Mixin would refuse is left out before start.
    scenarios.gameTest(
        "fabricMixin",
        modJars(native, ":test-mods:fabric-mixin", ":test-mods:fabric-mixin-broken"),
    ) {
        expectedOutput.addAll(
            nativeHello,
            "[Poly Mod Loader] Fabric mod fabric_mixin 1.0.0 (fabric-mixin-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-131 [limited] mixins: 2 Mixin configuration(s), 1 for DEDICATED_SERVER (fabric_mixin.mixins.json)",
            "[Poly Mod Loader] Fabric mod fabric_mixin_broken 1.0.0 (fabric-mixin-broken-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-132 [blocking] mixins: 1 required Mixin configuration(s) Mixin would stop the game for: fabric_mixin_broken.newer.mixins.json needs Mixin 0.9.0; Poly Mod Loader has 0.8.7",
            "PLEXUS-COMPAT-136 [limited] mixins: 1 Mixin configuration(s) Mixin will skip with an error, as under Fabric Loader: fabric_mixin_broken.missing.mixins.json is named in fabric.mod.json but not in the jar",
            "[Poly Mod Loader] Foreign mods: 1 loaded, 1 not loaded",
            "[Poly Mod Loader] Mixin: 1 configuration(s) from 1 Fabric mod(s) for DEDICATED_SERVER",
            "[fabric_mixin] config plugin loaded for com.example.compat.mixin.mixins",
            "[fabric_mixin] @Invoker and @Inject on SharedConstants: mixin-was-here, MC_DEBUG_PLAIN",
            "[fabric_mixin] @Accessor: Mth.SIN has 65536 entries",
            "[fabric_mixin] @ModifyArg on a mod class: sum = 32; MixinExtras @WrapOperation: product = 200",
            "[fabric_mixin] extend-enum: Difficulty.POLY_MIXIN_TEST is an enum constant: true",
            "[fabric_mixin] mixinextras is loaded: true",
            "[fabric_mixin] tickServer @Inject ran; getServerModName() = poly+fabric_mixin",
            "[fabric_mixin] getGameInstance() is this server: true",
            "PLEXUS-COMPAT-129 [limited] class tweaker: fabric_mixin.classtweaker (classTweaker, official): 0 line(s), 0 of them transitive, become 0 access widening(s) and 2 interface injection(s)",
            "[Poly Mod Loader] Fabric bridge transforms classes with environment stripping, interface injection (2 line(s)), enum extension (1 line(s)), then Mixin 0.8.7 (FabricMC 0.17.4+mixin.0.8.7) with MixinExtras 0.5.5",
            "[Poly Mod Loader] Initialising 1 foreign mod(s)",
            "[fabric_mixin] inject-interface: hello from an injected interface, tagged, generic com.example.compat.mixin.Injected${'$'}Tagged<java.lang.String>",
        )
    },
    // Quilt mods (ADR-0027): a mod with only quilt.mod.json runs on the Fabric bridge's Mixin, access widener and
    // entrypoint machinery and gets Quilt Loader's answers from Quilt Loader's API.
    // Its nested Fabric and Quilt mods load, the nested if_required mod nobody requires does not (307), Quilt's
    // pre_launch runs before Fabric's preLaunch and main, QSL's init does not run (303), and a world is saved.
    scenarios.gameTest("quiltOnly", modJars(native, ":test-mods:quilt-hello")) {
        expectedOutput.addAll(quiltHelloLines)
        expectedOutput.addAll(
            nativeHello,
            "[quilt_hello] sees fabric_lib: not loaded (isModLoaded false)",
            "[Poly Mod Loader] Foreign mods: 4 loaded, 1 not loaded",
            "[Poly Mod Loader] Loaded mods per loader: 1 Poly, 2 Fabric, 2 Quilt, 0 Forge",
        )
        unexpectedOutput.addAll(quiltUnexpected)
        // No mod here needs a QSL module.
        unexpectedOutput.add("PLEXUS-COMPAT-308")
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    // Quilt, Fabric and Poly mods see each other: Quilt Loader's API answers for Fabric and Poly mods, Fabric Loader's
    // and ModContext.mods() for the Quilt mod; fabric_lib 1.2.0 meets quilt_hello's optional "1.0.0" (Quilt reads it as
    // ^1.0.0) and does not break it (breaks is ALL of [fabric_lib, absent_b]); the jar with both metadata files runs as
    // a Fabric mod (310).
    scenarios.gameTest(
        "quiltFabricPoly",
        modJars(native, nativeLib, fabricLib, ":test-mods:fabric-api-user", ":test-mods:quilt-hello", ":test-mods:fabric-quilt-dual"),
    ) {
        expectedOutput.addAll(quiltHelloLines)
        expectedOutput.addAll(
            nativeHello,
            "[quilt_hello] sees fabric_lib: NORMAL_FABRIC, group loader.fabric, version 1.2.0",
            "[quilt_hello] sees native_lib: OTHER, group loader.poly, version 2.1.0",
            "[native_lib] sees quilt_hello: quilt 1.0.0",
            "[fabric_api_user] the main entrypoint FabricLoader returns is this instance: true",
            "[Poly Mod Loader] Fabric mod fabric_quilt_dual 1.0.0 (fabric-quilt-dual-$testModVersion.jar): EXPERIMENTAL - loaded through the Fabric bridge",
            "PLEXUS-COMPAT-310 [limited] metadata: the jar has both fabric.mod.json and quilt.mod.json; it runs as a Fabric mod from fabric.mod.json",
            "[fabric_quilt_dual] Fabric entrypoint ran; this mod's type is fabric",
            "[fabric_quilt_dual] FabricLoader sees quilt_hello: quilt 1.0.0 Quilt Hello, authors [PolyDev]",
            "[Poly Mod Loader] Foreign mods: 7 loaded, 1 not loaded",
            "[Poly Mod Loader] Loaded mods per loader: 2 Poly, 5 Fabric, 2 Quilt, 0 Forge",
        )
        unexpectedOutput.addAll(quiltUnexpected)
        unexpectedOutput.add("PLEXUS-COMPAT-308")
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    // Quilt mods that cannot run are reported with their reason and left out, and the game starts with the rest.
    scenarios.gameTest(
        "quiltBlocked",
        modJars(native, ":test-mods:quilt-qsl-resource", ":test-mods:quilt-blocked", ":test-mods:quilt-hello"),
    ) {
        expectedOutput.addAll(
            nativeHello,
            "[Poly Mod Loader] Quilt mod quilt_qsl_resource 1.0.0 (quilt-qsl-resource-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-308 [limited] dependencies: depends on the QSL module 'quilt_resource_loader'; the Quilt Standard Libraries have no release for Minecraft 26.x",
            "PLEXUS-COMPAT-114 [blocking] dependencies: requires 'quilt_resource_loader' *, which is not installed",
            "[Poly Mod Loader] Quilt mod quilt_plugin 1.0.0 (quilt-plugin-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-301 [blocking] loader plugin: declares a Quilt Loader plugin (experimental_quilt_loader_plugin)",
            "[Poly Mod Loader] Quilt mod quilt_malformed 1.0.0 (quilt-malformed-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-300 [blocking] metadata: intermediate_mappings is absent (so org.quiltmc:hashed), which Quilt Loader does not support",
            "[Poly Mod Loader] Quilt mod quilt_client_only 1.0.0 (quilt-client-only-$testModVersion.jar): BLOCKED - NOT loaded",
            "PLEXUS-COMPAT-305 [blocking] environment: minecraft.environment is client, process is DEDICATED_SERVER",
            "[Poly Mod Loader] Quilt mod quilt_init_only 1.0.0 (quilt-init-only-$testModVersion.jar): EXPERIMENTAL - loaded through the Quilt bridge",
            "PLEXUS-COMPAT-303 [limited] entrypoints [init]: QSL runs these entrypoints, and QSL has no release for Minecraft 26.x, so Quilt Loader does not run them either; Poly Mod Loader does not run them, and applies the mod's mixins and resources. This mod's initialization code does not run",
            "[Poly Mod Loader] Quilt mod quilt_hello 1.0.0 (quilt-hello-$testModVersion.jar): EXPERIMENTAL - loaded through the Quilt bridge",
            "[quilt_hello] entrypoint order: pre_launch -> preLaunch -> main",
            "[Poly Mod Loader] Foreign mods: 5 loaded, 5 not loaded",
            "[Poly Mod Loader] Loaded mods per loader: 1 Poly, 2 Fabric, 3 Quilt, 0 Forge",
        )
        unexpectedOutput.addAll(quiltUnexpected)
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    scenarios.gameTest("regressionTransformConflict", modJars(native, ":test-mods:transform-conflict")) {
        expectedOutput.addAll(
            "[Poly Mod Loader] Rejected transformation: PLEXUS-TRANSFORM-004 rule=poly:server_brand",
            "[Poly Mod Loader] Rejected transformation: PLEXUS-TRANSFORM-004 rule=transform_conflict:server_brand",
            nativeHello,
        )
    },
)

// Native API: the showcase mod's own in-game tests, run on Mojang's game test server.
val nativeApiGameTests = scenarios.gameTest("nativeApiGameTests", modJars(":examples:native-showcase-mod")) {
    gameArgumentOverrides.set(listOf("--universe", "universe", "--tests", "native_showcase:*"))
    expectedOutput.set(
        listOf(
            "[Poly Mod Loader] Native declarations: 7 block(s), 10 item(s), 3 entity type(s), 1 effect(s), 2 game rule(s), 1 sound(s), 1 command(s), 1 channel(s), 62 game test(s)",
            "[Poly Mod Loader] Registered 7 block(s) and 10 item(s) from mods",
            "[Poly Mod Loader] Registered 1 creative tab(s) from mods on 1 extra page(s)",
            "[Poly Mod Loader] Transformed net/minecraft/commands/Commands with [poly:commands]",
            "[Poly Mod Loader] Added 1 feature(s) to",
            "[Poly Mod Loader] Added 1 natural spawn(s) to",
            "[native_showcase] Loaded 2 showcase message(s)",
            "[native_showcase] Every mod is initialised",
            "All 62 required tests passed",
            "[native_showcase] Server starting (dedicated=false)",
            "[native_showcase] Overworld loaded",
            "[native_showcase] Overworld saving",
            "[native_showcase] Overworld unloaded",
            "[native_showcase] Server stopped after",
        ),
    )
}

// Real mods from Modrinth, fixed by compat/mod-corpus-<version>.json (mod, version, SHA-512). The jars are
// downloaded into build/poly/mod-corpus/<version>/<loader>/ and never committed.
val corpusRoot = layout.buildDirectory.dir("poly/mod-corpus/$minecraft")
val modCorpus = tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.ModCorpusTask>("modCorpus") {
    group = "verification"
    description = "Downloads the real-mod corpus listed in compat/mod-corpus-$minecraft.json (-PmodCorpusRefresh rebuilds the list from Modrinth)."
    lockFile = layout.projectDirectory.file("compat/mod-corpus-$minecraft.json")
    outputDirectory = corpusRoot
    gameVersion = minecraft
    refresh = providers.gradleProperty("modCorpusRefresh").map { it != "false" }.orElse(false)
    topPerLoader = providers.gradleProperty("modCorpusTop").map(String::toInt).orElse(40)
}
// Quilt full support Q5 (ADR-0027): every mod with a build for this version made for Quilt and not for Fabric, plus the
// mods those builds require, in compat/mod-corpus-quilt-<version>.json and build/poly/mod-corpus/<version>/quilt/.
val modCorpusQuilt = tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.ModCorpusTask>("modCorpusQuilt") {
    group = "verification"
    description = "Downloads the Quilt mod corpus listed in compat/mod-corpus-quilt-$minecraft.json (-PmodCorpusRefresh rebuilds the list from Modrinth)."
    lockFile = layout.projectDirectory.file("compat/mod-corpus-quilt-$minecraft.json")
    outputDirectory = corpusRoot
    gameVersion = minecraft
    loaders = listOf("quilt")
    refresh = providers.gradleProperty("modCorpusRefresh").map { it != "false" }.orElse(false)
    topPerLoader = 0
}
fun corpus(vararg loaders: String): FileCollection =
    files(loaders.map { loader -> corpusRoot.map { it.dir(loader).asFileTree.matching { include("*.jar") } } })

// Mojang's game test server is neither a dedicated server nor a client. Left out of the game test runs, and run in
// corpusFabricServer instead:
// - Xaero's mods finish their server setup only for a DedicatedServer ("server instanceof DedicatedServer"), so their
//   own config registry stays unfrozen on the game test server and they stop it, under Fabric Loader too.
// - JourneyMap's Forge jar carries a Fabric stub whose preLaunch stops the game on purpose ("JourneyMap for Forge in
//   Fabric Loader"), which Poly Mod Loader runs as Fabric Loader does.
val notOnGameTestServer = listOf("xaero*.jar", "journeymap-forge-*.jar")
// Mod builds that real Forge cannot run either, left out of every Forge run of the corpus (mods that require them are
// then left out with PLEXUS-COMPAT-209, as FML would refuse them):
// - GlitchCore 26.1.0.4 and TerraBlender 26.1.0.2 (the 26.1 corpus) declare no MixinConfigs in their manifests, the
//   only place Forge reads Mixin configurations from, so their Mixins never apply: GlitchCore's constructor throws its
//   platform stub's UnsupportedOperationException (PacketHandler.register), and TerraBlender's server start fails to
//   cast TheEndBiomeSource to its Mixin interface. Their later builds (GlitchCore 26.1.1.0.7, TerraBlender
//   26.1.2.0.3) name both configurations there. No other Forge jar of the 26.1 and 26.2 corpora has an undeclared
//   Forge Mixin configuration.
// - Cherished Worlds 16.0.1+26.1.2 (the 26.1 corpus) registers a listener class with a single @SubscribeEvent method
//   (ScreenEventsListener) in its client setup; Forge's EventBus (7.0.1 in Forge 62.0.9, 7.0.5 in 65.1.5) refuses
//   that with IllegalArgumentException "Only a single listener found", unconditionally (javap), so FML stops mod
//   loading on the client.
// - Better Advancements 0.6.0.76 (the 26.1.2 corpus) calls the package-private enum AdvancementTabType from its own
//   package and opens it only with a Fabric access widener (betteradvancements.accesswidener). Forge 64.1.5 reads no
//   access wideners (none of its libraries mention one), and neither its access transformer nor its binary patch makes
//   the class public (javap of the patched client jar), so its config load fails with IllegalAccessError on the client.
//   Vanilla 26.2 made the class public, so the 26.2 build runs.
// - Rhino 2101.2.7 (build 81 in the 26.1 corpus, build 85 in the 26.1.2 corpus) is a Minecraft 1.21 build that Modrinth
//   also lists for 26.1.x, with no pack.mcmeta. Forge's ResourcePackLoader.findPacks reads every mod file's root as a
//   pack and adds the warning fml.modloading.brokenresources when vanilla finds no metadata ("Missing metadata in pack
//   mod:rhino"), and ClientModLoader.completeModLoading shuts BusGroup.DEFAULT down on any loading warning before it
//   shows LoadingErrorScreen, whose buttons do not start it again (javap, Forge 64.1.5 and 65.1.5). Every game event,
//   ServerStartingEvent among them, then reaches no mod: Xaero's Minimap and World Map never create their server data
//   and disconnect the player on joining a world.
val brokenUnderForge = listOf(
    "GlitchCore-forge-26.1-26.1.0.4.jar",
    "TerraBlender-forge-26.1-26.1.0.2.jar",
    "cherishedworlds-forge-16.0.1+26.1.2.jar",
    "BetterAdvancements-Forge-26.1.2-0.6.0.76.jar",
    "rhino-2101.2.7-build.81.jar",
    "rhino-2101.2.7-build.85.jar",
)
// NeoForge mods that stop a real NeoForge server too (checked against NeoForge's own installed server, 2026-10-24),
// left out of the NeoForge server runs of the corpus; they run on the client:
// - Sodium's FML early service (SodiumWorkarounds) checks LWJGL, which a server does not have: FMLLoader.create stops in
//   ImmediateWindowHandler.load with NoClassDefFoundError org/lwjgl/Version before any mod loads. Sodium Extra, Reese's
//   Sodium Options and Iris (which do not declare Sodium as required) then stop the server when their Mixin plugins
//   reach Sodium's classes (NoClassDefFoundError .../sodium/client/services/PlatformRuntimeInformation).
// - ImmediatelyFast 1.16.5 (the 26.2 corpus) reads its configuration in its Mixin plugin with LWJGL's MathUtil when
//   Mixin selects its configurations (NoClassDefFoundError org/lwjgl/system/MathUtil), before the game starts. Its 26.1
//   build 1.15.3 does not, and runs on the server. Its 26.3 build 1.17.1 stops the same way under Poly Mod Loader
//   (corpusNeoForge 26.3, 2026-10-27); a server has no LWJGL, so NeoForge's own server would too, but that was not run.
val notOnNeoForgeServer = listOf(
    "sodium-neoforge-*.jar",
    "sodium-extra-neoforge-*.jar",
    "reeses-sodium-options-neoforge-*.jar",
    "iris-neoforge-*.jar",
    "ImmediatelyFast-NeoForge-1.16.5+26.2.jar",
    "ImmediatelyFast-NeoForge-1.17.1+26.3.jar",
)
// NeoForge mods that construct client classes on a server: real NeoForge's server stops at mod construction (after the
// dedicated server's EULA check). Zoomify's bootstrap needs KeyMapping$Category (NoClassDefFoundError).
val neoForgeClientClassesOnServer = listOf("zoomify-*.jar")
// NeoForge mod builds that the pinned NeoForge release cannot run, although their neoforge.mods.toml accepts it
// (checked against NeoForge's own installed server, 2026-10-24): the 26.1 corpus has builds for later 26.1.x NeoForge
// releases. On NeoForge 26.1.0.19-beta, Architectury 20.0.4 and Veinminer 2.10.4 need NeoForge's BreakBlockEvent,
// which that release does not have (NoClassDefFoundError at mod construction), and Jade 26.1.12's Mixin shadows
// ReloadableServerResources.registryAccess, which Minecraft 26.1 does not have (InvalidMixinException). Real NeoForge
// stops mod loading for each; mods that require them are then left out with PLEXUS-COMPAT-217.
// Two more fail on the 26.1 client, found with corpusNeoForgeClientMixins and the first corpusNeoForgeClient run:
// - Iris 1.11.4+mc26.1.2's Sodium mixins (mixins.iris.compat.sodium.json) target Sodium 0.9 members that the 26.1
//   corpus's Sodium 0.8.9+mc26.1.1 does not have (@Shadow uniformBufferManager, renderOutOfGraph); a mixin of one mod
//   into another mod's class, so it fails the same under NeoForge's own launcher.
// - Zoomify 2.17.0+mc26.1.2 names a method argument in @Local(name = "partialTicks", argsOnly = true). Mixin reads
//   argument names from the local variable table only from behaviour version 0.17.0 (Locals.getInitialMethodLocals),
//   and FML 11.0.5 (NeoForge 26.1.0.19-beta) gives configurations without a behaviorVersion 0.16.5
//   (MixinFacade.DEFAULT_BEHAVIOUR_VERSION; 0.17.1 in FML 11.0.15 for 26.1.2), so its critical injection into
//   Camera.calculateFov fails and mod loading stops (sources of FML 11.0.5 and sponge-mixin 0.17.0).
val brokenUnderNeoForge = listOf(
    "architectury-neoforge-20.0.4.jar",
    "veinminer-neoforge-2.10.4.jar",
    "Jade-mc26.1-NeoForge-26.1.12.jar",
    "iris-neoforge-1.11.4+mc26.1.2.jar",
    "zoomify-2.17.0+mc26.1.2-universal.jar",
).filter { minecraft == "26.1" }
// NeoForge mod builds that the pinned NeoForge release runs on a server but not on a client. Lambda Dynamic Lights
// 4.11.2+26.1.2 accepts Minecraft [26.1,26.1.2] and names no NeoForge version, so FML loads it on 26.1; its client
// initialisation (NeoForgePlatform$1.register, from LambDynLights.onInitializeClient through Yumi) needs NeoForge's
// TagsUpdatedEvent$ClientPacketReceived, which NeoForge 26.1.2.122's universal jar has and 26.1.0.19-beta's does not
// (NoClassDefFoundError in the fourth corpusNeoForgeClient run on 26.1, 2026-10-24; the same under NeoForge's own
// launcher, which loads the same jar with the same FML). The server never reaches that code (corpusNeoForge loads it).
// FancyMenu 3.9.14 for NeoForge 26.3 stops every client on NeoForge 26.3.0.98 (corpusNeoForgeClient and
// corpusFabricNeoForgeClient, 2026-10-28): its injection into Minecraft.resizeGui reads Minecraft.getInstance().gui,
// and NeoForge's EarlyWindowHandoff.completeWindowHandoff calls Window.onFramebufferResize whenever the early loading
// screen is up (EarlyLoadingScreenController.current() non-null, the default), from Minecraft.<init> before it assigns
// gui (checked with javap -c on the patched client and the universal jar). Poly Mod Loader's code is not on that
// path; the same launch under NeoForge's own launcher was not run. No newer 26.3 build on Modrinth (2026-10-28).
val brokenOnNeoForgeClient = when (minecraft) {
    "26.1" -> listOf("lambdynamiclights-4.11.2+26.1.2.jar")
    "26.3" -> listOf("fancymenu_neoforge_3.9.14_MC_26.3.jar")
    else -> listOf()
}
val lambDynamicLightsOnNeoForgeClient = brokenOnNeoForgeClient.none { it.startsWith("lambdynamiclights-") }
fun gameTestCorpus(vararg loaders: String): FileCollection =
    files(loaders.map { loader ->
        corpusRoot.map {
            it.dir(loader).asFileTree.matching { include("*.jar"); exclude(notOnGameTestServer); exclude(brokenUnderForge) }
        }
    })
// The corpus's NeoForge mods that a NeoForge game test server runs (corpusNeoForge).
fun neoForgeServerCorpus(): FileCollection =
    files(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(notOnNeoForgeServer); exclude(neoForgeClientClassesOnServer); exclude(brokenUnderNeoForge)
        }
    })
// The corpus's Fabric mods beside NeoForge on a server: as gameTestCorpus("fabric"), without the jars built for several
// loaders that NeoForge then runs and that a NeoForge server cannot (Zoomify's universal jar, as in corpusNeoForge).
fun fabricCorpusBesideNeoForgeServer(): FileCollection =
    files(corpusRoot.map {
        it.dir("fabric").asFileTree.matching {
            include("*.jar"); exclude(notOnGameTestServer); exclude(brokenUnderForge)
            exclude(notOnNeoForgeServer); exclude(neoForgeClientClassesOnServer); exclude(brokenUnderNeoForge)
        }
    })
// The counts below are those of the committed 26.2 corpus (nested mods included), reviewed mod by mod in
// docs/COMPATIBILITY.md; with -PmodCorpusRefresh they change. Other versions' corpora are checked for a clean start,
// world save and server start, without fixed counts, until their counts are reviewed the same way.
val reviewedCorpus = minecraft == "26.2"
fun corpusCounts(reviewed: String, otherwise: String = "[Poly Mod Loader] Foreign mods: "): String =
    if (reviewedCorpus) reviewed else otherwise
val corpusScenarios = listOf(
    // Mojang's game test server: world creation, ticks and saving. Fabric API freezes registries only from
    // net.minecraft.server.Main, so the harness mod does the same on this start (see its source).
    scenarios.gameTest("corpusFabric", gameTestCorpus("fabric") + modJars(gameTestHarness)) {
        dependsOn(modCorpus)
        expectedOutput.addAll(
            corpusCounts("[Poly Mod Loader] Foreign mods: 98 loaded, 73 not loaded"),
            "[poly_gametest_harness] froze registries after mod initialisation",
            worldsSavedLine,
        )
    },
    // The real dedicated server start (net.minecraft.server.Main) with every Fabric mod: bootstrap, Mixin, mod
    // initialisation and Fabric API's registry freeze, up to the EULA check (Poly Mod Loader never accepts it).
    scenarios.gameTest("corpusFabricServer", corpus("fabric")) {
        dependsOn(modCorpus)
        launchTarget.set("dedicated_server")
        gameArgumentOverrides.set(listOf<String>())
        expectedOutput.set(
            listOf(
                corpusCounts("[Poly Mod Loader] Foreign mods: 100 loaded, 74 not loaded"),
                corpusCounts("[Poly Mod Loader] Initialising 100 foreign mod(s)", "[Poly Mod Loader] Initialising "),
                "You need to agree to the EULA",
            ),
        )
    },
    // NeoForge itself runs the corpus's NeoForge mods on the game test server (ADR-0026, N6), with the harness that ticks
    // game tests, which NeoForge ticks only in development (neoForgeGameTest): FML constructs the mods, a world is
    // created, tested and saved. The same jars pass on NeoForge's own installed game test server with the same harness.
    scenarios.gameTest(
        "corpusNeoForge",
        files(corpusRoot.map {
            it.dir("neoforge").asFileTree.matching {
                include("*.jar"); exclude(notOnNeoForgeServer); exclude(neoForgeClientClassesOnServer); exclude(brokenUnderNeoForge)
            }
        }) + modJars(":test-mods:neoforge-gametest-harness", ":test-mods:neoforge-environment-probe"),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        expectedOutput.addAll(
            // As under NeoForge, mods do not see Fabric Loader's or Quilt Loader's API (no Fabric or Quilt mods run), so
            // Yumi (in Lambda Dynamic Lights) runs on NeoForge and finds Lambda Dynamic Lights' default configuration;
            // when it saw Fabric Loader, the lookup came back empty and the client stopped (2026-10-24).
            "[neoforge_environment_probe] Fabric Loader API visible: false, Quilt Loader API visible: false",
            "for lambdynlights finds lambdynlights.toml: true",
            corpusCounts(
                "[Poly Mod Loader] NeoForge 26.2.0.93 loads 38 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
                "[Poly Mod Loader] NeoForge ",
            ),
            // The same 56 modules NeoForge's own installation builds its game content class loader from, and the probe.
            corpusCounts("FML 11.0 loaded 50 mod(s); game layer of 57 module(s)", "[Poly Mod Loader] FML "),
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    // The real dedicated server start with the corpus's NeoForge mods (N8): Poly Mod Loader's requirements check,
    // NeoForge's files, FML's discovery, early services, plugins, Mixin and game layer, and the game's start, up to the
    // EULA check. NeoForge checks the EULA before it constructs mods (as real NeoForge does; Poly Mod Loader never
    // accepts it), so mod construction is checked on the game test server (corpusNeoForge).
    scenarios.gameTest(
        "corpusNeoForgeServer",
        files(corpusRoot.map {
            it.dir("neoforge").asFileTree.matching { include("*.jar"); exclude(notOnNeoForgeServer); exclude(brokenUnderNeoForge) }
        }),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        launchTarget.set("dedicated_server")
        gameArgumentOverrides.set(listOf<String>())
        expectedOutput.set(
            listOf(
                corpusCounts(
                    "[Poly Mod Loader] NeoForge 26.2.0.93 loads 37 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
                    "[Poly Mod Loader] NeoForge ",
                ),
                "[Poly Mod Loader] FML ",
                "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
                "You need to agree to the EULA",
            ),
        )
    },
    // MinecraftForge runs the Forge mods and the jars built for several loaders (ADR-0021 G6) on the game test server:
    // FML constructs them, a world is created, ticks and is saved. (A dedicated server checks the EULA before Forge
    // loads mods, and Poly Mod Loader never accepts it.)
    scenarios.gameTest("corpusForge", gameTestCorpus("forge")) {
        dependsOn(modCorpus)
        classpath(forgeClassPath)
        // The requirements check (PLEXUS-COMPAT-209), FML constructing the mods, and the world created, ticked and saved.
        expectedOutput.addAll(
            corpusCounts(
                "MinecraftForge 26.2-65.1.5: 32 of 38 Forge mod file(s) meet their requirements; 6 left out",
                "MinecraftForge $minecraft-",
            ),
            "Forge mod loading, version ",
            "All 1 required tests passed",
            worldsSavedLine,
        )
    },
    // The real dedicated server start with every Forge mod of the corpus (Forge full support B4): Poly Mod Loader's
    // requirements check, Forge's files, FML's layers and transformation services, Mixin, and Forge's server launch
    // handler starting the game, up to the EULA check. A dedicated server checks the EULA before Forge constructs mods
    // (Poly Mod Loader never accepts it), so mod construction is checked on the game test server (corpusForge).
    scenarios.gameTest(
        "corpusForgeServer",
        files(corpusRoot.map { it.dir("forge").asFileTree.matching { include("*.jar"); exclude(brokenUnderForge) } }),
    ) {
        dependsOn(modCorpus)
        classpath(forgeClassPath)
        launchTarget.set("dedicated_server")
        gameArgumentOverrides.set(listOf<String>())
        expectedOutput.set(
            listOf(
                "MinecraftForge $minecraft-",
                "[Poly Mod Loader] Phase GAME_LAUNCH: MinecraftForge $minecraft-",
                "You need to agree to the EULA",
            ),
        )
    },
    // Fabric and Forge mods of the corpus in one instance (ADR-0021 G8): Fabric runs the Fabric mods, MinecraftForge
    // the Forge mods, and one Mixin serves both.
    scenarios.gameTest("corpusFabricForge", gameTestCorpus("fabric", "forge") + modJars(gameTestHarness, ":test-mods:fabric-host-patch-probe")) {
        dependsOn(modCorpus)
        classpath(forgeClassPath)
        expectedOutput.addAll(
            // The corpus mods, the harness and the probe below. Fabric mods whose mixins do not fit Forge's patch are
            // not loaded (ADR-0030), and with them the Fabric mods that need them.
            // 2026-10-27: no Fabric API module is left out next to Forge any more; Architectury, Balm and Lithium are.
            // Puzzles Lib runs since its item toss event follows Forge's ForgeHooks.onPlayerTossEvent (87 -> 88 loaded).
            // 2026-10-29: fabric-content-registries-v0 is left out (Forge moved drowning and fluid jumping into
            // FluidType hooks), and Puzzles Lib, which uses its classes, with it (88 -> 86 loaded).
            // 2026-10-29: the link check reads Forge's patched game first (ADR-0031), and fabric-biome-api-v1 writes
            // ChunkGenerator.featuresPerStep as a Supplier, which Forge made a ClearableLazy (86 -> 85 loaded).
            corpusCounts("[Poly Mod Loader] Foreign mods: 85 loaded, 81 not loaded"),
            corpusCounts(
                "PLEXUS-COMPAT-224 4 mod(s) are NOT loaded, because Poly Mod Loader cannot run them as written on the game as MinecraftForge 26.2-65.1.5 patches it: architectury, balm, fabric-content-registries-v0, lithium",
                "mod(s) are NOT loaded, because Poly Mod Loader cannot run them as written on the game as MinecraftForge ",
            ),
            "share Fabric's Mixin",
            "Forge mod loading, version ",
            // Fabric API features whose mixins meet members Forge's patch added or changed (test-mods/fabric-host-patch-probe).
            // Forge's own getConnection() serves Fabric's accessor; the rest of that accessor mixin applies (212).
            "[poly_host_patch_probe] fabric-networking-api-v1: ServerCommonPacketListenerImpl implements ServerCommonPacketListenerImplAccessor: true; getConnection() is the listener's connection: true; getServer() is the listener's server: true",
            // Forge changed the field Fabric's attribute builder accessor reads to a Map: getBuilder() wraps it, through
            // the game class loader's own copy of AttributeBuilderBridge (linked to Forge's Guava).
            "[poly_host_patch_probe] fabric-object-builder-api-v1: AttributeSupplier\$Builder implements AttributeSupplierBuilderAccessor: true; getBuilder() returns dev.plexusmodloader.compat.fabric.mixin.AttributeBuilderBridge\$ForwardingBuilder",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        // Nothing runs half-applied: a mixin failure in the game would be one the check before the launch missed.
        unexpectedOutput.addAll("PLEXUS-COMPAT-225", "Critical injection failure")
    },
    // Fabric and NeoForge mods of the corpus in one instance (ADR-0026 30, 31): Fabric runs the Fabric mods, NeoForge's
    // FML the NeoForge mods and the jars built for both, and one Mixin (Fabric's) serves both, as Sinytra Connector
    // runs Fabric mods on NeoForge. Fabric mods register with NeoForge's registries open; Fabric mixins that meet
    // NeoForge's patches are moved to the patch (PLEXUS-COMPAT-226, 212) or keep the mod out (224, ADR-0030) as next to Forge.
    scenarios.gameTest(
        "corpusFabricNeoForge",
        fabricCorpusBesideNeoForgeServer() + neoForgeServerCorpus() +
            modJars(gameTestHarness, ":test-mods:neoforge-gametest-harness", ":test-mods:neoforge-environment-probe"),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        expectedOutput.addAll(
            "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
            "share Fabric's Mixin",
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
            // With Fabric mods, Yumi in Lambda Dynamic Lights for NeoForge picks Fabric Loader's API and still finds
            // the mod's default configuration through Fabric's container for it (ADR-0026 36).
            "[neoforge_environment_probe] Fabric Loader API visible: true",
            "for lambdynlights finds lambdynlights.toml: true",
            // Cloth Config's Fabric build (cloth-config) and NeoForge build (cloth_config) are one mod under IDs each
            // loader spells its way: NeoForge's build runs, the Fabric build is left out, and Fabric mods that need it
            // use NeoForge's build. Both builds register Cloth's example config; with both, the client stopped
            // (ADR-0026 37, corpusFabricNeoForgeClient 2026-10-24).
            "PLEXUS-COMPAT-139 Fabric mod cloth-config (cloth-config-",
            "loads its own build of it as cloth_config (the same ID as NeoForge spells it)",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        unexpectedOutput.addAll("PLEXUS-COMPAT-216")
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    // The join of corpusFabricNeoForge without a window (ADR-0026 40, 41): fabric-network-probe joins the game test
    // server as a client over a memory channel, through login and the configuration phase, where Fabric API's and
    // NeoForge's networking announce their channels (minecraft:register), negotiate and run their tasks, into the play
    // phase, where it sends its own Fabric API payload and echoes neoforge-network-probe's NeoForge payload, both
    // answered by the server, and gets a Fabric payload inside a bundle. Before ADR-0026 40, Fabric API's registration
    // payload met NeoForge's codec for minecraft:register and the server dropped the connection; before ADR-0026 41, a
    // play-phase payload Fabric API did not take reached Fabric API's configuration-phase handler ("Unknown addon"), and
    // NeoForge's bundle filter dropped Fabric's bundled payloads.
    scenarios.gameTest(
        "corpusFabricNeoForgeJoin",
        fabricCorpusBesideNeoForgeServer() + neoForgeServerCorpus() +
            modJars(
                gameTestHarness,
                ":test-mods:neoforge-gametest-harness",
                ":test-mods:fabric-network-probe",
                ":test-mods:neoforge-network-probe",
            ),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        description = "A fake client joins the game test server with the corpus's Fabric and NeoForge mods, through login and configuration into the play phase, and exchanges Fabric's and NeoForge's play payloads."
        expectedOutput.addAll(
            "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
            "[fabric_network_probe] reached the configuration phase: true",
            // Fabric API's registry sync does not fit NeoForge's patch and is not loaded (PLEXUS-COMPAT-224, ADR-0030), but
            // other Fabric API modules register fabric: channels (fabric:custom_ingredient_sync, fabric:recipe_sync/...;
            // measured true on 26.2 and 26.3, 2026-10-28).
            "[fabric_network_probe] the server registered Fabric channels: true",
            "[fabric_network_probe] reached the play phase: true",
            "[fabric_network_probe] the server received Fabric echo \"hello\"",
            "[fabric_network_probe] the server answered Fabric's play payload: true",
            "[neoforge_network_probe] the server received echo \"hello\" from PolyNetProbe",
            "[fabric_network_probe] the server answered NeoForge's play payload: true",
            "[fabric_network_probe] a Fabric payload in a bundle arrived: true",
            "[fabric_network_probe] the server kept the connection",
            // NeoForge's own channel registration (written with NeoForge's codec), its negotiation, and Fabric API's
            // common networking all went through.
            "minecraft:unregister (",
            "neoforge:network (ModdedNetworkPayload)",
            "c:register (CommonRegisterPayload)",
            // Fabric API's configuration-phase handler keeps to the configuration listener (ADR-0026 41).
            "PLEXUS-COMPAT-222 Fabric mod fabric-networking-api-v1 (fabric-networking-api-v1.mixins.json, ServerCommonPacketListenerImplMixin)",
            "All 1 required tests passed",
        )
        unexpectedOutput.addAll(
            "Failed to encode packet",
            "may not be sent to the client",
            "PLEXUS-COMPAT-216",
            "Unknown addon",
        )
    },
    // Sinytra Connector's own case: the corpus's Fabric mods on NeoForge, with no NeoForge build of any of them, so the
    // Fabric builds of Architectury, Balm, Lithium, Puzzles Lib and Forge Config API Port run against NeoForge's patch
    // (docs/PERFORMANCE.md measurement 9 found them failing under Connector). Only the harnesses are NeoForge mods.
    scenarios.gameTest(
        "corpusFabricOnNeoForge",
        fabricCorpusBesideNeoForgeServer() + modJars(gameTestHarness, ":test-mods:neoforge-gametest-harness"),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        expectedOutput.addAll(
            "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        unexpectedOutput.addAll("PLEXUS-COMPAT-216", "PLEXUS-COMPAT-139")
        expectedFiles.add("universe/gametestworld/level.dat")
    },
    // The real dedicated server start with the corpus's Fabric and NeoForge mods, up to the EULA check: Poly Mod Loader's
    // checks, NeoForge's files, FML's discovery and game layer, the shared Mixin and NeoForge's start of the game.
    // NeoForge's server checks the EULA before the game's bootstrap, so neither NeoForge's nor Fabric's mods initialise
    // before it (as corpusNeoForgeServer; Poly Mod Loader never accepts the EULA).
    scenarios.gameTest(
        "corpusFabricNeoForgeServer",
        files(corpusRoot.map {
            it.dir("fabric").asFileTree.matching { include("*.jar"); exclude(notOnNeoForgeServer); exclude(brokenUnderNeoForge) }
        }) + files(corpusRoot.map {
            it.dir("neoforge").asFileTree.matching { include("*.jar"); exclude(notOnNeoForgeServer); exclude(brokenUnderNeoForge) }
        }),
    ) {
        dependsOn(modCorpus)
        runsNeoForge()
        launchTarget.set("dedicated_server")
        gameArgumentOverrides.set(listOf<String>())
        expectedOutput.set(
            listOf(
                "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
                "[Poly Mod Loader] FML ",
                "share Fabric's Mixin",
                "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
                "You need to agree to the EULA",
            ),
        )
        unexpectedOutput.addAll("PLEXUS-COMPAT-216")
    },
    scenarios.gameTest("corpusAll", gameTestCorpus("fabric", "neoforge", "forge") + modJars(native, fabric, gameTestHarness)) {
        dependsOn(modCorpus)
        // With Forge mods, NeoForge does not run (ADR-0026 34): its mods are named with the reason, then diagnosed.
        expectedOutput.add("NeoForge mod file(s) are NOT loaded: MinecraftForge mods are in the same game")
        expectedOutput.addAll(nativeHello, fabricHello, corpusCounts("[Poly Mod Loader] Foreign mods: 98 loaded, 160 not loaded"), corpusCounts("[Poly Mod Loader] Initialising 98 foreign mod(s)", "[Poly Mod Loader] Initialising "), worldsSavedLine)
    },
)

// Diagnosis aid, not part of any check: NeoForge on the game test server with every jar of -PneoForgeProbeDir=<folder>
// and the harness that ticks game tests (-PmodProbeTarget=dedicated_server: the dedicated server up to the EULA check).
providers.gradleProperty("neoForgeProbeDir").orNull?.let { folder ->
    scenarios.gameTest("neoForgeProbe", files(fileTree(folder) { include("*.jar") }) + modJars(":test-mods:neoforge-gametest-harness")) {
        runsNeoForge()
        if (providers.gradleProperty("modProbeTarget").orNull == "dedicated_server") {
            launchTarget.set("dedicated_server")
            gameArgumentOverrides.set(listOf<String>())
            expectedOutput.set(listOf("You need to agree to the EULA"))
        } else {
            expectedOutput.addAll("All 1 required tests passed")
        }
    }
}

// The Quilt corpus (Quilt full support Q5, Q7, Q9; ADR-0027): every Quilt-only build of this Minecraft version and the
// mods they require (modCorpusQuilt). The same jars on Mojang's game test server (world creation, GameTest, saving), on
// the real dedicated server up to the EULA check, and together with the corpus's MinecraftForge mods.
// InvSearch Storage Indexer starts a web server (the JDK's HttpServer, whose dispatcher thread is not a daemon) when it
// initialises, so a dedicated server that stops at the EULA check never exits with it, under Quilt Loader too
// (quiltLoaderReference); it is left out of the dedicated server run only. The game test server stops the JVM itself.
val keepsTheServerJvmAlive = listOf("invsearch-storage-indexer-*.jar")
// Quilt corpus builds that real Quilt Loader cannot run on the client either, left out of the client runs:
// - NeoFullbright 1.0+mc26.2 (fabric.mod.json only): its ClientLanguageMixin has a non-static @ModifyArg handler on a
//   static target, which Mixin 0.8.7 refuses when ClientLanguage loads ("non-static callback method ... targets a
//   static method which is not supported", ModifyArgInjector.checkTarget), so the client stops while initialising.
//   Fabric Loader 0.19.5 and Quilt Loader 0.31.0-beta.4 apply mixins with the same Mixin
//   (net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7). Found by mixinAuditQuiltClient, its only failure in the 26.2,
//   26.1 and 26.1.2 Quilt corpora.
val brokenOnQuiltClient = listOf("NeoFullbright-1.0+mc26.2-quilt.jar")
// Quilt corpus data pack mods whose data the game cannot load, left out of the runs that load a world: PurpurPacks'
// 26.3 builds (pack.mcmeta max_format 107.1; 26.3's data format is 121) still hold 26.2-format enchantments and
// advancements, so 26.3's codecs stop the registry load ("No key type in MapLike[{"condition":"minecraft:match_tool"...",
// then recipe_unlocked advancements). Fabric API's resource loader and the game's codecs read them, as under Quilt
// Loader (corpusQuilt 26.3, 2026-10-27; with these five left out it passes).
val quiltDataBrokenOnGame = when (minecraft) {
    "26.3" -> listOf(
        "enchant_infinity_on_crossbow_v2.12-quilt.jar",
        "enchant_looting_on_shears_v1.13-quilt.jar",
        "enchant_elytra_ignores_unbreaking_v2.12-quilt.jar",
        "crafting_stonecutting_wood_doors_and_trapdoors_v1.12-quilt.jar",
        "crafting_stonecutting_wood_slabs_and_stairs_v1.13-quilt.jar",
    )
    else -> listOf()
}
val quiltBrokenUnderNeoForge = listOf("more-beets-1.0-release.jar").filter { minecraft == "26.1" }
fun quiltCorpus(gameTestServer: Boolean): FileCollection =
    files(corpusRoot.map {
        it.dir("quilt").asFileTree.matching {
            include("*.jar")
            exclude(if (gameTestServer) notOnGameTestServer + quiltDataBrokenOnGame else keepsTheServerJvmAlive)
        }
    })
// What no Quilt corpus run may show: a bridge that threw (002), and crashes.
val quiltCorpusUnexpected = listOf(
    "PLEXUS-COMPAT-002",
    "Exception in thread",
    "---- Minecraft Crash Report ----",
)
// Quilt mods of the corpus whose data the saved world must enable: a resource-only mod of each author family (its
// dependency on QSL's quilt_resource_loader is skipped "unless" Fabric API's resource loader is there), and two mods
// with classes and data.
val quiltDataPacks = when (minecraft) {
    "26.2" -> listOf(
        "beacon_base_amethyst", "crafting_re_dye_glass", "mr_magic_items", "stewbeet_lifestealfr",
        "vintage_animal_paintings_pack", "dwarfsandgiants", "gateway_of_doom",
    )
    "26.1" -> listOf(
        "beacon_base_amethyst", "crafting_re_dye_glass", "mr_magic_items", "stewbeet_lifestealfr",
        "stewbeet_more_apples", "dwarfsandgiants", "gateway_of_doom",
    )
    "26.1.2" -> listOf("mr_magic_items", "stewbeet_lifestealfr", "stewbeet_more_apples", "dwarfsandgiants", "gateway_of_doom")
    "26.3" -> listOf(
        "beacon_base_amethyst", "crafting_re_dye_glass", "mr_magic_items", "stewbeet_lifestealfr",
        "vintage_animal_paintings_pack", "dwarfsandgiants",
    )
    else -> listOf()
}
val quiltCorpusScenarios = listOf(
    scenarios.gameTest("corpusQuilt", quiltCorpus(true) + modJars(gameTestHarness)) {
        dependsOn(modCorpusQuilt)
        description = "The Quilt mod corpus on the game test server: world creation, GameTest, saving."
        // 26.2: 114 Quilt-only builds and 3 required mods, nested mods included; every mod left out is compared with
        // real Quilt Loader in docs/COMPATIBILITY.md ("Quilt 완전 지원 점검").
        expectedOutput.addAll(
            corpusCounts("[Poly Mod Loader] Foreign mods: 135 loaded, 125 not loaded"),
            corpusCounts("[Poly Mod Loader] Loaded mods per loader: 0 Poly, 55 Fabric, 80 Quilt, 0 Forge", "[Poly Mod Loader] Loaded mods per loader: "),
            corpusCounts("[Poly Mod Loader] Mixin: 55 configuration(s) from 42 Fabric mod(s) and 12 Quilt mod(s) for DEDICATED_SERVER", "[Poly Mod Loader] Mixin: "),
            corpusCounts("[Poly Mod Loader] Initialising 135 foreign mod(s)", "[Poly Mod Loader] Initialising "),
            corpusCounts("[voicechat] Voice chat server started at port", "All 1 required tests passed"),
            worldsSavedLine,
        )
        unexpectedOutput.addAll(quiltCorpusUnexpected)
        expectedFiles.add("universe/gametestworld/level.dat")
        // Most Quilt-only builds are data packs packed as mods. Fabric API's resource loader turns every loaded mod into
        // a pack, Quilt mods included, and the saved world lists them as enabled data packs.
        val world = runDirectory.file("universe/gametestworld/level.dat")
        doLast {
            val level = GZIPInputStream(world.get().asFile.inputStream()).use { it.readBytes() }
                .toString(Charsets.ISO_8859_1)
            val missing = quiltDataPacks.filter { "\u0000${it.length.toChar()}$it" !in level }
            if (missing.isNotEmpty()) {
                throw GradleException("corpusQuilt: the saved world does not enable the data packs of $missing")
            }
            println("corpusQuilt: the saved world enables the data packs of ${quiltDataPacks.size} Quilt mod(s)")
        }
    },
    scenarios.gameTest("corpusQuiltServer", quiltCorpus(false)) {
        dependsOn(modCorpusQuilt)
        description = "The Quilt mod corpus on the real dedicated server, up to the EULA check."
        launchTarget.set("dedicated_server")
        gameArgumentOverrides.set(listOf<String>())
        expectedOutput.set(
            listOf(
                corpusCounts("[Poly Mod Loader] Foreign mods: 133 loaded, 80 not loaded"),
                corpusCounts("[Poly Mod Loader] Initialising 133 foreign mod(s)", "[Poly Mod Loader] Initialising "),
                corpusCounts("[voicechat] Compatibility version 20", "[Poly Mod Loader] Loaded mods per loader: "),
                "You need to agree to the EULA",
            ),
        )
        unexpectedOutput.addAll(quiltCorpusUnexpected)
    },
    // As Fabric mods do with NeoForge (corpusFabricNeoForge, ADR-0026 30): one Mixin serves the Quilt, Fabric and
    // NeoForge mods; a jar built for Quilt and NeoForge runs as a NeoForge mod, and the Quilt build of a mod whose
    // NeoForge build FML loads is left out (139).
    // More Beets 1.0 (a data pack built for Quilt and NeoForge, so NeoForge runs it) declares pack format 4 without
    // supported_formats; NeoForge 26.1.0.19-beta's ResourcePackLoader stops the game test server on it, with NeoForge
    // alone too (neoForgeProbe with only that jar, 2026-10-24), where 26.2.0.93 warns and reads it another way.
    scenarios.gameTest(
        "corpusQuiltNeoForge",
        quiltCorpus(true).filter { it.name !in quiltBrokenUnderNeoForge } + neoForgeServerCorpus() +
            modJars(gameTestHarness, ":test-mods:neoforge-gametest-harness"),
    ) {
        dependsOn(modCorpus, modCorpusQuilt)
        runsNeoForge()
        description = "The Quilt mod corpus with the corpus's NeoForge mods on the game test server."
        expectedOutput.addAll(
            "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
            "share Fabric's Mixin",
            "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        if (minecraft == "26.2") {
            // The 26.2 Quilt corpus's Cloth Config is the Fabric build; NeoForge's build cloth_config runs instead and
            // stands in for it (ADR-0026 37). Better Shulker Boxes, which needs it and Fabric API's interaction events
            // (loaded since the wraps of calls NeoForge replaced run on its calls, 2026-10-28), runs on it.
            expectedOutput.addAll(
                "loads its own build of it as cloth_config (the same ID as NeoForge spells it)",
                "mods that need cloth-config or cloth-config2 use NeoForge ",
                "Fabric mod bettershulker 1.5.1 (better-shulker-boxes-quilt-1.5.1.jar): EXPERIMENTAL - loaded",
            )
        }
        unexpectedOutput.addAll(quiltCorpusUnexpected + "PLEXUS-COMPAT-216")
        expectedFiles.add("universe/gametestworld/level.dat")
        // Fabric API's resource loader, which turns the Quilt mods into data packs (corpusQuilt), does not fit NeoForge's
        // patch and is not loaded (PLEXUS-COMPAT-224, ADR-0030), so most Quilt mods' data packs are not enabled (on 26.2
        // all but mr_magic_items, 2026-10-25). The saved world still enables NeoForge's mod data.
        val world = runDirectory.file("universe/gametestworld/level.dat")
        doLast {
            val level = GZIPInputStream(world.get().asFile.inputStream()).use { it.readBytes() }
                .toString(Charsets.ISO_8859_1)
            fun enabled(pack: String) = "\u0000${pack.length.toChar()}$pack" in level
            if (!enabled("mod_data")) {
                throw GradleException("corpusQuiltNeoForge: the saved world does not enable NeoForge's mod data")
            }
            println("corpusQuiltNeoForge: the saved world enables NeoForge's mod data and the data packs of " +
                "${quiltDataPacks.filter(::enabled)} of the Quilt mods ${quiltDataPacks}")
        }
    },
    scenarios.gameTest("corpusQuiltForge", quiltCorpus(true) + gameTestCorpus("forge") + modJars(gameTestHarness)) {
        dependsOn(modCorpus, modCorpusQuilt)
        classpath(forgeClassPath)
        description = "The Quilt mod corpus with the corpus's MinecraftForge mods on the game test server."
        // As Fabric mods do with Forge (corpusFabricForge, ADR-0021 G8): one Mixin serves the Quilt, Fabric and Forge
        // mods; a jar built for Quilt and Forge runs as a Forge mod (G6), and the Quilt build of a mod whose Forge build
        // MinecraftForge loads is left out (139).
        expectedOutput.addAll(
            // Fabric API modules whose mixins do not fit Forge's patch are not loaded, nor the mods that need them
            // (ADR-0030).
            // 2026-10-27: no Fabric API module is left out next to Forge any more; All Stacks Infinite (a redirect of a
            // call Forge makes an instance call) and Dwarfs and Giants (a shadow field whose type Forge changed) are (224).
            corpusCounts("[Poly Mod Loader] Foreign mods: 122 loaded, 127 not loaded"),
            // FusionFix (Quilt) needs Fusion; Forge's build of Fusion, mostly the same classes, stands in for the Fabric
            // build it leaves out (ADR-0026 37).
            corpusCounts("mods that need fusion use MinecraftForge 26.2-65.1.5's build fusion 1.3.16", "share Fabric's Mixin"),
            corpusCounts("FusionFix has now started!", "share Fabric's Mixin"),
            corpusCounts(
                "MinecraftForge 26.2-65.1.5: 41 of 47 Forge mod file(s) meet their requirements; 6 left out",
                "MinecraftForge $minecraft-",
            ),
            corpusCounts("[Poly Mod Loader] Loaded mods per loader: 0 Poly, 51 Fabric, 71 Quilt, 42 Forge", "[Poly Mod Loader] Loaded mods per loader: "),
            corpusCounts("b-side-1.0.jar is built for several loaders (QUILT and Forge); MinecraftForge runs it", "share Fabric's Mixin"),
            corpusCounts("PLEXUS-COMPAT-139 Quilt mod voicechat (voicechat-quilt-2.6.25+26.2.jar) is NOT loaded: MinecraftForge 26.2-65.1.5 loads its own build of voicechat", "share Fabric's Mixin"),
            "share Fabric's Mixin",
            "Forge mod loading, version ",
            "All 1 required tests passed",
            worldsSavedLine,
        )
        unexpectedOutput.addAll(quiltCorpusUnexpected)
    },
)

// Real Quilt Loader, not Poly Mod Loader, on the vanilla dedicated server with the Quilt corpus (Quilt full support Q10):
// first the mods the Quilt-only builds require (the base), which must reach the EULA check, then each Quilt-only build
// on top of the base, the way a player installs it. The report (build/reports/poly/quilt-loader-reference-<version>.txt)
// says for each whether Quilt Loader reaches the EULA check, and with how many mods, or Quilt Loader's own error lines,
// to compare with what Poly Mod Loader loads (docs/COMPATIBILITY.md). Headless, network on the first run.
val quiltLoaderReferenceClasspath = configurations.create("quiltLoaderReference") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    // The libraries of Quilt Loader's own launcher profile (meta.quiltmc.org/v3/versions/loader/<game>/<loader>/server).
    quiltLoaderReferenceClasspath(libs.sponge.mixin)
    quiltLoaderReferenceClasspath(libs.quilt.json5)
    quiltLoaderReferenceClasspath(libs.quilt.reference.asm)
    quiltLoaderReferenceClasspath(libs.quilt.reference.asm.analysis)
    quiltLoaderReferenceClasspath(libs.quilt.reference.asm.commons)
    quiltLoaderReferenceClasspath(libs.quilt.reference.asm.tree)
    quiltLoaderReferenceClasspath(libs.quilt.reference.asm.util)
    quiltLoaderReferenceClasspath(libs.quilt.config)
    quiltLoaderReferenceClasspath(libs.quilt.loader)
}
val quiltCorpusLock = layout.projectDirectory.file("compat/mod-corpus-quilt-$minecraft.json").asFile
/** The jar files of the Quilt corpus whose lock entry's reason matches [quiltOnly] (a Quilt-only build, or required). */
fun quiltCorpusJars(quiltOnly: Boolean): FileCollection = files(corpusRoot.map { root ->
    if (!quiltCorpusLock.exists()) {
        listOf<File>()
    } else {
        @Suppress("UNCHECKED_CAST")
        val mods = (groovy.json.JsonSlurper().parse(quiltCorpusLock) as Map<String, Any>)["mods"] as List<Map<String, Any>>
        mods.filter { it.containsKey("file") && (it["reason"] == "quilt-only") == quiltOnly }
            .map { root.dir("quilt").file(it["file"] as String).asFile }
    }
})
tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.QuiltLoaderReferenceTask>("quiltLoaderReference") {
    group = "verification"
    description = "Runs real Quilt Loader on the vanilla dedicated server with each mod of the Quilt corpus (network on the first run)."
    dependsOn(modCorpusQuilt)
    launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>("prepareMinecraftServer")
        .flatMap { it.launchSpecFile })
    quiltLoaderClasspath.from(quiltLoaderReferenceClasspath)
    base.from(quiltCorpusJars(false))
    eachAlone.from(quiltCorpusJars(true))
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    runDirectory.set(layout.buildDirectory.dir("poly/quilt-loader-reference/$minecraft"))
    reportFile.set(layout.buildDirectory.file("reports/poly/quilt-loader-reference-$minecraft.txt"))
}

// Real Fabric API (from the corpus) used by the fabric-api-events test mod: a fake player breaks blocks with
// PlayerBlockBreakEvents listeners, recipes sit behind Fabric resource conditions, a crafting remainder is read through
// the stack. The same lines without and with MinecraftForge, whose patch moved the code Fabric API hooks there
// (ADR-0021 G8). Next to Forge the modules whose mixins do not fit Forge's patch are not loaded (ADR-0030).
val fabricApiEvents = if (minecraft == "26.2") {
    val mods = files(corpusRoot.map { it.dir("fabric").asFileTree.matching { include("fabric-api-*.jar") } }) +
        modJars(":test-mods:fabric-api-events", gameTestHarness)
    val lines = listOf(
        "[fabric_api_events] PlayerBlockBreakEvents.BEFORE minecraft:stone, block entity none",
        "[fabric_api_events] PlayerBlockBreakEvents.AFTER minecraft:stone, block entity none",
        "[fabric_api_events] survival destroyBlock(minecraft:stone) = true, block left: minecraft:air",
        "[fabric_api_events] PlayerBlockBreakEvents.BEFORE minecraft:chest, block entity minecraft:chest",
        "[fabric_api_events] PlayerBlockBreakEvents.AFTER minecraft:chest, block entity minecraft:chest",
        "[fabric_api_events] survival destroyBlock(minecraft:chest) = true, block left: minecraft:air",
        "[fabric_api_events] PlayerBlockBreakEvents.BEFORE minecraft:obsidian, block entity none",
        "[fabric_api_events] PlayerBlockBreakEvents.CANCELED minecraft:obsidian, block entity none",
        "[fabric_api_events] survival destroyBlock(minecraft:obsidian) = false, block left: minecraft:obsidian",
        "[fabric_api_events] PlayerBlockBreakEvents.BEFORE minecraft:dirt, block entity none",
        "[fabric_api_events] PlayerBlockBreakEvents.AFTER minecraft:dirt, block entity none",
        "[fabric_api_events] creative destroyBlock(minecraft:dirt) = true, block left: minecraft:air",
        "[fabric_api_events] FabricBlockState.getAppearance(minecraft:stone) = minecraft:stone",
        "[fabric_api_events] FabricBlockState.getProvidedEnchantmentPower(minecraft:bookshelf) = 1.0",
        "[fabric_api_events] enchanting table offers minecraft:sharpness for minecraft:diamond_sword: false",
        "[fabric_api_events] enchanting table offers minecraft:sharpness for minecraft:iron_sword: true",
        "[fabric_api_events] default max stack size of minecraft:stick: 16",
        "[fabric_api_events] recipe behind a met Fabric condition loaded: true",
        "[fabric_api_events] recipe behind an unmet Fabric condition loaded: false",
        "[fabric_api_events] crafting remainder of a lava bucket: minecraft:bucket",
        "All 1 required tests passed",
    )
    listOf(
        scenarios.gameTest("fabricApiGameTest", mods) {
            dependsOn(modCorpus)
            description = "Real Fabric API's block break events, resource conditions and crafting remainders on the vanilla game."
            expectedOutput.addAll(lines)
        },
        scenarios.gameTest("forgeFabricApiGameTest", mods) {
            dependsOn(modCorpus)
            classpath(forgeClassPath)
            systemProperty("poly.forge", "always")
            description = "Real Fabric API with MinecraftForge: every module but fabric-content-registries-v0 loaded."
            expectedOutput.addAll(lines)
            expectedOutput.addAll(
                "Fabric mod fabric_api_events 1.0.0 (fabric-api-events-",
                "Forge mod loading, version 65.1.5",
                // Forge moved the air supply and fluid jump code into FluidType hooks; ADR-0030 leaves the module out.
                "PLEXUS-COMPAT-224 1 mod(s) are NOT loaded",
                "MinecraftForge 26.2-65.1.5 patches it: fabric-content-registries-v0.",
                "useCustomEnchantingChecks into net/minecraft/world/item/enchantment/EnchantmentHelper",
            )
            unexpectedOutput.addAll("PLEXUS-COMPAT-225", "Critical injection failure")
        },
        // The same lines next to NeoForge, whose patch moved the code Fabric API hooks (ADR-0026 30): resource
        // conditions through NeoForge's conditional codec, block break events through events-interaction. The item
        // module is left out there (224); the remainder line then comes from NeoForge's own stack call.
        // NeoForge starts only with a NeoForge mod file in the game; the harness is one.
        scenarios.gameTest("neoForgeFabricApiGameTest", mods + modJars(":test-mods:neoforge-gametest-harness")) {
            dependsOn(modCorpus)
            runsNeoForge()
            description = "Real Fabric API's block break events, resource conditions and crafting remainders next to NeoForge."
            expectedOutput.addAll(lines)
            expectedOutput.addAll(
                "Fabric mod fabric_api_events 1.0.0 (fabric-api-events-",
                "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
                "@Inject skipData into net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener",
                // The block module runs as Poly Mod Loader's build on NeoForge's hooks (ADR-0031).
                "runs its build on the host's hooks instead (ADR-0031): fabric-block-api-v1-3.1.0+poly.neoforge.26.2.jar",
                "runs its build on the host's hooks instead (ADR-0031): fabric-item-api-v1-14.5.0+poly.neoforge.26.2.jar",
            )
            unexpectedOutput.addAll("PLEXUS-COMPAT-225", "Critical injection failure")
        },
    )
} else {
    emptyList()
}

// Diagnosis aid, not part of any check: the game test server with every jar of -PmodProbeDir=<folder>, such as a
// subset of the corpus while looking for the mod that breaks a run.
// -PmodProbeTarget=dedicated_server starts net.minecraft.server.Main instead, which stops at the EULA check.
providers.gradleProperty("modProbeDir").orNull?.let { folder ->
    scenarios.gameTest("modProbe", files(fileTree(folder) { include("*.jar") })) {
        // -PmodProbeForge: with MinecraftForge's file system jars on the class path, as the Forge scenarios run.
        if (providers.gradleProperty("modProbeForge").isPresent) {
            classpath(forgeClassPath)
        }
        if (providers.gradleProperty("modProbeTarget").orNull == "dedicated_server") {
            launchTarget.set("dedicated_server")
            gameArgumentOverrides.set(listOf<String>())
            expectedOutput.set(listOf("You need to agree to the EULA"))
        }
    }
}

// Diagnosis aid that opens a game window, not part of any check: the world clientWorldReloadTest opens, with the
// fabric-mixin test mod and every jar of -PclientProbeDir=<folder> (real Fabric mods). Run it alone, after the
// headless checks (AGENTS.md).
// One client verification scenario: every windowed client task reloads the world saved by worldCreateSaveTest through
// clientWorldReloadTest's launch spec, class path, JDK and transform cache; only the mods, the world name, the run
// folder and the expected lines differ.
fun clientWorldReload(name: String, configure: dev.plexusmodloader.devtools.gradle.minecraft.ClientWorldReloadTask.() -> Unit) {
    tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.ClientWorldReloadTask>(name) {
        group = "verification"
        val reload = tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.ClientWorldReloadTask>("clientWorldReloadTest")
        dependsOn("worldCreateSaveTest", "prepareMinecraftClient")
        launchSpecFile.set(reload.flatMap { it.launchSpecFile })
        // Through a Callable: reload.map would open clientWorldReloadTest's window first.
        classpath.from(Callable { reload.get().classpath })
        javaLauncher.set(reload.flatMap { it.javaLauncher })
        sourceWorld.set(reload.flatMap { it.sourceWorld })
        transformCacheDirectory.set(reload.flatMap { it.transformCacheDirectory })
        configure()
    }
}

providers.gradleProperty("clientProbeDir").orNull?.let { folder ->
    clientWorldReload("clientModProbe") {
        description = "Opens the saved world in the client with real Fabric mods from -PclientProbeDir (opens a window)."
        devModJars.from(modJars(":test-mods:fabric-mixin"))
        devModJars.from(fileTree(folder) { include("*.jar") })
        worldName.set("poly_fabric_probe")
        runDirectory.set(layout.buildDirectory.dir("poly/client-mod-probe"))
        timeoutSeconds.set(300)
        expectedOutput.addAll(
            "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.client.main.Main",
            "foreign mod(s) are initialised when Minecraft reaches its mod initialization point",
            "[Poly Mod Loader] Initialising",
            "[fabric_mixin] client createTitle @Inject ran",
            "joined the game",
        )
    }
}

// MinecraftForge on the client (ADR-0021 G7; opens a window, run it alone after the headless checks): Forge's client
// launch handler with the Forge test mods, and fabric-client-session, a Fabric mod that plays 200 ticks in the world
// worldCreateSaveTest saved and then saves and quits, so Forge and Fabric mods also run together on the client.
clientWorldReload("forgeClient") {
    description = "Opens the saved world in a MinecraftForge client with Forge test mods and a Fabric mod (opens a window)."
    classpath.from(forgeClassPath)
    systemProperties.add("fabric_client_session.titleScreenshot=true")
    devModJars.from(modJars(":test-mods:forge-hello", ":test-mods:forge-at", ":test-mods:forge-mixin", ":test-mods:fabric-client-session"))
    worldName.set("poly_forge_client")
    runDirectory.set(layout.buildDirectory.dir("poly/forge-client-$minecraft"))
    timeoutSeconds.set(300)
    expectedOutput.addAll(
        "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 loads 3 Forge mod(s) itself on Poly Mod Loader's game class loader",
        "Forge mod loading, version 65.1.5",
        // Poly Mod Loader's early loading window in place of Forge's (ForgeEarlyWindowOff), closed when the game's shows.
        "[Poly Mod Loader] Early loading window opened",
        "Early loading window: disabled net.minecraftforge.earlydisplay providing net.minecraftforge.fml.loading.ImmediateWindowProvider",
        "ImmediateWindowProvider fmlearlywindow, disabling",
        "[Poly Mod Loader] Early loading window closed",
        "[forge_hello] Common setup: item forge_hello:hello_item is registered",
        "[forge_at] Mth.SIN has 65536 entries",
        "joined the game",
        "[forge_mixin] tickServer @Inject ran",
        "[fabric_client_session] 200 client ticks in the world; save and quit",
        "[fabric_client_session] left the saved world; stopping the client",
        // fabric-client-session returns to the title screen before it stops the client. Forge's patch draws its
        // branding instead of the vanilla version text; Poly Mod Loader's lines go above Forge's (ForgeTitleBranding).
        "[Poly Mod Loader] Loaded mods per loader: 0 Poly, 1 Fabric, 0 Quilt, 3 Forge",
        "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 patched the target of Poly Mod Loader's optional rule poly:client_title_mod_counts; that rule is off",
        "[Poly Mod Loader] Title screen: 2 line(s) of mod counts drawn above MinecraftForge's branding: Fabric mods: 1 | Forge mods: 3",
    )
}

// A Fabric mod and a Forge mod together on the client, as a player reported (2026-10-23): Sodium with its Fabric API
// modules and Physics Mod. Forge patches LevelExtractor.extractVisibleBlockEntities to take a Frustum, where Sodium
// injects with the vanilla parameters; without the bridge (HostPatchAdjustments) Mixin rejected that handler and took
// Fabric API's mixins on the same class with it, and the first frame failed with a ClassCastException
// (LevelRendererExtensions). Opens a window; run it alone after the headless checks.
if (minecraft == "26.2") {
    clientWorldReload("forgeSodiumClient") {
        description = "Opens the saved world in a MinecraftForge client with Sodium (Fabric) and Physics Mod (Forge) (opens a window)."
        dependsOn(modCorpus)
        classpath.from(forgeClassPath)
        devModJars.from(modJars(":test-mods:fabric-client-session"))
        devModJars.from(corpusRoot.map { it.dir("fabric").asFileTree.matching { include("sodium-fabric-*.jar") } })
        devModJars.from(corpusRoot.map { it.dir("forge").asFileTree.matching { include("physics-mod-*.jar") } })
        worldName.set("poly_forge_sodium")
        runDirectory.set(layout.buildDirectory.dir("poly/forge-sodium-client-$minecraft"))
        timeoutSeconds.set(400)
        expectedOutput.addAll(
            "LevelExtractorMixin",
            "the injection runs with the patched parameters",
            "Forge mod loading, version 65.1.5",
            "joined the game",
            "[fabric_client_session] 200 client ticks in the world; save and quit",
            "[fabric_client_session] left the saved world; stopping the client",
        )
    }
}

// Physics Mod (Forge) breaking a block and a zombie in front of the player, alone and with Sodium (Fabric), with
// screenshots in <run>/screenshots/physics-*.png to look at (a player reported no debris or entity pieces with
// Sodium, 2026-10-23). fabric-client-session's physics probe does the breaking. Open a window; run them alone.
if (minecraft == "26.2") {
    for ((name, withSodium) in listOf("physicsProbeClient" to false, "physicsProbeSodiumClient" to true)) {
        clientWorldReload(name) {
            description = "Physics Mod breaks a block and a zombie on a MinecraftForge client" +
                (if (withSodium) " with Sodium" else "") + "; saves screenshots (opens a window)."
            dependsOn(modCorpus)
            classpath.from(forgeClassPath)
            devModJars.from(modJars(":test-mods:fabric-client-session"))
            devModJars.from(corpusRoot.map { it.dir("forge").asFileTree.matching { include("physics-mod-*.jar") } })
            if (withSodium) {
                devModJars.from(corpusRoot.map { it.dir("fabric").asFileTree.matching { include("sodium-fabric-*.jar") } })
            }
            systemProperties.add("fabric_client_session.physicsProbe=true")
            worldName.set(if (withSodium) "poly_physics_sodium" else "poly_physics")
            runDirectory.set(layout.buildDirectory.dir("poly/$name-$minecraft"))
            timeoutSeconds.set(400)
            expectedOutput.addAll(
                "Forge mod loading, version 65.1.5",
                "[fabric_client_session] physics probe: stone and a zombie at",
                "[fabric_client_session] physics probe: block broken and zombie killed",
                "[fabric_client_session] screenshot physics-after-30.png",
                "[fabric_client_session] left the saved world; stopping the client",
            )
        }
    }
}

// The real Forge mods of the corpus on a MinecraftForge client (ADR-0021 G7; opens a window, run it alone after the
// headless checks), with fabric-client-session to play 200 ticks in the saved world and quit.
clientWorldReload("corpusForgeClient") {
    description = "Opens the saved world in a MinecraftForge client with every Forge mod of the real-mod corpus (opens a window)."
    dependsOn(modCorpus)
    classpath.from(forgeClassPath)
    devModJars.from(modJars(":test-mods:fabric-client-session"))
    // Crash Assistant starts its own crash-report program on the client, which outlives the game and keeps the run
    // folder open.
    devModJars.from(corpusRoot.map {
        it.dir("forge").asFileTree.matching { include("*.jar"); exclude("CrashAssistant-*.jar"); exclude(brokenUnderForge) }
    })
    worldName.set("poly_forge_corpus")
    runDirectory.set(layout.buildDirectory.dir("poly/corpus-forge-client-$minecraft"))
    timeoutSeconds.set(400)
    expectedOutput.addAll(
        // The pinned Forge release of this Minecraft version (forge-releases.properties).
        "MinecraftForge $minecraft-",
        "Forge mod loading, version ",
        "joined the game",
        "[fabric_client_session] 200 client ticks in the world; save and quit",
        "[fabric_client_session] left the saved world; stopping the client",
    )
}

// NeoForge on the client (ADR-0026, N7; opens a window, run it alone after the headless checks): FML's client startup with
// Poly Mod Loader's early window in place of its own, the NeoForge test mod, a Poly Mod, and neoforge-client-session, a NeoForge mod that plays 200 ticks in
// the world worldCreateSaveTest saved, then saves, quits to the title screen and stops the client.
if (minecraft == "26.2") {
    clientWorldReload("neoForgeClient") {
        description = "Opens the saved world in a NeoForge client with the NeoForge test mod and a Poly Mod (opens a window)."
        javaAgents.from(polyAgentJar)
        devModJars.from(modJars(":test-mods:neoforge-hello", ":test-mods:neoforge-client-session", native, nativeLib))
        worldName.set("poly_neoforge_client")
        runDirectory.set(layout.buildDirectory.dir("poly/neoforge-client-$minecraft"))
        timeoutSeconds.set(300)
        expectedOutput.addAll(
            // Poly Mod Loader's early loading window; FML opens none (headless start).
            "[Poly Mod Loader] Early loading window opened",
            "Not loading early display in headless mode",
            "[Poly Mod Loader] Early loading window closed",
            "[Poly Mod Loader] NeoForge 26.2.0.93 loads 2 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
            "[Poly Mod Loader] Loaded mods per loader: 2 Poly, 0 Fabric, 0 Quilt, 0 Forge, 2 NeoForge",
            "[neoforge_hello] Constructed by FML in module neoforge_hello (neoforge_hello)",
            "[neoforge_hello] Common setup: item neoforge_hello:hello_item is registered",
            "[neoforge_hello] Poly Mod native_lib: isLoaded = true",
            "[minimal_native_mod] Client entrypoint called",
            "joined the game",
            "[neoforge_client_session] 200 client ticks in the world; save and quit",
            "[neoforge_client_session] left the saved world; stopping the client",
            // NeoForge's patch draws its branding instead of the vanilla version text; Poly Mod Loader's lines go above
            // NeoForge's (NeoForgeTitleBranding).
            "patched the target of Poly Mod Loader's optional rule poly:client_title_mod_counts; that rule is off",
            "[Poly Mod Loader] Title screen: 2 line(s) of mod counts drawn above NeoForge's branding: Poly mods: 2 | NeoForge mods: 2",
        )
    }
}

// Headless check of the client's Mixins with the corpus's NeoForge mods (no window): NeoForge's FML starts for the client
// without its early window (-Dpoly.neoforge.mixinAudit=true), and instead of the game Poly Mod Loader loads every class
// the mods' Mixin configurations target, through FML's class loader, and reports each that fails. Mod construction and
// rendering are not checked here (corpusNeoForgeClient opens the window for that).
scenarios.gameTest(
    "corpusNeoForgeClientMixins",
    files(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient)
        }
    }) + modJars(":test-mods:neoforge-client-session"),
) {
    dependsOn(modCorpus, "prepareMinecraftClient")
    description = "Loads the client classes the corpus's NeoForge mods' Mixins target, through NeoForge on the client, without a window."
    runsNeoForge()
    systemProperty("poly.neoforge.mixinAudit", "true")
    launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>("prepareMinecraftClient")
        .flatMap { it.launchSpecFile })
    launchTarget.set("client")
    gameArgumentOverrides.set(listOf<String>())
    // Poly Mod Loader's rule for the title screen's mod counts targets NeoForge's BrandingControl, in NeoForge's
    // universal jar; it must be planned there, not left off.
    unexpectedOutput.add("optional rule poly:client_title_mod_counts_neoforge; that rule is off")
    expectedOutput.set(listOf(
        "NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
        "Not loading early display in headless mode.",
        "Mixin audit: ",
        " Mixin configuration(s) loaded through FML's class loader, 0 failed",
    ))
}

// The client classes that the corpus's Fabric and NeoForge mods' Mixins target, loaded through NeoForge on the client
// with one Mixin serving both (ADR-0026 30), without a window: the headless check of the client side of
// corpusFabricNeoForge. A Fabric mixin that NeoForge's patch stops is reported (138) and does not fail a class.
scenarios.gameTest(
    "corpusFabricNeoForgeClientMixins",
    files(corpusRoot.map {
        it.dir("fabric").asFileTree.matching { include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient) }
    }) + files(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient)
        }
    }),
) {
    dependsOn(modCorpus, "prepareMinecraftClient")
    description = "Loads the client classes the corpus's Fabric and NeoForge mods' Mixins target, through NeoForge on the client, without a window."
    runsNeoForge()
    systemProperty("poly.neoforge.mixinAudit", "true")
    launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>("prepareMinecraftClient")
        .flatMap { it.launchSpecFile })
    launchTarget.set("client")
    gameArgumentOverrides.set(listOf<String>())
    expectedOutput.set(listOf(
        "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
        "Not loading early display in headless mode.",
        // One build of Cloth Config: NeoForge's (ADR-0026 37).
        "loads its own build of it as cloth_config (the same ID as NeoForge spells it)",
        "Mixin audit: ",
        " Mixin configuration(s) loaded through FML's class loader, 0 failed",
    ))
    if (minecraft == "26.2") {
        // Mixins that a left-out or failing mixin would take down with it (ADR-0026 39): Fabric API's guiRendererReady
        // casts to the interface of GuiRendererMixin, which NeoForge's GuiRenderer cannot take, and its wrap of
        // RenderPipeline.Snippet's constructor, which NeoForge gave another parameter, would make Mixin drop Iris's
        // PipelineBuilderMixin on the same class. The audit fails on code that casts to a missing mixin type.
        expectedOutput.addAll(
            "its handler guiRendererReady uses net/fabricmc/fabric/impl/client/rendering/GuiRendererExtensions",
            "at NEW Lcom/mojang/blaze3d/pipeline/RenderPipeline\$Snippet;: NeoForge 26.2.0.93's patch calls the constructor",
        )
    }
}

// The corpus's Fabric and NeoForge mods on a NeoForge client up to right before Minecraft.<init> creates the game window
// (ADR-0026 38): FML starts without its early window (poly.neoforge.headless), NeoForge loads its mods,
// Fabric mods initialise, and Fabric API's afterModInit freezes the built-in registries a second time after NeoForge;
// fabric-client-bootstrap-probe then stops the client. The headless check of corpusFabricNeoForgeClient's start.
scenarios.gameTest(
    "corpusFabricNeoForgeClientBootstrap",
    files(corpusRoot.map {
        it.dir("fabric").asFileTree.matching { include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient) }
    }) + files(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient)
        }
    }) + modJars(":test-mods:fabric-client-bootstrap-probe"),
) {
    dependsOn(modCorpus, "prepareMinecraftClient")
    description = "Starts a NeoForge client with the corpus's Fabric and NeoForge mods up to right before the game window, after mod initialization."
    runsNeoForge()
    systemProperty("poly.neoforge.headless", "true")
    systemProperty("fabric_client_bootstrap_probe.stop", "true")
    launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>("prepareMinecraftClient")
        .flatMap { it.launchSpecFile })
    launchTarget.set("client")
    gameArgumentOverrides.set(listOf<String>())
    expectedOutput.set(listOf(
        "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
        "Not loading early display in headless mode.",
        "[fabric_client_bootstrap_probe] Minecraft.<init> reached the game window after mod initialization; registries frozen: root = true, item = true, block = true",
    ))
    unexpectedOutput.addAll("Registry is already frozen", "PLEXUS-COMPAT-110")
}

// The real NeoForge mods of the corpus on a NeoForge client (ADR-0026, N7; opens a window, run it alone after the headless
// checks), with neoforge-client-session to play 200 ticks in the saved world and quit. The client-only mods that stop a
// NeoForge server (Sodium, Iris, ImmediatelyFast, Zoomify, ...) run here.
clientWorldReload("corpusNeoForgeClient") {
    description = "Opens the saved world in a NeoForge client with every NeoForge mod of the real-mod corpus (opens a window)."
    dependsOn(modCorpus)
    javaAgents.from(polyAgentJar)
    devModJars.from(modJars(":test-mods:neoforge-client-session", ":test-mods:neoforge-environment-probe"))
    devModJars.from(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient)
        }
    })
    worldName.set("poly_neoforge_corpus")
    runDirectory.set(layout.buildDirectory.dir("poly/corpus-neoforge-client-$minecraft"))
    timeoutSeconds.set(400)
    expectedOutput.addAll(
        "NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
        "[Poly Mod Loader] FML ",
        "joined the game",
        "[neoforge_environment_probe] Fabric Loader API visible: false, Quilt Loader API visible: false",
        "[neoforge_client_session] 200 client ticks in the world; save and quit",
        "[neoforge_client_session] left the saved world; stopping the client",
        "line(s) of mod counts drawn above NeoForge's branding",
    )
    if (lambDynamicLightsOnNeoForgeClient) {
        expectedOutput.add("for lambdynlights finds lambdynlights.toml: true")
    }
}

// The corpus's Fabric and NeoForge mods in one NeoForge client (ADR-0026 30; opens a window, run it alone after the
// headless checks, corpusFabricNeoForgeClientMixins first): one Mixin serves both, neoforge-client-session plays 200
// ticks in the saved world and quits.
clientWorldReload("corpusFabricNeoForgeClient") {
    description = "Opens the saved world in a NeoForge client with every Fabric and NeoForge mod of the real-mod corpus (opens a window)."
    dependsOn(modCorpus)
    javaAgents.from(polyAgentJar)
    devModJars.from(modJars(":test-mods:neoforge-client-session"))
    devModJars.from(corpusRoot.map {
        it.dir("fabric").asFileTree.matching { include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient) }
    })
    devModJars.from(corpusRoot.map {
        it.dir("neoforge").asFileTree.matching {
            include("*.jar"); exclude(brokenUnderNeoForge); exclude(brokenOnNeoForgeClient)
        }
    })
    worldName.set("poly_fabric_neoforge_corpus")
    runDirectory.set(layout.buildDirectory.dir("poly/corpus-fabric-neoforge-client-$minecraft"))
    timeoutSeconds.set(500)
    expectedOutput.addAll(
        "Fabric or Quilt mod(s) run together; one Mixin serves both (ADR-0026 30)",
        "share Fabric's Mixin",
        // One build of Cloth Config, NeoForge's: with both, its example config was registered twice and the client
        // stopped (ADR-0026 37).
        "loads its own build of it as cloth_config (the same ID as NeoForge spells it)",
        "joined the game",
        "[neoforge_client_session] 200 client ticks in the world; save and quit",
        "[neoforge_client_session] left the saved world; stopping the client",
    )
}

// The same installation with Forge mods (ADR-0024; opens a window, run it alone after the headless checks): the
// profile's -javaagent puts Forge's file systems on the class path, and Forge's client runs Forge mods with
// fabric-client-session, which plays 200 ticks in the saved world and then saves and quits (as forgeClient does in
// development). On 26.2 the Forge mods are the Forge test mods, which are built against 26.2's Forge; on another
// version they are the real Forge mods of that version's corpus, as corpusForgeClient runs them (Forge full support B6).
launcherProfile("launcherProfileForgeTest", "poly/launcher-profile-forge-$minecraft/minecraft") {
    description = "Installs Poly Mod Loader with the installer jar and starts the profile like the Minecraft Launcher with Forge mods (opens a window)."
    modJars.from(modJars(":test-mods:fabric-client-session"))
    worldName.set("poly_profile_forge")
    expectedOutput.add("[Poly Mod Loader] Added Forge's file systems to the JVM class path")
    if (minecraft == "26.2") {
        modJars.from(modJars(":test-mods:forge-hello", ":test-mods:forge-at", ":test-mods:forge-mixin"))
        timeoutSeconds.set(300)
        expectedOutput.addAll(
            "[Poly Mod Loader] MinecraftForge 26.2-65.1.5 loads 3 Forge mod(s) itself on Poly Mod Loader's game class loader",
            "Forge mod loading, version 65.1.5",
            "[forge_hello] Common setup: item forge_hello:hello_item is registered",
            "[forge_at] Mth.SIN has 65536 entries",
            "joined the game",
            "[forge_mixin] tickServer @Inject ran",
        )
    } else {
        dependsOn(modCorpus)
        // The jars corpusForgeClient runs (Crash Assistant's own program outlives the game and keeps the folder open).
        modJars.from(corpusRoot.map {
            it.dir("forge").asFileTree.matching { include("*.jar"); exclude("CrashAssistant-*.jar"); exclude(brokenUnderForge) }
        })
        timeoutSeconds.set(400)
        expectedOutput.addAll(
            // The pinned Forge release of this Minecraft version (forge-releases.properties).
            "MinecraftForge $minecraft-",
            "Forge mod loading, version ",
            "joined the game",
        )
    }
    expectedOutput.addAll(
        "[fabric_client_session] 200 client ticks in the world; save and quit",
        "[fabric_client_session] left the saved world; stopping the client",
    )
}

// The same installation with NeoForge mods (ADR-0026, N9; opens a window, run it alone after the headless checks): the
// profile's -javaagent opens java.lang.invoke to FML's modules, and NeoForge's client runs NeoForge mods with
// neoforge-client-session, which plays 200 ticks in the saved world and then saves and quits. On 26.2 the NeoForge mods
// are the NeoForge test mod; on another version they are the real NeoForge mods of that version's corpus, as
// corpusNeoForgeClient runs them.
launcherProfile("launcherProfileNeoForgeTest", "poly/launcher-profile-neoforge-$minecraft/minecraft") {
    description = "Installs Poly Mod Loader with the installer jar and starts the profile like the Minecraft Launcher with NeoForge mods (opens a window)."
    modJars.from(modJars(":test-mods:neoforge-client-session"))
    worldName.set("poly_profile_neoforge")
    if (minecraft == "26.2") {
        modJars.from(modJars(":test-mods:neoforge-hello"))
        timeoutSeconds.set(300)
        expectedOutput.addAll(
            "[Poly Mod Loader] NeoForge 26.2.0.93 loads 2 NeoForge mod file(s) itself on Poly Mod Loader's game class loader",
            "[neoforge_hello] Common setup: item neoforge_hello:hello_item is registered",
        )
    } else {
        dependsOn(modCorpus)
        modJars.from(corpusRoot.map {
            it.dir("neoforge").asFileTree.matching { include("*.jar"); exclude(brokenUnderNeoForge) }
        })
        timeoutSeconds.set(400)
        expectedOutput.add("NeoForge mod file(s) itself on Poly Mod Loader's game class loader")
    }
    expectedOutput.addAll(
        "[Poly Mod Loader] Phase GAME_LAUNCH: NeoForge ",
        "joined the game",
        "[neoforge_client_session] 200 client ticks in the world; save and quit",
        "[neoforge_client_session] left the saved world; stopping the client",
    )
    // Without Forge mods nothing of Forge is downloaded (ADR-0024).
    absentPaths.add(".poly/forge")
}

// The real-mod corpus on the client (opens a window, so it is not part of compatibilityCorpus; run it alone after the
// headless checks, AGENTS.md): every Fabric mod of the corpus plus fabric-mixin, in the world worldCreateSaveTest
// saved, up to joining it. Fabric's preLaunch, Mixin on client classes (Sodium, Iris, Lithium) and the client
// initialisation point in the Minecraft constructor all run here.
clientWorldReload("corpusFabricClient") {
    description = "Opens the saved world in the client with every Fabric mod of the real-mod corpus (opens a window)."
    dependsOn(modCorpus)
    // fabric-client-session plays 200 ticks in the world, then saves and quits as a player does.
    devModJars.from(modJars(":test-mods:fabric-mixin", ":test-mods:fabric-client-session"))
    devModJars.from(corpus("fabric"))
    // CustomSkinLoader's one jar for every loader sits in the corpus's forge folder; players put it in a Fabric
    // mods folder too, and it is the mod that needs Fabric Loader's launcher (FabricLauncherBase.addToClassPath).
    devModJars.from(corpusRoot.map { it.dir("forge").asFileTree.matching { include("CustomSkinLoader_Universal-*.jar") } })
    worldName.set("poly_fabric_corpus")
    runDirectory.set(layout.buildDirectory.dir("poly/corpus-fabric-client-$minecraft"))
    timeoutSeconds.set(300)
    expectedOutput.addAll(
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.client.main.Main",
        corpusCounts("[Poly Mod Loader] Foreign mods: 140 loaded, 37 not loaded"),
        corpusCounts("[Poly Mod Loader] Initialising 140 foreign mod(s)", "[Poly Mod Loader] Initialising "),
        // CustomSkinLoader adds its common jar with FabricLauncherBase and reads version.json through the system class
        // loader to pick the rules for this version.
        "(CustomSkinLoader Bootstrap) Added CustomSkinLoader Common jar to Fabric class path",
        "(CustomSkinLoader Bootstrap) Transformed Fabric target net.minecraft.client.resources.SkinManager with",
        "[fabric_mixin] client createTitle @Inject ran",
        "[fabric_mixin] getGameInstance() is this client: true",
        corpusCounts("(Iris) Shaders are disabled because no valid shaderpack is selected", "joined the game"),
        "joined the game",
        "[fabric_client_session] 200 client ticks in the world; save and quit",
        // Only when the integrated server stops (opening the world saves too).
        "(MinecraftServer) Stopping server",
        "(MinecraftServer) Saving worlds",
        "[fabric_client_session] left the saved world; stopping the client",
    )
}

// The Quilt corpus on the client (Quilt full support Q6; opens a window, so it is not part of compatibilityCorpus; run it
// alone after the headless checks, AGENTS.md): every jar of modCorpusQuilt plus quilt-hello, in the world
// worldCreateSaveTest saved; 200 ticks in the world, save and quit, then the client stops.
clientWorldReload("corpusQuiltClient") {
    description = "Opens the saved world in the client with every mod of the Quilt mod corpus (opens a window)."
    dependsOn(modCorpusQuilt)
    devModJars.from(modJars(":test-mods:fabric-client-session", ":test-mods:quilt-hello"))
    devModJars.from(corpusRoot.map {
        it.dir("quilt").asFileTree.matching { include("*.jar"); exclude(brokenOnQuiltClient) }
    })
    worldName.set("poly_quilt_corpus")
    // One folder per Minecraft version, so each version's client log stays after the next version's run.
    runDirectory.set(layout.buildDirectory.dir("poly/corpus-quilt-client/$minecraft"))
    timeoutSeconds.set(300)
    expectedOutput.addAll(
        "[Poly Mod Loader] Phase GAME_LAUNCH: net.minecraft.client.main.Main",
        "[Poly Mod Loader] Foreign mods: ",
        // quilt_hello's client side: Quilt Loader's API, the client entrypoint, and Quilt's stripping on the client.
        "[quilt_hello] client entrypoint ran",
        "[quilt_hello] MinecraftQuiltLoader.getEnvironmentType() = CLIENT, QuiltLoader.getRawGameVersion() = $minecraft",
        "[quilt_hello] stripped on CLIENT: @ClientOnly method present = true, @DedicatedServerOnly method present = false",
        "joined the game",
        "[fabric_client_session] 200 client ticks in the world; save and quit",
        // Only when the integrated server stops (opening the world saves too).
        "(MinecraftServer) Stopping server",
        "(MinecraftServer) Saving worlds",
        "[fabric_client_session] left the saved world; stopping the client",
    )
}

// The same corpus assessed for client and server without starting the game (ModCorpusAssessmentTest).
dependencies {
    testImplementation(libs.gson)
    testImplementation(project(":plexus-bridge-api"))
}
tasks.register<Test>("corpusAssessment") {
    group = "verification"
    description = "Assesses the real-mod corpus for client and server: each mod alone and all together (no game)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*ModCorpusAssessmentTest") }
    // The Quilt corpus sits in the same folder (quilt/) and is assessed with the rest.
    dependsOn("prepareMinecraftClient", "prepareMinecraftServer", modCorpus, modCorpusQuilt)
    val launchSpecs = layout.buildDirectory.dir("poly/minecraft")
    systemProperty("poly.corpus", corpusRoot.get().asFile.absolutePath)
    systemProperty("poly.clientSpec", launchSpecs.get().file("$minecraft-client.json").asFile.absolutePath)
    systemProperty("poly.serverSpec", launchSpecs.get().file("$minecraft-server.json").asFile.absolutePath)
    systemProperty("poly.corpusReport", layout.buildDirectory.dir("reports/poly").get().asFile.absolutePath)
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}
// The Quilt corpus alone, as a Quilt player installs it, for the client and the server (Quilt full support Q6, Q7): the
// verdicts on the client side without opening a window (build/reports/poly/quilt/).
tasks.register<Test>("corpusAssessmentQuilt") {
    group = "verification"
    description = "Assesses the Quilt mod corpus alone for client and server: each mod alone and all together (no game)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*ModCorpusAssessmentTest") }
    dependsOn("prepareMinecraftClient", "prepareMinecraftServer", modCorpusQuilt)
    val launchSpecs = layout.buildDirectory.dir("poly/minecraft")
    systemProperty("poly.corpus", corpusRoot.get().dir("quilt").asFile.absolutePath)
    systemProperty("poly.clientSpec", launchSpecs.get().file("$minecraft-client.json").asFile.absolutePath)
    systemProperty("poly.serverSpec", launchSpecs.get().file("$minecraft-server.json").asFile.absolutePath)
    systemProperty("poly.corpusReport", layout.buildDirectory.dir("reports/poly/quilt").get().asFile.absolutePath)
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}

// Every mixin of the Quilt corpus applied to its targets without starting the game (Quilt full support Q6): the
// fabric-mixin-audit harness loads, without initialising, each class a Mixin configuration of that side targets and
// reports each one Mixin fails, then stops the JVM before the game's main class runs. The client variant therefore
// opens no window. Headless; a FAILED line fails the task.
listOf("client", "server").forEach { side ->
    val auditName = "mixinAuditQuilt" + side.replaceFirstChar(Char::uppercase)
    val auditMods = files(corpusRoot.map {
        it.dir("quilt").asFileTree.matching { include("*.jar"); if (side == "client") exclude(brokenOnQuiltClient) }
    })
    scenarios.gameTest(auditName, auditMods + modJars(":test-mods:fabric-mixin-audit")) {
        dependsOn(modCorpusQuilt)
        description = "Applies every $side mixin of the Quilt mod corpus to its targets, without starting the game."
        val prepare = if (side == "client") "prepareMinecraftClient" else "prepareMinecraftServer"
        launchSpecFile.set(tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.PrepareMinecraftTask>(prepare)
            .flatMap { it.launchSpecFile })
        launchTarget.set(if (side == "client") "client" else "dedicated_server")
        // A second guard for the client: without the required --accessToken, Minecraft's client main stops at its
        // option parser, before any window, should the harness ever not stop the JVM first.
        gameArgumentOverrides.set(if (side == "client") listOf("--polyMixinAuditOnly") else listOf<String>())
        systemProperty("poly.mixinAudit", "true")
        expectedOutput.set(listOf("[poly_mixin_audit] audited "))
        unexpectedOutput.set(listOf("[poly_mixin_audit] FAILED", "[Poly Mod Loader] Phase GAME_LAUNCH"))
    }
}

tasks.register("compatibilityCorpus") {
    group = "verification"
    description = "Real mods from Modrinth: no-game assessment for both sides and game test server runs (network)."
    dependsOn(
        corpusScenarios, quiltCorpusScenarios, fabricApiEvents, "corpusAssessment", "corpusNeoForgeClientMixins",
        "corpusFabricNeoForgeClientMixins", "corpusFabricNeoForgeClientBootstrap",
        "mixinAuditQuiltClient", "mixinAuditQuiltServer",
    )
}

tasks.register("compatibilityMatrix") {
    group = "verification"
    description = "Runs every mixed-loader and regression scenario on the game test server."
    dependsOn(matrix)
}

// Performance with real Fabric mods (Fabric plan F7): the same startup and tick benchmarks as above, with the corpus's
// Fabric mods (as corpusFabric runs them on the game test server) in the Poly Mod Loader variant. The vanilla variant runs no
// mods, so the difference is what those mods cost through Poly Mod Loader, not Poly Mod Loader against Fabric Loader.
val fabricBenchmarkMods = gameTestCorpus("fabric") + modJars(gameTestHarness)
// One benchmark comparison: the startup and tick benchmarks of every host loader wire the base task's launch spec, Poly
// Mod Loader runtime, JDK, run count and transform cache the same way; only the mod set, the loader's own class path
// wiring and the description differ.
fun startupBenchmark(loader: String, mods: FileCollection, configure: dev.plexusmodloader.devtools.gradle.minecraft.StartupBenchmarkTask.() -> Unit) {
    tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.StartupBenchmarkTask>("startupBenchmark$loader") {
        group = "verification"
        val base = tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.StartupBenchmarkTask>("startupBenchmark")
        launchSpecFile.set(base.flatMap { it.launchSpecFile })
        // Through a Callable: base.map would make this task run the base benchmark too.
        polyRuntime.from(Callable { base.get().polyRuntime })
        devModJars.from(mods)
        javaLauncher.set(base.flatMap { it.javaLauncher })
        runs.convention(5)
        workDirectory.set(layout.buildDirectory.dir("poly/startup-benchmark-${loader.lowercase()}"))
        transformCacheDirectory.set(base.flatMap { it.transformCacheDirectory })
        configure()
    }
}

fun tickBenchmark(loader: String, mods: FileCollection, configure: dev.plexusmodloader.devtools.gradle.minecraft.TickBenchmarkTask.() -> Unit) {
    tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.TickBenchmarkTask>("tickBenchmark$loader") {
        group = "verification"
        val base = tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.TickBenchmarkTask>("tickBenchmark")
        launchSpecFile.set(base.flatMap { it.launchSpecFile })
        // Through a Callable: base.map would make this task run the base benchmark too.
        polyRuntime.from(Callable { base.get().polyRuntime })
        devModJars.from(mods)
        probeAgent.from(tickProbe)
        javaLauncher.set(base.flatMap { it.javaLauncher })
        runs.convention(5)
        holdTicks.convention(1200)
        warmupTicks.convention(200)
        providers.gradleProperty("tickBenchmarkHeap").orNull?.let { heapSize = it }
        workDirectory.set(layout.buildDirectory.dir("poly/tick-benchmark-${loader.lowercase()}"))
        transformCacheDirectory.set(base.flatMap { it.transformCacheDirectory })
        configure()
    }
}

startupBenchmark("Fabric", fabricBenchmarkMods) {
    description = "Game test server startup-to-exit time: vanilla versus Poly Mod Loader with the corpus's Fabric mods."
    dependsOn(modCorpus)
}
tickBenchmark("Fabric", fabricBenchmarkMods) {
    description = "Server tick times, heap and GC: vanilla versus Poly Mod Loader with the corpus's Fabric mods."
    dependsOn(modCorpus)
}
// The same comparisons with the corpus's Forge mods (Forge full support B7): MinecraftForge runs them on Poly Mod
// Loader's side, with Forge's file systems on the class path as in the other Forge scenarios. Not a comparison with
// Forge's own launcher.
val forgeBenchmarkMods = gameTestCorpus("forge")
startupBenchmark("Forge", forgeBenchmarkMods) {
    description = "Game test server startup-to-exit time: vanilla versus Poly Mod Loader with the corpus's Forge mods."
    dependsOn(modCorpus)
    polyRuntime.from(forgeClassPath)
}
tickBenchmark("Forge", forgeBenchmarkMods) {
    description = "Server tick times, heap and GC: vanilla versus Poly Mod Loader with the corpus's Forge mods."
    dependsOn(modCorpus)
    polyRuntime.from(forgeClassPath)
}
// The same comparisons with the corpus's NeoForge mods (NeoForge full support N12): NeoForge runs them on Poly Mod
// Loader's side (with Poly Mod Loader's Java agent, as the launcher profile starts it, and the harness that ticks game
// tests, which NeoForge ticks only in development), as corpusNeoForge runs them. Not a comparison with NeoForge's own
// launcher.
val neoForgeBenchmarkMods = files(corpusRoot.map {
    it.dir("neoforge").asFileTree.matching {
        include("*.jar"); exclude(notOnNeoForgeServer); exclude(neoForgeClientClassesOnServer); exclude(brokenUnderNeoForge)
    }
}) + modJars(":test-mods:neoforge-gametest-harness")
startupBenchmark("NeoForge", neoForgeBenchmarkMods) {
    description = "Game test server startup-to-exit time: vanilla versus Poly Mod Loader with the corpus's NeoForge mods."
    dependsOn(modCorpus)
    polyJavaAgents.from(polyAgentJar)
}
tickBenchmark("NeoForge", neoForgeBenchmarkMods) {
    description = "Server tick times, heap and GC: vanilla versus Poly Mod Loader with the corpus's NeoForge mods."
    dependsOn(modCorpus)
    polyJavaAgents.from(polyAgentJar)
}

// The same comparisons with the Quilt corpus (Quilt full support Q11): the Quilt-only builds and the mods they require,
// as corpusQuilt runs them on the game test server. Vanilla runs no mods, so the difference is what those mods cost
// through Poly Mod Loader, not Poly Mod Loader against Quilt Loader.
// Craftable Bucket of Sulfur Cube and its Easy variant list sulfur:tick in minecraft:tick but ship no such function, so
// the game refuses the whole minecraft:tick tag ("Couldn't load tag minecraft:tick as it is missing following
// references", vanilla's TagLoader) and no data pack's tick function runs, the benchmark's own included. Omni's common
// mixin on LivingEntity.jumpFromGround tests "instanceof LocalPlayer", a client class, so a server stops with
// NoClassDefFoundError as soon as a mob jumps (the benchmark's pigs do), under Quilt Loader too. They are left out of
// the benchmark only, so that it measures its workload.
val quiltBenchmarkMods = files(corpusRoot.map {
    it.dir("quilt").asFileTree.matching {
        include("*.jar")
        exclude(notOnGameTestServer)
        exclude("craftable-bucket-of-sulfur-cube-*.jar", "omni-*.jar")
    }
}) + modJars(gameTestHarness)
startupBenchmark("Quilt", quiltBenchmarkMods) {
    description = "Game test server startup-to-exit time: vanilla versus Poly Mod Loader with the Quilt mod corpus."
    dependsOn(modCorpusQuilt)
}
tickBenchmark("Quilt", quiltBenchmarkMods) {
    description = "Server tick times, heap and GC: vanilla versus Poly Mod Loader with the Quilt mod corpus."
    dependsOn(modCorpusQuilt)
}

// Poly Mod Loader against Sinytra Connector on real NeoForge (docs/PERFORMANCE.md, measurement 9): the same startup and
// tick benchmarks with a third variant, a NeoForge 26.2.0.93 dedicated server installed by NeoForge's own installer
// (installNeoForgeReferenceServer; Poly Mod Loader is not involved there) with Sinytra Connector, Forgified Fabric API
// and Launchpad, the mods Connector's README lists as required (compat/connector-reference-26.2.json, pinned by SHA-512
// like the corpus). Both sides run only the corpus's Fabric mods that both load on the game test server (checked
// 2026-10-24 by running each side with the whole corpus): Fabric API on the Poly Mod Loader side, Forgified Fabric API
// on the Connector side, each side's own equivalent. Each side also gets its own game test harness: Fabric API freezes
// registries only from the dedicated server's main (fabric-gametest-harness), and NeoForge in production ticks no game
// tests (neoforge-gametest-harness). Left out because Connector does not load them (each one stops the server):
// - architectury, balm, lithium: a Mixin fails its injection check on a method NeoForge patched
//   (MixinBaseSpawner.checkSpawnerSpawn, LivingEntityMixin.clearAllEffects, block.hopper.LevelMixin).
// - puzzleslib: Connector's jar transformer stops on one of its Mixin classes ("Error parsing property 'slice'").
// - ForgeConfigAPIPort (Fabric build): it ships its own net.neoforged.fml.config classes, and NeoForge's own fail to
//   link (LinkageError, loader constraint violation on IConfigSpec).
// - zoomify: its universal jar also has a neoforge.mods.toml, which NeoForge reads, and asks for the language provider
//   klf (KotlinLangForge), which is not installed; it is a client mod.
// Connector loads lambdynamiclights (and the libraries it nests) on the server too, which Poly Mod Loader leaves out as
// client-only (PLEXUS-COMPAT-108, as Fabric Loader does), so it is not in the common set either.
if (minecraft == "26.2") {
    val modCorpusConnector = tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.ModCorpusTask>("modCorpusConnector") {
        group = "verification"
        description = "Downloads Sinytra Connector and the mods it requires, listed in compat/connector-reference-$minecraft.json."
        lockFile = layout.projectDirectory.file("compat/connector-reference-$minecraft.json")
        outputDirectory = corpusRoot
        gameVersion = minecraft
        loaders = listOf("connector")
        // The lock lists Connector and its requirements by hand; there is nothing to rebuild from Modrinth's top list.
        refresh = false
        topPerLoader = 0
    }
    val neoForgeReference = libs.versions.neoforge.get()
    val installNeoForgeReferenceServer =
        tasks.register<dev.plexusmodloader.devtools.gradle.minecraft.NeoForgeServerInstallTask>("installNeoForgeReferenceServer") {
            group = "verification"
            description = "Installs a real NeoForge $neoForgeReference dedicated server with NeoForge's own installer (network), for the Connector benchmark."
            neoForgeVersion = neoForgeReference
            // The installer Poly Mod Loader itself checks for 26.2 (poly-bridge-forge, neoforge-releases.properties).
            installerSha1 = "97205829a020fc9389f504a77f590b54157c8dde"
            javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
            serverDirectory.set(layout.buildDirectory.dir("poly/neoforge-reference/$neoForgeReference/server"))
        }
    val connectorCommonMods = listOf(
        "appleskin-fabric-*", "cloth-config-*", "collective-*", "entityculling-fabric-*", "fabric-language-kotlin-*",
        "fancymenu_fabric_*", "ferritecore-*-fabric", "geckolib-fabric-*", "Jade-*-Fabric-*", "jei-*-fabric-*",
        "konkrete_fabric_*", "melody_fabric_*", "mezz_config-*-fabric-*", "notenoughanimations-fabric-*",
        "placeholder-api-*", "skinlayers3d-fabric-*", "veinminer-fabric-*", "voicechat-fabric-*",
        "yet_another_config_lib_v3-*-fabric",
    ).map { "$it.jar" }
    val connectorPolyMods = files(corpusRoot.map {
        it.dir("fabric").asFileTree.matching { include(connectorCommonMods); include("fabric-api-*.jar") }
    }) + modJars(gameTestHarness)
    val connectorReferenceMods = files(corpusRoot.map { it.dir("fabric").asFileTree.matching { include(connectorCommonMods) } }) +
        files(corpusRoot.map { it.dir("connector").asFileTree.matching { include("*.jar") } }) +
        modJars(":test-mods:neoforge-gametest-harness")
    // Every run must load the same mods: Poly Mod Loader's count (nested jars included; the 23 not loaded are older
    // copies of nested libraries and Fabric API's client modules), and FML's mod list (its "Name version (id)" lines).
    val connectorPolyExpected = listOf("[Poly Mod Loader] Foreign mods: 89 loaded, 23 not loaded")
    val connectorReferenceExpected = listOf(
        "Sinytra Connector 3.1.0-beta.3+26.2 (connector)", "Forgified Fabric API 0.161.0+26.2+4.0.2 (fabric_api)",
        "Launchpad 1.10.2+26.2 (launchpad)", "AppleSkin 3.0.10+mc26.2 (appleskin)",
        "Cloth Config v26.2 26.2.155 (cloth_config)", "Collective 8.60 (collective)", "EntityCulling 1.11.3 (entityculling)",
        "Fabric Language Kotlin 1.14.2+kotlin.2.4.21 (fabric_language_kotlin)", "FancyMenu 3.9.14 (fancymenu)",
        "FerriteCore 9.0.0 (ferritecore)", "GeckoLib 5 5.5.5 (geckolib)", "Jade 26.2.11+fabric (jade)",
        "Just Enough Items 30.47.0.239 (jei)", "Konkrete 1.11.1 (konkrete)", "Melody 1.0.17 (melody)",
        "MezzConfig 0.6.8 (mezz_config)", "NotEnoughAnimations 1.12.5 (notenoughanimations)",
        "Placeholder API 3.1.0-beta.1+26.2 (placeholder_api)", "3d-Skin-Layers 1.11.4 (skinlayers3d)",
        "Veinminer 2.12.1 (veinminer)", "Simple Voice Chat 2.6.24+26.2 (voicechat)",
        "YetAnotherConfigLib 3.9.7+26.2-fabric (yet_another_config_lib_v3)",
    )
    startupBenchmark("Connector", connectorPolyMods) {
        description = "Game test server startup: vanilla, Poly Mod Loader with Fabric mods, and NeoForge with Sinytra Connector and the same Fabric mods."
        dependsOn(modCorpus, modCorpusConnector, installNeoForgeReferenceServer)
        // -PstartupBenchmarkRuns=<n>; -PtickBenchmarkHeap=<size> pins the heap of all three variants, as in the tick run.
        providers.gradleProperty("startupBenchmarkRuns").orNull?.let { runs = it.toInt() }
        providers.gradleProperty("tickBenchmarkHeap").orNull?.let { heapSize = it }
        referenceName = "connector"
        referenceServer.set(installNeoForgeReferenceServer.flatMap { it.serverDirectory })
        referenceNeoForgeVersion = neoForgeReference
        referenceModJars.from(connectorReferenceMods)
        polyExpectedOutput.set(connectorPolyExpected)
        referenceExpectedOutput.set(connectorReferenceExpected)
    }
    tickBenchmark("Connector", connectorPolyMods) {
        description = "Server tick times, heap and GC: vanilla, Poly Mod Loader with Fabric mods, and NeoForge with Sinytra Connector and the same Fabric mods."
        dependsOn(modCorpus, modCorpusConnector, installNeoForgeReferenceServer)
        providers.gradleProperty("tickBenchmarkRuns").orNull?.let { runs = it.toInt() }
        providers.gradleProperty("tickBenchmarkTicks").orNull?.let { holdTicks = it.toInt() }
        referenceName = "connector"
        referenceServer.set(installNeoForgeReferenceServer.flatMap { it.serverDirectory })
        referenceNeoForgeVersion = neoForgeReference
        referenceModJars.from(connectorReferenceMods)
        polyExpectedOutput.set(connectorPolyExpected)
        referenceExpectedOutput.set(connectorReferenceExpected)
    }
}
