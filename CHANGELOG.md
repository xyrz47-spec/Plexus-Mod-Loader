# Changelog

## 1.4.1 — 2026-10-30

### Highlights

- Rebranded in-game branding, early loading window titles, and game window titles to Plexus Mod Loader.
- Native mod specification now looks for `plexus.mod.json` first, while retaining 100% backward compatibility for existing mods with `poly.mod.json`.
- Updated game brand system ID to `plexus:game_brand`, with seamless alias fallback for `poly:game_brand` and default brand string `plexus`.
- Runtime mod ID now recognizes `plexus` in mod dependency declarations while continuing to support `poly`.

### 주요 변경

- 인게임 브랜딩, 초기 로딩 창 및 게임 윈도우 타이틀을 Plexus Mod Loader로 변경했습니다.
- 네이티브 모드 메타데이터 명세로 `plexus.mod.json`을 기본 지원하며, 기존 `poly.mod.json`을 사용하는 모드와의 100% 하위 호환성을 유지합니다.
- 게임 브랜드 시스템 ID를 `plexus:game_brand`로 갱신하고 `poly:game_brand` 별칭 및 기본 브랜드 문자열 `plexus`를 완벽 지원합니다.
- 모드 의존성 선언(`depends`)에서 런타임 모드 ID로 `plexus`를 공식 지원하며 기존 `poly` 선언도 그대로 동작합니다.

### Rebranding & Compatibility

- Updated game window titles across supported versions (26.1, 26.2, 26.3) to display `Minecraft[*] Plexus <version>`.
- Updated early loading window and loading overlay branding to display Plexus Mod Loader, with mod loader line showing `Plexus` mod counts.
- Updated title screen mod count translation keys to display "Plexus mods" (English) and "Plexus 모드" (Korean).
- `GameBrandSystem.ID` is now `plexus:game_brand` with `GameBrandSystem.LEGACY_ID` (`poly:game_brand`) mapped transparently in system replacement resolution.
- `ModFileScanner` now checks `plexus.mod.json` first and falls back to `poly.mod.json` for native mods.
- `ModDependencyResolver` supports both `plexus` and `poly` as reserved runtime mod identifiers.

## 1.4.0 — 2026-10-30

### Highlights

- Every loader now shares one early loading window on the client that looks like the game's loading screen before its first frame. It is not yet supported on macOS and can be turned off with `-Dpoly.earlyWindow=off`.
- Fabric mods next to NeoForge 26.2: more Fabric API modules load (resource conditions, crash report info, and block and item modules built on NeoForge's hooks), so more Fabric mods run; 47 loaded (was 40) in the popular Fabric mods set.
- Fixed NeoForge stopping at start when Forgified Fabric API was installed, preventing conflicts from its nested loader plugin.
- With Forge: Fabric API fluid and item hooks are only claimed where they really run, and a few mods (like fabric-biome-api-v1 next to Forge) are now left out instead of failing later.

### 주요 변경

- 클라이언트에서 모든 로더가 게임의 첫 프레임 이전 로딩 화면과 똑같이 생긴 하나의 초기 로딩 창을 함께 띄웁니다. macOS는 아직 지원하지 않으며 `-Dpoly.earlyWindow=off`로 끌 수 있습니다.
- NeoForge 26.2와 함께 쓰는 Fabric 모드: 리소스 조건, 충돌 보고서 정보, NeoForge 훅 기반 블록 및 아이템 모듈이 로드되어 더 많은 Fabric 모드가 실행됩니다. 인기 Fabric 모드 묶음에서 47개가 로드(이전 40개)됩니다.
- Forgified Fabric API가 설치되었을 때 중첩된 로더 플러그인으로 인해 NeoForge가 시작 시 멈추던 문제가 해결됩니다.
- Forge 환경: Fabric API 유체 및 아이템 훅은 실제로 실행되는 곳에서만 적용되며, Forge 옆의 fabric-biome-api-v1 등 일부 모드는 나중에 실패하는 대신 미리 제외됩니다.

### Loading screen

- Every loader now shares one early loading window on the client that opens as Poly Mod Loader starts and looks exactly like the game's loading screen before its first frame: the Mojang red background (or black with the monochrome logo option), the Mojang Studios logo, and the progress bar, plus Poly Mod Loader's spinning gold cube and name at the bottom right, mods per loader under the bar, and launch steps at the bottom left. MinecraftForge and NeoForge no longer open FML's early window, and the window closes 0.3 seconds after the game's window draws its first loading screen frame, 20 seconds after the game names its window, or at the latest 3 minutes after launch. The window is not yet supported on macOS and can be turned off with `-Dpoly.earlyWindow=off`, verified by `clientWorldReloadTest` on 26.2 and 26.3, `neoForgeClient` and `forgeClient` on 26.2, and headless checks (`check`, `worldCreateSaveTest`, `compatibilityMatrix`, `forgeGameTest`, `neoForgeWorldTest`).
- 클라이언트에서 모든 로더가 Poly 모드 로더 시작 시점부터 게임의 첫 프레임 이전 로딩 화면과 완전히 똑같이 생긴 하나의 초기 로딩 창을 띄웁니다: Mojang 빨간색 배경(단색 로고 옵션 시 검은색), Mojang Studios 로고, 진행 막대, 오른쪽 아래의 회전하는 금색 Poly 큐브와 이름, 막대 아래의 로더별 모드 수, 왼쪽 아래의 부팅 진행 상태를 보여 줍니다. MinecraftForge와 NeoForge는 더 이상 FML 자체 초기 창을 열지 않으며, 창은 게임 창이 첫 로딩 화면 프레임을 그리고 0.3초 뒤, 게임이 창 이름을 지정하고 20초 뒤, 또는 시작 후 최대 3분 뒤에 닫힙니다. macOS는 아직 지원하지 않으며 `-Dpoly.earlyWindow=off`로 끌 수 있고, 26.2와 26.3의 `clientWorldReloadTest`, 26.2의 `neoForgeClient`와 `forgeClient`, 헤드리스 검증 작업(`check`, `worldCreateSaveTest`, `compatibilityMatrix`, `forgeGameTest`, `neoForgeWorldTest`)으로 확인했습니다.

### Fixes

- Shipped the second host module under ADR-0031 (`fabric-item-api-v1` `14.5.0+poly.neoforge.26.2` in `poly-fabric-api-neoforge-v26_2`), replacing the original on `POLY-COMPAT-224` under NeoForge 26.2.0.93. Replaced `FabricItemImpl` with `ItemTooltipHandlerMixin`, added `ClientItemEventHooks`, declared `getOrDefault` in `DataComponentMapBuilderMixin`, and prioritized `EnchantmentEvents.ALLOW_ENCHANTING` in `IItemExtensionMixin`. Modules now compile against NeoForge's patched client jar via `installNeoForgeClient` and `ClassTweakedJarTask`. `HostPatchAdjustments` now recognizes mod interfaces extending host interfaces as overriding defaults (`POLY-COMPAT-226`). `corpusFabricNeoForge` 26.2 improved to 47 loaded / 69 excluded (was 46 / 70, 3 `224` modules left), `corpusFabricOnNeoForge` to 81 / 77 (was 78 / 80), and `corpusFabricNeoForgeClientMixins` verified 756 target classes (109 configs, 0 failed).
- ADR-0031에 따라 두 번째 호스트 모듈인 NeoForge 26.2.0.93용 `fabric-item-api-v1`(`14.5.0+poly.neoforge.26.2`, 모듈 `poly-fabric-api-neoforge-v26_2`)을 탑재하여 `POLY-COMPAT-224` 발생 시 대체하도록 했습니다. `FabricItemImpl`을 제거하고 `ItemTooltipHandlerMixin`, `ClientItemEventHooks`, `DataComponentMapBuilderMixin`, `IItemExtensionMixin` 수정을 적용했습니다. `installNeoForgeClient`와 `ClassTweakedJarTask`를 통해 패치된 클라이언트 jar를 대상으로 컴파일하며, `HostPatchAdjustments`는 호스트 인터페이스를 확장하는 모드 인터페이스의 기본 메서드를 재정의로 허용합니다(`POLY-COMPAT-226`). `corpusFabricNeoForge` 26.2는 47개 로드·69개 제외(이전 46 / 70, `224` 모듈 3개)로 개선되었고, `corpusFabricOnNeoForge`는 81 / 77(이전 78 / 80), `corpusFabricNeoForgeClientMixins`는 109개 설정의 756개 대상 클래스 모두 실패 0개로 통과했습니다.
- Shipped the first host module under ADR-0031 (`fabric-block-api-v1` `3.1.0+poly.neoforge.26.2` in `poly-fabric-api-neoforge-v26_2`), allowing `HostModules` to replace the module on `POLY-COMPAT-224` under NeoForge 26.2.0.93; `corpusFabricNeoForge` 26.2 improved to 46 loaded / 70 excluded (was 45 / 71) with 4 `224` exclusions remaining. Updated `ModLinkVerifier` to inspect host-patched jars before vanilla jars, identifying that `fabric-biome-api-v1` accesses `ChunkGenerator.featuresPerStep` as a `Supplier` against Forge's `ClearableLazy` field and excluding it with `POLY-COMPAT-112`; `corpusFabricForge` is now 85 / 81 on 26.2 (was 86 / 80) and 84 / 82 on 26.3 (was 85 / 81), and `corpusQuiltForge` 26.3 is 97 / 84 (was 98 / 83).
- ADR-0031에 따라 첫 호스트 모듈(`fabric-block-api-v1` `3.1.0+poly.neoforge.26.2`, 모듈 `poly-fabric-api-neoforge-v26_2`)을 탑재하여 NeoForge 26.2.0.93에서 원본 모듈이 `POLY-COMPAT-224`로 제외될 때 `HostModules`가 대체하도록 했습니다. `corpusFabricNeoForge` 26.2는 46개 로드, 70개 제외(이전 45 / 71)로 개선되었으며 `224` 제외는 4개가 남았습니다. 패치된 jar를 먼저 검사하도록 링크 검사(`ModLinkVerifier`)를 변경해 Forge에서 `ClearableLazy`로 변경된 `ChunkGenerator.featuresPerStep`을 `Supplier`로 쓰는 `fabric-biome-api-v1`을 `POLY-COMPAT-112`로 제외했습니다. `corpusFabricForge`는 26.2에서 85 / 81(이전 86 / 80), 26.3에서 84 / 82(이전 85 / 81), `corpusQuiltForge` 26.3은 97 / 84(이전 98 / 83)가 되었습니다.
- Fixed NeoForge stopping with `POLY-COMPAT-208` (`NoSuchMethodError: FabricLoaderImpl.setup()`) when Forgified Fabric API was installed due to its nested `forgified-fabric-loader` FML plugin. Poly Mod Loader now patches `FMLLoader.loadPlugins` (`NeoForgeSharedMixinPatch`, FML 11 and 12) to exclude foreign Fabric Loader plugins (`PolyFml.withoutForeignFabricLoader`) when Fabric's Mixin is shared. The game, 48 NeoForge mods, and the Fabric test mod now load and pass GameTest. Two results still differ from the original Fabric API, so route A is not yet listed as supported in `COMPATIBILITY.md`.
- Forgified Fabric API 설치 시 중첩된 `forgified-fabric-loader` FML 플러그인이 자체 로더를 구동하여 `POLY-COMPAT-208`(`NoSuchMethodError: FabricLoaderImpl.setup()`)로 NeoForge가 중단되던 문제를 수정했습니다. Fabric Mixin 공유 시 `FMLLoader.loadPlugins`를 패치(`NeoForgeSharedMixinPatch`, FML 11·12)하여 외부 Fabric Loader 플러그인을 제외(`PolyFml.withoutForeignFabricLoader`)하도록 처리했습니다. 게임, 48개 NeoForge 모드, Fabric 테스트 모드가 정상 로드되어 GameTest를 통과합니다. 다만 원본 Fabric API 대비 두 결과가 달라 경로 A는 아직 `COMPATIBILITY.md`에 지원으로 기재하지 않습니다.
- With NeoForge 26.2: Fabric API's `fabric-resource-conditions-api-v1` loads. An `@Inject` in `RegistryLoadTask$PendingRegistration.loadFromResource` moves before NeoForge's conditional codec call (`NeoForgeInjectionPoints`), checking Fabric conditions first. A new bridge (`HostConditionalSkip`) in `SimpleJsonResourceReloadListener` (also its `scanDirectoryWithModifier`, which `RecipeManager` uses) converts Fabric's skip marker to `DataResult.success(Optional.empty())`, matching NeoForge's skip and dropping the head handler; other shapes remain `POLY-COMPAT-224`. New task `neoForgeFabricApiGameTest` passes. `corpusFabricNeoForge` loads 45 foreign mods (was 44 / 72; 5 modules left out with `224`), `corpusFabricNeoForgeClientMixins` loads 747 target classes (108 configs, 0 failed). 26.3 is unchanged (43 / 73, 7 modules left out).
- NeoForge 26.2와 함께: Fabric API의 `fabric-resource-conditions-api-v1`이 로드됩니다. `RegistryLoadTask$PendingRegistration.loadFromResource`의 `@Inject`가 NeoForge 조건부 코덱 호출 앞으로 이동하여(`NeoForgeInjectionPoints`) Fabric 조건을 먼저 검사합니다. `SimpleJsonResourceReloadListener`(`RecipeManager`가 쓰는 `scanDirectoryWithModifier` 포함)에서는 새 다리(`HostConditionalSkip`)가 Fabric의 스킵 마커를 `DataResult.success(Optional.empty())`로 변환하고 헤드 처리기를 생략하며, 다른 형태는 `POLY-COMPAT-224`로 처리합니다. 신규 작업 `neoForgeFabricApiGameTest`가 통과했습니다. `corpusFabricNeoForge`는 외래 모드 45개를 로드하고(전에는 44 / 72, `224` 5개 모듈), `corpusFabricNeoForgeClientMixins`는 108개 설정의 대상 클래스 747개를 실패 0개로 로드했습니다. 26.3은 변동 없이 유지됩니다(43 / 73, 7개 모듈 제외).
- With NeoForge (26.2 and 26.3): Fabric API's `fabric-crash-report-info-v1` loads. NeoForge moved `ServerWatchdog.createWatchdogCrashReport` into `createWatchdogCrashReport(String, long, String)` calling `ThreadInfoUtil.getEntireStacktrace(threadInfo)`. A `@WrapOperation` bridge (`HostThreadDump`) runs Fabric's `printEntireThreadDump` handler; only a static `(Object)Object` handler calling `ThreadPrinting.fullThreadInfoToString` is bridged (`POLY-COMPAT-224` otherwise). `corpusFabricNeoForge` loads 44 foreign mods on 26.2 (was 43 / 73, 6 left out with `224`) and 43 / 73 on 26.3 (was 42 / 74, 7 left out) without `POLY-COMPAT-225`. `corpusFabricNeoForgeClientMixins` loads 744 target classes (107 configs, 0 failed); the watchdog was not triggered in game.
- NeoForge와 함께(26.2·26.3): Fabric API의 `fabric-crash-report-info-v1`이 로드됩니다. NeoForge가 `ServerWatchdog.createWatchdogCrashReport` 본문을 `createWatchdogCrashReport(String, long, String)`로 옮겨 `ThreadInfoUtil.getEntireStacktrace(threadInfo)`를 호출하므로, 새 `@WrapOperation` 다리(`HostThreadDump`)가 Fabric의 `printEntireThreadDump` 처리기를 실행합니다. `ThreadPrinting.fullThreadInfoToString`만 호출하는 정적 `(Object)Object` 처리기만 연결되며 다른 것은 `POLY-COMPAT-224`입니다. `corpusFabricNeoForge`: 26.2에서 외래 모드 44개가 로드되고(전에는 43 / 73, `224` 6개), 26.3에서 43 / 73개(전에는 42 / 74, `224` 7개)이며 `POLY-COMPAT-225`는 없습니다. `corpusFabricNeoForgeClientMixins`는 107개 설정의 대상 클래스 744개를 실패 0개로 로드했습니다. 감시견 자체는 게임에서 트리거되지 않았습니다.
- Before launch, `HostPatchAdjustments` now compares bytecode when checking conflicting interface default methods added by Fabric mixins and host patches (`POLY-COMPAT-226`): they must make the same calls in the same order, treating class-cast receivers and `self()` as identical. MinecraftForge and Fabric API `getCraftingRemainder` pass this comparison. The check also inspects defaults under vanilla interfaces, reporting Fabric API's `ItemStackMixin` next to NeoForge (26.2 and 26.3) as `POLY-COMPAT-224` because NeoForge calls `Item.getCraftingRemainder(ItemInstance)` instead of `ItemStack`. Mod counts do not change: 26.2 `corpusFabricNeoForge` passes (43 foreign mods loaded, 73 not loaded).
- 게임 시작 전 `HostPatchAdjustments`가 Fabric mixin과 호스트 패치의 기본 메서드 충돌을 검사할 때 바이트코드를 비교합니다(`POLY-COMPAT-226`). 호출 순서가 같고 수신자(`this` 캐스트 또는 `self()`)가 같아야 동등으로 허용하며, MinecraftForge와 Fabric API의 `getCraftingRemainder`는 이를 통과합니다. 또한 바닐라 인터페이스 하위의 기본 메서드도 검사하여, `Item.getCraftingRemainder(ItemInstance)`를 호출하는 NeoForge(26.2·26.3) 옆의 Fabric API `ItemStackMixin`을 `POLY-COMPAT-224`로 보고합니다. 로드 모드 수는 변하지 않아 26.2 `corpusFabricNeoForge`가 통과합니다(외래 모드 43개 로드, 73개 제외).
- With Forge (26.2 and 26.3): Fabric API's fluid and item mixins are only reported as adapted (`POLY-COMPAT-226`) where they actually run. Bridges for boat support and swimming run Forge's call before executing Fabric's handlers, crafting remainder handlers are dropped only when bytecode confirms they just call `ItemStack.getCraftingRemainder()`, and enchanting checks prioritize Fabric's denial. Drowning and fluid jumping cannot be bridged and are reported as `POLY-COMPAT-224`, leaving out `fabric-content-registries-v0` and, on 26.2, Puzzles Lib (`POLY-COMPAT-111`). `corpusFabricForge`: 86 foreign mods loaded on 26.2 (was 88), 85 on 26.3 (was 86); `corpusQuiltForge` on 26.3: 98 (was 99).
- Forge와 함께(26.2·26.3): Fabric API의 유체 및 아이템 mixin은 실제로 실행되는 곳에서만 적응(`POLY-COMPAT-226`)으로 보고됩니다. 보트 지원과 수영 다리는 Forge의 호출을 먼저 한 뒤 Fabric 처리기를 실행하고, 제작 잔여물 처리기는 바이트코드로 `ItemStack.getCraftingRemainder()`만 호출함을 확인할 때만 생략하며, 마법 부여 검사는 Fabric의 거부를 우선합니다. 다리를 둘 수 없는 익사와 유체 점프는 `POLY-COMPAT-224`로 보고되어 `fabric-content-registries-v0`과 26.2의 Puzzles Lib(`POLY-COMPAT-111`)이 빠집니다. `corpusFabricForge`: 외래 모드가 26.2에서 86개(전에는 88개), 26.3에서 85개(전에는 86개) 로드되고, 26.3 `corpusQuiltForge`는 98개(전에는 99개) 로드됩니다.
- Minecraft 26.3, with Forge: Fabric API's entity events and item modules load again, and with them the Fabric mods that need them. Fabric API's bed occupation hook follows Forge's `BlockState.setBedOccupied`, which 26.3 calls where vanilla now calls `Level.setBlockAndUpdate`; its fishing rod check (`allowModdedRods`) runs on Forge's `canPerformAction(FISHING_ROD_CAST)` with Forge's answer. `corpusFabricForge` on 26.3: 86 foreign mods loaded (was 80); `corpusQuiltForge`: 99 (was 95).
- Minecraft 26.3, Forge와 함께: Fabric API의 entity events·item 모듈과 그것이 필요한 Fabric 모드가 다시 로드됩니다. 침대 사용 훅은 26.3 바닐라가 `Level.setBlockAndUpdate`를 부르는 자리의 Forge `BlockState.setBedOccupied`를 따르고, 낚싯대 확인(`allowModdedRods`)은 Forge의 `canPerformAction(FISHING_ROD_CAST)` 결과를 받아 실행됩니다. 26.3 `corpusFabricForge`는 외부 모드 86개(전에는 80개), `corpusQuiltForge`는 99개(전에는 95개)를 로드합니다.
- With NeoForge (26.2 and 26.3): Fabric API's interaction events (`fabric-events-interaction-v0`) load, and with them the Fabric and Quilt mods that need them, such as Better Shulker Boxes on 26.2. A `@WrapOperation` of a call NeoForge replaced at the same place on the same object now runs on NeoForge's call with NeoForge's arguments and result, also when NeoForge reorders or adds arguments: the pick-block call `getCloneItemStack` (NeoForge adds the player), the fishing rod check (`canPerformAction(ItemAbility)`), and the container `setChanged` calls NeoForge moves into its own `setItem`. On 26.3, `corpusFabricNeoForge`: 41 foreign mods loaded (was 40); `corpusQuiltNeoForge`: 85 (was 83); `corpusFabricOnNeoForge`: 73 (was 68). Nine Fabric API modules are still left out next to NeoForge (`POLY-COMPAT-224`).
- NeoForge와 함께(26.2·26.3): Fabric API의 상호작용 이벤트(`fabric-events-interaction-v0`)와 그것이 필요한 Fabric·Quilt 모드(26.2의 Better Shulker Boxes 등)가 로드됩니다. NeoForge가 같은 자리, 같은 객체에서 다른 호출로 바꾼 호출의 `@WrapOperation`은 이제 NeoForge의 호출에서 NeoForge의 인수와 결과로 실행되며, NeoForge가 인수 순서를 바꾸거나 인수를 더해도 됩니다: 블록 집기 호출 `getCloneItemStack`(NeoForge가 플레이어를 더함), 낚싯대 확인(`canPerformAction(ItemAbility)`), NeoForge가 자기 `setItem` 안으로 옮긴 컨테이너 `setChanged` 호출. 26.3에서 `corpusFabricNeoForge`는 외부 모드 41개(전에는 40개), `corpusQuiltNeoForge`는 85개(전에는 83개), `corpusFabricOnNeoForge`는 73개(전에는 68개)를 로드합니다. NeoForge 옆에서는 Fabric API 모듈 9개가 아직 빠집니다(`POLY-COMPAT-224`).
- With NeoForge: Fabric API's transfer API loads on 26.2 and 26.3, and its entity events on 26.2. A hopper moves items through a block's Fabric storage where NeoForge finds neither a container nor its own item handler; Fabric's effect removal events (`ALLOW_EARLY_REMOVE`, `BEFORE_REMOVE`) run on NeoForge's removal in `removeAllEffects`; the sleeping direction and bed occupation events follow NeoForge 26.2's `getBedDirection` and `setBedOccupied`. NeoForge 26.3 moves the sleep checks into a lambda that does not take the bed block Fabric API's handlers take, so entity events are still left out there. 26.2: `corpusFabricNeoForge` 43 foreign mods loaded (was 41), `corpusFabricOnNeoForge` 75 (was 73), `corpusQuiltNeoForge` 107 (was 104, Gateway of Doom among them). 26.3: 42, 74, 86 and `corpusFabricNeoForgeJoin` 43 (was 41, 73, 85, 42). Seven Fabric API modules are still left out next to NeoForge 26.2, eight next to 26.3.
- NeoForge와 함께: Fabric API의 transfer 모듈이 26.2·26.3에서, entity events 모듈이 26.2에서 로드됩니다. 호퍼는 NeoForge가 용기도 자기 아이템 핸들러도 찾지 못한 블록에서 그 블록의 Fabric 저장소로 아이템을 옮기고, Fabric의 효과 제거 이벤트(`ALLOW_EARLY_REMOVE`, `BEFORE_REMOVE`)는 `removeAllEffects`에서 NeoForge의 제거 위에서 실행되며, 잠자는 방향·침대 사용 이벤트는 NeoForge 26.2의 `getBedDirection`·`setBedOccupied`를 따릅니다. NeoForge 26.3은 수면 검사를 Fabric API 처리기가 받는 침대 블록을 받지 않는 람다로 옮겨서, 26.3에서는 entity events가 아직 빠집니다. 26.2: `corpusFabricNeoForge` 외부 모드 43개(전에는 41개), `corpusFabricOnNeoForge` 75개(전에는 73개), `corpusQuiltNeoForge` 107개(전에는 104개, Gateway of Doom 포함). 26.3: 42·74·86개, `corpusFabricNeoForgeJoin` 43개(전에는 41·73·85·42개). NeoForge 26.2 옆에서 Fabric API 모듈 7개, 26.3 옆에서 8개가 아직 빠집니다.
- A `@WrapOperation` of a call the host replaced runs on the host's call only when its handler makes that call once, with its own arguments; the bridge makes the host's call before the handler runs, so a handler that skips or changes the call is left out (`POLY-COMPAT-224`) as before. An injection whose handler takes the target method's parameters moves into another method only when that method's parameters start with them; otherwise the mod is left out instead of stopping the game as it starts.
- 호스트가 다른 호출로 바꾼 호출의 `@WrapOperation`은 처리기가 그 호출을 자기 인수로 한 번 할 때만 호스트의 호출에서 실행됩니다. 다리가 처리기보다 먼저 호스트의 호출을 하므로, 호출을 건너뛰거나 바꾸는 처리기는 전처럼 빠집니다(`POLY-COMPAT-224`). 대상 메서드의 매개변수를 받는 처리기의 주입은, 옮길 메서드의 매개변수가 그것으로 시작할 때만 그 메서드로 옮깁니다. 아니면 게임 시작 중에 멈추지 않고 그 모드를 뺍니다.

## 1.3.0 — 2026-10-28

### Highlights

- The loading screens are Poly Mod Loader's with every loader and keep Minecraft's look: Forge's and NeoForge's early window shows Poly Mod Loader's spinning gold cube in place of the anvil and the fox, and the game's loading screen keeps the Mojang logo and progress bar with Poly Mod Loader's cube, name and each loader's mod count.
- The game window is titled `Minecraft* Poly <Minecraft version>` with every loader, from the first frame.
- Minecraft 26.1 and 26.1.2: Fabric mods next to Forge mods no longer stop the game as it starts (with Fabric API); 76 and 83 mods of the other loaders load in the popular-mod sets.

### 주요 변경

- 어느 로더로 실행해도 로딩 화면이 Poly 모드 로더의 것이 되고, Minecraft의 모습은 그대로입니다. Forge·NeoForge 초기 창은 모루·여우 자리에 회전하는 Poly 금색 큐브를 보여 주고, 게임의 로딩 화면은 Mojang 로고와 진행 막대를 그대로 두고 Poly 큐브와 이름, 로더별 모드 수를 덧그립니다.
- 게임 창 제목이 어느 로더에서든 첫 화면부터 `Minecraft* Poly <Minecraft 버전>`입니다.
- Minecraft 26.1·26.1.2: Forge 모드와 함께 쓰는 Fabric 모드가 더는 게임을 멈추지 않습니다. 인기 모드 묶음에서 다른 로더의 모드가 각각 76개, 83개 로드됩니다.

### Loading screen

- The loading screens are Poly Mod Loader's with every loader, and keep the game's look. MinecraftForge's and NeoForge's early loading window shows Poly Mod Loader's spinning gold cube in place of Forge's anvil and logo and NeoForge's running fox (and NeoForge's window icon and label), and the game's own loading screen that follows keeps the Mojang logo, background and progress bar, with the same cube and name at the bottom right and the mods each loader runs under the bar. Before, Forge and NeoForge showed their own early window, and the game's screen turned into a dark Poly Mod Loader screen. The three system properties that were meant to turn the early window off (`neoforge.earlyprogresswindow`, `fml.earlyprogresswindow`, `forge.forceDisplay`) were never read by FML and are gone.
- The game window's title is `Minecraft* Poly <Minecraft version>` with every loader (`Minecraft* Forge 26.2` and `Minecraft NeoForge* 26.3` before), from the early window on.
- 로딩 화면이 어느 로더에서든 Poly 모드 로더의 것이 되고, 게임의 모습은 그대로 둡니다. MinecraftForge·NeoForge의 초기 로딩 창은 Forge의 모루·로고와 NeoForge의 달리는 여우(NeoForge는 창 아이콘과 글자도) 자리에 회전하는 Poly 금색 큐브를 보여 주고, 이어지는 게임의 로딩 화면은 Mojang 로고·배경·진행 막대를 그대로 두고 오른쪽 아래에 같은 큐브와 이름을, 막대 아래에 로더별 모드 수를 덧그립니다. 전에는 Forge·NeoForge가 자기 초기 창을 띄웠고, 게임 화면은 어두운 Poly 화면으로 바뀌었습니다. 초기 창을 끄려던 시스템 속성 세 개(`neoforge.earlyprogresswindow`, `fml.earlyprogresswindow`, `forge.forceDisplay`)는 FML이 읽지 않는 것이라 지웠습니다.
- 게임 창 제목이 로더와 상관없이 초기 창부터 `Minecraft* Poly <Minecraft 버전>`입니다(전에는 `Minecraft* Forge 26.2`, `Minecraft NeoForge* 26.3`).

