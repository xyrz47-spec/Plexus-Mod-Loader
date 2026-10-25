# ADR-0019: Supporting obfuscated Minecraft versions (before 26.1)

- Status: proposed (2026-10-21). The facts below were read from Mojang's files. Update 2026-10-24: the remap step and the self-check are implemented, and the PoC criteria below are met for the 1.21.11 dedicated server (see "PoC result"). 1.21.11 is listed as an *experimental* version, not a supported one. Acceptance waits for the project owner's call on the mappings licence (point 3).
- Related: ADR-0001 (target 26.2, Java 25), ADR-0003 (game class loader), ADR-0004 (transformation engine), ADR-0005 (shared libraries), ADR-0010 (game layer), ADR-0011 (several Minecraft versions), ADR-0017 (launcher profile), ADR-0018 (class tweakers, `official` namespace)

## Context
Poly Mod Loader compiles and runs against the game's real class names. That works because Minecraft 26.1 and later ship without obfuscation (ADR-0001). Each step below depends on it:
- The game layer `poly-game-v<id>` is compiled against the client jar (ADR-0010).
- Transformation rules name `owner.name descriptor` as `javap` prints them from that jar (ADR-0004).
- The link check compares mod bytecode with the game jar (ADR-0014).
- The Fabric bridge accepts only class tweakers in the `official` namespace (ADR-0018, `POLY-COMPAT-126`).

The project owner wants older versions too. The launcher files of 1.8.9, 1.12.2, 1.14.4, 1.15.2, 1.16.4, 1.16.5, 1.17.1, 1.18.1, 1.18.2, 1.19.4, 1.20.6, 1.21.10 and 1.21.11 are in `MinecraftFiles/<version>/` (git-ignored). In all of them, the game classes sit in the default package under short names (`a`, `fvz`, `dzq`). Only entry points and a few annotated classes keep their names. ADR-0011 left this open: "a mapping layer for older (obfuscated) versions does not exist yet".

### Facts per version
Sources: each `<version>.json` and `<version>.jar` in `MinecraftFiles/` (read by scratch scripts, 2026-10-21). Server jars were checked with HTTP range requests on their zip central directory, without downloading them in full. Server mapping files of 1.14.4, 1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6 and 1.21.11 were downloaded, checked against their SHA-1, and searched by name.

| Version | `javaVersion` | Class major (client) | Launch format | Mojang mappings | LWJGL / natives | Server jar | log4j | slf4j in libraries | gson | `version.json` in jar (pack formats) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1.8.9 | 8 | 50 | `minecraftArguments` | none | 2.9.4-nightly, 4 natives-only entries | plain jar, `Main-Class: net.minecraft.server.MinecraftServer` | 2.0-beta9 | none | 2.2.4 | **absent** |
| 1.12.2 | 8 | 52 | `minecraftArguments` | none | 2.9.4-nightly, 2 natives-only entries | plain jar, `MinecraftServer` | 2.8.1 | none | 2.8.0 | **absent** |
| 1.14.4 | 8 | 52 | `arguments` | client 4.7 MB, server 3.6 MB | 3.2.1/3.2.2, `natives` + `classifiers` | plain jar, `MinecraftServer` | 2.8.1 | none | 2.8.0 | yes (4) |
| 1.15.2 | 8 | 52 | `arguments` | yes | 3.2.1/3.2.2, `classifiers` | plain jar, `MinecraftServer` | 2.8.1 | none | 2.8.0 | yes (5) |
| 1.16.4 | 8 | 52 | `arguments` | yes | 3.2.1/3.2.2, `classifiers` | plain jar, `net.minecraft.server.Main` | 2.8.1 | none | 2.8.0 | yes (6) |
| 1.16.5 | 8 | 52 | `arguments` | client 5.7 MB, server 4.4 MB | 3.2.1/3.2.2, `classifiers` | plain jar, `net.minecraft.server.Main` | 2.8.1 | none | 2.8.0 | yes (6) |
| 1.17.1 | 16 | 60 | `arguments` | yes | 3.2.1/3.2.2, `classifiers` | plain jar, `net.minecraft.server.Main` | 2.14.1 | 1.8.0-beta4 | 2.8.0 | yes (7/7) |
| 1.18.1 | 17 | 61 | `arguments` | yes | 3.2.1/3.2.2, `classifiers` | bundler | 2.14.1 | 1.8.0-beta4 | 2.8.8 | yes (8/8) |
| 1.18.2 | 17 | 61 | `arguments` | yes | 3.2.1/3.2.2, `classifiers` | bundler | 2.17.0 | 1.8.0-beta4 | 2.8.9 | yes (8/9) |
| 1.19.4 | 17 | 61 | `arguments` | yes | 3.3.1, natives as separate artifacts | bundler | 2.19.0 | 2.0.1 | 2.10 | yes (13/12) |
| 1.20.6 | 21 | 65 | `arguments` | client 9.4 MB, server 7.3 MB | 3.3.3, separate artifacts | bundler | 2.22.1 | 2.0.9 | 2.10.1 | yes (32/41) |
| 1.21.10 | 21 | 65 | `arguments` | yes | 3.3.3, separate artifacts | bundler | 2.24.1 | 2.0.16 | 2.11.0 | yes (69.0/88.0) |
| 1.21.11 | 21 | 65 | `arguments` | client 11.8 MB, server 8.7 MB | 3.3.3, separate artifacts | bundler | 2.25.2 | 2.0.17 | 2.13.2 | yes (75.0/94.1) |
| 26.2 (today) | 25 | 69 | `arguments` | none needed | 3.4.1, separate artifacts | bundler | 2.26.0 | 2.0.17 | 2.14.0 | yes (88.0/107.1) |

