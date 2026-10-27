# Poly Mod Loader — Project Overview & Status

<p align="center">
  <img src="logo.svg" alt="Poly Mod Loader" width="100" height="100">
</p>

<p align="center">
  <strong>The Next-Generation Multi-Loader and Native Bytecode Engine for Minecraft</strong><br>
  Running Fabric, Forge, NeoForge, and Quilt mods in a unified JVM runtime with zero game-level compromise.<br>
  <a href="DEVELOPING.md">Developer Guide</a> ·
  <a href="ARCHITECTURE.md">Architecture</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="https://www.polymc.net">Official Website</a> ·
  <a href="#한국어">한국어 안내</a>
</p>

---

## 1. What is Poly Mod Loader?

**Poly Mod Loader (Poly)** is an advanced, in-engine modding platform and bytecode virtualization layer for **Minecraft: Java Edition** (supporting versions `26.1`, `26.1.1`, `26.1.2`, `26.2`, and `26.3` under **Java 25**).

Unlike external launcher wrappers (such as Prism, CurseForge App, or Modrinth App) that merely manage profile directories and launch separate games, Poly is an **in-game bytecode loader**:
- It replaces vanilla Minecraft's startup sequence and hosts a unified classloader.
- It dynamically adapts foreign mod bytecode (Fabric, Forge, NeoForge, Quilt) on the fly, allowing mods from different loaders to run together in the same world, server, or client session.
- It provides its own clean **Native API** with declarative bytecode transforms, headless gametesting, and version-agnostic abstractions.

---

## 2. Core Pillars & Technical Differentiators

```
+-----------------------------------------------------------------------------------+
|                                  Poly Mod Loader                                  |
+-----------------------------------------------------------------------------------+
|  [ Native API ]   [ Fabric Bridge ]   [ Forge Bridge ]   [ NeoForge Bridge ]      |
|  - System API     - FabricLoader API  - FML Virtualizer  - FML 11 Layer Bridge    |
|  - Lifecycle API  - QuiltLoader API   - ModLauncher Shim - Shared Mixin Engine    |
+-----------------------------------------------------------------------------------+
|                       Poly Transform Engine (ASM 9.11)                            |
|  - Declarative Bytecode Transforms  - Dynamic Host Patch Adjustments (ADR-0030)  |
|  - Unified Mixin 0.8.7 Service      - Granular Conflict Pre-flight Assessment     |
+-----------------------------------------------------------------------------------+
|                     Poly Transforming ClassLoader & JRE 25                        |
+-----------------------------------------------------------------------------------+
|                         Minecraft: Java Edition (26.x)                            |
+-----------------------------------------------------------------------------------+
```

### 1) True In-JVM Multi-Loader Concurrency
Poly does not run separate games or heavy emulation layers. A single game classloader hosts Poly, Fabric, Forge, and NeoForge mod code simultaneously. Forge and NeoForge's FML services run inside Poly's managed layers, while Fabric and Quilt mods are driven through unified Mixin and ClassTweaker pipelines.

### 2) Pre-flight Conflict & Patch Assessment (`POLY-COMPAT-224`)
When Forge or NeoForge patches a vanilla Minecraft class, traditional Fabric Mixins fail because injection targets or local variable tables differ. Poly incorporates an automated **Host Patch Check (`HostPatchCheck`)**:
- It inspects all mod mixins against the host's actual bytecode before launch.
- If a mixin can be safely adapted (via parameter shifting, expression wrapping, or method retargeting), Poly rewrites the injection point on the fly (`HostPatchAdjustments`).
- If an incompatible mixin is found, Poly isolates the conflicting mod before the JVM starts, outputting clear diagnostic codes (`POLY-COMPAT-224`) instead of crashing the game mid-launch.

### 3) Near-Zero Performance Overhead
- **Startup Time & Memory:** Memory footprint and startup duration are within measurement noise compared to vanilla and host loaders.
- **Shader & GPU Optimization:** Full support for major graphics mods (Sodium, Iris, Lithium) with unified render pipelines.
- **Custom Loading Screen:** Fast, modern `#0E131F` dark overlay (`PolyLoadingOverlay`) with real-time mod count overlays per loader.

---

## 3. Current Project Status (Release 1.2.3)

| Component | Target / Scope | Status | Notes |
| :--- | :--- | :---: | :--- |
| **Supported Minecraft** | `26.1`, `26.1.1`, `26.1.2`, `26.2`, `26.3` | **Stable** | Java 25 required across all platforms. |
| **Fabric Mod Support** | Fabric Loader 0.19.5 API / Mixin 0.8.7 | **Full** | 138+ popular Fabric mods verified concurrently on 26.2. |
| **Quilt Mod Support** | Quilt Loader 0.31.x / `quilt.mod.json` | **Full** | 114+ Quilt-only Modrinth mods verified. |
| **Forge Mod Support** | MinecraftForge 62.0.9 ~ 66.1.0 | **Full** | Verified on client, dedicated server, and GameTest servers. |
| **NeoForge Mod Support** | NeoForge 26.1.0 ~ 26.3.0 | **Full** | Runs via unified FML layer; runs alongside Fabric/Quilt. |
| **Fabric API on Forge** | Every Fabric API module of the 26.2 corpus | **Partial** | `corpusFabricForge` on 26.2 (2026-10-27): no Fabric API module is left out next to Forge; Architectury, Balm, Lithium and Puzzles Lib still are (`POLY-COMPAT-224`). |
| **Fabric API on NeoForge** | 10 modules left out on 26.2 | **Partial** | `corpusFabricNeoForge` on 26.2 (2026-10-27): block, content-registries, crash-report-info, data-generation, entity-events, events-interaction, item, registry-sync, resource-conditions and transfer are left out (`POLY-COMPAT-224`). |
| **NeoForge Injection Points** | Every `NeoForgeInjectionPoints` entry | **Checked** | `NeoForgeInjectionPointsTest` against NeoForge 26.2.0.93's patched client and vanilla 26.2 (2026-10-27). The four 1.2.3 client entries named calls vanilla does not make; `openMenu` was corrected, the other three removed. |
| **Custom Loading UI** | `PolyLoadingOverlay` | **Done** | Font corruption fix merged in 1.2.3; loader mod count overlay. |
| **Bug Reporting Flow** | GitHub Issues Direct Route | **Done** | Exceptions & crash UI direct users to official issue tracker. |

