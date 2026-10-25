# ADR-0025: 다른 로더의 모드를 호스트 로더의 모드 목록에 보이기

- 상태: 채택 (2026-10-23)
- 관련: ADR-0009(로더 브리지), ADR-0016(Fabric Loader API), ADR-0021(Forge를 Forge 자신의 코드로 실행, G8 Fabric 모드와 함께)
- 계획: [`docs/plans/FORGE_SUPPORT.md`](../plans/FORGE_SUPPORT.md) G8 후속

## 배경

- **플레이어 보고(스크린샷으로 확인).** Physics Mod(Forge, `physics-mod-3.2.4-mc-26.2-forge.jar`)와 Sodium(Fabric, `sodium-fabric-0.9.2+mc26.2.jar`)을 함께 넣으면 블록 파편과 래그돌이 잘못 그려지거나 보이지 않습니다. Physics Mod만 넣으면 정상입니다.
- **원인(`javap`로 확인).** Physics Mod의 `net.diebuddies.bridge.ModLoaderFunctions.isModLoaded(id)`는 `ModList.getModFileById(id) != null`이고, `isModVersionOrNewer`는 그 파일의 `IModFileInfo.versionString()`을 Maven 버전으로 비교합니다. `StarterClient`는 `isModLoaded("sodium"/"rubidium"/"embeddium")`로 Sodium 경로를 고릅니다. Physics Mod는 mixin 설정 플러그인이나 `LoadingModList`를 쓰지 않습니다(클래스 상수 검색). FML의 모드 목록은 Sodium을 모르므로 Physics Mod는 바닐라 렌더링 경로를 탑니다. 그러나 Sodium이 그 경로를 바꿨고, Physics Mod 자신의 `sodium.*` mixin은 여전히 적용됩니다.
- **반대쪽도 같았습니다.** Fabric 쪽 `FabricLoader.isModLoaded`는 Poly Mod와 Fabric 모드만 보았습니다. 런타임이 Forge 모드 jar를 FML에 넘기고 브리지 목록(`BridgeLaunchContext.mods`)에서 뺐기 때문입니다. 수정 전 `forgeFabricGameTest` 로그: `[fabric_api_user] Forge mod forge_hello is loaded: false (no container)`, `[forge_hello] ModList.isLoaded("fabric_lib") = false`, `getModFileById("fabric_lib") = null`, Poly Mod `minimal_native_mod`도 `false`.
- **FML의 `ModList`(`fmlcore` 26.2-65.1.5와 26.1-62.0.9, `javap -p -c`로 비교해 같음).**
  - 모든 메서드가 `static`입니다(`ModList.get()` 없음).
  - `<clinit>`이 `LoadingModList.getModFiles()`에서 `MOD_FILES`(`ModFileInfo::getFile` → `ModFile::getModFileInfo`)를, `LoadingModList.getMods()`에서 `SORTED_LIST`를 만들고, 각 파일의 `getMods()`로 `FILE_BY_ID = Map.copyOf(…)`(`private static final`)를 만듭니다.
  - `getModFileById(String)`는 `return (IModFileInfo) FILE_BY_ID.get(id);`입니다.
  - `isLoaded(String)`는 `return indexedMods.containsKey(id);`이고, `indexedMods`는 `ModContainer`의 지도로 FML의 `ModLoader`가 `setLoadedMods`로 채웁니다.
  - `getModContainerById`도 `indexedMods`를 봅니다. `getModFiles`·`getMods`·`size`·`forEachModFile`·`applyForEachModFile`은 `MOD_FILES`·`SORTED_LIST`·`mods`를 봅니다.