Other things the files show:
- **Obfuscation.** 1.21.11 has 10,201 classes in the default package, 6,574 of them with names of one to four lowercase letters. Inner classes are renamed too (`GpuBuffer$a`). Classes that keep their names include `net.minecraft.client.main.Main`, `net.minecraft.client.ClientBrandRetriever`, `net.minecraft.server.MinecraftServer` (most of its members are obfuscated, but not all; `getServerModName` keeps its name), `net.minecraft.server.Main` (1.16 and later), and `net.minecraft.gametest.Main` (1.21.10 and 1.21.11 only). The server mappings map the class to itself for 7 classes in 1.14.4 and 18 in 1.21.11.
- **Plain server jars carry their libraries.** Every plain server jar (1.8.9–1.17.1) contains libraries such as `com/google`, `io/netty` and `org/apache`, and 1.17.1 also `org/slf4j`, next to the game classes. From 1.18.1 the bundler lists one server jar in `META-INF/versions.list`, with `META-INF/main-class` set to `net.minecraft.server.Main`.
- **The jars are signed.** Every client jar has `META-INF/MOJANGCS.SF` and `MOJANGCS.RSA`.
- **The mappings header** (first bytes of `client_mappings` for 1.14.4 and 1.21.11, fetched with a range request) reads:
  > (c) 2020 Microsoft Corporation. These mappings are provided "as-is" and you bear the risk of using them. You may copy and use the mappings for development purposes, but you may not redistribute the mappings complete and unmodified. Microsoft makes no warranties, express or implied, with respect to the mappings provided here. Use and modification of this document or the source code (in any form) of Minecraft: Java Edition is governed by the Minecraft End User License Agreement […]

  The format is ProGuard (`named -> obfuscated:`, member lines with line ranges). From 1.21.x it also has `# {"fileName":…}` comments.
- **Game tests.** Searched by Mojang name in the server mappings:

  | | 1.14.4 | 1.15.2 | 1.16.5 | 1.17.1 | 1.18.2 | 1.19.4 | 1.20.6 | 1.21.11 |
  |---|---|---|---|---|---|---|---|---|
  | `gametest.framework.GameTestServer` | absent | `jb` | `lk` | `mf` | `np` | `qj` | `tt` | `th` |
  | `gametest.framework.GameTestRegistry` (code-registered tests) | absent | yes | yes | yes | yes | yes | yes | **absent** (tests are data-driven) |
  | `net.minecraft.gametest.Main` (headless test server) | absent | absent | absent | absent | absent | absent | absent | present, unobfuscated |

  `worldCreateSaveTest` and `tickBenchmark` start `net.minecraft.gametest.Main` (`BenchmarkLaunch`), so as written they can run only on 1.21.10 and later.