---

## 4. Immediate Roadmap & Future Milestones

### Milestone A: Client-Side & NeoForge Fabric API Adaptation (In Progress)
- **Done (2026-10-27):** the NeoForge injection points are checked against NeoForge's patched client (`NeoForgeInjectionPointsTest`), and the Fabric API modules left out next to Forge (none) and NeoForge (10) were measured on 26.2.
- **`fabric-registry-sync-v0` next to NeoForge:** now the module that keeps the most others out; `fabric-menu-api-v1` no longer meets NeoForge's patch, but needs its `RegistryEntryAddedCallback` (`POLY-COMPAT-111`).
- **`fabric-screen-api-v1`:** Retarget `GuiMixin:onExtractGui` for NeoForge client UI mods (`ModMenu`, etc.). NeoForge's `Gui.extractRenderState` no longer calls `Screen.extractRenderStateWithTooltipAndSubtitles`; find where it moved before adding a point.
- **`fabric-data-attachment-api-v1`:** Integrate NeoForge's `AttachmentType` extensions.
- **Rendering & Model Modules:** Adapt `fabric-renderer-api-v1`, `fabric-rendering-v1`, and `fabric-model-loading-api-v1` for client-side shaders and resource packs.

### Milestone B: Minecraft 26.3 Real-World Corpus Verification
- **Done headless (2026-10-27):** the 26.3 corpus list (`mod-corpus-26.3.json`: 43 Fabric, 42 Forge, 44 NeoForge) passes `corpusFabric` (98 loaded, 73 not), `corpusFabricServer` (100 / 74), `corpusForge` (30 of 38), `corpusForgeServer` (33 of 41), `corpusNeoForge` (FML loads 50) and `corpusNeoForgeServer` (49).
- Still open: the Quilt list for 26.3 (`mod-corpus-quilt-26.3.json`), the mixed-loader tasks (`corpusFabricForge`, `corpusFabricNeoForge`, ...) and the window runs on 26.3.
- Hardening against 26.3-specific game changes (SDL keyboard handling, shader `#include`, data pack overlay formats).

### Milestone C: Technical Benchmarking
- Publish detailed memory, tick-rate, and startup benchmarks comparing Poly against standalone loaders.

---

<a name="한국어"></a>
## 한국어 안내 (Korean Overview)

### 1. 프로젝트 개요
**Poly Mod Loader (Poly)**는 마인크래프트 Java Edition (최신 `26.1` ~ `26.3`, **Java 25**)을 위한 차세대 인게임 바이트코드 변환 엔진이자 멀티로더 통합 플랫폼입니다.

외부에서 프로필을 분리 실행하는 일반 런처(Prism Launcher, CurseForge 등)와 달리, Poly는 **게임 프로세스 내부에서 동작하는 모드로더 엔진**입니다:
- Fabric, Forge, NeoForge, Quilt 모드를 하나의 마인크래프트 게임 안에서 동시에 실행할 수 있습니다.
- 서로 다른 모드로더 간의 클래스 충돌, 믹스인(Mixin) 패치 불일치, 레지스트리 동결 시점 차이를 런타임에 바이트코드 레벨에서 자동으로 조율합니다.
- 플랫폼에 종속되지 않는 고유의 **Native API**와 선언적 바이트코드 변환기를 제공합니다.

### 2. 핵심 기술
1. **단일 JVM 멀티로더 공존:**
   별도의 가상머신 분리 없이 단일 클래스로더 상에서 FML(Forge/NeoForge) 모듈 레이어와 Fabric/Quilt Mixin 파이프라인을 완벽히 통합 구동합니다.
2. **사전 호환성 정밀 진단 (`POLY-COMPAT-224`):**
   게임이 켜진 후 알 수 없는 오류로 튕기는 대신, 시작 전 단계에서 호스트 로더의 바이트코드 패치와 모드의 주입점을 전수 대조하여 호환되지 않는 모드를 사전에 식별하고 명확한 진단 코드를 제공합니다.
3. **바이트코드 동적 적응 (`HostPatchAdjustments`):**
   Forge나 NeoForge가 바닐라 메서드를 변경한 경우, Fabric API 모듈들의 주입점을 런타임에 올바른 대체 지점으로 자동 연결합니다.
4. **독자적인 로딩 경험:**
   타 로더의 조기 로딩 창 및 모드 수 표기를 정리하고, 세련된 `#0E131F` 다크 테마 커스텀 로딩 화면과 로더별 실시간 모드 수 뱃지 오버레이를 제공합니다.

---

## 5. Development & Contributions

Poly Mod Loader is open source under the **Mozilla Public License 2.0 (MPL-2.0)**.
- Developer instructions, building from source, and module navigation: **[DEVELOPING.md](DEVELOPING.md)**
- Deep dive into internal transform systems and classloader topology: **[ARCHITECTURE.md](ARCHITECTURE.md)**
- Issue tracking & feature requests: **[GitHub Issues](https://github.com/xyrz47-spec/Poly-Mod-Loader/issues)**