- **`fmlcore`의 자리.** 매니페스트가 `FMLModType: LIBRARY`라서 FML이 PLUGIN 레이어에 둡니다. Poly 모드 로더는 ModLauncher처럼 PLUGIN 레이어 클래스를 변환하지 않고 정의하므로, 클래스 변환 단계(`BridgeClassTransformer`)는 `ModList`를 보지 못합니다(처음 구현을 `forgeFabricGameTest`로 확인).
- **forgespi 8.0.0**(두 Forge 버전이 같음): `IModFileInfo`(`getMods`, `versionString`, `getFile`, `showAsResourcePack`, …), `IModInfo`(`getModId`, `getDisplayName`, `getVersion`은 Maven `ArtifactVersion`, …), `IModFile`(`getFilePath`, `getFileName`, `findResource`, `getSecureJar`, `getScanResult`, `getProvider`, …). `ModContainer`(fmlcore)는 추상 클래스이고 로딩 단계(`modLoadingStage`), 모드 이벤트 버스, 설정(`addConfig`), 확장점(`registerExtensionPoint`)을 가집니다.

## 결정

1. **호스트의 모드를 다른 로더에(Fabric 쪽).**
   - `GameHost.mods()`(내부 SPI, 기본값 빈 목록)가 호스트가 실행하는 모드를 `BridgeLaunchContext.LoadedMod`로 돌려줍니다. 새 구성 요소 `runByHost`가 `true`입니다(기존 8개 인자 생성자는 `false`).
   - Forge는 각 jar의 `mods.toml` 항목(이름은 `displayName`, 없으면 모드 ID로 FML과 같게)과 `META-INF/jarjar/`의 중첩 모드를 줍니다.
   - 런타임은 이 모드를 `BridgeLaunchContext.mods` **맨 앞**에 둡니다. 그래서 Fabric 쪽에서 호스트 모드의 ID를 다른 모드의 `provides`가 차지하지 못합니다. 진입점 순서는 바뀌지 않습니다(진입점은 이 목록으로 부르지 않음).
   - Fabric 브리지는 이 모드의 컨테이너를 만들되, 루트 경로는 비우고(`getRootPaths()`가 빈 목록), 출처(`getOrigin`)는 그 jar로 둡니다. 형식은 `minecraftforge`입니다. Fabric API의 리소스 로더는 `builtin`이 아닌 모든 컨테이너의 루트 경로로 팩을 만들기 때문입니다(`ModPackResourcesUtil.getModResourcePacks`, `javap`로 확인). 루트를 주면 Forge가 이미 싣는 리소스가 두 번 실립니다.
2. **다른 모드를 호스트에(Forge 쪽).**
   - `GameHost.otherMods(List<LoadedMod>)`(기본값 무시)를 런타임이 `prepare` 직전에 한 번 부릅니다. 넘기는 것은 Poly 모드 로더가 실행하는 모든 모드(Poly Mod와 다른 로더의 모드)입니다.
   - Forge 브리지는 이것을 `ForeignModIndex`로 색인합니다. 자기 ID와 `provides` ID로 찾고, 같은 ID면 먼저 온 모드의 자기 ID가 이깁니다. Fabric 모드의 표시 이름은 `fabric.mod.json`의 `name`입니다.
   - `forge-host.jar`(BOOT 레이어 `cpw.mods.modlauncher` 모듈, 내보낸 패키지)의 `ForeignModList`가 그 모드마다 forgespi 보기를 둡니다. `IModFileInfo`는 모드 하나, 파일 버전 = 모드 버전, 언어 로더 없음, 리소스·데이터 팩 아님입니다. `IModInfo`는 ID, 이름, `DefaultArtifactVersion(버전)`, 이름 공간 = ID입니다. `IModFile`은 jar 경로·이름과 jar 안 `findResource`입니다.
   - FML만 가진 것(`getSecureJar`, `getScanResult`, `getProvider`, `setSecurityStatus`)은 지어낸 값 대신 이유를 담은 `UnsupportedOperationException`을 던집니다.
   - 이를 위해 `cpw.mods.modlauncher` 모듈은 `net.minecraftforge.forgespi`와 `maven.artifact`가 BOOT 모듈이면 그것을 `requires`합니다.