- **Hook targets of the 26.2 adapter** (looked up by Mojang name in the server mappings):
  - 1.21.11 and 1.20.6 have all of these, with the same signatures as 26.2: `BuiltInRegistries.freeze()`, `KnownPack`, `Commands.<init>(CommandSelection, CommandBuildContext)`, `PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)`, `CustomPacketPayload.codec(FallbackProvider, List)`, `BuiltInPackSource.loadPacks(Consumer)`, `WorldDimensions.checkStability`. One difference: `MinecraftServer.createLevels` takes a `ChunkProgressListener` in 1.20.6.
  - 1.19.4 has `BuiltInRegistries.freeze()`, `BuiltInPackSource.loadPacks`, `WorldDimensions.checkStability` and the two-argument `Commands.<init>`. It has no `KnownPack` and no `CustomPacketPayload`, and `placeNewPlayer` takes `(Connection, ServerPlayer)`.
  - 1.18.2 and 1.17.1 have none of `BuiltInRegistries`, `KnownPack`, `CustomPacketPayload`, `BuiltInPackSource` or `WorldDimensions`. Their `Commands.<init>` takes only `CommandSelection`.
  - `MinecraftServer.getServerModName()` keeps its name and descriptor in every mapping file checked (1.14.4–1.21.11). `Block.playerWillDestroy` keeps its parameter list in 1.17.1–1.21.11. Its return type is `void` in 1.19.4 and older (as checked) and `BlockState` in 1.20.6 and later.
  - Only the targets named here were looked up (13 lookups). The 26.2 adapter has 46 rule IDs, so the rest are unchecked.
- **What the build rejects today**, read from `MinecraftDistributionInstaller` (not run: a version that is not in `gradle/minecraft-versions.properties` is refused before the installer runs):
  - `checkClientLaunchFormat` rejects 1.8.9 and 1.12.2 (`minecraftArguments`) before anything else.
  - A library without `downloads.artifact` is rejected ("natives-only entries"). This affects only 1.8.9 and 1.12.2.
  - **1.14.4–1.18.2 pass this check but would not work.** Their LWJGL entries have an `artifact` *and* `natives`/`classifiers`. The installer takes only the artifact, so the native libraries are never fetched. A client is then expected to fail when LWJGL loads (inferred from the code, not run).
  - `installServer` requires a bundler (`META-INF/versions.list`) and rejects every server up to 1.17.1.
  - The installer ignores `client_mappings`/`server_mappings`.
- **What Poly Mod Loader itself assumes:**
  - **Java 25 at runtime.** `poly-runtime-impl` uses `java.lang.classfile` (`ModLinkVerifier`, `ForeignAccessWidenings`), which is final only since JDK 24. Every module targets Java 25.
  - **A newer gson.** `poly-mod-metadata` calls `JsonParser.parseString`, which gson added in 2.8.6. 1.8.9–1.17.1 ship 2.2.4 or 2.8.0, and ADR-0005/ADR-0017 put Minecraft's gson on the class path in place of Poly Mod Loader's.
  - **slf4j from the game.** Poly Mod Loader logs through slf4j-api 2.0 (ADR-0005). Minecraft ships no slf4j up to 1.16.5, and 1.17–1.18 ship 1.8.0-beta4.
  - **`version.json` in the game jar.** `PolyRuntime` and `ClassPathGameJarLocator` identify the game by it (`POLY-BOOTSTRAP-001`, `-006`). 1.8.9 and 1.12.2 do not have one.
  - **A Java 25 toolchain for version modules.** `java.<version>` in `minecraft-versions.properties` sets the toolchain of that version's modules (`MinecraftCompilePlugin`). Those modules depend on `poly-game-api`, which is compiled for Java 25. A value below 25 would therefore not compile.

## Options considered

