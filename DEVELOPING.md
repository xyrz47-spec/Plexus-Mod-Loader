# Poly Mod Loader — Developer & Contributor Guide

Welcome to the Poly Mod Loader contributor documentation. This guide provides all necessary instructions to clone, build, test, debug, and contribute to Poly Mod Loader.

---

## 1. Prerequisites & Environment Setup

### Required Tools
- **Java Development Kit (JDK) 25:** Poly Mod Loader is built with and targets Java 25. Recommended: [Eclipse Temurin 25](https://adoptium.net/) or Microsoft Build of OpenJDK 25.
  ```powershell
  java -version   # Must output java version "25" or higher
  ```
- **Git 2.40+**

---

## 2. Getting the Code & Building

```powershell
# Clone the repository
git clone https://github.com/xyrz47-spec/Poly-Mod-Loader.git
cd Poly-Mod-Loader

# Verify code formatting and run all unit tests
.\gradlew.bat check

# Build the distribution installer (.jar)
.\gradlew.bat :poly-installer:installerDistributions
```

> **Note on Minecraft Assets:** On the first run, the build automatically downloads the required Minecraft client/server assets from Mojang's official servers and verifies their integrity against SHA-1 checksums. Minecraft files are cached locally in `~/.gradle/caches/poly-minecraft` and are never committed to the repository.

---

## 3. Key Gradle Tasks

| Task | Target / Action | Notes |
| :--- | :--- | :--- |
| `.\gradlew check` | Runs Spotless format checks and all unit test suites across all modules. | Mandatory before submitting any PR or commit. |
| `.\gradlew spotlessApply` | Automatically formats all Java sources according to project style. | Uses palantir-java-format (4 spaces, 120 columns), removes unused imports. |
| `.\gradlew :poly-installer:installerDistributions` | Builds `PolyModLoader-<version>.jar`, the only download for every platform. | Outputs to `poly-installer/build/distributions/`. |
| `.\gradlew :poly-bootstrap:runClient` | Launches the Minecraft client in development mode with Poly active. | Minecraft 26.2 client with debug hooks. |
| `.\gradlew :poly-bootstrap:runServer` | Launches the dedicated server in development mode. | Requires accepting `run/server/eula.txt`. |
| `.\gradlew :poly-bootstrap:serverBootSmokeTest` | Headless smoke test: boots server, verifies Poly runtime initialization, stops. | Fast, CI-friendly check (no UI). |
| `.\gradlew :poly-bootstrap:worldCreateSaveTest` | Creates a world, executes tick lifecycle, saves to disk, and exits cleanly. | Verifies save and data compatibility. |
| `.\gradlew publishSdk` | Builds and publishes the Poly SDK to the local Maven cache (`.poly/sdk-maven`). | Used by mod developers targeting Native API. |

`serverBootSmokeTest`, `worldCreateSaveTest` and the other game tests load the mods in `examples/` and `test-mods/`, which this public repository leaves out (`settings.gradle.kts`); here they stop with a message. `check`, `spotlessApply`, `installerDistributions`, `runClient`, `runServer` and `publishSdk` work without them.

Every pull request and every push to `main` runs `.github/workflows/ci.yml` on GitHub Actions (Windows, JDK 25): `check`, `-p build-logic check` and `installerDistributions`.

---

## 4. Multi-Project Module Hierarchy

Poly Mod Loader is structured as a decoupled multi-project build. Each module has a strictly defined single responsibility:

```
Poly-Mod-Loader
├── poly-bootstrap/                # JVM Entrypoint (PolyLauncherMain), dev launch tasks
├── poly-runtime-api/              # Shared API base: GameSide and the @ApiStability markers
├── poly-runtime-impl/             # Core runtime coordinator, mod loading pipeline, dependency solver
├── poly-transform-api/            # Declarative bytecode transform rules (no ASM types exposed)
├── poly-transform-engine/         # ASM execution engine, pre-flight planner, bytecode cache
├── poly-bridge-api/               # Cross-loader bridge SPI (ModLoaderBridge, GameHost)
├── poly-bridge-fabric/            # Fabric & Quilt loader emulation, unified Mixin 0.8.7 service
├── poly-bridge-forge/             # MinecraftForge & NeoForge FML virtualizer, patch retargeting
├── poly-event-api/                # Native API: event keys, listener registration, server lifecycle events
├── poly-game-api/                 # Native API: blocks, items, entities, commands, networking, client, game tests
├── poly-game-v1_21_11/            # Minecraft 1.21.11 game layer (experimental, not supported)
├── poly-game-v26_1/               # Minecraft 26.1 / 26.1.1 / 26.1.2 game layer & custom loading overlay
├── poly-game-v26_2/               # Minecraft 26.2 game layer & custom loading overlay
├── poly-game-v26_3/               # Minecraft 26.3 game layer & SDL keycode adapters
├── poly-version-adapter-api/      # Version adapter and game layer contracts
├── poly-version-adapter-v*/       # Per-version launch targets and built-in transform rules (26.1, 26.2, 26.3, 1.21.11)
├── poly-system-api/               # Native API: replaceable systems (poly:game_brand)
├── poly-system-registry/          # Built-in systems, replacement resolution and lookup
├── poly-lifecycle-api/            # Mod entrypoints (common, client, server) and ModContext
├── poly-mod-metadata/             # Parser and validator for poly.mod.json (Fabric, Quilt and FML metadata: the bridges)
└── poly-installer/                # Cross-platform installer jar (Java Swing GUI and command line)
```

---

## 5. Architectural Disciplines & Coding Conventions

### 1) Strict Version Isolation (Rule of Separation)
- **Never import Minecraft or game-version-specific classes into common modules.**
- Modules like `poly-runtime-api`, `poly-transform-engine`, `poly-bridge-api`, and `poly-system-api` must remain 100% free of `net.minecraft.*` imports.
- Any game-specific interaction must go through `poly-game-api` or a specific version layer (`poly-game-v26_2`, etc.).

### 2) Pre-Flight Validation over Silent Failures
- If foreign mod bytecode or Mixin declarations cannot be safely reconciled with the host loader's patches, report a structured error code (`POLY-COMPAT-...`) before launch.
- Never wrap injectors in silent failsoft try-catch blocks that could cause world corruption later.

### 3) Spotless Java Formatting
All Java code is formatted by palantir-java-format through Spotless (`check` fails on any difference):
```powershell
.\gradlew.bat spotlessApply
```
- No wildcard imports (`import java.util.*;` is forbidden).
- 4 spaces indentation, Unix LF line endings.
- State in the JavaDoc when a value can be absent (an `Optional`, or `null` where the JavaDoc says so). JetBrains `@Nullable` / `@NotNull` appear only where Minecraft's own signatures carry them.

---

## 6. How to Contribute

1. **Fork & Branch:** Create a feature branch from `main`:
   ```powershell
   git checkout -b feature/my-enhancement
   ```
2. **Implement & Test:** Write clean, modular code backed by unit tests or GameTest assertions.
3. **Format & Verify:**
   ```powershell
   .\gradlew.bat spotlessApply
   .\gradlew.bat check
   ```
4. **Submit PR:** Submit a Pull Request targeting `main`. Include a clear description of the problem solved, testing methodology, and any relevant error codes.