3. **`ModList`의 두 메서드만, 검사한 형태일 때만 바꿉니다(`ForgeModListPatch`).**
   - `getModFileById`는 FML의 답이 `null`일 때만, `isLoaded`는 FML의 답이 `false`일 때만 `ForeignModList`의 같은 이름 정적 메서드에 묻습니다. FML의 답이 먼저이므로 Forge 모드는 자기 ID를 지킵니다.
   - 메서드 본문이 위의 `javap` 형태(명령어 5개 / 4개)와 정확히 같을 때만 바꿉니다. 다르면 그대로 두고 `POLY-COMPAT-211`(새 코드)으로 알리며, 실행은 계속합니다. 그 기능만 꺼지는 것이고 실패를 성공으로 기록하지 않습니다.
   - 적용 위치는 클래스 변환이 아니라 레이어 모듈의 읽기입니다. `PolyModLauncher`가 SERVICE·PLUGIN·GAME 레이어를 정의하기 전에, `net.minecraftforge.fml` 패키지를 가진 모듈의 `ModList.class`를 패치해서 읽게 합니다(`ForgeLaunch.Request.layerModules`). BOOT 레이어 night-config 패치와 같은 방식이고, 읽기 코드도 함께 씁니다(`PatchedClassReference`).
   - 다른 모드가 없으면(Forge만) 아무것도 바꾸지 않습니다.
4. **모든 로더의 모드를 Poly Mod에(Poly 쪽, 2026-10-23 추가).**
   - 공개 API `ModContext.mods()`(`LoadedMods`, PREVIEW)가 `BridgeLaunchContext.mods`와 같은 목록, 곧 호스트가 실행하는 모드와 Poly 모드 로더가 실행하는 모드(Poly Mod, Fabric 모드)를 돌려줍니다. 런타임의 `RuntimeLoadedMods`가 구현합니다.
   - 로더 이름은 소문자 문자열(`poly`, `fabric`, `minecraftforge`)입니다. 열거형이 아니라서 로더가 늘어도 API를 바꾸지 않습니다.
   - 같은 ID는 Forge 쪽과 같은 규칙입니다. 모드 자신의 ID가 다른 모드의 `provides` ID보다 먼저입니다.
   - 근거: `dependencyCrossLoader`(Poly + Fabric)와 `forgeFabricGameTest`(Forge + Fabric + Poly)에서 `native_lib`이 `fabric_lib`과 `forge_hello`를 봅니다(`RuntimeLoadedModsTest`).
5. **바꾸지 않는 것(일부러).**
   - `getModFiles`, `getMods`, `size`, `forEach…`/`apply…`, `getAllScanData`: 이 목록은 FML과 Forge가 모드를 다루는 데 씁니다(클래스 상수 검색, 26.2-65.1.5). Forge의 `ResourcePackLoader`는 `getModFiles`·`applyForEachModFile`로 모드 리소스 팩을, FML의 `ModLoader`는 `getModFiles`로 모드 로딩을, `CapabilityManager`는 `getAllScanData`로 FML의 스캔 결과를 씁니다. 다른 로더의 모드가 Forge 리소스로 두 번 실리거나, FML이 하지 않은 스캔을 요구받게 됩니다.
   - `getModContainerById`/`getModObjectById`: `ModContainer`는 FML의 생명주기 객체입니다. 다른 로더의 모드는 FML의 로딩 단계·모드 버스·설정·확장점에 들어가지 않으므로, 컨테이너를 주면 상태(`getCurrentState`)를 거짓으로 알리고, 받은 설정·확장점 등록은 아무도 처리하지 않습니다. 비어 있는 `Optional`이 사실입니다.
   - `LoadingModList.getModFileById`: 구체 클래스 `ModFileInfo`를 돌려주므로 Poly 모드 로더가 만들 수 없습니다. 이것을 쓰는 Forge 모드(대개 mixin 설정 플러그인)는 여전히 다른 로더의 모드를 보지 못합니다.
   - Fabric 쪽에 `forge` 모드 자신은 넣지 않습니다(`mods.toml`로 설치한 모드만). Forge가 실행 중이라는 것만으로 Fabric 모드가 Forge용 경로를 고를 근거는 없습니다.