### A. Remap the game jar to Mojang names and run the remapped jar
The build, or the installer on the player's machine, downloads the official mappings for the version and checks their SHA-1 against the version JSON. It writes a copy of the client jar, or of the server jar taken out of the bundler, with every class and member renamed to Mojang's names and the signature files removed. That copy is the game jar the game class loader defines (ADR-0003). NeoForge and Paper have also run Mojang-named code at runtime since 1.20.5, as their projects announced (not checked here).
- **For:** the game layer, transformation rules, `javap` checks, the link check, transform reports and stack traces all use one namespace, as on 26.x. Name strings in the game layer keep working unchanged: reflection on `Options.keyMappings` and `BiomeGenerationSettings.features` (ADR-0010) and `MethodHandles` lookups by name (`MinecraftServerView`). The ADR-0011 workflow (`newMinecraftVersion`, fix compile errors, `javap`) applies as is. Mod transform rules (ADR-0007) are in the same namespace as built-in ones. No namespace field has to be added to `TransformRuleDefinition`.
- **Against:** the remapped jar is Minecraft code and must be produced on each machine. It may never be published, and neither may the mappings. The player's first start downloads 9–12 MB of mappings and remaps about 10,000 classes (time not measured). The remapper must handle the class hierarchy: a call through a subclass of a method declared in a superclass, interface bridges, `invokedynamic` and lambda names, record components, and inner-class attributes. Game code that relies on obfuscated names at runtime would change behaviour. Gson reflection over Mojang classes without `@SerializedName` would change JSON keys. Whether any such code exists has not been checked. The launcher still puts the obfuscated vanilla jar on the system class path (ADR-0017), so the locator must pick the remapped jar.

### B. Keep the obfuscated runtime and remap Poly Mod Loader to obfuscated names at build time
The game layer is still written and compiled against Mojang names. A build step then remaps its bytecode, and every rule target, to the obfuscated names of that version, like Fabric Loom (`remapJar`, intermediary) or Forge before 1.20.5 (SRG). The game jar runs unmodified.
- **For:** the player's game jar is byte-identical to vanilla. No mappings are downloaded or remapping done on the player's machine, so the first start is quicker. Fabric does the same thing at runtime with intermediary.
- **Against:** every name string must be remapped or resolved through a table shipped with the adapter: rule owners, names and descriptors (ADR-0004), reflection and `MethodHandles` lookups in the game layer, and class tweaker targets. A string that is missed fails only at runtime. Mod-declared transform rules (ADR-0007) would need either obfuscated names for each version or a runtime resolver, so a rule namespace has to be added everywhere. Stack traces, transform reports and `-Dpoly.transformDumpDir` output are obfuscated. Every build output for these versions needs a remap step, and its tests must run on the remapped output.

### C. Drop versions that have no official mappings
1.8.9 and 1.12.2 have no `client_mappings`/`server_mappings`. Supporting them would mean community mappings (MCP, Yarn, Feather and others), each with its own license and coverage, on top of the other gaps listed in the facts.

Fabric mods are a separate question for both A and B. On 1.21.11 and older, Fabric mods are compiled against Fabric's intermediary names, not Mojang names. Under A, the Fabric bridge would have to remap each mod from intermediary to Mojang names when it loads. Under B, it would remap to obfuscated names. Either way Poly Mod Loader needs the intermediary mappings (published by FabricMC; license to be checked, believed to be CC0-1.0), and that needs its own ADR. Until then, a Fabric mod on a pre-26.1 version is blocked with a new `POLY-COMPAT-` code (number assigned when implemented). Its class tweaker would also be blocked by `POLY-COMPAT-126`, since its namespace is `intermediary`.

## Decision (proposed)
1. **Option A for 1.14.4 through 1.21.11.** Poly Mod Loader runs these versions with a game jar remapped to Mojang names using Mojang's official mappings. The reason is one namespace everywhere: string-based access in the game layer, built-in and mod transform rules, the link check and diagnostics stay as they are on 26.x. B moves the remap into every name string, and misses show up only at runtime.
2. **Option C for 1.8.9 and 1.12.2: out of scope.**
   - They have no official mappings.
   - Their JSONs use `minecraftArguments`.
   - LWJGL 2 is natives-only.
   - Their jars have no `version.json`.
   - 1.8.9 uses log4j 2.0-beta9, which predates `%msg{nolookups}`.
   - Their servers are plain jars.

   Reconsider only if the owner accepts a community mapping set and its license.