### Fixes

- Minecraft 26.1 and 26.1.2, with Forge: Fabric API no longer stops the game with `POLY-COMPAT-225` (`MinecraftServerMixin.onUnloadWorldAtShutdown`). Forge renamed the local `level` to `levelx` only where the server closes its worlds, and the check before launch looked for the name in the whole method; it now looks in scope at the injection point, and the `@Local` captures the renamed local (`POLY-COMPAT-212`). `corpusFabricForge`: 76 foreign mods load on 26.1, 83 on 26.1.2.
- With Forge, an injection at `Mob.setTarget`'s `asValidTarget` call moves to the target of Forge's `LivingChangeTargetEvent`, which Forge assigns there instead (Puzzles Lib's change-target event on 26.3). Puzzles Lib 26.3 is still left out next to Forge, for Fabric API modules it needs.
- Minecraft 26.1 and 26.1.2, Forge와 함께: Fabric API가 더는 `POLY-COMPAT-225`로 게임을 멈추지 않습니다(`MinecraftServerMixin.onUnloadWorldAtShutdown`). Forge는 서버가 월드를 닫는 자리에서만 지역 변수 `level`을 `levelx`로 바꿨는데, 시작 전 검사는 메서드 전체에서 이름을 찾았습니다. 이제 주입 지점에서 보이는 범위에서 찾고, `@Local`이 바뀐 이름을 따라갑니다(`POLY-COMPAT-212`). `corpusFabricForge`: 26.1에서 외부 모드 76개, 26.1.2에서 83개가 로드됩니다.
- Forge와 함께일 때 `Mob.setTarget`의 `asValidTarget` 호출에 대한 주입은 Forge가 그 자리에서 대입하는 `LivingChangeTargetEvent`의 대상으로 옮겨집니다(26.3의 Puzzles Lib 대상 변경 이벤트). 26.3의 Puzzles Lib은 필요한 Fabric API 모듈 때문에 Forge 옆에서 아직 빠집니다.

## 1.2.5 — 2026-10-28

### Highlights

- Minecraft 26.3: Fabric and Quilt mods run next to NeoForge and Forge mods again. In 1.2.4 these games stopped at startup or while loading.
- Minecraft 26.3 with Forge: Fabric API's registry sync and the mods that need it load; Fabric mods that add blocks no longer stop the game, and data packs with Fabric API load without a crash.
- With Forge, Puzzles Lib's Fabric build (and the mods that need it) loads again on 26.2.
- Mods that Poly Mod Loader cannot run next to Forge or NeoForge are left out before the game starts, with the reason, instead of crashing it.

### 주요 변경

- Minecraft 26.3: Fabric·Quilt 모드를 NeoForge·Forge 모드와 한 게임에서 다시 쓸 수 있습니다. 1.2.4에서는 이런 게임이 시작하거나 불러오는 중에 멈췄습니다.
- Minecraft 26.3, Forge와 함께: Fabric API의 레지스트리 동기화와 그것이 필요한 모드가 로드됩니다. 블록을 더하는 Fabric 모드가 게임을 멈추지 않고, Fabric API가 있을 때 데이터 팩도 크래시 없이 읽습니다.
- Forge와 함께일 때 26.2에서 Puzzles Lib의 Fabric 판(과 그것이 필요한 모드)이 다시 로드됩니다.
- Forge·NeoForge 옆에서 실행할 수 없는 모드는 게임을 멈추는 대신 게임 시작 전에 이유와 함께 빠집니다.

### Fixes

- With Forge, Puzzles Lib's Fabric build loads again (and with it the Fabric mods that need it): its injection into `ServerPlayer.drop` follows Forge's `ForgeHooks.onPlayerTossEvent`, and its `@Local` capture is matched by the vanilla local's name where Forge added a second local of the same type.
- With Forge, Fabric injections into `PersistentEntitySectionManager.addEntity` move into Forge's `addEntityWithoutEvent`, which holds the vanilla body.
- With NeoForge, Fabric API's registry sync `@Inject` at `Bootstrap.wrapStreams()` moves to `GameData.vanillaSnapshot()`, which NeoForge calls there instead. `fabric-registry-sync-v0` is still left out for other parts.
- Minecraft 26.3: Fabric and Quilt mods next to NeoForge mods start again. NeoForge 26.3's FML 12 adds a second Mixin class processor (`neoforge:mixin_frame_context`), which Poly Mod Loader's shared Mixin left empty, so every such game stopped with `POLY-COMPAT-208` before 1.2.5. The shared Mixin now fills it, and the shape checks accept FML 11 and FML 12 only.
- Minecraft 26.3, with Forge: a Forge mod that ships a newer MixinExtras (Carry On 2.12.0) no longer stops the game. With a shared Mixin, FML's copy of MixinExtras is left out of the game layer, as NeoForge's host already did.
- Next to Forge, the check before launch now leaves out a Fabric or Quilt mod with `POLY-COMPAT-224` when Forge's patch made a call it redirects static or instance (All Stacks Infinite's `consumeFuel`), or changed the type of a field it `@Shadow`s (Dwarfs and Giants' `RangedBowAttackGoal.mob`). Before, Mixin stopped the game with `POLY-COMPAT-225`.
- Minecraft 26.3, with Forge: loading data packs with Fabric API no longer stops with a `ClassCastException`. Forge 66 wraps the registry element decoder in `ConditionCodec`, so the call Fabric API's loot table hook wraps returns an `Optional`; the hook now runs through a bridge that hands it the table and gives Forge back its `Optional`, so Forge's condition check and `onJsonDataParsed` still run (`POLY-COMPAT-226`). Other wraps of such a call are left out before launch (`POLY-COMPAT-224`).
- Next to NeoForge, a Fabric mod's own copy of the host loader's classes (Forge Config API Port's Fabric build carries 36 of FML's and NeoForge's config classes) is checked against the host's classes, which are the ones that run. A mod that uses a member the host's class lacks is left out before the game starts with the new `POLY-COMPAT-141`. Before, the 26.3 server stopped with `NoSuchFieldError: ModConfig$Type.SERVER`.
- Minecraft 26.3, with Forge: Fabric and Quilt mods that register blocks or creative tabs in their initializer no longer stop the game with "Registry minecraft:block is already frozen". Without Fabric API's registry sync (left out under Forge 66), vanilla freezes the built-in registries before mods initialize; Poly Mod Loader now opens them (and Forge's wrappers' own `frozen` flag) around that initialization and freezes them again afterwards. It reads Forge's write checks from the class files first; a Forge release that checks something else stops the launch with the new `POLY-COMPAT-227` before those mods run. `corpusQuiltForge` on 26.3 passes: 91 foreign mods loaded, 90 left out.
- Minecraft 26.3, with Forge: Fabric API's registry sync (`fabric-registry-sync-v0`) loads again, and with it `fabric-object-builder-api-v1`. Forge 66's data generators read `RegistryDataLoader.getWorldAndDimensionRegistries()` where vanilla reads `WORLD_REGISTRIES`, which registry sync redirects; the redirect now moves to Forge's call through a bridge that returns Fabric's list followed by Forge's own entries (`POLY-COMPAT-226`). `corpusFabricForge` on 26.3: 80 foreign mods loaded (was 76); `corpusQuiltForge`: 95 (was 91).
- Minecraft 26.3: Fabric·Quilt 모드와 NeoForge 모드를 함께 쓰는 게임이 다시 시작됩니다. NeoForge 26.3의 FML 12가 Mixin 클래스 처리기를 하나 더 두는데(`neoforge:mixin_frame_context`), Poly 모드 로더의 공유 Mixin이 이를 비워 두어 이런 게임은 모두 `POLY-COMPAT-208`로 멈췄습니다. 이제 공유 Mixin이 그 자리를 채우고, 형태 검사는 FML 11과 FML 12만 받아들입니다.
- Minecraft 26.3, Forge와 함께: 더 새 MixinExtras를 품은 Forge 모드(Carry On 2.12.0)가 있어도 게임이 멈추지 않습니다. Mixin을 공유할 때는 FML의 MixinExtras 사본을 게임 계층에서 뺍니다(NeoForge 쪽은 이미 그렇게 했습니다).
- Forge와 함께일 때, Forge 패치가 리다이렉트 대상 호출을 static↔인스턴스로 바꿨거나(All Stacks Infinite의 `consumeFuel`) `@Shadow` 필드의 형을 바꾼 경우(Dwarfs and Giants의 `RangedBowAttackGoal.mob`) 시작 전 검사가 그 Fabric·Quilt 모드를 `POLY-COMPAT-224`로 뺍니다. 전에는 Mixin이 `POLY-COMPAT-225`로 게임을 멈췄습니다.
- Minecraft 26.3, Forge와 함께: Fabric API가 있을 때 데이터 팩을 읽다가 `ClassCastException`으로 멈추지 않습니다. Forge 66은 레지스트리 요소 디코더를 `ConditionCodec`으로 감싸서, Fabric API의 전리품 테이블 훅이 감싸는 호출이 `Optional`을 돌려줍니다. 이제 그 훅은 브리지를 거쳐 테이블을 받고 Forge에는 다시 `Optional`을 돌려주므로, Forge의 조건 검사와 `onJsonDataParsed`도 그대로 실행됩니다(`POLY-COMPAT-226`). 그런 호출을 다르게 감싸는 경우는 시작 전에 뺍니다(`POLY-COMPAT-224`).
- NeoForge와 함께일 때, Fabric 모드가 품은 호스트 로더 클래스의 사본(Forge Config API Port의 Fabric 판은 FML·NeoForge 설정 클래스 36개를 품음)을 실제로 실행되는 호스트의 클래스와 대조합니다. 호스트 클래스에 없는 멤버를 쓰는 모드는 새 오류 코드 `POLY-COMPAT-141`로 게임 시작 전에 뺍니다. 전에는 26.3 서버가 `NoSuchFieldError: ModConfig$Type.SERVER`로 멈췄습니다.
- Minecraft 26.3, Forge와 함께: 초기화에서 블록이나 크리에이티브 탭을 등록하는 Fabric·Quilt 모드가 "Registry minecraft:block is already frozen"으로 게임을 멈추지 않습니다. Fabric API의 레지스트리 동기화가 빠지면(Forge 66에서) 바닐라가 모드 초기화 전에 내장 레지스트리를 얼립니다. 이제 Poly 모드 로더가 그 초기화 동안 레지스트리(와 Forge 래퍼 자신의 `frozen` 표시)를 열고 끝나면 다시 얼립니다. 먼저 클래스 파일에서 Forge의 쓰기 검사를 읽고, 다른 것을 검사하는 Forge 릴리스면 그 모드들이 실행되기 전에 새 오류 코드 `POLY-COMPAT-227`로 시작을 멈춥니다. 26.3 `corpusQuiltForge`가 통과합니다(외부 모드 91개 로드, 90개 제외).
- Minecraft 26.3, Forge와 함께: Fabric API의 레지스트리 동기화(`fabric-registry-sync-v0`)가 다시 로드되고, 그것이 필요한 `fabric-object-builder-api-v1`도 로드됩니다. Forge 66의 데이터 생성기는 바닐라가 `WORLD_REGISTRIES`를 읽는 자리에서 `RegistryDataLoader.getWorldAndDimensionRegistries()`를 부르는데, 레지스트리 동기화가 바로 그 읽기를 바꿉니다. 이제 그 `@Redirect`는 브리지를 거쳐 Forge의 호출로 옮겨지고, Fabric의 목록 뒤에 Forge 자신의 항목을 붙여 돌려줍니다(`POLY-COMPAT-226`). 26.3 `corpusFabricForge`는 외부 모드 80개(전에는 76개), `corpusQuiltForge`는 95개(전에는 91개)를 로드합니다.
- Forge의 `ForgeHooks.onPlayerTossEvent`를 따라 Puzzles Lib의 `ServerPlayer.drop` 주입을 옮기고, Forge가 같은 형의 지역 변수를 하나 더 만든 곳에서 `@Local`을 바닐라 변수 이름으로 찾습니다. 그래서 Forge와 함께 Puzzles Lib의 Fabric 판(과 그것이 필요한 Fabric 모드)이 다시 로드됩니다.
- Forge와 함께일 때 `PersistentEntitySectionManager.addEntity`에 대한 Fabric 주입은 바닐라 본문을 가진 Forge의 `addEntityWithoutEvent`로 옮겨집니다.
- NeoForge와 함께일 때 Fabric API 레지스트리 동기화의 `Bootstrap.wrapStreams()` 주입(`@Inject`)은 NeoForge가 그 자리에서 부르는 `GameData.vanillaSnapshot()`으로 옮겨집니다. `fabric-registry-sync-v0`는 다른 부분 때문에 여전히 빠집니다.

## 1.2.4 — 2026-10-27

### Highlights

- Fixes three bugs from 1.2.2: NeoForge mods such as JEI, Kotlin for Forge, Lithium and MoreCulling load again; a Fabric dedicated server with Fabric API starts again; and Fabric API's entity attributes work next to Forge and NeoForge.
- Fabric and NeoForge mods together: JEI's MezzConfig no longer stops the server.
- Minecraft 26.3: Fabric mods that use the renamed rendering classes (AppleSkin, Cloth Config, GeckoLib, YACL, ...) are no longer left out on servers.
- The installer is now one `.jar` for every system (Java 17 or newer); the Windows `.exe` is gone.

### 주요 변경

- 1.2.2에서 생긴 버그 세 가지를 고쳤습니다. JEI, Kotlin for Forge, Lithium, MoreCulling 같은 NeoForge 모드가 다시 로드되고, Fabric API를 넣은 Fabric 전용 서버가 다시 시작되며, Forge·NeoForge와 함께일 때 Fabric API의 엔티티 속성 기능이 동작합니다.
- Fabric 모드와 NeoForge 모드를 함께 쓸 때 JEI의 MezzConfig 때문에 서버가 멈추지 않습니다.
- Minecraft 26.3: 이름이 바뀐 렌더링 클래스를 쓰는 Fabric 모드(AppleSkin, Cloth Config, GeckoLib, YACL 등)가 서버에서 더 이상 빠지지 않습니다.
- 설치 프로그램은 이제 모든 시스템에서 `.jar` 하나입니다(Java 17 이상). Windows `.exe`는 없어졌습니다.

### Fixes

- NeoForge mods with a `[modproperties.<id>]` table in `neoforge.mods.toml` load again. Since 1.2.2 they were left out with `POLY-COMPAT-217` ("Invalid key: Unexpected ':'"), whether or not Fabric mods were installed: on 26.2 JEI, Kotlin for Forge, Lambda Dynamic Lights, Lithium and MoreCulling, and the mods that need them.
- A Fabric dedicated server with Fabric API starts again. Since 1.2.2 Poly Mod Loader added a `getBuilder()` to `AttributeSupplier$Builder` also without Forge or NeoForge, and Mixin then refused Fabric API's accessor in `Bootstrap.validate`. Now only Forge's and NeoForge's patched class gets it.
- With Forge or NeoForge, Fabric API's `getBuilder()` on `AttributeSupplier$Builder` no longer throws `LinkageError` (loader constraint violation on Guava's `ImmutableMap$Builder`); the game class loader has its own copy of the bridge.
- With NeoForge, a mod shipped as a game library whose JarJar holds the mod (MezzConfig's `-standalone.jar`, needed by JEI) counts as one NeoForge runs, so its Fabric build is left out (`POLY-COMPAT-139`) instead of running next to it. Before, the server stopped: "More than one MezzConfig physical-side provider is present".
- NeoForge injection points: the four client entries 1.2.3 added named calls vanilla does not make, so they moved nothing. `ServerPlayer.openMenu` now has the two calls NeoForge really moved (`closeContainer()` and `send(Packet)`), and the other three are removed. `NeoForgeInjectionPointsTest` checks every entry against NeoForge's patched client and the vanilla client.
- Minecraft 26.3: `com.mojang.renderpearl` (Blaze3D's new name) counts as a client package in the link check. Before, nine Fabric mods of the 26.3 corpus (AppleSkin, Cloth Config, GeckoLib, YACL, ...) and the mods that need them were left out on the server with `POLY-COMPAT-112`.
- `neoforge.mods.toml`에 `[modproperties.<id>]` 표가 있는 NeoForge 모드가 다시 로드됩니다. 1.2.2부터 Fabric 모드가 있든 없든 `POLY-COMPAT-217`("Invalid key: Unexpected ':'")로 빠졌습니다. 26.2에서는 JEI, Kotlin for Forge, Lambda Dynamic Lights, Lithium, MoreCulling과 이것들이 필요한 모드입니다.
- Fabric API를 넣은 Fabric 전용 서버가 다시 시작됩니다. 1.2.2부터 Forge·NeoForge가 없어도 `AttributeSupplier$Builder`에 `getBuilder()`를 넣어, Mixin이 `Bootstrap.validate`에서 Fabric API의 accessor를 거부했습니다. 이제 Forge·NeoForge가 패치한 클래스에만 넣습니다.
- Forge·NeoForge와 함께일 때 Fabric API의 `AttributeSupplier$Builder.getBuilder()`가 더 이상 `LinkageError`(Guava `ImmutableMap$Builder`의 로더 제약 위반)를 던지지 않습니다. 게임 클래스로더가 브리지를 따로 한 벌 정의합니다.
- NeoForge에서, 실제 모드를 JarJar로 품은 game library jar(JEI가 필요로 하는 MezzConfig의 `-standalone.jar`)도 NeoForge가 실행하는 모드로 셉니다. 그래서 같은 모드의 Fabric 판은 함께 실행되지 않고 빠집니다(`POLY-COMPAT-139`). 전에는 서버가 "More than one MezzConfig physical-side provider is present"로 멈췄습니다.
- NeoForge 주입 지점: 1.2.3에서 넣은 클라이언트 항목 4개는 바닐라에 없는 호출을 가리켜 아무것도 옮기지 않았습니다. `ServerPlayer.openMenu`는 NeoForge가 실제로 옮긴 호출 두 개(`closeContainer()`, `send(Packet)`)로 고쳤고, 나머지 셋은 지웠습니다. `NeoForgeInjectionPointsTest`가 모든 항목을 NeoForge의 패치된 클라이언트와 바닐라 클라이언트에 대조합니다.
- Minecraft 26.3: 링크 검사가 `com.mojang.renderpearl`(Blaze3D의 새 이름)을 클라이언트 패키지로 봅니다. 전에는 26.3 모드 묶음의 Fabric 모드 9개(AppleSkin, Cloth Config, GeckoLib, YACL 등)와 이것들이 필요한 모드가 서버에서 `POLY-COMPAT-112`로 빠졌습니다.

### Installer

- The installer is `PolyModLoader-<version>.jar` only, on every platform (Java 17 or newer). The Windows `.exe` and its Rust launcher are gone, and releases attach the jar and `SHA256SUMS.txt`. Releases up to 1.2.3 keep their `.exe`.
- 설치 프로그램은 모든 시스템에서 `PolyModLoader-<버전>.jar` 하나입니다(Java 17 이상). Windows `.exe`와 그 Rust 실행기를 없앴고, 릴리스에는 jar와 `SHA256SUMS.txt`만 올립니다. 1.2.3까지의 릴리스에 있는 `.exe`는 그대로 둡니다.

## 1.2.3 — 2026-10-26

### Highlights

- Loading screen text rendering glitch fix: resolved font rendering and character set corruption on the custom Poly loading overlay across Minecraft 26.1, 26.2, and 26.3 using standard Component literal formatting with ChatFormatting color codes.
- Direct GitHub Issues routing for bug reports: error screens, crash handlers, and report documentation links now navigate directly to official GitHub Issues for immediate tracking and transparent issue resolution.
- NeoForge Fabric API compatibility adaptations: added NeoForgeInjectionPoints to route injection point replacements for client screens, container menus, and data attachment hooks under NeoForge.

### 주요 변경

- 로딩 화면 텍스트 깨짐 현상 수정: Minecraft 26.1, 26.2, 26.3 커스텀 Poly 로딩 오버레이에서 발생하던 폰트 렌더링 및 문자 깨짐 오류를 표준 Component literal 및 ChatFormatting 색상 코드로 수정하여 깨짐 없이 선명하게 렌더링되도록 개선했습니다.
- 오류 발생 시 GitHub Issues 바로 연결: 오류 발생 화면 및 버그 리포트 링크를 공식 GitHub Issues 페이지로 직접 연결하여 신속하고 투명하게 문제를 접수/추적할 수 있도록 개선했습니다.
- NeoForge 환경 Fabric API 호환 적응점 확대: NeoForge 환경에서 클라이언트 화면, 컨테이너 메뉴, 데이터 어태치먼트 훅을 위한 주입점 대체 규칙(`NeoForgeInjectionPoints`)을 추가하여 호환성을 한층 더 강화했습니다.

### Client and loader adaptations

- Standardized text rendering in `PolyLoadingOverlay`: replaced manual glyph manipulation with Minecraft's built-in `Component.literal` and `ChatFormatting` pipeline across `poly-game-v26_1`, `poly-game-v26_2`, and `poly-game-v26_3`, eliminating character encoding artifacts and unmapped glyph square icons across diverse GPU backends and window sizes.
- Streamlined crash report and error handling: routed error and feedback CTAs directly to `https://github.com/xyrz47-spec/Poly-Mod-Loader/issues/new`, providing user-friendly diagnostic issue reporting.
- Added `NeoForgeInjectionPoints`: adapted client screen mixins, menu hooks, and data attachments for NeoForge host patches.

## 1.2.2 — 2026-10-26

### Highlights

- Dedicated custom Poly loading screen (`PolyLoadingOverlay`): runs a sleek dark `#0E131F` loading overlay across Minecraft 26.1, 26.2 and 26.3, replacing vanilla and third-party loader loading screens.
- Live loader mod count badge overlay: displays real-time active mod counts per loader (`Fabric`, `Forge`, `NeoForge`, `Quilt`, and `Poly`) directly on the Poly loading screen.
- Suppressed host loader early windows and branding mod counts: hides Forge and NeoForge early progress windows, and strips third-party "(X mods loaded)" counts from title screen branding.
- Extensive Fabric API adaptations on Forge and NeoForge: adapted `fabric-item-api-v1`, `fabric-registry-sync-v0`, `fabric-content-registries-v0` (via FluidWrap), `fabric-data-generation-api-v1`, `fabric-entity-events-v1` (via BedWrap), `fabric-object-builder-api-v1` (via AttributeBuilderBridge), and Priority 3 modules so multi-loader modpacks run without crash or incompatibility.
- Modernized documentation and brand identity: unified branding as "Poly", clarified in-game bytecode engine identity vs external launchers, and enhanced security and verification transparency.

### 주요 변경

- Poly 전용 독자 로딩 화면(`PolyLoadingOverlay`): Minecraft 26.1, 26.2, 26.3 전 버전에 걸쳐 기존 및 타 로더의 로딩 화면을 대체하는 세련된 `#0E131F` 다크 테마 커스텀 로딩 화면을 적용했습니다.
- 실시간 로더별 모드 수 오버레이: 로딩 화면 상단에 로더별(`Fabric`, `Forge`, `NeoForge`, `Quilt`, `Poly`) 및 통합 모드 수를 실시간으로 보여주는 커스텀 뱃지 UI를 탑재했습니다.
- 타 로더 조기 로딩 창 및 모드 수 표기 차단: Forge 및 NeoForge의 고유 조기 진행 윈도우(`earlyprogresswindow`)를 끄고, 타이틀 화면 브랜딩 문자열에서 타 로더 모드 수 표기(`(X mods loaded)` 등)를 완전히 제거했습니다.
- Forge 및 NeoForge 환경에서의 Fabric API 호환성 대폭 강화: `fabric-item-api-v1`, `fabric-registry-sync-v0`, `fabric-content-registries-v0`(FluidWrap), `fabric-data-generation-api-v1`, `fabric-entity-events-v1`(BedWrap), `fabric-object-builder-api-v1`(AttributeBuilderBridge) 및 Priority 3 모듈을 호스트 패치에 맞추어 자동 적응하도록 브릿지를 구축하여 충돌 없이 함께 실행됩니다.
- 브랜드 정체성 및 사이트 신뢰성 강화: 공식 명칭을 "Poly"로 단일화하고, 런처가 아닌 마인크래프트 내부 바이트코드 변환 엔진 정체성을 명시하였으며, 보안 검증(SHA-256, 코드 서명) 체계를 보강했습니다.

### Fabric and Quilt mods next to a host loader

- Adapted `fabric-content-registries-v0`: added `FluidWrap` (`@WrapOperation` bridge) adaptations in `HostPatchAdjustments` and injection point replacements in `ForgeInjectionPoints` for Forge's custom fluid patches across `AbstractBoat` (`checkInWater` & `isUnderwater` -> `canBoatInFluid`), `Entity` (`updateSwimming` -> `isInWaterOrSwimmable` and `canStartSwimming`; `isVisuallyCrawling` -> `isInWaterOrSwimmable`), and `LivingEntity` (`travelInFluid` -> `travelInFluid(Vec3, FluidState)`, `baseTick` -> `ForgeHooks.onLivingBreathe`, and `aiStep` -> `jumpInFluid`), filtering out dangling MixinExtras `@Expression` / `@Definitions` annotations. The number of Fabric API modules blocked by `POLY-COMPAT-224` under MinecraftForge drops to 2 (`fabric-item-api-v1`, `fabric-registry-sync-v0`).
- Adapted `fabric-data-generation-api-v1`: added compound method & point retargeting for `Placement.IN_METHOD` in `HostPatchAdjustments` and injection point replacements in `ForgeInjectionPoints` for Forge's tag saving lambda changes on `TagsProvider.lambda$run$5` (`DataProvider.saveStable` -> `lambda$run$6`, and `TagFile.<init>(List, boolean)` -> `lambda$run$6:TagFile.<init>(List, boolean, List)`), allowing Fabric API's tag-building injectors to cleanly adapt across MinecraftForge. The number of Fabric API modules blocked by `POLY-COMPAT-224` under MinecraftForge drops to 3 (`fabric-content-registries-v0`, `fabric-item-api-v1`, `fabric-registry-sync-v0`).
- Adapted `fabric-entity-events-v1`: added `@WrapOperation bridge` adaptations in `HostPatchAdjustments` and injection point replacements in `ForgeInjectionPoints` for Forge's bed patches on `LivingEntity` (`getBedOrientation` -> `BlockState.getBedDirection`, `lambda$stopSleeping$0` -> `BlockState.setBedOccupied`), routing Fabric API's `EntitySleepEvents.MODIFY_SLEEPING_DIRECTION` and `EntitySleepEvents.SET_BED_OCCUPATION_STATE` cleanly across MinecraftForge. The number of Fabric API modules blocked by `POLY-COMPAT-224` under MinecraftForge drops to 4 (`fabric-content-registries-v0`, `fabric-data-generation-api-v1`, `fabric-item-api-v1`, `fabric-registry-sync-v0`).
- Adapted `fabric-object-builder-api-v1`: added `AttributeBuilderBridge` to bridge `AttributeSupplierBuilderAccessor.getBuilder()` (`ImmutableMap.Builder`) with Forge/NeoForge's `Map` field on `AttributeSupplier$Builder`, adapted the `@Accessor` via `POLY-COMPAT-212` in `HostAddedMembers`, and injected the bridge method and interface via `FabricClassTransformer`. The number of Fabric API modules blocked by `POLY-COMPAT-224` under MinecraftForge drops to 5.
- Priority 3 Fabric API native patch adaptations: supported equivalent collection getters in `HostAddedMembers` (allowing `TagFile.remove()` on Forge and NeoForge to be served by the host's record component accessor, resolving `fabric-tag-api-v1` with `POLY-COMPAT-212`), added `sameReturnType` retargeting for `@ModifyExpressionValue` in `HostPatchAdjustments`, and added NeoForge 3-arg `BlockParticleOption` constructor retarget rules for `Entity.spawnSprintParticle` and `LivingEntity.checkFallDamage` (`fabric-particles-v1`). The number of Fabric API modules blocked by `POLY-COMPAT-224` under MinecraftForge drops to 6.
- Before the dependency check, `HostPatchCheck` asks each bridge which mods cannot run as written on the host's patched classes (`ModLoaderBridge.hostIncompatibilities`, read from the host's jars, no class defined); those get a blocking `POLY-COMPAT-224` with every mixin and reason, and the mods that need them are left out with `114`. Every copy of a mod (installed and nested, in different versions) is checked.
- Kept, because the mixin still works as written: injections moved to where the host put the same point, bridges for added or reordered parameters, calls with trailing arguments, calls moved into a helper (new information code `POLY-COMPAT-226`), members the patch added with the same code and renamed locals (`212`), receiver guards (`222`).
- Removed: forcing every injector of those mods to `require = 0` and local capture to `CAPTURE_FAILSOFT`, leaving mixins out of their configuration, taking injector annotations off, renaming mixin methods (`213`) and accessors that throw when called (`214`). `138`, `213` and `214` are retired.
- The comparison now also sees slice boundaries the patch removed, injection points the patch moved out of a slice, `NEW` of a constructor the patch no longer calls, and, for a selector without a descriptor, only the first method of that name, as Mixin picks it (NeoForge's `BaseContainerBlockEntity.setItem(int, ItemStack)` now calls a new overload that holds the vanilla body).
- A failure the check before the launch did not foresee is no longer skipped: the second check before Mixin starts stops the launch, and a Mixin error in the game keeps Mixin's action, as under Fabric Loader. Both are reported as Poly Mod Loader's (`POLY-COMPAT-225`), with the mod to remove.
- 26.2, game test server (headless, 2026-10-25; 26.1 and 26.1.2 not run again): `corpusFabricForge` loads 42 foreign mods and leaves out 124 (before: 91 and 75), 14 of them with `224` (10 Fabric API modules, Architectury, Balm, Lithium, Puzzles Lib); `corpusFabricNeoForge` loads 28 Fabric mods (before: 54), 16 Fabric API modules with `224`; `corpusFabricOnNeoForge` 37 (before: 96). Next to NeoForge, Fabric API's resource loader is left out, so most Quilt mods' data packs are no longer enabled (`corpusQuiltNeoForge`), and Fabric API's registry sync no longer registers its `fabric:` channels (`corpusFabricNeoForgeJoin`). `forgeFabricApiGameTest` no longer shows Fabric's block break events next to Forge, because the modules behind them are left out. Every run creates, tests and saves its world with no Mixin error.

## 1.2.1 — 2026-10-25

### Highlights

- The installer window has a language menu in its top right corner: switch between English and 한국어 (Korean) at any time; what you have entered stays. The window still opens in your system's language.

### 주요 변경

- 설치 프로그램 창 오른쪽 위에 언어 메뉴가 생겼습니다. English와 한국어를 언제든 바꿀 수 있고, 입력한 값은 그대로 남습니다. 창은 여전히 시스템 언어로 열립니다.

### Installer

- `InstallerWindow.Language`: the window starts in `Language.system()`; choosing another language rebuilds the window in place and carries over the target, Minecraft version, folders, memory sizes, check boxes, the Advanced section and the log. The menu is disabled while an install runs.

### Building from source

- The public source repository holds the loader's source and build: `examples/`, `test-mods/`, `docs/` and the CI workflows are not in it. Without `examples/` and `test-mods/` the build includes neither; the loader, the installer and the unit tests build, and the game test tasks that need those mods stop with a message.
- Minecraft asset downloads no longer fail when several projects fill the shared cache at once on a fresh machine (Windows refused the second move of the same file); I/O errors and HTTP 429/5xx answers are retried.

## 1.2.0 — 2026-10-25

### Highlights

- Minecraft 26.3 is supported. Poly Mods run on it without being rebuilt, including their default key bindings (26.3 reads the keyboard through SDL; Poly Mod Loader converts its key codes, ADR-0029). Fabric, Quilt, Forge (26.3-66.1.0) and NeoForge (26.3.0.98) mods run with the compatibility test mods on 26.3; the real-mod corpus for 26.3 is not run yet.
- A mod can carry data and assets for several Minecraft versions in one jar: Poly Mod Loader now reads the overlays of a mod's `pack.mcmeta`, as the game does for its own packs. 26.3 changed several data formats (`worldgen/feature`, `material_rule`) and compiles shaders with `#include` and explicit locations, so mods that ship such files need a 26.3 overlay.
- Poly Mod Loader is now open source under the Mozilla Public License 2.0, and its source is public at https://github.com/xyrz47-spec/Poly-Mod-Loader. You may use, modify and redistribute it for any purpose, including commercial ones; files of Poly Mod Loader that you change and distribute stay under the MPL 2.0. Mods, modpacks and launchers choose their own license.
- Code in `templates/` and `examples/` is under MIT No Attribution, so it can be copied into mods freely. The names "Poly" and "Poly Mod Loader" and the logo are not covered by the license; `TRADEMARKS.md` says how to use them.
- Releases up to 1.1.0 keep the PolyLoader Non-Commercial License 1.0 they were distributed under.

### 주요 변경

- Minecraft 26.3을 지원합니다. Poly 모드는 다시 빌드하지 않고 26.3에서 실행되며, 기본 키 바인딩도 그대로입니다(26.3은 키보드를 SDL로 읽고, Poly 모드 로더가 키 코드를 바꿉니다, ADR-0029). Fabric·Quilt·Forge(26.3-66.1.0)·NeoForge(26.3.0.98) 모드가 26.3에서 호환성 테스트 모드와 함께 실행됩니다. 26.3 실제 모드 묶음 검증은 아직 하지 않았습니다.
- 모드 하나에 여러 Minecraft 버전용 데이터와 리소스를 담을 수 있습니다. 게임이 자기 팩에 하듯 모드의 `pack.mcmeta` 오버레이를 읽습니다. 26.3은 데이터 형식 몇 가지(`worldgen/feature`, `material_rule`)를 바꾸고 셰이더를 `#include`와 명시적 위치로 컴파일하므로, 그런 파일을 든 모드는 26.3 오버레이가 필요합니다.
- Poly 모드 로더가 Mozilla Public License 2.0을 따르는 오픈소스가 되었고, 소스를 https://github.com/xyrz47-spec/Poly-Mod-Loader 에 공개합니다. 상업적 목적을 포함해 어떤 목적으로든 사용, 수정, 재배포할 수 있습니다. 고쳐서 배포하는 Poly 모드 로더 파일은 MPL 2.0으로 남습니다. 모드, 모드팩, 런처는 자기 라이선스를 정합니다.
- `templates/`와 `examples/`의 코드는 MIT No Attribution이라 모드에 자유롭게 가져다 쓸 수 있습니다. "Poly", "Poly 모드 로더"라는 이름과 로고는 라이선스에 포함되지 않으며, 쓰는 방법은 `TRADEMARKS.md`에 있습니다.
- 1.1.0까지의 릴리스는 배포 당시의 PolyLoader Non-Commercial License 1.0을 따릅니다.

### Minecraft 26.3

- New modules `poly-version-adapter-v26_3` and `poly-game-v26_3`: natural mob spawns as the biome attribute `NATURAL_MOB_SPAWNS`, the merged `ChunkGenerator.buildTerrain`, features that carry their configuration, compiled density functions, the `renderpearl` rendering API (mod shader uniforms bound in `FrontendRenderPass.setPipeline`), and the spectated-entity post effect slot.
- Key codes (ADR-0029): `KeyCodes` and default keys stay GLFW values on every version; on 26.3 the game layer converts them to SDL scancodes, and a key 26.3 does not have starts unbound with `POLY-CLIENT-008`.
- Mod packs use the overlays their `pack.mcmeta` picks for the running pack format (all versions); an unreadable `pack.mcmeta` is `POLY-RESOURCE-010` and the pack is used without overlays.
- Forge 26.3-66.1.0 and NeoForge 26.3.0.98 run Forge and NeoForge mods on 26.3. NeoForge for 26.3 (FML 12) no longer runs graphics bootstrap plugins on a server, as on NeoForge's own server.
- A stopped server logs `[Poly Mod Loader] Server stopped after saving and closing its worlds` (26.3 no longer logs that all dimensions are saved).
- Checked on 26.3: `check`, `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests` (62), `compatibilityMatrix` (17 scenarios), `neoForgeWorldTest`, `forgeWorldTest`, `serverInstallTest` (installed server with Forge, Quilt and NeoForge mods), and with a window `clientWorldReloadTest` (mod shaders, uniforms, key bindings) and `launcherProfileTest`. 26.1 and 26.2 pass the same headless checks. Not run on 26.3 yet: the real-mod corpus (`compatibilityCorpus`).
- Test worlds made by the 26.3 game test server are flat in every dimension, which the game opens only after a backup prompt; the client checks give the copied world the game's own nether and end generators. `serverInstallTest` installs the Minecraft version the build runs, which its test mods are built for.

### License (ADR-0028)

- `LICENSE` is the Mozilla Public License 2.0 text; `templates/LICENSE` and `examples/LICENSE` are MIT No Attribution; `TRADEMARKS.md` is new; `THIRD_PARTY_NOTICES.md`, which every jar carries in `META-INF`, names the license and where the source is (MPL 2.0 section 3.2).

## 1.1.0 — 2026-10-25

### Highlights

- NeoForge mods now run in the same game as Fabric and Quilt mods on Minecraft 26.1, 26.1.2 and 26.2: a client with about 60 Fabric and 50 NeoForge mods joins a world, plays, saves and quits; the game test server and the dedicated server run them too.
- Fabric mods that NeoForge with Sinytra Connector could not start in our test (Architectury, Balm, Lithium, Puzzles Lib, Forge Config API Port) run next to NeoForge on Poly Mod Loader.
- What cannot run together is left out before the game starts, with the reason. Only Forge mods and NeoForge mods together are still not possible (`POLY-COMPAT-221`).

### 주요 변경

- Minecraft 26.1, 26.1.2, 26.2에서 NeoForge 모드를 Fabric·Quilt 모드와 한 게임에서 실행합니다. Fabric 모드 약 60개와 NeoForge 모드 약 50개를 넣은 클라이언트가 월드에 들어가 플레이하고 저장한 뒤 종료하며, 게임 테스트 서버와 전용 서버에서도 실행됩니다.
- 우리 테스트에서 NeoForge + Sinytra Connector가 시작하지 못한 Fabric 모드(Architectury, Balm, Lithium, Puzzles Lib, Forge Config API Port)가 Poly 모드 로더에서는 NeoForge 옆에서 실행됩니다.
- 함께 실행할 수 없는 것은 게임 시작 전에 이유와 함께 빠집니다. Forge 모드와 NeoForge 모드를 한 게임에 넣는 것만 아직 안 됩니다(`POLY-COMPAT-221`).

### NeoForge with Fabric and Quilt (ADR-0026 30-43)
- NeoForge mods now run in the same game as Fabric and Quilt mods (ADR-0026 30-33), as Forge mods already do: one Mixin serves every loader, NeoForge's FML does not start its own, and Fabric mods initialise with NeoForge's registries open. Checked on the game test server and the dedicated server with the real-mod corpus (`corpusFabricNeoForge`, `corpusFabricOnNeoForge`, `corpusFabricNeoForgeServer`, `corpusQuiltNeoForge`) and headless on the client (`corpusFabricNeoForgeClientMixins`), on 26.2, 26.1.2 and 26.1; see `docs/COMPATIBILITY.md`. `POLY-COMPAT-216` now names this combination only when NeoForge's Mixin start is not the code Poly Mod Loader checked.
- Fabric mixins that a host loader's patch stops are handled more carefully next to NeoForge and Forge: injections that read state a stopped injection sets stop too, a mixin that would replace an argument the host computes does not run (both `POLY-COMPAT-138`), Mixin puts field initialisers right after the delegate constructor call, and a widened field no longer stops NeoForge's field-to-getter coremod.
- A mod built for Fabric and for the host loader under IDs each loader spells its way runs once (ADR-0026 37). Cloth Config is `cloth-config` for Fabric and `cloth_config` for NeoForge; with both builds, the client stopped because both registered Cloth's example config (`POLY-COMPAT-110`). The same mod is now recognised by the same ID, the ID as FML spells it, a provided ID, or at least half of its classes in one host jar. The host's build runs, the other build is left out with `POLY-COMPAT-139`, and when the two builds are mostly the same code, the host's build meets the mods that need the left-out one (Better Shulker Boxes keeps running with NeoForge's Cloth Config; next to Forge, FusionFix now runs with Forge's Fusion).
- With NeoForge and Fabric API on the client, Fabric API's freeze of the built-in registries after mod initialization no longer stops the game with `Registry is already frozen` (ADR-0026 38): NeoForge has frozen them by then, so a second freeze leaves out the tag binding for registries already frozen (`poly:builtin_registries_refreeze`). A first freeze is unchanged.
- A Fabric mixin whose injection casts to an interface that another, left-out mixin would have added (or calls a left-out accessor mixin) no longer runs half-applied: the injection is taken out with `POLY-COMPAT-138` before the game starts, instead of a `ClassCastException` in the game (Fabric API's `guiRendererReady` next to NeoForge, ADR-0026 39). A wrap of a constructor call that the host's patch gives other parameters is taken out too, so Mixin no longer drops the other mods' mixins on that class (Iris next to Fabric API). NeoForge's client Mixin audit now fails on code that uses such a missing type.
- NeoForge and Fabric API networking on one connection (ADR-0026 40): Fabric API's payload codec is wired into NeoForge's codec again (a Mixin wrap of a call the host passes more arguments to now runs through a bridge), NeoForge's own channel-registration payloads are written with NeoForge's codec where Fabric API owns the id on the wire, and NeoForge's channel checks leave Fabric payloads to Fabric API. Before, a client with both could not join a world (`Failed to encode packet ... minecraft:register`). A host-parameter bridge now keeps MixinExtras `@Local` and other parameter annotations (Fabric API's loot table event under NeoForge).
- Play-phase networking with NeoForge and Fabric API (ADR-0026 41): a player no longer drops about two seconds after joining with `Unknown addon`. NeoForge's play listener passes custom payloads to its parent class, where vanilla's ignores them, so a NeoForge mod's payload reached Fabric API's configuration-phase handler; a Fabric mixin injection into such a method now keeps to the listeners it sees in vanilla (`POLY-COMPAT-222`). NeoForge's bundle filter no longer drops Fabric payloads sent in a bundle (a bundle of only Fabric payloads closed the connection). The headless join check `corpusFabricNeoForgeJoin` now goes into the play phase and exchanges both loaders' payloads, on 26.2, 26.1.2 and 26.1.
- With NeoForge and Fabric API on the client, rendering in a world no longer stops with `Conflicting default methods` (ADR-0026 42): where NeoForge's patch and a Fabric mod (Fabric API's `FabricBlockStateModel`, EntityCulling) give one game interface two defaults of the same method, the interface keeps NeoForge's, as with Forge (`POLY-COMPAT-223`). The NeoForge Mixin audit now also finds such classes without starting the game.
- A NeoForge mod's Mixin configuration no longer drops whole when one of its targets loaded before Mixin selected it (ADR-0026 43): with Iris and Fabric mods on 26.2, all of Lithium's `lithium.mixins.json` was skipped while Lithium's other mixins ran, and the integrated server stopped with a `ClassCastException` (`BlockEntityGetter`). Only the mixins into such a class are skipped now (`POLY-COMPAT-220`), and the Mixin audit also checks the mod classes the game's transformed code calls.
- New codes: `POLY-COMPAT-220` (a NeoForge mod's mixin whose target loaded before its configuration was selected does not apply, as under NeoForge's own Mixin), `POLY-COMPAT-221` (with Forge mods in the game, NeoForge mods are not loaded; ADR-0026 34 explains why Forge and NeoForge cannot share one game yet), `POLY-COMPAT-222` (information: a Fabric mixin injection keeps to the receivers it has in vanilla where the host's patch makes a subclass's override call it), `POLY-COMPAT-223` (a game interface that the host's patch and a Fabric mod give two defaults of one method keeps the host's; limited when their code differs).

### 바뀐 점

- NeoForge 모드가 Forge 모드처럼 Fabric·Quilt 모드와 한 게임에서 실행됩니다(ADR-0026 30~33). 하나의 Mixin이 모든 로더를 맡고, NeoForge의 FML은 자기 Mixin을 시작하지 않으며, Fabric 모드는 NeoForge의 레지스트리가 열린 채로 초기화됩니다. 실제 모드 묶음으로 게임 테스트 서버와 전용 서버에서(`corpusFabricNeoForge`, `corpusFabricOnNeoForge`, `corpusFabricNeoForgeServer`, `corpusQuiltNeoForge`), 클라이언트는 창 없이(`corpusFabricNeoForgeClientMixins`) 26.2, 26.1.2, 26.1에서 확인했습니다(`docs/COMPATIBILITY.md`). `POLY-COMPAT-216`은 이 조합에서는 NeoForge의 Mixin 시작이 확인한 코드와 다를 때만 나옵니다.
- 호스트 로더의 패치로 멈추는 Fabric mixin을 NeoForge·Forge 옆에서 더 조심스럽게 다룹니다: 멈춘 주입이 설정하는 상태를 읽는 주입도 멈추고, 호스트가 직접 계산한 인자를 바꾸는 mixin은 실행하지 않으며(둘 다 `POLY-COMPAT-138`), Mixin은 필드 초기화를 위임 생성자 호출 바로 뒤에 두고, 넓힌 필드 때문에 NeoForge의 필드-getter coremod가 멈추지 않습니다.
- Fabric용과 호스트 로더용으로 ID를 다르게 쓴 같은 모드는 한 번만 실행됩니다(ADR-0026 37). Cloth Config는 Fabric에서 `cloth-config`, NeoForge에서 `cloth_config`입니다. 두 빌드가 다 실행되면 둘 다 Cloth의 예제 설정을 등록해 클라이언트가 멈췄습니다(`POLY-COMPAT-110`). 이제 다음 중 하나로 같은 모드를 알아봅니다: 같은 ID, FML 표기로 바꾼 ID, 제공 ID, 또는 클래스의 절반 이상이 한 호스트 jar에 있음. 호스트 빌드가 실행되고 다른 빌드는 `POLY-COMPAT-139`로 빠집니다. 두 빌드가 대부분 같은 코드이면, 빠진 빌드가 필요한 모드는 호스트 빌드가 대신 충족합니다(Better Shulker Boxes는 NeoForge의 Cloth Config로 계속 실행되고, Forge 옆에서는 FusionFix가 이제 Forge의 Fusion으로 실행됨).
- 클라이언트에서 NeoForge·Fabric API와 함께일 때, Fabric API가 모드 초기화 뒤 내장 레지스트리를 얼리면서 `Registry is already frozen`으로 멈추던 문제를 고쳤습니다(ADR-0026 38). NeoForge가 그때 이미 얼렸으므로, 두 번째 얼리기에서는 이미 언 레지스트리의 태그 묶기를 건너뜁니다(`poly:builtin_registries_refreeze`). 첫 번째 얼리기는 바뀌지 않습니다.
- 다른 mixin(빠진 것)이 더했을 인터페이스로 바꾸거나 빠진 접근자 mixin을 부르는 Fabric mixin 주입은, 이제 반쯤 적용된 채 실행되지 않습니다. 게임 안의 `ClassCastException` 대신 시작 전에 `POLY-COMPAT-138`로 빠집니다(NeoForge 옆 Fabric API의 `guiRendererReady`, ADR-0026 39). 호스트 패치가 다른 매개변수를 넘기는 생성자 호출을 감싸는 주입도 빠지므로, Mixin이 그 클래스의 다른 모드 mixin을 버리지 않습니다(Fabric API 옆 Iris). NeoForge 클라이언트 Mixin 검사는 이런 빠진 형을 쓰는 코드를 실패로 셉니다.
- NeoForge와 Fabric API의 네트워킹이 한 연결에서 동작합니다(ADR-0026 40). Fabric API의 페이로드 코덱이 다시 NeoForge의 코덱에 붙고(호스트가 인자를 더 넘기는 호출을 감싸는 Mixin은 다리로 실행), Fabric API가 선을 맡은 식별자로 NeoForge가 보내는 자기 등록 페이로드는 NeoForge의 코덱으로 쓰며, NeoForge의 채널 검사는 Fabric 페이로드를 Fabric API에 맡깁니다. 전에는 둘을 함께 쓴 클라이언트가 월드에 들어가지 못했습니다(`Failed to encode packet ... minecraft:register`). 호스트 매개변수 다리가 MixinExtras `@Local` 같은 매개변수 주석을 이제 지킵니다(NeoForge 옆 Fabric API의 전리품 표 이벤트).
- NeoForge·Fabric API와 함께인 놀이 단계 네트워킹(ADR-0026 41): 접속하고 약 2초 뒤 `Unknown addon`으로 끊기던 문제를 고쳤습니다. NeoForge의 놀이 리스너는 사용자 지정 페이로드를 상위 클래스로 넘기는데 바닐라는 무시하므로, NeoForge 모드의 페이로드가 Fabric API의 설정 단계 핸들러에 닿았습니다. 이제 그런 메서드로의 Fabric mixin 주입은 바닐라에서 보는 리스너에서만 실행됩니다(`POLY-COMPAT-222`). NeoForge의 묶음 걸러내기가 묶음으로 보낸 Fabric 페이로드를 더는 빼지 않습니다(Fabric 페이로드만 든 묶음은 연결을 끊었음). 창 없는 접속 검사 `corpusFabricNeoForgeJoin`이 이제 놀이 단계에 들어가 두 로더의 페이로드를 주고받습니다(26.2·26.1.2·26.1).
- 클라이언트에서 NeoForge·Fabric API와 함께일 때 월드에서 그리다가 `Conflicting default methods`로 멈추던 문제를 고쳤습니다(ADR-0026 42). NeoForge의 패치와 Fabric 모드(Fabric API의 `FabricBlockStateModel`, EntityCulling)가 한 게임 인터페이스에 같은 메서드의 default를 둘 주면, Forge 때처럼 NeoForge 것을 씁니다(`POLY-COMPAT-223`). NeoForge Mixin 감사가 이런 클래스도 게임을 실행하지 않고 찾습니다.
- NeoForge 모드의 Mixin 설정이, Mixin이 그 설정을 고르기 전에 대상 하나가 로드되었다고 통째로 빠지지 않습니다(ADR-0026 43). 26.2에서 Iris·Fabric 모드와 함께일 때 Lithium의 `lithium.mixins.json` 전체가 빠지고 Lithium의 다른 mixin만 실행되어, 통합 서버가 `ClassCastException`(`BlockEntityGetter`)으로 멈췄습니다. 이제 그런 클래스로의 mixin만 빠지고(`POLY-COMPAT-220`), Mixin 감사가 게임의 변환된 코드가 부르는 모드 클래스도 확인합니다.
- 새 코드: `POLY-COMPAT-220`(설정이 선택되기 전에 대상 클래스가 로드된 NeoForge 모드의 mixin은 NeoForge 자신의 Mixin에서처럼 적용되지 않음), `POLY-COMPAT-221`(Forge 모드가 함께 있으면 NeoForge 모드를 로드하지 않음; Forge와 NeoForge가 아직 한 게임을 나눌 수 없는 이유는 ADR-0026 34번), `POLY-COMPAT-222`(정보: 호스트의 패치가 하위 클래스의 재정의에서 부르게 만든 메서드로의 Fabric mixin 주입은 바닐라에서와 같은 수신 객체에서만 실행), `POLY-COMPAT-223`(호스트의 패치와 Fabric 모드가 같은 메서드의 default를 둘 준 게임 인터페이스는 호스트 것을 씀; 코드가 다르면 limited).

## 1.0.0 — 2026-10-24

### Highlights

- Poly Mod Loader 1.0.0, the first stable release: Fabric, Forge, NeoForge and Quilt mods are all fully supported on Minecraft 26.1, 26.1.2 and 26.2, each checked with popular real mods on the client, the game test server, the dedicated server and installed profiles.
- NeoForge mods now run by default with NeoForge's own loader, no option needed; the files NeoForge needs are downloaded on the first start.
- Fabric, Forge and Quilt mods can be mixed in one game. NeoForge mods do not run in the same game as Fabric or Quilt mods yet: they are left out with the reason (`POLY-COMPAT-216`).
- For mod developers: a Poly Mod that declares `"poly": ">=0.1.0-alpha <0.2.0"` does not load on 1.0.0, because that range ends below 1.0.0. Declare `">=1.0.0 <2.0.0"`, as the mod template, the examples and the SDK documentation now do.

### 주요 변경

- 첫 정식 버전 Poly 모드 로더 1.0.0: Minecraft 26.1, 26.1.2, 26.2에서 Fabric, Forge, NeoForge, Quilt 모드를 모두 완전히 지원합니다. 로더마다 실제 인기 모드로 클라이언트, 게임 테스트 서버, 전용 서버, 설치한 프로필에서 확인했습니다.
- 이제 NeoForge 모드는 옵션 없이 기본으로 NeoForge 자신의 로더로 실행됩니다. NeoForge에 필요한 파일은 처음 시작할 때 받습니다.
- Fabric, Forge, Quilt 모드는 한 게임에 섞어 쓸 수 있습니다. NeoForge 모드는 아직 Fabric·Quilt 모드와 한 게임에서 실행되지 않으며, 이때는 이유와 함께 빠집니다(`POLY-COMPAT-216`).
- 모드 개발자 안내: `"poly": ">=0.1.0-alpha <0.2.0"`을 선언한 Poly 모드는 1.0.0에서 로드되지 않습니다. 이 범위가 1.0.0 아래에서 끝나기 때문입니다. 모드 템플릿, 예제, SDK 문서처럼 `">=1.0.0 <2.0.0"`으로 바꿔 주세요.


### NeoForge (ADR-0026, adopted)

- NeoForge mods are fully supported on Minecraft 26.1, 26.1.2 and 26.2 (`docs/COMPATIBILITY.md` "NeoForge 완전 지원 점검"): the real-mod corpus's NeoForge mods play a saved world on the client, save it and quit (`corpusNeoForgeClient`: 54 mods on 26.2, 55 on 26.1.2, 36 on 26.1), run on the game test server and the dedicated server, and the installed launcher profile and server run them. Not supported yet: NeoForge mods in the same game as Fabric or Quilt mods (`POLY-COMPAT-216`).
- NeoForge mods run by default with NeoForge's own loader (FML 11) on Minecraft 26.1 (NeoForge 26.1.0.19-beta), 26.1.1 (26.1.1.15-beta), 26.1.2 (26.1.2.122) and 26.2 (26.2.0.93). NeoForge has only beta builds for 26.1 and 26.1.1; Poly Mod Loader uses the last one. `-Dpoly.neoforge=off` keeps the old behaviour (NeoForge mods diagnosed and left out).
- The real-mod corpus's NeoForge mods run on the game test server (`corpusNeoForge`) and start the dedicated server up to the EULA check (`corpusNeoForgeServer`) on 26.1, 26.1.2 and 26.2, with the same game layer as NeoForge's own installation. Mods that stop a NeoForge server under NeoForge's own launcher too (Sodium and the mods that use its classes, ImmediatelyFast's 26.2 build, Zoomify; on 26.1 three builds for later NeoForge releases) are left out of the server runs; each was checked against NeoForge's own server.
- An installed server runs NeoForge mods from `mods/` on the game test server and the dedicated server (`serverInstallTest`); the launch jar's Java agent opens `java.lang.invoke` to NeoForge's loader.
- A NeoForge server now has the libraries NeoForge's own server class path has (LWJGL's core and tinyfd on 26.1.x, tinyfd on 26.2), so ImmediatelyFast's 26.1 build starts on a server as under NeoForge.
- NeoForge's `ModList.getModFileById` and `isLoaded` answer for Poly Mods too (ADR-0025); new code `POLY-COMPAT-218` when NeoForge's `ModList` is not the checked code.
- The log line is now `Loaded mods per loader: N Poly, N Fabric, N Quilt, N Forge, N NeoForge`, and the title screen has a "NeoForge mods" line (English and Korean), drawn above NeoForge's own branding. The lines are added to the list NeoForge's branding is drawn from (`BrandingControl.getBrandings`), so mods that draw the title screen's branding themselves, such as FancyMenu, show them too. `POLY-COMPAT-219` is retired before any release used it.
- Sodium's NeoForge jar (an FML library with an early service, the mod nested in it) now loads as under NeoForge: FML loads it as an early service and the nested mod from its JarJar, also for mods outside the mods folder. Before, FML saw it as a library only, and Iris, Sodium Extra and Reese's Sodium Options found Sodium missing on the client. Regression scenario `neoForgeEarlyService`.
- A NeoForge launch could hang while mods were constructed in parallel: the game class loader asked FML's plugin class loader under its own class loading lock, while that loader asked the game class loader under its lock. The game class loader now asks its parent and fallbacks without holding its lock.
- The NeoForge requirements check (`POLY-COMPAT-217`) takes nested jars from JarJar's `META-INF/jarjar/metadata.json`, as FML does, so Lambda Dynamic Lights' nested API (in `META-INF/jars/`) counts as installed.
- When no Fabric or Quilt mod runs (NeoForge or Forge mods only, or only Poly Mods), game and mod code no longer see Fabric Loader's and Quilt Loader's API, which Poly Mod Loader carries for its bridges; under NeoForge and Forge they are not there either. Yumi, in Lambda Dynamic Lights, took the NeoForge client for a Fabric one, could not find Lambda Dynamic Lights' default configuration, and the client stopped while starting (ADR-0026, decision 27). New test mod `neoforge-environment-probe`, which `corpusNeoForge` uses to make the same lookup headless.
- Build: the 26.1 NeoForge client runs leave out Lambda Dynamic Lights 4.11.2+26.1.2, whose client code needs a NeoForge class that NeoForge 26.1.0.19-beta does not have (real NeoForge stops there too); it still runs on the 26.1 server.
- NeoForge mods still do not run together with Fabric or Quilt mods (`POLY-COMPAT-216`); ADR-0026 gives the reasons.
- New tasks: `neoForgeWorldTest` (every supported version), `corpusNeoForgeServer`, `startupBenchmarkNeoForge`, `tickBenchmarkNeoForge`, and the window tasks `neoForgeClient`, `corpusNeoForgeClient`, `launcherProfileNeoForgeTest`. New test mod `neoforge-client-session`. `corpusNeoForgeRun` is replaced by `corpusNeoForge`.

## 0.2.0-beta.3 — 2026-10-24

### Highlights

- Quilt mods are fully supported on Minecraft 26.1, 26.1.2 and 26.2: checked with every Quilt-only build on Modrinth for each version on the client, the game test server and the dedicated server, also together with Forge mods, and compared with real Quilt Loader; an installed server and launcher profile run a Quilt mod too. Mods that need Quilt Standard Libraries (QSL), which has no release for 26.x, are left out with the reason.
- A Fabric or Quilt mod whose Mixin the Mixin library itself refuses is now left out before the game starts with the reason (`POLY-COMPAT-140`), instead of keeping the client from joining a world.
- Minecraft 1.21.11 is an **experimental** version for development: its modules build and its dedicated server boots through Poly Mod Loader on a game jar remapped to Mojang names (ADR-0019 PoC). It is **not supported**: the installer, the SDK and the website do not offer it.

### 주요 변경

- Minecraft 26.1, 26.1.2, 26.2에서 Quilt 모드를 완전히 지원합니다. 버전마다 Modrinth의 Quilt 전용 빌드 전체로 클라이언트, 게임 테스트 서버, 전용 서버에서 확인했고, Forge 모드와 함께 넣은 경우와 실제 Quilt Loader의 결과도 비교했습니다. 설치한 서버와 런처 프로필에서도 Quilt 모드를 실행했습니다. 26.x 판이 없는 Quilt 표준 라이브러리(QSL)가 필요한 모드는 이유와 함께 빠집니다.
- Mixin 라이브러리 자신이 거부하는 Mixin을 가진 Fabric·Quilt 모드는 이제 게임 시작 전에 이유와 함께 빠집니다(`POLY-COMPAT-140`). 전에는 클라이언트가 월드에 들어가지 못했습니다.
- Minecraft 1.21.11은 개발용 **실험** 버전입니다. 모듈이 빌드되고, Mojang 이름으로 다시 매핑한 게임 jar로 전용 서버가 Poly 모드 로더를 거쳐 시작됩니다(ADR-0019 PoC). **지원 버전이 아닙니다.** 설치 프로그램·SDK·웹사이트에 나오지 않습니다.

### Quilt (full support checks, ADR-0027)

- A Fabric jar built for Quilt that requires `quilt_loader` now loads, as under Quilt Loader (Better Shulker Boxes, F34, InvSearch Storage Indexer, MTA NYC Subway Signs). `breaks` and `conflicts` on `quilt_loader` in `fabric.mod.json` are ignored, and `FabricLoader.isModLoaded("quilt_loader")` stays `false`.
- `POLY-COMPAT-124` no longer names nested mods that are left out only because they are for the other side (`108`, `305`), such as Fabric API's client modules inside Simple Voice Chat on a dedicated server. Fabric Loader and Quilt Loader leave them out there too.
- With mods of another loader installed, a class path jar that holds no classes and only a `Class-Path` (Gradle's way to start a JVM with a long class path on Windows) now stands for its entries. Before, the game class loader followed it to the vanilla game jar, so with MinecraftForge FML found the vanilla game instead of Forge's patched one.
- New `POLY-COMPAT-140`: a Fabric or Quilt mod whose mixin has an injection point `target` that Mixin itself refuses is left out before the game starts when its Mixin configuration is required (a warning otherwise). Before, the target class failed to load in the game; Even Better Moon and Sun kept the client from ever joining a world.
- Build: `mixinAuditQuiltClient` and `mixinAuditQuiltServer` apply every mixin of the Quilt corpus to its targets without starting the game (test mod `fabric-mixin-audit`); NeoFullbright 1.0+mc26.2, whose mixin Mixin refuses on the client under every loader, is left out of the client runs.
- Build: client checks (`corpusQuiltClient` and the other `ClientWorldReloadTask` runs) start Java with an argument file, so a long mod list no longer exceeds Windows' command line limit.
- Build: `modCorpusQuilt` (every Quilt-only build on Modrinth for the version), `corpusQuilt`, `corpusQuiltServer`, `corpusQuiltForge`, `corpusAssessmentQuilt`, `corpusQuiltClient` (window), `quiltLoaderReference` (real Quilt Loader on the vanilla server), `startupBenchmarkQuilt`, `tickBenchmarkQuilt`; `serverInstallTest` and `launcherProfileTest` run a Quilt mod too.




### Older Minecraft versions (ADR-0019)

- `remapMinecraft -Pminecraft=<version> [-Pside=server]` remaps an obfuscated client or server jar with Mojang's mappings (ASM, hierarchy-aware, lambdas included), computes stack map frames again, and self-checks it: no default-package class left, no duplicate member, every reference to a game class resolves, every class links on Java 25. All eleven versions from 1.14.4 to 1.21.11 pass. Mappings and remapped jars stay in the Gradle cache.
- For a version with official mappings the build compiles and runs against the remapped jar (`RemappedGameJar`).
- `gradle/minecraft-versions.properties` has an `experimental` list (`newMinecraftVersion -Pexperimental=true`): modules that build and run with `-PpolyMinecraft`, left out of the installer, the SDK and the website.
- `poly-version-adapter-v1_21_11` and `poly-game-v1_21_11`, copied from 26.1 with 57 compile fixes and retargeted rules (`Gui.render`, `GlRenderPass.setPipeline`, `GameRenderer.getFov`, `Player.interactOn` without a click location, and others; see `docs/plans/MINECRAFT_1_21.md`). `BuiltInRulesTest` passes against the remapped jar.
- Commands and results: `serverBootSmokeTest -PpolyMinecraft=1.21.11` passes on Java 25 with no added JVM option. `worldCreateSaveTest -PpolyMinecraft=1.21.11` fails: the example mod `native_showcase` ships a 26.x dimension type that 1.21.11 cannot read; without that one attribute the world, the game test and the save passed. The client was not started. Fabric mods are not blocked yet on 1.21.11.

## 0.2.0-beta.2 — 2026-10-24

### Highlights

- Quilt mods (experimental, Minecraft 26.2): Poly Mod Loader now reads `quilt.mod.json` with Quilt Loader's rules and runs Quilt mods with the Quilt Loader API, their Mixins and access wideners, next to Fabric mods and Poly Mods. Checked with Simple Voice Chat's Quilt build. Mods that need Quilt Standard Libraries (QSL) are left out with the reason, because QSL has no release for Minecraft 26.x.
- NeoForge preview (still experimental and off by default): a NeoForge mod that NeoForge would refuse is now left out with the reason instead of stopping the launch, and more real mods run, such as mods that need Kotlin for Forge or YACL, and Lithium.
- The title screen and the log count Quilt mods on their own line.

### 주요 변경

- Quilt 모드(실험, Minecraft 26.2): 이제 `quilt.mod.json`을 Quilt Loader 규칙대로 읽고, Quilt 모드를 Quilt Loader API, Mixin, access widener와 함께 Fabric 모드·Poly 모드 옆에서 실행합니다. Simple Voice Chat의 Quilt 빌드로 확인했습니다. Quilt 표준 라이브러리(QSL)가 필요한 모드는 이유와 함께 빠집니다. QSL은 Minecraft 26.x 판이 없기 때문입니다.
- NeoForge 미리보기(여전히 실험이고 기본값은 꺼짐): NeoForge가 거부할 NeoForge 모드는 실행 전체를 멈추는 대신 이유와 함께 빠집니다. Kotlin for Forge나 YACL이 필요한 모드, Lithium 같은 실제 모드가 더 실행됩니다.
- 타이틀 화면과 로그에 Quilt 모드 수가 따로 표시됩니다.

### Quilt (ADR-0027)

- `quilt.mod.json` is read as Quilt Loader 0.31 reads it (`QuiltModJsonReader`): a bare version such as `1.0.0` means `^1.0.0`, `optional` dependencies are checked only when that mod is installed, `any`/`all` groups and `unless`, `provides` with versions, `breaks`. Nested mods in `quilt_loader.jars` load like Fabric nested jars; a nested mod with `load_type` `if_required` that no loaded mod requires is left out (`POLY-COMPAT-307`), as under Quilt Loader.
- The Quilt Loader API (`org.quiltmc.loader.api`, from Quilt Loader 0.31.0-beta.4) is packed into the Fabric bridge jar, with Poly's own `QuiltLoaderImpl`, `GameProvider` and `DefaultLanguageAdapter`. `QuiltLoader` sees every loaded mod of every loader as a `ModContainer`. Quilt Loader internals that are not packed are blocked by the link check (`POLY-COMPAT-111`).
- Entrypoints `pre_launch` (before every `preLaunch`), `main`, `client` and `server` run; Fabric and Quilt mods share one entrypoint store. QSL's `init`, `client_init` and `server_init` are not run (`303`), as under Quilt Loader without QSL.
- Quilt mods' Mixin configurations join the one Mixin (compatibility level 0.10.0), every `access_widener` entry applies, and `@ClientOnly`, `@DedicatedServerOnly` and `@Requires` strip Quilt mods' classes as Quilt Loader does.
- A jar with both `fabric.mod.json` and `quilt.mod.json` runs as a Fabric mod (`310`), so jars that already ran keep running.
- New codes `POLY-COMPAT-300`–`310`: invalid `quilt.mod.json` (300), loader plugins (301), language adapters (302), QSL entrypoints (303), other entrypoints (304), environment (305), the modifiable file system overlay (306), unused nested mods (307), QSL or Quilted Fabric API dependencies (308, with `114`), fields with no effect (309), jars with both metadata files (310).
- Forge's mod list shows a Quilt mod's name from `quilt.mod.json`. The log line is now `Loaded mods per loader: N Poly, N Fabric, N Quilt, N Forge`, and the title screen has a "Quilt mods" line (English and Korean).
- Quilt Loader's licence (Apache-2.0) is in `THIRD_PARTY_NOTICES.md`.

### NeoForge (experimental, `-Dpoly.neoforge=on`)

- A NeoForge mod that FML 11 would refuse is left out before FML starts, with the mods that need it (`POLY-COMPAT-217`): the `javafml` loader version, a missing or left-out required mod, a version outside a declared range, an installed incompatible mod.
- Jars with `neoforge.mods.toml` besides `fabric.mod.json` (Collective, Iris, Zoomify) go to NeoForge when NeoForge runs, as NeoForge reads only its own metadata.
- The game class loader falls back to FML's plugin libraries last, as FML's class loader does (Kotlin for Forge's standard library, YACL's nested libraries), and classes that FML's class processors generate (Mixin's for Lithium) are defined.
- Other loaders' mod lists include the mods FML runs.

## 0.2.0-beta.1 — 2026-10-24

### Highlights

- Beta: MinecraftForge mods are fully supported on Minecraft 26.1, 26.1.2 and 26.2, checked with real popular Forge mods on the client, the game test server, the dedicated server and installed profiles.
- NeoForge preview (experimental, off by default): with `-Dpoly.neoforge=on`, Poly Mod Loader runs NeoForge's own loader (NeoForge 26.2.0.93) and loads NeoForge mods with it. Without it, NeoForge mods are left out as before.

### 주요 변경

- 베타: Minecraft 26.1, 26.1.2, 26.2에서 MinecraftForge 모드를 완전히 지원합니다. 실제 인기 Forge 모드로 클라이언트, 게임 테스트 서버, 전용 서버, 설치한 프로필에서 확인했습니다.
- NeoForge 미리보기(실험, 기본값은 꺼짐): `-Dpoly.neoforge=on`을 주면 Poly 모드 로더가 NeoForge 자신의 로더(NeoForge 26.2.0.93)를 실행해 NeoForge 모드를 로드합니다. 켜지 않으면 지금처럼 NeoForge 모드는 빠집니다.

### Mod SDK repository

- The mod plugin's SDK repository (`PolyModPlugin.SDK_REPOSITORY_URL`), the mod template and the Crystal Garden example use `https://www.polymc.net/maven/`.

### NeoForge on Poly Mod Loader, experimental (ADR-0026, N0-N3)

- The game jar with NeoForge's patches is built on the player's PC from the installer's `NFPATCHBUNDLE001` bundle (`NeoForgePatchBundle`); for NeoForge 26.2.0.93 and 26.1.2.122, client and server, every file is byte-identical to what NeoForge's installer makes.
- With `-Dpoly.neoforge=on` and NeoForge mods, `NeoForgeGameHost` downloads NeoForge's files (pinned installer SHA-1, `neoforge-releases.properties`), defines NeoForge's loader libraries as modules of the game class loader and runs FML's own `FMLLoader.create`. Two checked calls of `FMLLoader` are replaced (`NeoForgeLoaderPatch`): the game class loader defines FML's game layer, and FML loads the NeoForge mods Poly Mod Loader found instead of scanning the mods folder. FML's class processors (access transformers, enum extension, Mixin) run in the bridge stage.
- Poly Mod Loader's Java agent now also opens `java.lang.invoke` to NeoForge's modules, as NeoForge's own launcher profile does with `--add-opens` (`JdkModuleAccess`).
- On a dedicated server, NeoForge, a NeoForge test mod and a Poly Mod start up to the EULA check. Not yet: NeoForge with Fabric mods (NeoForge's mods are left out then), the client, 26.1, NeoForge's own game test server (it does not start Mojang's test under NeoForge's own launcher either).
- New codes: `POLY-COMPAT-215` (NeoForge's `FMLLoader` is not the checked code; the launch stops), `POLY-COMPAT-216` (NeoForge cannot run in this launch; its mods are left out).

## 0.1.0-alpha.18 — 2026-10-23

### Highlights

- Forge, Fabric and Poly mods see each other: Forge mods find Fabric and Poly mods through FML's `ModList`, Fabric mods find Forge mods through `FabricLoader`, and Poly Mods find every loader's mods through the new `ModContext.mods()`. Physics Mod (Forge) with Sodium (Fabric) now draws block debris and ragdolls.
- With Fabric API and Forge together, crafting works again (it failed on every crafting remainder), Fabric data behind resource conditions no longer stops the data pack from loading, and Fabric's block breaking events fire.
- The title screen shows how many mods each loader loaded, in the bottom-left corner above the game version: "Poly mods: 2", "Fabric mods: 14", "Forge mods: 3", in the game's language (English and Korean so far). With Forge mods, the lines go above Forge's own branding lines.
- With Forge, more of Fabric API works: its networking and recipe APIs' server connection access, tag files that remove entries with `fabric:remove` (next to Forge's own `remove`), and the data generation advancement builder. Fabric API's default attribute changes (`FabricDefaultAttributeRegistry.MODIFY`) still do not work with Forge, and now say why instead of crashing with a `ClassCastException`.
- Forge and Fabric mods together now also run on Minecraft 26.1 in our tests, and the 26.1 Forge client and a server installed for 26.1.2 run too.

### 주요 변경

- Forge·Fabric·Poly 모드가 서로를 봅니다. Forge 모드는 FML `ModList`로 Fabric·Poly 모드를, Fabric 모드는 `FabricLoader`로 Forge 모드를, Poly 모드는 새 `ModContext.mods()`로 모든 로더의 모드를 확인합니다. 그래서 Physics Mod(Forge)와 Sodium(Fabric)을 함께 넣어도 블록 파편과 래그돌이 제대로 나옵니다.
- Fabric API와 Forge를 함께 넣으면 조합이 다시 됩니다(남는 아이템마다 실패했음). 리소스 조건 뒤의 Fabric 데이터가 더는 데이터 팩 로딩을 멈추지 않고, Fabric의 블록 부수기 이벤트가 실행됩니다.
- 타이틀 화면 왼쪽 아래, 게임 버전 위에 로더별로 로드한 모드 수가 나옵니다("Poly 모드: 2개", "Fabric 모드: 14개", "Forge 모드: 3개"). 게임 언어를 따르며 지금은 영어와 한국어가 있습니다. Forge 모드가 있으면 Forge의 브랜딩 줄 위에 나옵니다.
- Forge와 함께 쓸 때 Fabric API가 더 많이 동작합니다. 네트워킹·레시피 API의 서버 연결 접근, `fabric:remove`로 항목을 빼는 태그 파일(Forge의 `remove`와 함께), 데이터 생성의 발전 과제 빌더가 됩니다. Fabric API의 기본 속성 변경(`FabricDefaultAttributeRegistry.MODIFY`)은 Forge와 함께 여전히 동작하지 않지만, 이제 `ClassCastException`으로 멈추지 않고 이유를 알려 줍니다.
- 시험에서 Minecraft 26.1에서도 Forge와 Fabric 모드가 함께 실행되고, 26.1 Forge 클라이언트와 26.1.2용으로 설치한 서버도 실행됩니다.

### Forge and Fabric on 26.1, the 26.1 Forge client and installed server (2026-10-23, Forge full support B2, B6)

- Fixed: with Fabric mods, a Forge mod whose classes carry Fabric's `@Environment(CLIENT)` (a mod built from code shared with its Fabric build, such as No Chat Reports for Forge) stopped the server with "Cannot load class ... in environment type SERVER". Fabric's environment stripping now leaves the classes of the mods Forge runs alone, as Forge ignores those annotations. `corpusFabricForge` now passes on 26.1 too
- Known side effect of ADR-0025: Balm for Forge now sees the Fabric mod `fabric-permissions-api-v0` and tries its Fabric integration class, which its Forge build does not have; Balm logs the error and goes on
- `corpusForgeClient` passes on 26.1 (Forge 62.0.9): the saved world opens, 200 ticks, save and quit. Cherished Worlds 16.0.1+26.1.2 is left out of the corpus runs: Forge's own EventBus refuses its single-listener class, so real Forge fails the same way
- `serverInstallTest -PserverInstallMinecraft=<version>` installs and starts any supported version; on 26.1.2 Forge 64.1.5 runs on the game test server and the dedicated server. Its first phase no longer mistakes the mod counts line ("0 Forge") for Forge running

### Fabric mixins that meet members Forge's patch added or changed (2026-10-23, ADR-0021 G8)

- Four Fabric API mixins were dropped whole with Forge (`POLY-COMPAT-138` "does not apply to the game"), because Forge's patch adds or changes a member the mixin also adds or uses. The target class then did not implement the mixin's interface, so Fabric code casting to it would have thrown `ClassCastException`. Mixin also skips every other mixin of a class once one fails, so fabric-recipe-api-v1's accessor on the same class went with it, without a message. Each case was checked with `javap` on Forge 26.2-65.1.5's patched classes and Fabric API 0.161.0
- New rules, decided before Mixin starts by comparing the vanilla and the patched class (`HostAddedMembers`, `HostRenamedLocals`, unit tests in `HostPatchAdjustmentsTest`):
  - An `@Accessor` or `@Invoker` whose method the patch added as a public method with exactly the accessor's code: Mixin does not generate it and the patched method serves it (`ServerCommonPacketListenerImpl.getConnection()` returns field `connection`, for fabric-networking-api-v1 and fabric-recipe-api-v1). The same method added with other code leaves the mixin out when its configuration requires `@Overwrite`; otherwise Mixin replaces the patched method, as before, and that is now reported (`POLY-COMPAT-213`; architectury's `LiquidBlockAccessor.getFluid()` replaces Forge's `LiquidBlock.getFluid()`, which Forge's supplier-built liquid blocks need)
  - A method a mixin adds that the patch also added with the same instructions stays the patched class's (`Advancement$Builder.save(Consumer, Identifier)`, fabric-data-generation-api-v1). With other code, the mixin's method gets its own name (`poly$<mixin>$<name>`) and the calls in the same mod's mixins follow it (`TagFile.remove()`: Forge's list from `"remove"`, Fabric's from `"fabric:remove"`; fabric-tag-api-v1's `TagLoaderMixin` now reads Fabric's). Other code calling that name, also through Fabric's `FabricTagFile` interface, reaches Forge's method (`POLY-COMPAT-213`)
  - An `@Accessor` of a field whose type the patch changed (`AttributeSupplier$Builder.builder`, `ImmutableMap.Builder` in vanilla, `Map` with Forge): no accessor of the old type can exist. The rest of the mixin applies, so the cast works, and calling the accessor throws `POLY-COMPAT-214`, which names the field and both types. `FabricDefaultAttributeRegistry.MODIFY` uses it and does not work with Forge; `FabricDefaultAttributeRegistry.register` does not use it
  - A MixinExtras `@Local(name = ...)` whose local the patch renamed, in the same slot and with a type the parameter accepts, captures it under the patched name and type. Forge renames `SequencedSet values` to `LinkedHashSet builder` in `TagLoader.tryBuildTag`, where fabric-tag-api-v1 removes entries; Mixin finds a local by name only with the parameter's exact type, and under Forge every injector is optional, so that removal silently did not run. A local no single local replaces is reported (`POLY-COMPAT-138`), unless the injection is already reported as not running
- New test mod `fabric-host-patch-probe` in `corpusFabricForge`: it casts a server packet listener to Fabric's networking accessor and calls it, loads a tag with Forge's `remove` and Fabric's `fabric:remove` (only `minecraft:stone` of stone, dirt and sand stays), calls the attribute builder accessor, and checks the advancement builder. The scenario's mod count is now 91
- `corpusFabricForge` (26.2): `POLY-COMPAT-138` lines 33 → 30; whole-mixin failures 4 → 0. The 30 left are injections whose call or field Forge's patch moved or removed (29) and the attribute builder accessor above
- New codes: `POLY-COMPAT-212` (a Fabric mixin adjusted to the patched class and working as written; logged at INFO), `POLY-COMPAT-213` (a Fabric mixin method and a patched method share a name and descriptor with other code: renamed, or replaced by Mixin), `POLY-COMPAT-214` (a Fabric accessor that cannot work on the patched game was called)

### Fabric API under Forge: block breaking events, resource conditions, crafting (2026-10-23, ADR-0021 G8)

- Crafting failed with Fabric API and Forge together: every call of `ItemStack.getCraftingRemainder()` (the crafting grid, furnace fuel, brewing) threw `IncompatibleClassChangeError: Conflicting default methods`, because Fabric API's `FabricItemStack` and Forge's `IForgeItemStack` both give `ItemStack` that default method (and `FabricItem`/`IForgeItem` give `Item` `getCraftingRemainder(ItemStack)`). Before Mixin starts, a Fabric mixin that adds such an interface now gets a method that calls the host's default; both defaults do the vanilla thing, and an item that overrides the method (Fabric or Forge) still wins. Reported with `POLY-COMPAT-138`
- A data file behind a Fabric resource condition that is not met (`fabric:load_conditions`) stopped the whole data pack from loading under Forge ("Failed to load vanilla datapack", `ClassCastException`): Forge's patch reordered the parameters of `SimpleJsonResourceReloadListener.lambda$scanDirectory$0`, so Fabric API's `skipData` was taken out and its skip marker became a recipe. An `@Inject` whose target's parameters the patch reordered (each type once) now gets the same bridge as one whose target only gained parameters
- Fabric API's `PlayerBlockBreakEvents` BEFORE, AFTER and CANCELED now fire under Forge (survival and creative, with the block entity). Forge's patch moves `Block.playerWillDestroy` and `Block.destroy` from `ServerPlayerGameMode.destroyBlock` into its new `removeBlock(BlockPos, boolean)`. The Forge bridge now says how each moved point stands (`InjectionPointReplacement.Placement`): BEFORE runs at the first call after the same permission checks (`ServerPlayer.preventsBlockDrops()`), and AFTER runs after each `removeBlock` call that removed the block, through a generated MixinExtras `@WrapOperation` (`AFTER_TRUE_RESULT`). The Fabric bridge checks that the patched classes have that shape before it moves anything (26.2-65.1.5 and 26.1-62.0.9 checked)
- An injector bound to the call itself (`@Redirect`, `@WrapOperation` and the like) now moves to a host's replacement only when that call has the same owner and descriptor
- New check scenarios `fabricApiGameTest` and `forgeFabricApiGameTest` (part of `compatibilityCorpus`, 26.2): the test mod `fabric-api-events` uses real Fabric API from the corpus, with the same 15 expected lines without and with Forge
- `corpusFabricForge`: injections that do not run under Forge (`POLY-COMPAT-138`) went from 28 to 25; still out are Fabric API's fluid hooks, sleep events, `UseItemOnBlock` wrap in `ItemStack.useOn`, the enchanting table check, and some Lithium, Architectury and Balm points (see `docs/COMPATIBILITY.md`)

### Mod counts per loader on the title screen (2026-10-23)

- Counting rule: every mod a loader actually loads counts once by mod ID, whether the player installed it or an installed mod bundled it (nested jars); blocked mods and mods left out do not count, nor do Poly Mod Loader itself, `minecraft`, `java`, `fabricloader` and `forge`. Forge mods are the mods FML loads when Forge runs the game. A loader with no mods has no line. The log says `Loaded mods per loader: N Poly, N Fabric, N Forge` at startup
- Without Forge, the new optional rule `poly:client_title_mod_counts` redirects the title screen's one call that draws the version text (`RedirectCall`); the hook draws that line and the counts above it, with the same font, position and fade. Checked with `javap` on 26.1, 26.1.1, 26.1.2 and 26.2
- With Forge, whose patch draws its branding instead of the version text, the Forge bridge's rule `poly:client_title_mod_counts_forge` redirects Forge's `BrandingControl.forEachLine` call and adds the lines above Forge's (checked on the patched clients of all four pinned Forge releases). Host loaders can now declare Poly Mod Loader rules for their patched game (`GameHost.transformRules`, ADR-0021)
- The texts are translation keys (`poly.title.mod_count.poly`, `.fabric`, `.forge`) in Poly Mod Loader's own `assets/poly/lang/en_us.json` and `ko_kr.json`; a resource pack can replace them, and a language without a file shows English
- When Forge's patch replaced the target of one of Poly Mod Loader's optional rules (`poly:server_brand`, `poly:client_title_mod_counts`), the log now says so at INFO instead of a "Rejected transformation" warning
- `fabric-client-session` (a test mod) now leaves the world with "Save and Quit to Title" and stays on the title screen for 2 seconds before it stops the client, so `forgeClient` and the corpus client runs also draw the title screen
- New error code `POLY-COMPAT-210`: Forge's `BrandingControl.forEachLine` cannot be reached from the title screen (a damaged Forge installation)

### Forge mods left out for their Mixin configurations: checked against real Forge (2026-10-23, B5)

- Real Forge 62.0.9 (Minecraft 26.1, Mixin 0.8.7) stops before Minecraft starts with each of the three 26.1 corpus mods that Poly Mod Loader leaves out for a Mixin configuration (`POLY-COMPAT-209`): Balm 26.1.0.6 (its nested kuma-api names `kuma_api.api.mixins.json`, which no jar has) and Boatiview 0.0.9 and No Chat Reports 2.19.0 (`compatibilityLevel` `JAVA_25`, unknown to Mixin 0.8.7). Mixin throws `MixinInitialisationError`, an `Error` its own `catch (Exception)` does not stop. Leaving these mods out stays as it is
- The check now reads the configurations as Mixin does: only `MixinConfigs` names ending in `.json` (Mixin ignores the others, so a missing one no longer leaves a mod out, and one Mixin serving Forge and Fabric mods no longer tries to read it), the level trimmed and upper-case (`java_21` is known), and the levels in nested jars as well as the mod's own
- `corpusForge`: 26.1 20 of 38 Forge mod files meet their requirements, 26.2 30 of 38, as before

### Forge and Fabric mods see each other (2026-10-23, ADR-0025)

- Player report: with Physics Mod (Forge) and Sodium (Fabric) installed together, block debris and ragdolls were drawn wrongly or not at all. Physics Mod asks Forge's mod list whether Sodium is installed (`ModList.getModFileById("sodium") != null`); Forge's list did not know Fabric mods, so Physics Mod took its vanilla render path while Sodium had replaced it. The other way round, Fabric mods' `FabricLoader.isModLoaded` did not see Forge mods either
- Forge's `ModList.getModFileById` and `ModList.isLoaded` now also answer for the Fabric mods and Poly Mods in the same game (their own and `provides` IDs), with a mod file that has the mod's ID, name, version and jar. Forge's own answer comes first, so a Forge mod keeps its ID. `getModContainerById` stays empty for them: they are not in Forge's mod lifecycle
- Fabric mods now see the Forge mods in the same game (`isModLoaded`, `getModContainer`, type `minecraftforge`), without resource roots, since Forge already loads their resources
- Poly Mods can now see the mods of every loader too: the new `ModContext.mods()` (`LoadedMods`, PREVIEW) answers `isLoaded`, `find` (ID, version, name, loader `poly`/`fabric`/`minecraftforge`, provided IDs) and `all()`. Before, a Poly Mod could only declare dependencies on other loaders' mods, not ask whether an optional one is there. The site's Operations page shows how
- Checked with screenshots (`physicsProbeSodiumClient`, 26.2): with Sodium, Physics Mod now breaks the block into debris and the killed zombie falls as a ragdoll, as with Physics Mod alone
- The change to Forge's `ModList` is made only when its code is exactly what Poly Mod Loader checked (Forge 26.2-65.1.5 and 26.1-62.0.9); otherwise the launch goes on without it and says so (`POLY-COMPAT-211`)
- A mod that switches on an optional integration because it now sees another loader's mod may meet new problems in that integration; report them with the log

## 0.1.0-alpha.17 — 2026-10-23

### Highlights

- Fixed a crash on the first frame when Sodium (Fabric) and a Forge mod such as Physics Mod are installed together.
- When Forge changes a game method that a Fabric mod hooks into, Poly Mod Loader now adapts the hook if Forge only added parameters, instead of losing other mods' changes to the same class.
- Forge mods now also run together on Minecraft 26.1 in our test (a world is created, tested and saved).

### 주요 변경

- Sodium(Fabric)과 Physics Mod 같은 Forge 모드를 함께 넣으면 첫 화면에서 튕기던 문제를 고쳤습니다.
- Fabric 모드가 연결하는 게임 메서드를 Forge가 바꿨을 때, Forge가 매개변수만 더한 경우에는 그 연결을 맞춰 줍니다. 더 이상 같은 클래스에 붙는 다른 모드의 변경까지 사라지지 않습니다.
- 시험에서 Minecraft 26.1에서도 Forge 모드 묶음이 함께 실행됩니다(월드 생성, 게임 테스트, 저장).

### Fabric mods whose injections Forge's patches changed (2026-10-23, ADR-0021 G8)

- Sodium with a Forge mod (Physics Mod) crashed on the first frame with a `ClassCastException` (`LevelExtractor` cannot be cast to Fabric API's `LevelRendererExtensions`). Forge patches `LevelExtractor.extractVisibleBlockEntities` to take one more parameter (`Frustum`); Sodium injects there with the vanilla parameters, Mixin rejected that handler ("Invalid descriptor"), and the failure took the other mods' mixins on the same class, Fabric API's among them, with it
- Before Mixin starts, an `@Inject` whose target method Forge's patch gave more parameters now runs through a bridge with the patched parameters that passes the handler the ones it was written for (`POLY-COMPAT-138` says so). When the patch changed parameters instead, only that injection is taken out and reported; the other mixins of the class still apply
- `forgeSodiumClient` (opens a window): Sodium and Physics Mod from the corpus enter a saved world on the client, play 200 ticks, save and quit

## 0.1.0-alpha.16 — 2026-10-23

### Highlights

- Forge mods now work in the Poly Mod Loader you install. Put Forge mods in `mods/` and the launcher profile or a server from the installer runs them. In our test the client entered a world with Forge test mods next to a Fabric mod, and the installed server ran a Forge mod.
- The first start with Forge mods downloads Forge's files (about 50 MB) from Forge's servers; without Forge mods nothing changes.
- Profiles and servers installed with an older installer cannot run Forge mods: install again with this version.
- Forge support is still experimental.

### 주요 변경

- 이제 설치한 Poly 모드 로더에서 Forge 모드가 실행됩니다. `mods/`에 Forge 모드를 넣으면 설치 프로그램이 만든 런처 프로필과 서버가 실행합니다. 시험에서는 클라이언트가 Forge 테스트 모드와 Fabric 모드를 함께 넣고 월드에 들어갔고, 설치한 서버가 Forge 모드를 실행했습니다.
- Forge 모드를 넣고 처음 시작하면 Forge 파일(약 50MB)을 Forge 서버에서 받습니다. Forge 모드가 없으면 달라지는 것이 없습니다.
- 이전 설치 프로그램으로 설치한 프로필과 서버는 Forge 모드를 실행하지 못합니다. 이 버전으로 다시 설치하세요.
- Forge 지원은 아직 실험 단계입니다.

### Forge mods in the installed Poly Mod Loader (2026-10-23, ADR-0024, ADR-0021 G9)

- Forge mods now load in the Poly Mod Loader that the installer sets up: the Minecraft Launcher profile and the dedicated server from `install-server`. Before, they were left out with `POLY-COMPAT-206` because Forge's file systems were not on the JVM class path
- Poly Mod Loader has a small Java agent (`SystemClassPathAgent`) that only keeps the JVM's `Instrumentation`. The launcher profile starts it with `-javaagent:${library_directory}/…`, and `polymodloader-server.jar` names it as `Launcher-Agent-Class`, so `java -jar polymodloader-server.jar` needs no extra flag
- Only when Forge mods are present, Forge's `securemodules`, `unsafe`, `JarJarFileSystems` and `roimfs` (downloaded from Forge's Maven with SHA-1 checks, as before) are added to the JVM class path. Without Forge mods nothing is downloaded and the class path is unchanged
- `POLY-COMPAT-206` now says why: started without the agent (install the profile or server again with this installer), or Java listed its file systems before Forge's were added
- Profiles and servers installed by an older installer have no agent; install again to use Forge mods

## 0.1.0-alpha.15 — 2026-10-23

### Highlights

- PolyLoader is now **Poly Mod Loader**. The installer is `PolyModLoader-<version>.exe` / `.jar`, the launcher profile is **Poly Mod Loader <Minecraft version>**, and the releases live at github.com/xyrz47-spec/Poly-Mod-Loader.
- A profile from an older installer stays in the launcher next to the new one; remove it there if you no longer need it.
- Mods made with the old `dev.polyloader` SDK need to be rebuilt with the new SDK.
- Forge mods now run on the client and on Minecraft 26.1 in our test setup. The installed Poly Mod Loader does not run Forge mods yet; that is the next step.

### 주요 변경

- PolyLoader의 이름이 **Poly 모드 로더**(Poly Mod Loader)로 바뀌었습니다. 설치 파일은 `PolyModLoader-<버전>.exe`/`.jar`, 런처 프로필은 **Poly Mod Loader <Minecraft 버전>**이고, 릴리스는 github.com/xyrz47-spec/Poly-Mod-Loader에 있습니다.
- 이전 설치 프로그램이 만든 프로필은 새 프로필 옆에 그대로 남습니다. 필요 없으면 런처에서 지우세요.
- 이전 `dev.polyloader` SDK로 만든 모드는 새 SDK로 다시 빌드해야 합니다.
- 시험 환경에서 Forge 모드가 클라이언트와 Minecraft 26.1에서도 실행됩니다. 설치한 Poly 모드 로더에서 Forge 모드를 실행하는 것은 다음 단계입니다.

### Renamed: Poly, and Poly Mod Loader (2026-10-23, ADR-0023)

- The product is now **Poly** and the mod loader **Poly Mod Loader** (Korean: Poly 모드 로더). PolyLoader is the old name
- Identifiers that held `polyloader` now hold `polymodloader`: Java packages `dev.polymodloader.*`, the Maven group and the mod plugin ID `dev.polymodloader.mod`, the installer `PolyModLoader-<version>.jar`/`.exe`, the server jar `polymodloader-server.jar`, and the launcher version `polymodloader-<version>-<minecraft>` with the profile "Poly Mod Loader <minecraft>"
- The public GitHub repository is now `xyrz47-spec/Poly-Mod-Loader`
- Unchanged: the `poly` prefix (modules, `poly.mod.json`, `POLY-` error codes, mod ID `poly`, `.poly/`), the website address and the license name
- Mods built against the `dev.polyloader` SDK need to be rebuilt with the new SDK. A profile installed by an older installer stays next to the new one until removed

### Forge on the client and on Minecraft 26.1 (2026-10-23, ADR-0021 G7)

- MinecraftForge runs on the client too: the real Forge mods of the corpus, with a Fabric mod, enter a saved world, play and save (`corpusForgeClient`, 26.2)
- When the host loader patched the target of one of Poly Mod Loader's own rules (Forge pages creative tabs itself), that rule is off with a warning instead of stopping the launch
- With Forge running, a jar built for several loaders is run by Forge also when Fabric mods are installed (JourneyMap's Forge jar carries a Fabric stub that stops Fabric Loader on purpose). In `corpusFabricForge` the Fabric side is now 90 mods
- Forge mods' Mixin configurations from nested jars carry the mod ID of the jar that ships them, so Mixin's generated member names stay valid
- MinecraftForge for Minecraft 26.1, 26.1.1 and 26.1.2 (Forge 62.0.9, 63.0.2, 64.1.5). A Forge patch applies to the class it names as its original, as Forge's installer does (some 26.1 patches name the patched class differently)

## 0.1.0-alpha.14 — 2026-10-23

### Highlights

- Forge mods and Fabric mods now run together in one game. In our test on a Minecraft 26.2 server, 91 popular Fabric mods (Fabric API among them) ran next to the Forge mods: the world was created and saved.
- Where a Fabric mod changes a part of the game that Forge changed too, Poly Mod Loader moves the change to Forge's version when it can, and otherwise logs which part of the mod does not run under Forge, instead of crashing.
- When the same mod is installed for both Fabric and Forge, the Forge version runs.
- Forge on the client is still in progress.

### 주요 변경

- 이제 Forge 모드와 Fabric 모드를 한 게임에서 함께 실행합니다. Minecraft 26.2 서버 시험에서 Fabric API를 포함한 인기 Fabric 모드 91개가 Forge 모드와 함께 실행되었고, 월드 생성과 저장까지 되었습니다.
- Fabric 모드가 Forge도 바꾼 게임 부분을 바꾸면, Poly 모드 로더가 가능한 경우 Forge 쪽 위치로 옮기고, 그렇지 않으면 그 모드의 어느 부분이 Forge에서 실행되지 않는지 로그에 남깁니다. 게임이 멈추지 않습니다.
- 같은 모드를 Fabric용과 Forge용으로 둘 다 넣으면 Forge용이 실행됩니다.
- 클라이언트의 Forge는 아직 진행 중입니다.

### Forge and Fabric mods together (2026-10-23, ADR-0021 G8)

- MinecraftForge mods and Fabric mods now run in one game. `POLY-COMPAT-207` (Forge mods left out next to Fabric mods) is retired
- One Mixin serves both: Fabric's Mixin and MixinExtras on Poly Mod Loader's class path also apply the Forge mods' `MixinConfigs`; Forge's own Mixin is left out of FML's boot layer, and Forge's MixinExtras library is given to FML without the MixinExtras copy it carries
- Fabric mods' mixins are checked against the classes Forge patched before Mixin starts: an injection point Forge moved follows it (`Bootstrap.wrapStreams` to `GameData.vanillaSnapshot`, where Fabric API's registry sync starts), an injection point Forge removed is reported and does not run, and a mixin whose shadowed or accessed member Forge removed is left out. Fabric injectors become optional and local captures soft, so one such mixin no longer stops the game or the other mods' mixins on the same class. New `POLY-COMPAT-138` [limited]
- Forge's registries are open for registration while the Fabric mods' initializers run, as during Forge's `RegisterEvent`
- Forge's field-to-method transformer sees fields another loader's class tweaker widened as private, as in the game it patched (Fabric API's biome API widens `Biome.climateSettings`)
- A Fabric mod that is also installed as a Forge mod with the same ID (the same mod built for both loaders) is left out; the Forge build runs. New `POLY-COMPAT-139`
- A class a bridge generates into a package a module holds is defined there; the game class loader reads the host's patched game, not the vanilla copy on the class path, for classes not yet defined
- Real-mod corpus on 26.2 (`corpusFabricForge`, game test server): 91 Fabric mods with the Forge mods; the world is created, a game test passes and the world is saved

## 0.1.0-alpha.13 — 2026-10-23

### Highlights

- Real Forge mods now run on a Minecraft 26.2 server through Poly Mod Loader: in our test set of 38 popular Forge mods, 30 load and the world is created and saved. The other 8 are client-only mods or need a different Minecraft version; Poly Mod Loader leaves them out and logs the reason.
- Fixed a Forge config save failure on Windows (`AccessDeniedException`) when a mod writes its config right after Forge creates it.
- Forge on the client, and Forge mods next to Fabric mods, are still in progress.

### 주요 변경

- 이제 Poly 모드 로더로 Minecraft 26.2 서버에서 실제 Forge 모드가 실행됩니다. 인기 Forge 모드 38개로 시험한 결과 30개가 로드되고 월드 생성과 저장까지 됩니다. 나머지 8개는 클라이언트 전용이거나 다른 Minecraft 버전용이라 Poly 모드 로더가 빼고 이유를 로그에 남깁니다.
- Windows에서 Forge가 설정 파일을 만든 직후 모드가 그 파일을 저장하면 실패하던 문제(`AccessDeniedException`)를 고쳤습니다.
- 클라이언트의 Forge, 그리고 Forge 모드와 Fabric 모드를 함께 실행하는 것은 아직 진행 중입니다.

### Forge: real mods (2026-10-23, ADR-0021 G6)

- A jar built for several loaders (`fabric.mod.json`, `neoforge.mods.toml` and `mods.toml` together, or a ModLauncher transformation service as in CustomSkinLoader) follows the loader the instance runs: MinecraftForge runs it when a mod only Forge runs is installed and no mod only Fabric runs; otherwise Fabric does, as before. Such jars used to count as Fabric mods and stopped every Forge mod (`POLY-COMPAT-207`)
- New `POLY-COMPAT-209`: a Forge mod FML would refuse is left out before FML starts, with the reason, and so are the mods that need it, so the other mods still run (FML stops the whole launch). Checked as FML does, with Forge's own Maven version ranges: the language loader's version, required mods, the version ranges of installed dependencies (`minecraft` and `forge` included, mods nested in jars counted), and, on a server, client-only mods (`@OnlyIn(Dist.CLIENT)` `@Mod` class, or `minecraft` needed on the client side only)
- Poly Mod Loader's game test launch of Forge sets Forge's `production` property: Forge behaves as on a player's server (it checks only Minecraft's classes for their side) and still runs game tests
- On a server, a jar without `mods.toml` whose `fabric.mod.json` says `"environment": "client"` is left out with `209` (CustomSkinLoader): FML cannot tell its side, and its ModLauncher service would add client code to Minecraft's classes
- The Forge mods Poly Mod Loader found that provide a ModLauncher transformation service, a mod locator or a dependency locator go to FML's service layer, as Forge does with such jars in `mods/` (Crash Assistant). Forge's own scan of `mods/` no longer decides: it also took jars Poly Mod Loader left out or gave to Fabric
- Mods that read ModLauncher's launch arguments through reflection (`Launcher.argumentHandler`) find them
- night-config 3.7.4, which Forge ships, left the file of every new empty config open; on Windows the next save that replaced the file failed (`AccessDeniedException`, Forge Config API Port saving Open Parties and Claims' server config at the world start). Poly Mod Loader closes it. night-config alone fails the same way, so a player's Forge on Windows meets this too
- New `POLY-BOOTSTRAP-007`: when the game stops with an error, Poly Mod Loader logs it and exits with status 1, so threads mods started no longer keep the process running without a game
- Real-mod corpus on 26.2 (`corpusForge`, game test server): FML constructs 30 of the 38 Forge mods, the world is created, a game test passes and the world is saved; 8 are left out with `209` (client-only mods on a server, and a library built for Minecraft 26.3 with the mod that needs it)

## 0.1.0-alpha.12 — 2026-10-23

### Highlights

- Forge mods can now use access transformers and Mixin, as they do under Forge. Forge support is still in progress: Forge mods do not run next to Fabric mods yet.

### 주요 변경

- 이제 Forge 모드가 Forge에서처럼 access transformer와 Mixin을 쓸 수 있습니다. Forge 지원은 아직 진행 중이고, Forge 모드와 Fabric 모드를 함께 실행하는 것은 아직 안 됩니다.

### Forge: access transformers and Mixin (2026-10-23, ADR-0021 G5)

- MinecraftForge mods' access transformers (`META-INF/accesstransformer.cfg`) apply, through Forge's own `accesstransformer` launch plugin. Test mod `forge-at` reads a private field and calls a private method of the game from its constructor
- MinecraftForge mods' Mixin configurations (the manifest's `MixinConfigs`) apply, with Forge's own Mixin 0.8.7 and MixinExtras: Forge's Mixin is a module of FML's boot layer, ahead of the Fabric fork on Poly Mod Loader's class path. Test mod `forge-mixin` injects into a method Forge patches and one Poly Mod Loader transforms
- FML's boot layer: a library's `module-info` becomes an open module, as in Forge's own boot layer; a package exported only to a module the class path stands in for is exported to the class path
- ModLauncher services: while a launch plugin is processing a class, a class nothing has is not offered to the plugins to generate (Mixin looks for classes while it builds its transformer)
- Still: Forge mods do not run next to Fabric mods (`POLY-COMPAT-207`), so the real-mod corpus's Forge folder, which has jars for several loaders, loads none yet

## 0.1.0-alpha.11 — 2026-10-22

### Highlights

- Fabric mods are now fully supported on Minecraft 26.1 and 26.2. With popular Fabric mods together (138 on 26.2, Sodium, Iris and Lithium among them) you can play a world, save it and quit, and they start a dedicated server. The only mods left out in our tests are ones Fabric Loader refuses too.
- CustomSkinLoader and veinminer now work, and collective now loads on servers.

### 주요 변경

- 이제 Minecraft 26.1과 26.2에서 Fabric 모드를 완전히 지원합니다. 인기 Fabric 모드(26.2는 Sodium, Iris, Lithium 등 138개)를 함께 넣고 월드를 플레이하고 저장한 뒤 나올 수 있고, 전용 서버도 시작됩니다. 테스트에서 빠진 모드는 Fabric Loader도 실행하지 않는 경우뿐이었습니다.
- CustomSkinLoader와 veinminer가 동작하고, collective가 서버에서도 로드됩니다.

### Fabric: Fabric Loader's launcher, and LaunchWrapper references (2026-10-22)

- Fabric Loader's internal `FabricLauncherBase.getLauncher()` and `FabricLauncher` are provided (signatures of Fabric Loader 0.19.5), answered by Poly Mod Loader's game class loader: `addToClassPath` adds a jar the mod unpacked, and class bytes, resources, the environment and the development flag are available. `getMappingConfiguration` and `getEntrypoint` are not (no Fabric mappings on unobfuscated Minecraft; Poly Mod Loader starts the game itself). CustomSkinLoader adds its common jar this way
- `FabricLoaderImpl.getGameProvider()` and a `GameProvider` with the methods that describe the game (ID, name, raw and normalized version, directory). veinminer reads the game version this way
- Link check: for these partial stand-ins of Fabric Loader's internal classes, members they lack block the mod (`POLY-COMPAT-111`) instead of failing later with `NoSuchMethodError`
- New bridge SPI `GameClassPath`: the game class loader takes jars while the game starts
- Link check: references to LaunchWrapper (`net.minecraft.launchwrapper`, a library of the 1.6-1.12 launchers, never game classes) are noted as a missing library (`POLY-COMPAT-113`) instead of blocking as missing game classes (`112`). Multi-version mods keep such code for old versions
- Real-mod corpus, client verdict: on 26.2 `customskinloader-bootstrap` now loads (128 foreign mods together, was 127; 43 directly installed Fabric mods, was 42); on 26.1 `customskinloader-bootstrap` and `veinminer` now load (122 together, was 116; 41 direct, was 39)
- New test mod `fabric-launcher-user` in `fabricLoaderApi`; `fabric-loader-user` now uses Fabric's own launcher `Knot`, which is still not provided

### Fabric: real clients, and another loader's code paths (2026-10-22)

- Link check: a class that uses NeoForge's or Forge's API, which nothing installed has, is that loader's code path in a multi-loader jar; it fails as it loads, under Fabric Loader too. Minecraft members it uses that this game lacks (methods NeoForge patches into Minecraft) are now noted (`POLY-COMPAT-113`) instead of blocking the mod (`112`). The same member in any other class still blocks. `collective` now loads on the dedicated server (it was blocked by its NeoForge network handler)
- The game class loader looks up resources as it loads classes: what its own jars and modules hold comes from them, not from a copy of the game jar on the JVM class path; Poly Mod Loader's own resources and the shared libraries' still come from the class path. The Minecraft Launcher puts the vanilla game jar on the class path; with Forge, FML must find Forge's patched game jar instead
- Real-mod corpus on the client (`corpusFabricClient`, a game window, both versions): the player joins the saved world, plays 200 ticks, saves and quits to the title screen, and the client stops (new test mod `fabric-client-session`). On 26.2, 140 foreign mods load and initialise; on 26.1, 128. CustomSkinLoader adds its common jar to the class path, picks its rules for the running version from `version.json` and transforms the skin classes; veinminer loads on 26.1
- Real-mod corpus on servers: on 26.2, dedicated server 100 foreign mods (was 99) and game test server 98 (was 97); on 26.1, 93 (was 88) and 91 (was 86). The directly installed Fabric mods still blocked are client-only mods on a server (`108`), the corpus's copies of one mod for several loaders (`117`) and, on 26.1, mods that need Minecraft 26.1.2 or a newer Sodium (`114`); Fabric Loader blocks all of them
- With CustomSkinLoader installed, the client leaves a "Client shutdown from post-main" crash report after it stops: the mod keeps non-daemon pool threads for a minute and Minecraft's shutdown watchdog reports after 15 seconds. The world is saved before that; it is the same under Fabric Loader

## 0.1.0-alpha.10 — 2026-10-22

### Highlights

- The installer window is tidier: names and fields line up, and the window keeps its size when you switch between client and server or open "Advanced".
- Poly Mods can now wait until Fabric mods are initialised (`LifecycleEvents.MODS_INITIALIZED`).

### 주요 변경

- 설치 프로그램 창을 정리했습니다. 이름과 입력 칸이 줄 맞춰 있고, 클라이언트·서버를 바꾸거나 "고급 설정"을 열어도 창 크기가 그대로입니다.
- Poly 모드가 Fabric 모드의 초기화가 끝날 때를 기다릴 수 있습니다(`LifecycleEvents.MODS_INITIALIZED`).

### Installer window (2026-10-22)

- One grid for every row, so names and fields line up whether "Advanced" is open or not; the window keeps its width when rows appear. Client or server is a two-part switch as wide as the fields; a folder field and its "Browse…" button are one control; the memory hint sits under the memory field; "Advanced" and Install sit on either side of a line at the bottom

### Native API: after every mod is initialised (2026-10-22, ADR-0022)

- New event `LifecycleEvents.MODS_INITIALIZED` (`ModsInitializedListener`, PREVIEW): once, on the main thread, after Minecraft's bootstrap when every mod has run its initialisers, including Fabric mods, which initialise where Fabric Loader runs them. It comes at the same point when no Fabric mod is installed. A Poly Mod that uses a Fabric mod waits for it here; its entrypoints still run before the game starts
- `dependencyCrossLoader`: `native_addon` sees the Fabric library "not ready" in its entrypoint and ready in this event. The `native-showcase-mod` example uses it

## 0.1.0-alpha.9 — 2026-10-22

### Highlights

- The installer can now set up a dedicated server. Choose "Server" and a folder; it downloads Mojang's server and writes `run.bat`, `run.sh` and `polymodloader-server.jar`. You still accept Minecraft's EULA yourself.

### 주요 변경

- 설치 프로그램으로 전용 서버도 설치할 수 있습니다. "서버"와 폴더를 고르면 Mojang 서버를 받고 `run.bat`, `run.sh`, `polymodloader-server.jar`를 만듭니다. Minecraft EULA 동의는 직접 합니다.

### Server install (2026-10-22)

- `ServerInstaller` (window: "Server"; command line: `install-server [--dir] [--minecraft] [--max-memory] [--min-memory]`): finds the version in Mojang's version manifest, downloads the version JSON and the server bundler checked by SHA-1, extracts the bundled server jar and libraries to `versions/` and `libraries/` checked by the bundler's SHA-256 (the bundler's own layout), and copies the Poly Mod Loader jars to `libraries/`
- `polymodloader-server.jar` holds only a manifest: `Main-Class` is the new `PolyServerMain` (`PolyLauncherMain` with the launch target defaulting to `dedicated_server`), and `Class-Path` lists every jar, so `java -jar polymodloader-server.jar nogui` works on any host. `ClassPathGameJarLocator` now follows manifest `Class-Path` entries, as the JVM does, to find the game jar
- `run.bat` and `run.sh` carry the memory setting; `mods/` is created. The installer never writes `eula.txt`: the first start stops at Minecraft's EULA check. Installing again keeps worlds, settings, mods and `eula.txt`
- New check `:poly-installer:serverInstallTest`: installs a server with the installer jar (network) and starts it through Poly Mod Loader up to the EULA check. Forge mods on an installed server are still left out (`POLY-COMPAT-206`), as on the client

## 0.1.0-alpha.8 — 2026-10-22

### Highlights

- A Forge mod now runs through Poly Mod Loader in our tests: Forge creates it, runs its setup event and registers its item, next to a Poly mod in the same game. An installed Poly Mod Loader does not load Forge mods yet.

### 주요 변경

- Forge 모드가 이제 Poly 모드 로더 위에서 실행됩니다(테스트 환경). Forge가 모드를 만들고 설정 이벤트를 실행하고 아이템을 등록하며, 같은 게임에서 Poly 모드도 함께 동작합니다. 설치한 Poly 모드 로더에서 Forge 모드가 로드되는 것은 아직입니다.

### Forge mods through FML (2026-10-22, ADR-0021 G4)

- FML sees exactly the Forge mods Poly Mod Loader found: their jars are resources of the game class loader, where FML's `ClasspathLocator` finds them. FML's own mods folder scan (`ModsFolderLocator`) is left out of the BOOT layer; it read every jar in `mods/` as a Forge mod and stopped at Fabric and Poly mods as "not a Forge mod"
- New test mod `forge-hello`, written as for Forge (`@Mod`, the mod event bus, `FMLCommonSetupEvent`, a `DeferredRegister` item) and compiled against the real 26.2 game classes and Forge's API. `forgeGameTest` now runs it with a Poly mod: FML constructs it in module `forge_hello`, its setup listener sees `forge_hello:hello_item` registered, and the Poly mod works in the same run
- Build: a module not named after a Minecraft version can compile against game classes with `polyCompileMinecraft=<version>` in its `gradle.properties` (`poly.minecraft-compile`)

## 0.1.0-alpha.7 — 2026-10-22

### Highlights

- The installer is simpler: the Minecraft version, the game's memory and Install. Everything else is under "Advanced".
- MinecraftForge itself now starts through Poly Mod Loader in our tests: Forge loads, creates a world and passes a game test. Forge mods do not load in an installed Poly Mod Loader yet.

### 주요 변경

- 설치 프로그램을 단순하게 바꿨습니다. Minecraft 버전, 게임 메모리, 설치 버튼만 보이고 나머지는 "고급 설정"에 있습니다.
- MinecraftForge 자체가 이제 Poly 모드 로더 위에서 시작됩니다(테스트 환경). Forge가 로드되고 월드를 만들고 게임 테스트를 통과합니다. 설치한 Poly 모드 로더에서 Forge 모드가 로드되는 것은 아직입니다.

### Installer (2026-10-22)

- The main view shows the Minecraft version, the memory (-Xmx) and Install; the launcher folder, the profile option, starting memory, closing when done, uninstall and the log are under "Advanced" (closed). Enter presses Install

### MinecraftForge on Poly Mod Loader's game class loader (2026-10-22, ADR-0021 G3b–G3d)

- `ForgeInstallation` prepares Forge's files on the player's PC: the installer (SHA-1 pinned per Minecraft version in `forge-releases.properties`), the libraries its `version.json` lists (each checked by SHA-1), and the game jar with Forge's binary patches, in `.poly/forge/<version>/`
- `ForgeBootModules` defines FML's BOOT layer in the game class loader. Libraries Poly Mod Loader's class path already has are shared; `cpw.mods.modlauncher` is ModLauncher's API plus Poly Mod Loader's own `Launcher`, `Environment` and launch (`forge-host.jar`, written from ModLauncher's public API, not copied)
- `PolyModLauncher` runs FML's services in ModLauncher's order and defines the SERVICE, PLUGIN and GAME layers in the game class loader. Launch plugins (access transformers, `RuntimeDistCleaner`, …) and transformation services become a class transformation stage. Forge's launch handler starts the game; `polymodloader_server_gametest` starts Mojang's game test server, which Forge only runs outside production
- Bridge API: `GameHost` and `ModLoaderBridge.gameHost` for a loader that starts the game itself, `GameModuleLayers` for module layers. The game class loader can define layers whose classes are not transformed, expose jars as resources only, and gives module packages their manifest's version attributes (FML reads its version from them)
- `PolyRuntime` asks for the host when Forge mods are found (or `-Dpoly.forge=always`): transformation is planned against the patched jar, FML finds and loads the Forge mods, and the game starts through Forge. New codes: `POLY-COMPAT-206` (Forge's files could not be prepared, or Poly Mod Loader's class path lacks Forge's file systems), `207` (Forge mods together with Fabric mods, not run yet), `208` (FML failed while starting)
- Java finds file systems on the class path only, so Forge's `securemodules`, `unsafe`, `JarJarFileSystems` and `roimfs` must be on Poly Mod Loader's class path, as Forge's own shim puts them. The installed launcher profile does not have them yet, so an installed Poly Mod Loader still leaves Forge mods out (`206`)
- On Forge's patched jar one built-in rule, `poly:server_brand`, no longer matches and is rejected; the other 70 apply. `-Dpoly.transform.disable=<rule IDs|*>` leaves built-in rules out, for diagnosing such conflicts
- New scenarios on 26.2: `forgeGameTest` (Forge's mod loading, a world, a GameTest, saving) and `forgeServerBoot` (up to the EULA check)

## 0.1.0-alpha.6 — 2026-10-22

### Highlights

- Groundwork for Forge mods: Poly Mod Loader can now give mods the Java module setup Forge is built on. Forge mods do not run yet, and nothing changes for players.

### 주요 변경

- Forge 모드를 위한 기반 작업: Forge가 전제로 하는 Java 모듈 구성을 Poly 모드 로더가 만들 수 있게 됐습니다. Forge 모드는 아직 실행되지 않고, 플레이어에게 바뀌는 점은 없습니다.

### Forge groundwork: modules in the game class loader (2026-10-22)

- ADR-0021 revised after reading Forge's own source jars: `fmlcore`, `javafmllanguage` and Forge itself use the JPMS game layer (services found on the layer, mod classes opened with `findModule`, module opens), so replacing only `fmlloader`'s state classes is not enough. The proposal now: the game class loader also defines the JPMS game layer for Minecraft, Forge, FML and Forge mods, and Poly Mod Loader implements ModLauncher's service contracts. Running ModLauncher itself stays rejected (it would own the game classes). G3 is split into G3a–G3d in `docs/plans/FORGE_SUPPORT.md`, with where each Forge jar goes
- G3a: `GameClassLoader.defineModuleLayer` defines a module layer whose modules belong to the game class loader. Their classes go through Poly Mod Loader's transformer and the bridges' stages like any other and land in their named module; `Class.forName(module, name)`, `ServiceLoader.load(layer, …)` and module resources work (`GameClassLoaderModulesTest`). Not used at run time yet; runs without modules are unchanged

## 0.1.0-alpha.5 — 2026-10-22

### Highlights

- The installer can set the game's memory: maximum (-Xmx) and starting (-Xms). It suggests a maximum that fits your PC.
- When the install is done, a notification says whether it worked, and the installer closes by itself. Click the notification to open the mods folder.

### 주요 변경

- 설치 프로그램에서 게임 메모리를 정할 수 있습니다. 최대(-Xmx)와 시작(-Xms) 메모리이며, PC에 맞는 최대 메모리를 미리 골라 둡니다.
- 설치가 끝나면 설치 여부를 알림으로 알려 주고, 설치 프로그램은 저절로 닫힙니다. 알림을 누르면 mods 폴더가 열립니다.

### Installer: memory, notification, closing when done (2026-10-22)

- New memory settings in the installer window ("Maximum memory (-Xmx)", "Starting memory (-Xms)") and on the command line (`--max-memory`, `--min-memory`; also `--xmx`, `--xms`). Sizes such as `4G`, `4096M` or `4096` (MiB). They are written to the launcher profile's `javaArgs`, which the launcher uses instead of its default `-Xmx2G`. Other JVM arguments already in the profile are kept; "Launcher default" leaves `javaArgs` alone. The window suggests 4G on a PC with about 8 GB and 6G with 16 GB or more. A maximum below 1G or a starting size above the maximum is refused; a maximum above the PC's memory gives a warning (`JavaMemory`)
- When an install or uninstall ends, a system notification says how it went (`DesktopNotification`, a short-lived tray icon). Clicking the notification after an install opens `mods/`. The installer then closes after 10 seconds, unless "Close the installer when done" is unticked or there is a warning to read. Without a notification area the installer shows its dialog as before
- `launcherProfileTest` installs with `--max-memory 3G --min-memory 1G`, checks the profile's `javaArgs`, and starts the game with them as the launcher does

## 0.1.0-alpha.4 — 2026-10-22

### Highlights

- Fabric mods that use Mixin now run, the way Fabric Loader runs them.
- Of 52 popular Fabric mods for 26.2, 42 load on the client and 28 on a dedicated server; most of the rest are client-only. All 44 Fabric API modules load.
- 137 Fabric mods together, Sodium, Iris and Lithium among them, load on the client and join a world. 99 get through a real dedicated server start.
- Fabric mods were also tested with popular mods for Minecraft 26.1: 88 start a real dedicated server, and the client joins a world.
- Fabric mods that use `getGameInstance` or class tweaker enum extension now load too. Poly Mod Loader now provides every part of Fabric Loader's public API.
- NeoForge and Forge mods still do not run. Work on Forge has started (plan and first building block).
- The download page shows what changed in every build.

### 주요 변경

- Mixin을 쓰는 Fabric 모드가 이제 실행됩니다. Fabric Loader와 같은 방식입니다.
- 26.2용 인기 Fabric 모드 52개 중 클라이언트에서 42개, 전용 서버에서 28개가 로드됩니다. 나머지는 대부분 클라이언트 전용입니다. Fabric API 모듈 44개도 모두 로드됩니다.
- Sodium, Iris, Lithium을 포함한 Fabric 모드 137개를 함께 넣고 클라이언트에서 월드에 들어갈 수 있습니다. 실제 전용 서버는 99개로 시작됩니다.
- Minecraft 26.1용 인기 모드로도 시험했습니다. 88개로 실제 전용 서버가 시작되고, 클라이언트는 월드에 들어갑니다.
- `getGameInstance`나 class tweaker의 enum 확장을 쓰는 Fabric 모드도 이제 로드됩니다. 이제 Fabric Loader 공개 API를 빠짐없이 제공합니다.
- NeoForge·Forge 모드는 아직 실행되지 않습니다. Forge 지원 작업을 시작했습니다(계획과 첫 구성 요소).
- 다운로드 페이지에서 빌드마다 바뀐 점을 볼 수 있습니다.

### Fabric mods on the client (2026-10-22)

- Fixed: the thread context class loader became the game class loader only after Fabric's `preLaunch`. Mod code that runs earlier and finds services with `ServiceLoader.load(Class)` failed; with Sodium and ImmediatelyFast, ImmediatelyFast's Mixin config plugin stopped the launch ("No implementation found for PlatformService"). It is now set right after the game class loader is created, before Mixin starts, as Fabric's Knot does
- New scenario `:poly-bootstrap:corpusFabricClient` (opens a window, not part of `compatibilityCorpus`): every Fabric mod of the 26.2 corpus plus `fabric-mixin` in the client, up to joining a saved world. 137 foreign mods load and initialise, and Iris, Sodium and the `fabric-mixin` client mixin run
- `fabric-mixin` accepts a missing `Mth.SIN` table: Lithium's `math.sine_lut` mixin removes it, under Fabric Loader too
- `FabricLoader.getGameInstance()` returns the game object, as in Fabric Loader: the client's `Minecraft` object, or the server whose `runServer()` started (new rule `poly:server_running`; integrated servers are not returned). The version adapters provide it (`GameVersionAdapter.gameInstance`), and bridges get it through `BridgeLaunchContext.gameInstance`. The Fabric bridge no longer blocks any public API member; `POLY-COMPAT-111` remains for Fabric Loader internal classes, and the `fabric-loader-user` test mod now uses one (`FabricLauncherBase`)
- Real-mod corpus for Minecraft 26.1 (`compat/mod-corpus-26.1.json`, 127 jars from Modrinth). Jars are now kept per version in `build/poly/mod-corpus/<version>/`. Only the reviewed 26.2 corpus has exact expected counts; other versions check a clean start, world save and server start. On 26.1: 88 foreign mods through the real dedicated server start, 86 on the game test server, and the client joins a world
- Class tweaker `extend-enum` lines are applied as in Fabric Loader: the enum gets a `public static final` stub constant after its existing ones, marked `org.spongepowered.asm.mixin.StubEnumConstant`, which Mixin's enum support lets the mod's mixin initialise (`EnumExtender`, adapted from FabricMC class-tweaker 0.3.0, Apache-2.0). `POLY-COMPAT-127` is retired. The `fabric-mixin` test mod adds `Difficulty.POLY_MIXIN_TEST`
- `launcherProfileTest` also installs the `fabric-mixin` test mod, so the installed launcher profile is checked to carry Mixin
- Forge groundwork (ADR-0021 G2, not used at run time yet): `ForgePatchSet` in `poly-bridge-forge` applies the binary patches of Forge's installer (`data/<side>.lzma`: LZMA zip of `.binpatch` files, GDIFF deltas, format read from `binarypatcher` 1.3.4) to the vanilla jar. For Forge 26.2-65.1.5's server patches the result is byte-identical to Forge's installer output for all 1394 classes (`ForgePatchSetTest` with `-PforgeInstaller`, `-PforgeVanillaServer`, `-PforgePatchedServer`). New library XZ for Java 1.9 (public domain)
- Plans: the rest of Fabric support in `docs/plans/FABRIC_FULL_SUPPORT.md`; Forge support in `docs/plans/FORGE_SUPPORT.md` with the proposed ADR-0021 (run Forge's own code on a Forge-patched game jar built on the player's PC; nothing implemented yet)

### Fabric mods run as under Fabric Loader: Mixin, interface injection, environment stripping (2026-10-22)

- Fabric mods that use Mixin now load and run (ADR-0020). Poly Mod Loader starts Mixin 0.8.7 (`net.fabricmc:sponge-mixin` 0.17.4, the build Fabric Loader 0.19.5 ships) and MixinExtras 0.5.5 on its game class loader through `PolyMixinService`, adapted from Fabric Loader's `MixinServiceKnot`. Configurations are added mod by mod with each mod's compatibility level, as in Fabric Loader. Mixin starts only when a loaded Fabric mod has a Mixin configuration
- The Fabric bridge applies Fabric Loader's own class transformations before Mixin, in Fabric Loader's order: `@Environment` stripping on mod and library classes, and class tweaker `inject-interface` on Minecraft classes. `extend-enum` stays blocked (`POLY-COMPAT-127`)
- New bridge SPI `ModLoaderBridge.classTransformer` (`BridgeClassTransformer`): a bridge's transformation step that the game class loader runs after Poly Mod Loader's own rules, and that also defines Mixin's generated classes
- Fabric entrypoints run where Fabric Loader runs them: `preLaunch` right after Mixin is ready, before the game; `main`, `client` and `server` after Minecraft's bootstrap (dedicated server and game test server: before `Util.startTimerHackThread()`; client: in the `Minecraft` constructor). The version adapters mark that point with the new transformation action `InvokeHookAtCall` (`GameVersionAdapter.bindModInitialization`). Poly Mods still initialise before the game. A Poly Mod that depends on a Fabric mod still gets load order and classes, but no longer the guarantee that the Fabric mod's initialisation finished first (ADR-0014 changed)
- When a foreign mod is loaded, the game class loader also owns Minecraft's library jars so Mixin can transform them (Fabric API changes DataFixerUpper's `TaggedChoice`), as Fabric's Knot does. Libraries Poly Mod Loader uses itself stay on the parent. Runs without foreign mods are unchanged (ADR-0003 changed)
- Built-in rules keep vanilla calls that Fabric API injects around: `poly:world_unloaded` and `poly:entity_joined_world` now call their hook next to the vanilla call (`InvokeHookAtCall`) instead of replacing it. The call redirects that must replace a call (`poly:block_place`, creative tab pages) run after the bridge step; if a Mixin `@Redirect` or `@WrapOperation` already took the call, Poly Mod Loader leaves its rule out of that class and warns (`POLY-TRANSFORM-011`)
- Same verdicts as Fabric Loader:
  - `::` entrypoint values (static and instance methods, constructors) and `preLaunch` are supported; `POLY-COMPAT-105` and `106` are retired. `POLY-COMPAT-101` (Mixin) is retired
  - `FabricLoader.getMappingResolver()` returns the `official` namespace and maps names to themselves, as Fabric Loader without mappings does
  - A Mixin configuration that is missing, unreadable or not an object is skipped with an error, as Mixin does (`POLY-COMPAT-136`, not blocking). Only a `required` configuration that needs a newer Mixin blocks the mod (`132`)
  - A class tweaker line whose target this game does not have is left out instead of blocking (`137`, not blocking). Targets outside the game jar still block (`128`)
- Link check: interfaces a mod injects and interfaces its mixins add count as supertypes. An injected interface no installed jar has blocks the mod (`135`). Classes of other-side-only mods (Fabric API client modules on a server), JDK incubator classes, and game members whose types no installed jar has are warnings (`113`)
- New codes: `POLY-COMPAT-131` (which Mixin configurations apply on this side), `132`, `133` (a bridge transformer failed on a class; for Mixin the error names the mixin and its mod), `134` (Mixin could not start, or two mods use the same configuration), `135`, `136`, `137`, `POLY-TRANSFORM-011`
- New libraries in the runtime and installer: Mixin, MixinExtras and ASM `asm-commons` (listed in `THIRD_PARTY_NOTICES.md`)
- New test mods: `fabric-mixin` (`@Inject`, `@Invoker`, `@Accessor`, `@ModifyArg`, MixinExtras `@WrapOperation`, a config plugin, a mixin stacked on a method Poly Mod Loader already transformed, plain, nested and generic interface injection), `fabric-mixin-broken`, `fabric-gametest-harness`. New scenarios `fabricMixin` (in `compatibilityMatrix`) and `corpusFabricServer` (the real `net.minecraft.server.Main` with every corpus Fabric mod, up to the EULA check)
- Real-mod corpus on Minecraft 26.2 (`compatibilityCorpus`): directly installed Fabric mods that load together went from 3 to 42 of 52 on the client and from 3 to 28 on the dedicated server (most of the rest are client-only, `108`); Fabric API submodules from 2 to 44 of 44 (37 on the server). 99 mods pass the real dedicated server start; 97 create a world, pass GameTest, tick and save on the game test server. NeoForge and Forge mods still do not load. Details in `docs/COMPATIBILITY.md`
- The client was verified later the same day (`corpusFabricClient`, above). `launcherProfileTest` was not run

## 0.1.0-alpha.3 — 2026-10-22 (first public release)

### Native API parity on Minecraft 26.1, 26.1.1 and 26.1.2

- The Native API expansion (event priorities and results, interaction events, cancellable block break and place, server, world, respawn and entity lifecycle events, the `poly:player_death` fix, identity-keyed entity data, tags, paged mod creative tabs, the client item tooltip event, `GameTestContext` helpers) now also runs on 26.1, 26.1.1 and 26.1.2. `poly-game-v26_1` uses the same code as 26.2; the 21 new rules in `Minecraft26_1Adapter` keep their 26.2 targets, descriptors and preconditions, checked with `javap -c` against the 26.1 jars; 26.1.1 and 26.1.2 have the same target classes on client and server. New `BuiltInRulesTest` for 26.1 (all 67 rules on both sides against the 26.1 classes). Verified for 26.1, 26.1.2 and 26.2: `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests` (62 game tests), `compatibilityMatrix` (11 scenarios). Client-only rules were checked without a window

### Minecraft 26.1, 26.1.1 and 26.1.2 (2026-10-21)

- Poly Mod Loader now also runs Minecraft 26.1, 26.1.1 and 26.1.2 (all unobfuscated, Java 25); 26.2 stays the default. New modules `poly-version-adapter-v26_1` and `poly-game-v26_1`, copied from 26.2 and fixed against the 26.1 jar. What differs from 26.2 is listed in each module's README: the HUD hook targets `Gui` (26.1 has no `Hud`), world painters hook `LevelRenderer.submitEntities` (no `submitFeatures`), mod shader pipelines declare uniforms one by one (no bind group layouts), block entity types are created reflectively (private constructor), and smaller API differences
- 26.1.1 and 26.1.2 run on the 26.1 modules: compared class by class, they change no transformation target and no game member the game layer uses. `gradle/minecraft-versions.properties` lists them as `hotfixes.26.1`; hotfixes have no modules of their own, can be picked with `-PpolyMinecraft`, and are offered by the installer
- Fixed: an adapter covering several versions (`GameVersionAdapter.gameVersions()`, ADR-0011) had all its built-in rules rejected on any version but the one they name (`POLY-TRANSFORM-006`). The runtime now plans the selected adapter's own rules for the running version (`AdapterTransformRules`); mod rules still need the exact version
- The test mods and `transform-demo-mod` are built for the version a build runs, so `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests` and `compatibilityMatrix` verify every supported version with the same expected lines. `compatibilityCorpus` still needs `compat/mod-corpus-<version>.json`, which exists for 26.2 only
- Verified for each of 26.1, 26.1.1 and 26.1.2 with `-PpolyMinecraft=<version>`: `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests` (52 game tests), `compatibilityMatrix` (11 scenarios). Client-only rules were checked without a window (transformation plan, preconditions, bytecode verification and hook signatures against the client jar); `clientWorldReloadTest` was not run
- Minecraft 26.3 is not supported yet. Its modules compile and pass `serverBootSmokeTest` and `compatibilityMatrix` (branch `wip-minecraft-26_3`), but `native-showcase-mod` ships 26.2-format worldgen JSON (`noise_settings`, `configured_feature`, block states) that 26.3 cannot read, so `worldCreateSaveTest` and `nativeApiGameTests` fail. Native mods that ship vanilla-format data are tied to its format version even with `"26.*"`

### Public Maven repository for the mod SDK (2026-10-21)

- Mod developers no longer need the Poly Mod Loader repository. The website hosts a Maven repository at `https://polyloader.dothome.co.kr/maven/` with the `dev.polymodloader.mod` plugin and everything a mod project resolves through it (ADR-0008, follow-up)
- `./gradlew publishSdk` publishes that set into `.poly/sdk-maven` (git-ignored, kept by `clean`, accumulates versions; another folder with `-PpolySdkRepo=<folder>`), including the included build's plugin, then checks it (`verifySdkRepository`):
  - 17 modules, found from the modules `dev.polymodloader.mod` uses (`poly-lifecycle-api`, `poly-bootstrap`, `poly-mod-metadata`, every version adapter) through the dependency graph. Not the installer, examples, test mods or tools
  - No source jars. JavaDoc jars only for `poly-lifecycle-api`, `poly-runtime-api`, `poly-system-api`, `poly-event-api` and `poly-game-api`
  - `dev.polymodloader:poly-gradle-plugin` and the `dev.polymodloader.mod` marker only, without sources
  - `dev.polymodloader:poly-mod-template:<version>@zip`, the mod template set to that version (`packageModTemplate`)
  - One version is about 2.8 MB. `publishToMavenLocal` is unchanged (all modules, with sources)
- JavaDoc is built with `--no-fonts`, which leaves out about 3.8 MB of web fonts per module
- New `initPolyMod -PmodId=<id> [-PmodName] [-PmodPackage]` in the `dev.polymodloader.mod` plugin renames an unzipped template in place, with the same renaming as `newPolyMod`
- Mod projects (template, `examples/crystal-garden`, `dev.polymodloader.mod`) look for Poly Mod Loader in the website repository first, then Maven Local. `-PpolyLocalSdk` puts Maven Local first, to try a local build of a version that is already published

### Local Minecraft files for new versions

- `MinecraftFiles/<version>/` (a copy of the launcher's `.minecraft/versions/<version>/`: `<version>.json`, `<version>.jar` or `client.jar`, optional `server.jar` bundler and Maven-layout `libraries/`) seeds the download cache instead of downloading. Another folder: `-PpolyMinecraftFiles=<path>`; mod projects use `<project>/MinecraftFiles` or the property
- Every local jar is checked against the SHA-1 in the version JSON before it is copied into the cache; a mismatch fails with the file, both hashes, and what to do. Missing files, other libraries, and assets are still downloaded. Local version JSONs with a different `id` or `inheritsFrom` (modded profiles) are rejected
- The installer now fails with a clear message for version JSONs it cannot run (pre-1.13 `minecraftArguments`, natives-only libraries, no `downloads.<side>`, non-bundler server jars) instead of a `NullPointerException`
- `newMinecraftVersion` prints which local files it found for `-Pto`. `MinecraftFiles/README.md` documents the layout and next steps; everything else in `MinecraftFiles/` is ignored by git. Versions before 26.1 are obfuscated and still need a mapping layer that does not exist

### Fabric class tweakers (2026-10-21)

- Fabric mods with a class tweaker (`accessWidener` in `fabric.mod.json`) are no longer blocked for that alone. The Fabric bridge reads the file with Fabric Loader 0.19.5's grammar (`accessWidener v1/v2`, `classTweaker v1/v2`, `transitive-*`) and turns its `accessible`/`extendable`/`mutable` lines into Poly Mod Loader access widenings, including the class access Fabric implies. The link check verifies every target against the game jar before the mod loads (ADR-0018)
- New codes:
  - `POLY-COMPAT-125`: class tweaker missing, unreadable or malformed, listing each bad line by number
  - `126`: namespace other than `official`
  - `127`: `inject-interface` / `extend-enum`, not supported yet
  - `128`: a target this game version lacks, blocking (link check)
  - `129`: what a class tweaker widens, informational
  - `130`: client targets left out on a dedicated server
- `POLY-COMPAT-102` (any class tweaker blocks) is retired
- Access widenings may now make a constructor `extendable` (protected; calls stay `invokespecial`). `mutable` leaves interface fields final. Transform engine revision 3
- Bridges can read other entries of the mod jar (`ModJarContents`), and `BridgeAssessment` carries access widenings
- New scenario `fabricClassTweaker` in `compatibilityMatrix` with test mods `fabric-class-tweaker` (widens and uses `Mth.SIN` and `SharedConstants.prefixDebugFlagName`) and `fabric-class-tweaker-stale` (blocked with `128`)
- Real-mod corpus:
  - All 80 class tweakers are read and match 26.2, with no format, namespace or target errors.
  - 42 still block on interface injection (`127`), and Mixin blocks 74 of the 80 mods.
  - Loadable foreign mods: 17 to 18 (`fabric-transitive-access-wideners-v1`).
  - The game test server applies 433 widenings and starts normally (`corpusFabric` 18 loaded / 156 not, `corpusAll` 19 / 247).

### Event priorities, decision results and interaction events

- `EventPriority` (`HIGHEST`, `HIGH`, `NORMAL`, `LOW`, `LOWEST`) and `EventRegistry.register(key, priority, listener)`. Listeners run by priority, then in registration order; the existing `register(key, listener)` uses `NORMAL`, so existing mods keep their behaviour
- `EventResult` (`PASS`, `ALLOW`, `DENY`): the standard result of decision events. The first listener that does not return `PASS` decides and later listeners are skipped. A decision listener returning `null` is disabled with the new code `POLY-EVENT-003`
- Server-side interaction events with results (`dev.polymodloader.api.event.interaction`): `InteractionEvents.USE_BLOCK`, `USE_ITEM`, `USE_ENTITY` (`InteractionResult`; `PASS` continues, anything else ends the click) and `ATTACK_ENTITY` (`EventResult.DENY` cancels). New `Hand` enum
- `BlockEvents.BREAK` (before, cancellable; fires before `BROKEN`), `BlockEvents.PLACE` (cancellable) and `PLACED` for players placing block items, including beds and doors
- New transformation rules (javap-verified against 26.2): `poly:use_block`, `poly:use_item`, `poly:use_entity`, `poly:attack_entity`, `poly:block_break`, `poly:block_place` (call redirect), `poly:block_placed`; hooks in the new `InteractionHooks`
- `GameTestContext.interactBlock`, `interactItem`, `interactEntity`, `attackEntity`, `breakBlock`: a mock server player acts like a client's input, events included
- native-showcase-mod: `warded_block` and 6 game tests proving that each event cancels its action and that the uncancelled action still works
- Roadmap of the remaining Fabric API / NeoForge gaps: `docs/API_PARITY_ROADMAP.md`

### Server, world, respawn and entity lifecycle events

- `ServerEvents.STARTING` (before worlds are created) and `STOPPED` (after worlds are saved and closed)
- `WorldEvents.LOADED`, `SAVING`, `UNLOADED` with the shared `WorldListener`
- `PlayerEvents.COPY` (old and new player object during a respawn, after entity data was copied) and `PlayerEvents.RESPAWNED`
- `EntityEvents.JOINED_WORLD` for every entity kind (spawned, loaded with its chunk, or arriving from another dimension)
- New transformation rules: `poly:world_loaded`, `poly:world_saving`, `poly:world_unloaded` (call redirect of `ServerLevel.close()` in `stopServer`), `poly:server_stopped`, `poly:player_respawned`, `poly:entity_joined_world` (call redirect, because `ServerLevel.EntityCallbacks` is package-private), `poly:player_death`; hooks in the new `LifecycleHooks`
- `GameTestContext.createConnectedMockPlayer`, `respawn`, `disconnect`: a player that joins over an in-memory connection and respawns through the client's respawn request
- Fixed: `EntityDataKey` data of a respawned player was lost, because entity data was kept in a map that compares entities by entity ID and the game hands the old ID to the new player object; it is now keyed by identity
- Fixed: `EntityEvents.DIED` never fired for server players (`ServerPlayer.die` does not call `LivingEntity.die`), and could not fire again for a respawned player for the same ID reason
- native-showcase-mod: `LifecycleContent` (respawn counter in entity data, lifecycle log lines checked by `nativeApiGameTests`) and 2 game tests

### Tags

- Tag API (`dev.polymodloader.api.tag`): `TagKey` (`block`, `item`, `entityType`), `TagRegistry`, `TagView` (`contains`, `entries`) from `WorldView.tags()`, and `WorldView.isBlockIn`, `EntityView.isIn`, `ItemStackRef.isIn`. A tag of the wrong kind is refused with `IllegalArgumentException`
- `ContentRegistrar.addToTag(tag, entries...)` writes `replace: false` tag files with optional entries to the generated data pack; entries that are not registered when the registries freeze are reported with the new code `POLY-CONTENT-007` and left out
- native-showcase-mod: `TagContent` (`#native_showcase:warded` now drives the warded block's protection, `#native_showcase:sparkly`, the warded block added to `#minecraft:mineable/pickaxe`) and 2 game tests

### Mod creative tabs

- `ContentRegistrar.creativeTab(path, CreativeTabDefinition)` declares a mod's own tab with an icon and entries (mod or game items), titled `itemGroup.<mod id>.<path>`. The game's tabs through the `CreativeTab` enum work as before
- Mod tabs go on extra pages of the creative screen (ten per page; the game's right-hand tabs stay on every page) with page buttons above the tabs. New rules: `poly:creative_tab_pages` (the game's position check does not know pages; Poly Mod Loader checks per page instead when mod tabs exist), client-only `poly:client_creative_tabs_clicked`/`_released`/`_hovered`/`_drawn` (call redirects of `CreativeModeTabs.tabs()`) and `poly:client_creative_page_buttons`
- Entries or icons that are not registered items when the registries freeze are reported with the new code `POLY-CONTENT-008` and left out
- New unit test `BuiltInRulesTest` (poly-game-v26_2) plans and applies every built-in rule to the real 26.2 classes for the client and the dedicated server and checks that every inserted call reaches a public static hook with the exact descriptor, and that hook classes resolve their private accessors. This covers client-only rules without opening a window
- The look of the paged creative screen (button placement, tab drawing) has not been checked by a person yet; no window-opening task was run
- native-showcase-mod: a "Native Showcase" tab

### Item tooltips

- `ClientEvents.ITEM_TOOLTIP` with `ItemTooltipListener` and `TooltipLines` (read, add, insert, remove plain-text lines; section-sign formatting codes work), fired on the client thread after the game's lines; not for tooltips the game hides. New client-only rule `poly:client_item_tooltip` (return hook of `ItemStack.getTooltipLines`), covered by `BuiltInRulesTest` and `ItemTooltipsTest`; the drawn tooltip was not looked at (no window-opening task was run)
- native-showcase-mod: items in `#native_showcase:sparkly` show "Sparkly" under their name

## 0.1.0-alpha.2 — 2026-10-21 (공개하지 않음, 내용은 0.1.0-alpha.3에 포함)

### 설치 프로그램, 첫 Poly 모드 튜토리얼, 라이선스, 배포 자동화 (2026-10-21)

- 플레이어용 설치 프로그램 `PolyModLoader-<버전>.jar`·`.exe`: Minecraft Launcher에 "Poly 모드 로더 26.2" 프로필을 추가하고 `mods/` 폴더를 만듭니다. 창과 명령줄(`install`, `uninstall`)을 모두 지원하고, `.exe`는 런처가 받아 둔 Java로 실행됩니다(ADR-0017)
- `poly.gameJar`가 없으면 클래스 경로에서 게임 jar를 찾습니다(런처 프로필 실행). 찾지 못하면 `POLY-BOOTSTRAP-006`
- `:poly-bootstrap:launcherProfileTest`: 설치 jar로 새 런처 폴더에 설치하고, 런처와 같은 방식으로 프로필을 실행해 월드 접속까지 확인
- "Poly 모드": Poly 모드 로더의 Native API로 만든 모드를 부르는 이름(Fabric 모드·Forge 모드처럼). 문서와 메시지를 이 이름으로 통일
- 튜토리얼 "첫 Poly 모드 만들기"를 새 예제 Crystal Garden으로 다시 씀: 자라는 블록(블록 상태·블록 엔티티), 엔티티 데이터·월드 저장소, 명령어·권한, 접속 확인, 블록 엔티티 그리기·HUD·키, 게임 테스트 4개
- 라이선스: PolyLoader Non-Commercial License 1.0(비상업적 사용·수정·배포 허용, 상업적 이용 제한, 자동 분석·AI 재구현을 파생물로 정의). 제3자 고지 `THIRD_PARTY_NOTICES.md`. 모든 jar의 `META-INF`에 둘 다 들어갑니다
- 쓰지 않던 빈 `vml-*` 폴더와 `templates/vml-mod-template` 삭제

### Fabric Loader 런타임 API (2026-10-21)

- Fabric 모드에 `FabricLoader.getInstance()`를 제공합니다: 모드 조회(Poly 모드 포함), `ModMetadata`(`custom` 값 포함), `ModContainer`·`ModOrigin`, 모든 키의 진입점, `ObjectShare`, 설정 폴더, 실행 환경·인수(ADR-0016)
- 언어 어댑터: `languageAdapters`를 선언한 모드가 어댑터를 제공하고, 그 어댑터를 쓰는 모드는 그 모드를 필요로 합니다(`fabric-language-adapter:<이름>`). 기본 어댑터는 클래스·정적 필드·정적 메서드를 지원합니다
- 브리지 jar는 Fabric Loader 본체 대신 공개 API와 그 닫힘만 담습니다(Apache-2.0 고지 포함). `getGameInstance`·`getMappingResolver`를 쓰는 모드는 링크 검사가 막습니다(`POLY-COMPAT-111`). 로더 런타임 패키지에서 실제로 없는 클래스도 이제 막습니다. 브리지 준비 실패는 `POLY-COMPAT-003`
- 새 시나리오 `fabricLoaderApi`와 테스트 모드 3개. 실제 모드 묶음에서 로드 가능한 외부 모드가 3개에서 17개로 늘었고(`placeholder-api`, `fabric-language-kotlin`과 Kotlin 라이브러리 13개 포함), 이 상태로 게임 테스트 서버가 정상 시작합니다

### Fabric 중첩 jar (2026-10-21)

- Fabric `jars`(jar-in-jar)를 풀어 각각 하나의 모드로 판정합니다. 여러 겹 중첩도 따라가고, `.poly/nested-jars/`에 내용 해시로 저장합니다. 바깥 모드가 로드될 때만 안쪽도 로드되고(`POLY-COMPAT-121`), 같은 모드의 사본은 하나만 씁니다(Native > 직접 설치 > 최고 버전, `122`). 출처 표시는 `120`, 없거나 읽을 수 없는 중첩 jar는 `123`, 일부만 로드되는 바깥 모드 경고는 `124`입니다(ADR-0015)
- 중첩 jar 자체로 차단하던 `POLY-COMPAT-103`은 폐기
- 새 시나리오 `nestedJars`와 테스트 모드 4개. 실제 모드 묶음에서 중첩 모드 142개를 새로 판정(예외·크래시 0건). 로드 가능한 직접 설치 모드는 `fabric-api` 하나뿐이며, 하위 모듈은 44개 중 1개만 로드됩니다(`124`)

### 로더를 넘는 의존성, 시작 전 링크 검사, 실제 모드 호환성 테스트 (2026-10-21)

- 서로 의존하는 모드: Poly 모드가 Fabric 모드에, Fabric 모드가 Native·Fabric 모드에 의존할 수 있습니다(`provides` 별칭 포함). 엔트리포인트는 로더를 가리지 않고 의존 대상이 먼저 돕니다(ADR-0014)
- Fabric 의존성은 Fabric Loader의 버전 해석기로 판정합니다. `minecraft`·`java`·`fabricloader` 조건도 이제 확인하므로, 다른 Minecraft 버전용 Fabric 모드는 로드되지 않습니다
- 차단 연쇄: 필요한 모드가 없음·차단됨·버전 불일치(`POLY-COMPAT-114`), `breaks`(`115`), `conflicts` 경고(`116`), 중복 ID(`117`). 각 모드가 모든 이유를 받습니다. Poly 모드가 차단된 외부 모드에 의존하면 `POLY-DEPENDENCY-009`. 다른 모드 의존 자체를 차단하던 `POLY-COMPAT-104`는 폐기
- 시작 전 링크 검사(`ModLinkVerifier`): 로드하려는 외부 모드가 이 게임 버전에 없는 Minecraft 멤버(`112`), 제공하지 않는 Fabric Loader 런타임(`111`), 없는 JDK 클래스·읽을 수 없는 클래스 파일(`118`), 없는 엔트리포인트(`119`)를 쓰면 게임 도중이 아니라 시작 전에 제외합니다
- 크래시 대신 진단: 깨진 jar(`POLY-DISCOVERY-003`), NeoForge 라이브러리 jar(`POLY-COMPAT-205`), 형식이 틀린 `fabric.mod.json`·`mods.toml` 값, 예외를 던지는 브리지(`POLY-COMPAT-002`)를 그 파일만 제외하고 계속 판정합니다
- 실제 모드 호환성 테스트 `compatibilityCorpus`: Modrinth에서 26.2용 인기 모드 124개(Fabric·NeoForge·Forge 각 상위 40개와 필수 의존 모드)를 SHA-512로 고정한 목록(`poly-bootstrap/compat/mod-corpus-26.2.json`)으로 받습니다. 클라이언트·서버에서 모드 하나씩과 전부 함께 판정하고, 게임 테스트 서버에서도 실행합니다. 결과: 판정 예외·크래시 0건, 로드 가능 0개(Mixin·FML 필요). COMPATIBILITY.md
- 새 시나리오 `dependencyCrossLoader`, `dependencyBlockedChains`와 테스트 모드 10개(test-mods/README.md)
- 고친 문제: `placeholder-api`처럼 Fabric Loader 런타임을 쓰는 라이브러리가 로드되어 게임 도중에 실패할 수 있던 문제. NeoForge 라이브러리가 "모드 아님"으로 보고되던 문제. 문서의 `POLY-COMPAT-110` 설명 오류(엔트리포인트 예외가 맞음)
- `build-logic`: Java가 OS 인증서 저장소의 루트만 신뢰하는 PC(HTTPS 검사 소프트웨어 등)에서도 `modCorpus`가 받도록, Windows에서는 Windows 신뢰 저장소도 함께 신뢰(`SystemTrustedTls`)

### 모드 제작 도구 (2026-10-21)

- `newPolyMod`(Poly 모드 로더 저장소 루트): `-PmodId=ruby_mod [-PmodName=… -PmodPackage=… -PmodDir=…]`로 템플릿에서 새 모드 프로젝트를 만들고 모드 ID·이름·패키지·엔트리포인트 클래스·프로젝트 이름을 모두 바꿈
- `dev.polymodloader.mod` 플러그인의 `polyResources { item("ruby"); block("ruby_block") { minedWith = "pickaxe" } }`: 아이템 정의·모델·블록 상태·전리품·영어 이름·도구 태그를 빌드 때 생성(`generatePolyResources`). 직접 쓴 파일이 이기고, `lang/en_us.json`과 도구 태그는 합침
- `checkPolyResources`(`check`에 포함): 게임이 건너뛰는 경로, JSON 문법, 없는 모델·텍스처, 빠뜨린 네임스페이스, 잘못된 번역 값을 게임을 켜기 전에 모두 보고. 새 오류 코드 `POLY-RESOURCE-004`~`009`
- 모드 템플릿: 아이템 JSON 3개를 `polyResources`로 바꾸고 블록 하나와 블록 게임 테스트를 추가

### 힙 고정 틱 측정, 클라이언트 프레임 벤치마크 (2026-10-21)

- `tickBenchmark -PtickBenchmarkHeap=2g`: 두 변형에 같은 고정 힙을 주고 GC 직후 힙도 비교(측정 3). 작업 부하를 돌 대신 유리로 채워 조명 계산 경쟁을 없앰
- `:poly-bootstrap:frameBenchmark`: 바닐라 대 Poly 모드 로더 클라이언트 프레임 시간(`Minecraft.runTick`) p50/p95/p99 비교
- `frameBenchmark`: 게임 창을 닫은 뒤 다음 창을 열기 전에 기다림(기본 20초, `-PframeBenchmarkCooldown`), 실행마다 결과를 `runs.jsonl`에 바로 기록. 개발 PC(NVIDIA 노트북 GPU)가 창을 연달아 다시 여는 순간 디스플레이 드라이버 오류(버그체크 0xD1)로 재부팅된 뒤 추가
- 첫 클라이언트 프레임 시간 측정(측정 4): 개발용 모드를 함께 넣은 Poly 모드 로더가 바닐라보다 프레임 p50이 0.14ms 김(측정된 저하), p95·p99는 편차 안. PERFORMANCE.md

### 서버 틱·힙·GC 측정 (2026-10-21)

- `:poly-bootstrap:tickBenchmark`: 바닐라와 Poly 모드 로더를 같은 부하(바닐라에서도 도는 데이터 팩)로 번갈아 실행해 서버 틱 시간 p50/p95/p99·50ms 초과 비율, 피크 힙, GC 횟수·정지 시간을 비교. 결과는 PERFORMANCE.md
- `tools/tick-probe`: 틱 메서드를 호출마다 재는 측정용 Java 에이전트(JDK Class-File API, 양쪽에 똑같이 붙임)
- 시작 시간 벤치마크와 실행 명령을 함께 쓰도록 정리(`BenchmarkLaunch`)

### 변환 엔진: 순서 계약, call-site 변경, 접근 확대, 캐시 상한 (2026-10-21)

- 같은 메서드를 고치는 규칙은 `after`가 하나의 순서를 정하면 함께 적용(시작 훅·반환 훅은 선언 순서대로 실행). 상수 교체와 같은 호출을 바꾸는 규칙 둘은 여전히 함께 쓸 수 없음 (ADR-0013)
- 새 동작 `RedirectCall`: 메서드 안의 특정 호출(모두 또는 n번째)을 정적 훅으로 바꿈
- 접근 확대 `AccessWidening`(Fabric access widener와 같은 뜻: `accessible`, `extendable`, `mutable`), 대상이 없으면 `POLY-TRANSFORM-010`
- `poly.mod.json`: `transforms`에 `invoke_hook_at_head`·`invoke_hook_before_return`·`redirect_call`과 `after`, 새 필드 `accessWidenings`(권한 `internal.callsite.intercept`, `internal.member.access`), 잘못된 접근 확대는 `POLY-METADATA-016`
- 변환 캐시 크기 상한(기본 256 MiB, `-Dpoly.transformCacheMaxMegabytes`), 가장 오래 쓰지 않은 항목부터 정리
- `transform-demo-mod`: 내장 규칙 뒤에 순서를 선언한 call-site 변경과 필드 접근 확대 예제

### 셰이더 텍스처·모드 유니폼, 상태별 블록 모델 (2026-10-21)

- 모드 셰이더 파이프라인에 텍스처(`ShaderPipelineDefinition.Builder.texture`, `Sampler0`~`3`)와 텍스처 좌표(`VertexSink.vertex(x, y, z, u, v, argb)`)
- 모드 유니폼(`Builder.uniform`, `ShaderUniform`): std140 블록 `ModUniforms`, 매 프레임 렌더 스레드에서 값을 올림, 잘못된 값은 `POLY-CLIENT-007`. 새 변환 규칙 `poly:client_shader_uniforms`(`RenderPass.setPipeline`)
- 블록 엔티티 그리기의 상태별 블록 모델(`BlockEntityCanvas.block(BlockStateView, …)`, `defaultState`), 상태 사본 `BlockStateView.with`
- 고친 문제: 깨끗한 체크아웃(`build/` 없음)에서 버전 모듈 컴파일이 실행 사양 파일을 찾지 못해 실패하던 문제

### 이름 변경: Poly 모드 로더 (2026-10-21)

- 프로젝트 이름을 Void Mod Loader(VML)에서 Poly 모드 로더로 바꿈. 패키지 `dev.polymodloader`, 모듈 `poly-*`, 메타데이터 `poly.mod.json`, 오류 코드 `POLY-*`(번호와 의미는 그대로), 모드 ID `poly`, 보고서 폴더 `.poly/`. 전체 대응표는 ADR-0012

### 문서 사이트와 API 레퍼런스 (2026-10-21)

- `site/`를 Docusaurus 3 문서 사이트로 바꿈: 시작하기, Native API, 테스트, 레퍼런스, 기여 섹션, Minecraft 버전별 문서 드롭다운, 오프라인 검색(한국어 포함), 버전 값 한 곳(`site/src/versionInfo.js`)
- API 레퍼런스: `./gradlew apiReference`가 공개 API의 Javadoc에서 패키지별 페이지를 만듦(모든 형식과 멤버의 시그니처·설명·인자·반환값·예외)

### 여러 Minecraft 버전 지원 구조 (2026-10-21)

- 지원 버전 목록 `gradle/minecraft-versions.properties`(버전, 기본 버전, 버전별 Java). 버전 카탈로그의 `minecraft` 항목은 없앰
- 버전마다 어댑터·게임 계층 모듈을 목록에서 자동으로 포함하고, 버전별 jar와 Java로 컴파일
- `-PpolyMinecraft=<버전>`으로 실행·검증 버전 선택. 모드 프로젝트는 `gradle.properties`의 `polyMinecraft`
- `./gradlew newMinecraftVersion -Pfrom=<버전> -Pto=<새 버전>`: 가장 가까운 버전의 모듈을 복사하고 이름을 바꿈. `./gradlew minecraftVersions`: 지원 버전 목록
- `poly.mod.json`의 `"minecraft"`가 와일드카드(`26.*`)와 범위(`>=26.2 <27`)를 받음. Native 예제와 템플릿은 `26.*`
- 버전 어댑터가 여러 핫픽스 버전을 맡을 수 있음(`GameVersionAdapter.gameVersions()`)

### Native API 4단계 (2026-10-20)

- 묶음 A: 엔티티 데이터 옵션(추적자 동기화, 버전·마이그레이션), 모든 종류 파일의 리로드 리스너(`onDataFiles`/`onAssetFiles`), `PlayerEvents.LOGIN_CHECK`, 월드 백업 개수 제한
- 묶음 E: 접속 준비 단계의 교환(`configurationExchange`: 서버 요청, 클라이언트 답, 서버 검사와 거절, 대기 시간)
- 묶음 D: 월드에 그리기(`worldPainter`: 선·상자·점·글자·셰이더 사각형), 블록 엔티티 그리기의 블록 모델(`BlockEntityCanvas.block`)과 셰이더 사각형, 모드 코어 셰이더 파이프라인(`shaderPipeline`)
- 묶음 C: 코드 배치 기능(`feature`, `FeaturePlacement`), 노이즈 지형에 끼우는 코드 density function(`densityFunction`), 코드 구조물(`structure`, `StructureDefinition`)과 생성 팩의 configured/placed feature·structure·structure_set
- 고친 문제: `addFeature`로 바꾼 바이옴을 쓰는 노이즈 차원에서 기능 배치 중 게임이 멈추던 문제(생성기의 기능 색인을 다시 만듦)
- 묶음 B: 블록 모양·충돌 모양(`BlockShape`, 상태별·방향별), 나는 몹과 헤엄치는 몹(`flying`, `swimming`: 길찾기·이동 제어·배회 목표), 몹 모델 키프레임 애니메이션(`KeyframeAnimation`, `ALWAYS`/`WHILE_MOVING`/`ON_DEMAND`, `MobView.playAnimation`)
- 고친 문제: `custom_goal_seeks_glow_block` 게임 테스트가 약 10번에 1번 실패하던 문제(목표 블록이 탐색 반경 끝에 있었음)

### Native API 3단계 (2026-10-19)

- 묶음 A(콘텐츠 심화): 이름 블록 속성(`choice`), 상태별 빛, 칸 배치 메뉴와 진행 막대, 아이템 데이터 컴포넌트와 실제 스택 참조(`ItemStackRef`), 투사체, 자연 스폰
- 묶음 B(게임플레이): 몹별 AI 목표(`AiGoal`, `MobView`), 속도·방향·중력·탑승 API와 조종 가능한 탈것 몹, 피해량 수정(`MODIFY_DAMAGE`), 발전 과제 트리거와 부여·취소
- 묶음 C(월드): 코드 바이옴(생성 팩에 기록), 코드 지형 생성기(`TerrainGenerator`), 청크 강제 로드, 청크 로드·언로드 이벤트, `biomeAt`. 생성 팩 버전을 내용 해시로 정함
- 묶음 D(클라이언트 그리기): 몹 상자 모델과 부품 애니메이션, 블록 엔티티 그리기(아이템·글자), 모드 파티클 종류, 시야각 이벤트·시점 전환, 모드 후처리 셰이더, 동적 텍스처와 `HudCanvas.drawTexture`
- 묶음 E(리소스·네트워크·운영): 데이터·에셋 리로드 리스너, 엔티티 데이터(저장·리스폰 유지·본인 동기화), 저장소 버전·마이그레이션·클라이언트 동기화, 블록 엔티티 데이터 버전, 권한 노드·공급자, 클라이언트 접속·퇴장 이벤트와 `leaveWorld`, 모드가 바뀐 월드의 자동 백업, 게임 테스트 공간 `TestArea`
- 고친 문제: 모드 블록 상태가 게임의 상태 번호 표에 들어가지 않아 클라이언트로 보낼 수 없던 문제(1단계부터 있던 결함)

### Native API 2단계 (2026-10-19)

- 블록 상태 속성, 블록 엔티티(데이터·틱·동기화), 상자형 인벤토리, 월드 저장소, 작업 예약, `WorldEvents.READY`
- 몹 타입(AI 목표·속성·생성 알·렌더러), 상태 효과, 게임 규칙, 사운드, 파티클, 피해 취소·사망 이벤트
- 모드 팩 `KnownPack` 표시: 데이터 레지스트리(월드 생성·차원·피해 유형)를 안정적으로 사용. 바이옴 기능 추가, 차원 이동
- 모드 화면(Screen)과 위젯, `ModContext.configDirectory()`
- 변환 엔진: 원시형 반환 메서드의 `RETURN_IF_NON_NULL`

### Native API 1단계 (2026-10-19)

- Native API(`poly-game-api`, 모두 `PREVIEW`)
  - 블록·아이템·음식·창작 탭
  - 월드·엔티티·플레이어 뷰
  - 플레이어·블록·클라이언트 이벤트
  - 명령어, 네트워크 페이로드 채널, 키 바인딩, HUD 레이어
  - 게임 내 테스트
- 모드 jar의 `assets/`·`data/`를 필수 리소스·데이터 팩으로 자동 추가
- 게임 계층 `poly-game-v26_2`: Minecraft 26.2 jar에 컴파일되고 게임 클래스로더가 로드(ADR-0010)
- 변환 동작 추가
  - `InvokeHookAtHead`(인수 전달, 취소·조기 반환)
  - `InvokeHookBeforeReturn`(인수·반환값 전달, 매개변수 재할당 시 거부)
- Gradle
  - `poly.minecraft-compile` 플러그인 추가
  - 모드 프로젝트용 `gameTests` 작업 추가
  - 모드 플러그인에 FabricMC 저장소를 추가해, 템플릿에서 `polyRuntime`을 해석하지 못하던 문제 수정
- 예제 `native-showcase-mod`, 템플릿에 아이템·게임 테스트 예시

## 0.1.0-alpha.1 — 2026-10-19 (개발자 미리보기)

Minecraft 26.2 / Java 25 대상 첫 미리보기입니다. 모든 공개 API는 `PREVIEW`입니다.

- 부트스트랩, 변환 클래스로더, 로딩 파이프라인(검색 → 메타데이터 → 의존성 → 시스템 충돌 → 변환 계획 → 시스템 등록 → 엔트리포인트 → 게임 → 종료)
- `poly.mod.json` 스키마 1: 엔트리포인트, 의존성, 충돌, 권한(`capabilities`), 시스템 교체, 변환 선언
- 시스템 교체 `poly:game_brand`, 서버 생명주기 이벤트
- 변환 엔진: 사전 계획, descriptor·바이트코드 사전조건, 충돌 거부, 진입 후크, 해시 기반 캐시
- 로더 브리지: Fabric 엔트리포인트 실행(Experimental), Fabric·NeoForge·Forge 기능별 진단
- 개발 도구: `dev.polymodloader.mod` Gradle 플러그인, 모드 템플릿, 실제 게임 검증 작업, 혼합 로더 행렬, 시작 시간 벤치마크

알려진 제한은 `docs/sprints/PHASE_0*.md`의 "남은 제한"과 `docs/COMPATIBILITY.md`에 있습니다.