## 고르지 않은 것

- **`LoadingModList`에 가짜 `ModFileInfo`를 더하기:** FML은 이 목록으로 언어 로더를 고르고 모드를 만들고 이벤트를 보내고 리소스 팩을 만듭니다. Fabric 모드를 FML이 만들려 하게 됩니다.
- **리플렉션으로 `ModList`의 정적 필드 바꾸기:** `FILE_BY_ID`는 `static final`이고 불변 지도(`Map.copyOf`)입니다. Java 25에서 `static final` 필드는 리플렉션으로 쓸 수 없습니다. `indexedMods`는 FML의 `ModLoader`가 나중에 다시 씁니다.
- **가짜 `ModContainer`:** 위 4번과 같은 이유입니다.
- **PLUGIN 레이어 전체를 변환:** ModLauncher는 PLUGIN 레이어를 변환하지 않습니다. 모든 FML 라이브러리 클래스에 Poly 모드 로더와 다른 로더의 변환이 걸려 동작이 달라집니다.

## 결과와 위험

- Forge 모드의 존재 질의(`ModList.getModFileById`, `ModList.isLoaded`)가 Fabric 모드와 Poly Mod를 봅니다. Fabric 모드의 `FabricLoader.isModLoaded`·`getModContainer`가 Forge 모드를 봅니다.
- **선택적 연동이 새로 켜질 수 있습니다.** 다른 로더의 모드가 있다고 보고 그 모드의 클래스를 부르는 코드가 이제 실행됩니다. Physics Mod의 Sodium 경로처럼 원하는 결과도 있지만, 그 연동이 다른 로더용 API(예: Forge 전용 클래스)를 기대하면 `NoClassDefFoundError` 같은 새 실패가 생길 수 있습니다. Sinytra Connector와 같은 성질의 위험입니다. 실제 묶음(`corpusFabricForge`)과 플레이어 보고로 봅니다.
- 같은 ID는 Forge가 이깁니다. 같은 모드의 Fabric 빌드는 이미 빠지고(`139`), Forge 모드 ID와 같은 `provides`는 Forge 쪽 색인에서 빠지며, FML의 답을 먼저 봅니다.
- 내부 SPI가 늘었습니다: `GameHost.mods()`, `GameHost.otherMods(…)`, `LoadedMod.runByHost`. 다른 호스트 로더(NeoForge 등)도 같은 계약으로 붙일 수 있습니다.

## 근거(실험, 2026-10-23)

- 단위 테스트:
  - `ForeignModIndexTest` 4개: 자기 ID와 `provides`, `fabric.mod.json` 이름, Forge ID 우선, 자기 ID가 다른 모드의 `provides`보다 우선, `mods.toml`과 중첩 jar에서 읽은 호스트 모드.
  - `ForgeModListPatchTest` 4개: FML 답 우선과 대체 답, 형태가 다른 메서드는 그대로(`unchanged`), 다른 클래스는 그대로, 모듈 읽기에서 `ModList.class`만 패치.
  - `ForeignModListTest` 2개: forgespi 보기의 ID·이름·버전·jar·`findResource`·FML 전용 메서드의 예외, 별칭 질의.
  - `FabricLoaderRuntimeTest.modsTheHostRunsAreVisibleWithoutRootPaths`: Fabric 쪽 컨테이너, 루트 경로 없음, 중첩 모드의 출처.