3. **Mappings stay on the user's machine.**
   - They are downloaded from the URL in the version JSON and checked against its SHA-1, or taken from `MinecraftFiles/<version>/client.txt` / `server.txt` after the same check.
   - They are kept in the Gradle cache, or in the launcher's folder for players.
   - They are never committed, put in a published artifact or bundled in the installer.
   - The same applies to remapped jars. This follows the license header quoted above, which allows development use and forbids redistributing them "complete and unmodified", and the EULA, which forbids redistributing game code. *The header was read; how the license applies to a player-side remap is the project owner's call and should be checked.*
4. **The remapper uses ASM.**
   - It is ASM's `ClassRemapper` with a hierarchy-aware `Remapper`, built from a ProGuard mappings parser and the class hierarchy of the game jar and its libraries.
   - ASM 9.11.1 is already a dependency, so no new third-party code ships.
   - Output is deterministic and cached by (jar SHA-1, mappings SHA-1, remapper revision), like the transform cache (ADR-0007).
   - Signature files are removed. `version.json`, resources and the manifest's `Main-Class` are kept.
   - tiny-remapper (LGPL-3.0), SpecialSource and NeoForge's AutoRenamingTool were not evaluated. Their licenses need checking before any is considered.
5. **Every supported version runs on Java 25.**
   - The bootstrap and runtime need JDK 24+ (`java.lang.classfile`) and are built for 25 (ADR-0001/0002). A JVM per version (8, 16, 17, 21) would mean building all version-independent modules for Java 8.
   - So `java.<version>=25` for every pre-26 entry in `gradle/minecraft-versions.properties`. The value now means "the Java Poly Mod Loader runs this version on", not `javaVersion.majorVersion`. The ADR-0011 text and the `newMinecraftVersion -Pjava` hint change accordingly.
   - Class files of major 50–65 load on JDK 25. Whether each game *runs* there is a test result, not an assumption.
   - Extra JVM options (`--add-opens`, `--sun-misc-unsafe-memory-access=allow`, `--enable-native-access=ALL-UNNAMED`) are added per version only when a recorded failure needs them. Each is written down with that failure.
   - The launcher profile must ask the launcher for Java 25 instead of the vanilla `jre-legacy`/`java-runtime-*`. *Whether a child version JSON's `javaVersion` overrides the parent's in the official launcher is unverified.*
6. **Libraries that Minecraft ships too old or not at all are Poly Mod Loader's job, per version.**
   - For gson below 2.8.6, Poly Mod Loader either relocates its own gson or stops using newer API. That choice is made when tier 3 starts.
   - Where Minecraft has no slf4j 2.0, Poly Mod Loader brings slf4j-api 2.0 plus the log4j binding that matches the game's log4j.
   - ADR-0005's pinning still applies wherever Minecraft ships the library.
7. **Order.** Each tier starts only after the previous one has passed its checks.
   - **Tier 1: 1.21.11, then 1.21.10, then 1.20.6.**
     - Java 21 bytecode.
     - LWJGL 3.3.x natives as separate artifacts, which the installer already handles.
     - Bundler servers.
     - Every 26.2 hook target that was checked is present (see the facts).
     - `net.minecraft.gametest.Main` on 1.21.10 and later. 1.20.6 needs another way to run game tests, through `GameTestServer` and `GameTestRegistry`.
   - **Tier 2: 1.19.4, 1.18.2, 1.18.1, 1.17.1.**
     - Java 16/17 bytecode.
     - 1.17–1.18 need LWJGL `classifiers` natives extracted.
     - 1.17.1 needs a plain-server path. Its server jar shades gson, netty, log4j and slf4j, which must not be defined twice next to the shared libraries of ADR-0005.
     - The game layer needs new hooks: no `KnownPack` or `CustomPacketPayload` in any of them, and no `BuiltInRegistries` or `BuiltInPackSource` in 1.17.1–1.18.2.
     - 1.17.1 and 1.18.1 ship log4j 2.14.1 (Log4Shell). Every launch must use `%msg{nolookups}` or Mojang's patched `logging` configuration. The dev configuration `log4j2-dev.xml` already uses `%msg{nolookups}`.
   - **Tier 3: 1.16.5, 1.16.4, 1.15.2, 1.14.4.**
     - Java 8 bytecode on Java 25.
     - No slf4j; gson 2.8.0.
     - Plain servers; `MinecraftServer` as server main up to 1.15.2.
     - Game tests through `GameTestRegistry` from 1.15.2. None in 1.14.4.
   - **Out of scope:** 1.8.9, 1.12.2 (point 2).
8. **Version support is claimed only per version.** A version counts as supported only after it passes its own checks: `serverBootSmokeTest`, `nativeApiGameTests` or what replaces it, and `compatibilityMatrix`, each run with `-PpolyMinecraft=<version>`. The results go in `docs/COMPATIBILITY.md`. A Native API feature a version's game layer cannot provide must fail clearly (`CapabilityResult.unsupported`, ADR-0011), not be skipped.

## Per-version plan
"New hooks" means the 26.2 hook target is missing or has a different signature in that version. "Game tests" says how `nativeApiGameTests` and `worldCreateSaveTest` can run there.

| Version | Tier | Installer / runtime work | Game layer work (beyond the remap) | Game tests |
|---|---|---|---|---|
| 1.21.11 | 1 (PoC) | mappings + remap; Java 25 profile | copy of 26.2; fix what fails to compile | `net.minecraft.gametest.Main`, data-driven tests as in 26.2 |
| 1.21.10 | 1 | same as 1.21.11 | copy of 1.21.11, or the same adapter if the targets match (ADR-0011 point 4) | as 1.21.11 |
| 1.20.6 | 1 | same | `createLevels(ChunkProgressListener)`; check the rest of the 46 rules | no `gametest.Main`: start `GameTestServer` another way; code-registered tests (`GameTestRegistry`) |
| 1.19.4 | 2 | same; slf4j 2.0.1, gson 2.10 | new hooks for networking (no `CustomPacketPayload`), pack identity (no `KnownPack`), `placeNewPlayer(Connection, ServerPlayer)`, `void playerWillDestroy` | `GameTestRegistry` |
| 1.18.2, 1.18.1 | 2 | + LWJGL 3.2 `classifiers` natives; slf4j 1.8.0-beta4 next to Poly Mod Loader's slf4j 2.0; 1.18.1 log4j 2.14.1 needs `nolookups` | + content registration without `BuiltInRegistries`, packs without `BuiltInPackSource`, `Commands(CommandSelection)` | `GameTestRegistry` |
| 1.17.1 | 2 | + plain server jar with shaded libraries | as 1.18 | `GameTestRegistry` |
| 1.16.5, 1.16.4 | 3 | + no slf4j in the game; gson 2.8.0 (relocate or avoid `parseString`); Java 8 bytecode on 25 | as 1.17, plus older world-gen and data pack code | `GameTestRegistry` (present in 1.16.5) |
| 1.15.2 | 3 | as 1.16; server main is `MinecraftServer` | as 1.16 | `GameTestRegistry` (present in 1.15.2) |
| 1.14.4 | 3 | as 1.15.2 | as 1.16 | none (no game test framework in the mappings): server boot and world save only |
| 1.12.2, 1.8.9 | out of scope | no official mappings; `minecraftArguments`; LWJGL 2; no `version.json` | — | — |