- `forgeFabricGameTest`(26.2): 수정 전 실패, 수정 후 통과. `[forge_hello] ModList.isLoaded("fabric_lib") = true`, `getModFileById("fabric_lib") = Fabric Library 1.2.0 (mod fabric_lib)`, `getModFileById("fabric_lib_alias") = mod fabric_lib`, `minimal_native_mod: isLoaded = true; getModContainerById("fabric_lib") present = false`, `[fabric_api_user] Forge mod forge_hello is loaded: true (minecraftforge 1.0.0, 0 root path(s))`.
- `forgeGameTest`(26.2, Forge + Poly Mod): `minimal_native_mod`이 보이고, 없는 Fabric 모드는 `false`/`null`입니다.
- `corpusFabricForge`(26.2): `FML's ModList [getModFileById, isLoaded] also answers for 90 Poly Mod(s) and other loaders' mod(s)`, 월드 생성·GameTest·저장 통과, 수치(`Foreign mods: 90 loaded, 75 not loaded`) 그대로. 이전 실행과 비교해 새 경고는 하나입니다. FallingTree(Forge)가 VeinMiner(Fabric)를 보고 `VeinMiner is present, this may lead to incompatibilities`라고 알립니다. 다른 로더의 모드를 보는 것이 의도대로 동작한 예입니다.
- `check`, `compatibilityMatrix`, `corpusForge`(26.2) 통과. 26.1: `compatibilityMatrix`, `forgeWorldTest`, `corpusForge` 통과.
- 26.1 `corpusFabricForge`는 이 변경 전 커밋(`451a7df`)에서도 같은 곳에서 멈춥니다. NoChatReports Forge 빌드의 생성자가 Fabric `@Environment(CLIENT)`가 붙은 `NCRClient`를 조건 없이 불러, 서버에서 Fabric 환경 제거가 거부합니다(`133`). 모드 목록과 관계없습니다. 이 시나리오의 기대 줄은 Forge 65.1.5(26.2)를 적고 있어 26.1용이 아닙니다.
- `forgeFabricGameTest`와 `forgeGameTest`는 26.2에만 있습니다(Forge 테스트 모드가 26.2 릴리스로 빌드됨). 26.1의 `ModList`는 `javap`로 26.2와 같음을 확인했고, 26.1 `corpusFabricForge`가 멈추기 전에 `FML's ModList [getModFileById, isLoaded] also answers for 82 Poly Mod(s) and other loaders' mod(s)`를 기록해 Forge 26.1-62.0.9에서도 패치가 적용됨을 확인했습니다. 26.1에서 Forge 모드가 그 답을 읽는 것은 확인하지 않았습니다.
- Physics Mod + Sodium의 화면은 창이 뜨는 `physicsProbeSodiumClient`로 확인해야 합니다(헤드리스로는 확인하지 않음).

## NeoForge (2026-10-24, ADR-0026 19번)

- NeoForge의 FML 11도 같은 방식입니다. `ModList`는 인스턴스 메서드(`fileById`·`indexedMods` 인스턴스 필드)라서 `ForgeModListPatch`의 `Shape.NEOFORGE`로 검사하고, `neoforge-host.jar`의 `ForeignModList`가 neoforgespi 보기를 줍니다. 형태가 다르면 그대로 두고 `POLY-COMPAT-218`(Forge의 `211`에 해당)로 알립니다.
- 근거: `ForgeModListPatchTest`의 NeoForge 테스트 2개(실제 FML 11.0.16 `ModList`가 바뀌고 검증기를 통과함, 정적 메서드 모양은 바꾸지 않음), `neoForgeGameTest`(26.2): `[neoforge_hello] Poly Mod native_lib: isLoaded = true; getModFileById = Native Library 2.1.0 (mod native_lib); getModContainerById present = false`, 없는 모드는 `false`/`null`.
- NeoForge 모드는 Fabric·Quilt 모드와 함께 실행되지 않으므로(ADR-0026 22번), NeoForge 쪽에서 답하는 다른 로더의 모드는 지금 Poly 모드뿐입니다.