## What the build needs
- Progress (2026-10-24): `minecraftVersionDiff -Pfrom=26.1 -Pto=<obfuscated version>` downloads `downloads.client_mappings` with the SHA-1 check (or seeds `MinecraftFiles/<version>/client.txt`), keeps it in the Gradle cache, reads it (`MojangMappings` in build-logic) and compares the 26.1 modules with the jar through Mojang names. Nothing is remapped yet; server mappings are not downloaded. Results: [`docs/plans/MINECRAFT_1_21.md`](../plans/MINECRAFT_1_21.md) (tier 1) and [`docs/plans/MINECRAFT_1_16_5.md`](../plans/MINECRAFT_1_16_5.md) (tier 3: 200 used classes missing, 539 members not found; 53 of the 62 game layer files of 26.1 import a missing class).
- Progress (2026-10-24): `remapMinecraft -Pminecraft=<version>` remaps the client jar with ASM (`MojangJarRemapper` in build-logic, hierarchy-aware, lambdas included), keeps it in the Gradle cache keyed by (jar SHA-1, mappings SHA-1, remapper revision), checks that two runs give the same SHA-1, and runs the self-check below (`RemappedJarCheck`, and `RemappedJarLoadCheck` in a Java 25 JVM that links every class without initializing it). All eleven versions from 1.14.4 to 1.21.11 pass: no default-package class left, no duplicate member, every field and method reference to a game class resolves, and every class loads and links except `MacosUtil` (1.17.1 and later), which needs the macOS-only `ca.weblite.objc`. Remap time 3.8–14.2 s on the development PC with frames computed again (revision 2). Two findings: the mappings of 1.14.4–1.16.5 list 278–302 classes the jar does not have (package-info and classes the obfuscator removed), so the check is "every jar class is in the mappings", not the reverse; and the client jar is not yet the compile or `gameJar` input, and server jars are not remapped. Results: [`docs/plans/MINECRAFT_1_21.md`](../plans/MINECRAFT_1_21.md).
- **`MinecraftDistributionInstaller` / `LocalMinecraftFiles`:**
  - Download `downloads.client_mappings` and `server_mappings` with the SHA-1 check, and seed them from `MinecraftFiles/<version>/`.
  - Remove the `natives-only` rejection for `classifiers` entries, and extract the natives for the current OS (`natives` map, `extract.exclude`).
  - Add a plain-server path that separates game classes from shaded libraries (tier 2 and later).
  - Keep refusing `minecraftArguments` with a message that points to this ADR.
- **A remap step** (task or part of `prepareMinecraft`), whose output is the `gameJar` of the launch spec and the jar `poly.minecraft-compile` compiles version modules against.
- **Rule checking against the remapped jar.** Run the adapter's rules through `ClassTransformPlanner` against the remapped jar in a test, so a wrong owner, name or descriptor fails `check` and not the first game start. Manual `javap` checks (AGENTS.md) are made against the remapped jar.
- **A remap self-check.** Every class in the mappings exists under its Mojang name. No default-package class is left except those that map to themselves. Every class loads with `initialize=false` in an isolated loader, so the verifier sees each class once.
- **The installer and runtime for players** (ADR-0017): download the mappings and remap on the player's machine at install time or on first start. Ask for Java 25. Make the locator prefer the remapped jar over the vanilla jar on the class path.
- **`newMinecraftVersion`:** `-Pfrom=26.2 -Pto=1.21.11 -Pjava=25` works as is (module ID `1_21_11`). The guide must say not to take `-Pjava` from `javaVersion.majorVersion` for pre-26 versions.

## Risks
- **Untested JVM.** Mojang never ran these versions on Java 25. Known areas:
  - LWJGL 3.2.x and Netty 4.1.25 use `sun.misc.Unsafe`, which JDK 24+ warns about.
  - Strong encapsulation since JDK 16/17 may need `--add-opens` on Java 8-era versions.
  - log4j 2.8.1 and Guava 21 on a modern JDK.

  None of this was run here. Only test results count.
- **Remapping correctness.** A missed inherited member or lambda name gives `NoSuchMethodError` or `AbstractMethodError` late in the game. The self-check and full class loading reduce this but do not remove it.
- **Name-dependent game code.** Gson reflection or `Class.forName` on obfuscated names inside Minecraft would change behaviour after remapping. Scan each jar before claiming a version.
- **License and EULA.** Rests on the mappings header and on never redistributing. See decision 3.
- **Cost of copied game layers** (ADR-0011): ten more versions means ten more copies of about 55 game layer files and 46 rules. The ADR-0011 trigger to share code between versions ("three or more versions") is met. Sharing is decided separately, after tier 1.
- **Fabric ecosystem.** Without intermediary support, the bridge does nothing on old versions. Most of the value of old versions (1.12.2, 1.16.5, 1.20.x mod packs) is in existing Forge and Fabric mods.

## PoC result (2026-10-24)
- All six acceptance criteria below hold; the details and the 57 compile fixes are in [`docs/plans/MINECRAFT_1_21.md`](../plans/MINECRAFT_1_21.md). `serverBootSmokeTest -PpolyMinecraft=1.21.11` passes on Java 25 with **no added JVM option**. The server remap takes about 4.5 s on the development PC.
- **Experimental versions.** `gradle/minecraft-versions.properties` gained `experimental=`: such a version has modules the build compiles and tests and `-PpolyMinecraft` can run, but `runnable()` (the installer), the SDK and the website leave it out, `poly-bootstrap` carries its adapter only when the build runs it, and `installerPayload` refuses such a build. This keeps point 8: nothing claims 1.21.11 works for players.
- **Finding: obfuscated jars have minimal stack map frames.** The obfuscator keeps only live locals in a frame, so the frame at a method's last `return` can hold no locals, and a hook that `InvokeHookBeforeReturn` inserts there cannot load `this` (a `VerifyError` in `Entity.load`). The remapper now computes frames again, javac-style, with superclasses from the jar, the version's libraries and the JDK (remapper revision 2). The transform engine is unchanged.
- **Finding: version-specific data.** `worldCreateSaveTest -PpolyMinecraft=1.21.11` fails because the example mod `native_showcase` ships a 26.x dimension type (`minecraft:visual/ambient_light_color` does not exist in 1.21.11). With that line removed for one run, world creation, the example mods' rules, the game test and the save passed. How one mod ships data for several data pack formats is not decided.
- **Not done yet:** Fabric mods are not blocked on 1.21.11 (a test mod that touches no game class runs through the bridge); the client was not started; nothing is remapped on players' machines.

## First step / PoC
Goal: **the 1.21.11 dedicated server boots under Poly Mod Loader from a remapped jar, and a Native mod receives one Native API event.**

Scope: server only, no client, no Fabric mods, no game tests.

Acceptance criteria. All must hold, and the commands and results go in the CHANGELOG:
1. `server_mappings` of 1.21.11 is downloaded or seeded and checked against the SHA-1 in `1.21.11.json`. Nothing from it is committed.
2. The server jar taken out of the bundler is remapped:
   - Same output hash on two runs.
   - No default-package classes left except identity-mapped ones.
   - Signature files removed, `version.json` kept.
   - Every class loads with `initialize=false`.
3. `newMinecraftVersion -Pfrom=26.2 -Pto=1.21.11 -Pjava=25` creates `poly-version-adapter-v1_21_11` and `poly-game-v1_21_11`, and both compile against the remapped jar. Game layer features that do not compile are removed or answer `unsupported`. They are not left half-working.
4. At least `poly:server_brand` (`net/minecraft/server/MinecraftServer.getServerModName()Ljava/lang/String;`) and `poly:player_joined`, or one server lifecycle event, pass the planner against the remapped jar. `.poly/transformation-report.json` lists them as applied, with no rejected rule.
5. `.\gradlew.bat :poly-bootstrap:serverBootSmokeTest -PpolyMinecraft=1.21.11` passes on Java 25. A test Native mod's handler for a server lifecycle event logs its line. Any JVM option that had to be added is written down with the failure that needed it.
6. `.\gradlew.bat check` and `.\gradlew.bat -p build-logic check` pass. The 26.2 checks are unchanged.

If the PoC passes, this ADR is accepted, with the measured remap time and any JVM options added. If it fails on the remap or the JVM, the failure is recorded here and option B is reconsidered.

## Consequences
- Poly Mod Loader would support versions only from 1.14.4. 1.8.9 and 1.12.2 stay unsupported.
- One namespace (Mojang names) everywhere. Only the remap step and its cache know about obfuscation.
- `minecraft-versions.properties` `java.<version>` means the runtime Java (25), not the vanilla Java.
- The installer and the first start get heavier: a mappings download and a remap.
- Each tier adds installer work (natives, plain servers, libraries) as well as game layer work. None of it is implemented yet.
