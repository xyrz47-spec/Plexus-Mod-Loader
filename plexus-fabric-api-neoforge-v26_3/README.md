# plexus-fabric-api-neoforge-v26_3 — NeoForge 26.3용 Fabric API 호스트 모듈

Plexus Mod Loader의 Mixin 브리지로도 NeoForge 옆에서 원래대로 실행할 수 없는 Fabric API 모듈을 위해, `NeoForge 26.3.0.98`의 훅 위에서 동작하는 자체 Fabric API 구현체("호스트 모듈", `ADR-0031`)를 빌드합니다. 26.2판은 [`plexus-fabric-api-neoforge-v26_2`](../plexus-fabric-api-neoforge-v26_2/README.md)입니다.

## 이 모듈이 하는 일
- Fabric API `0.165.0+26.3`의 같은 모듈과 공개 API가 같은 네 모듈을 빌드합니다: `fabric-block-api-v1`(`3.1.0+plexus.neoforge.26.3`), `fabric-item-api-v1`(`14.8.0+plexus.neoforge.26.3`), `fabric-registry-sync-v0`(`8.0.4+plexus.neoforge.26.3`), `fabric-object-builder-api-v1`(`24.1.12+plexus.neoforge.26.3`, 원본이 로드돼도 대체하는 `always`).
- Sinytra Forgified Fabric API에는 26.3 브랜치가 없습니다. 그래서 소스는 26.2 호스트 모듈(FFAPI `26.2.x` 커밋 `40f8ce0` + Plexus Mod Loader 변경)에 Fabric API의 `0.161.0+26.2`→`0.165.0+26.3` 변경을 3방향 병합으로 더한 것입니다(같은 palantir 형식으로 맞춘 뒤 병합). 각 파일의 헤더가 출처와 변경을 적습니다. 26.3에 새로 생긴 파일은 Fabric API `0.165.0+26.3`에서 가져왔습니다.
- 26.2판과 다른 점:
  - registry-sync: NeoForge 26.3은 `DataPackRegistryEvent.NewRegistry` 대신 `NewDatapackRegistryEvent`(world·reloadable 레지스트리를 따로 받음)를 씁니다. `DynamicRegistriesImpl`이 26.3 공개 API(`getAllDynamicRegistries`, `getWorldRegistries`, `getReloadableRegistries`, `registerReloadable`)를 이 이벤트와 게임의 레지스트리 목록(NeoForge 패치가 모든 로더의 레지스트리로 채움) 위에서 구현합니다. 새 `NewDatapackRegistryEventAccessor`가 이벤트를 만들고 처리합니다. `FabricRegistryInit`의 `onInitialize`는 내장 레지스트리에 속성(`SYNCED`, 새 `SAVE_DATA_VALIDATED`)만 줍니다. 동기화는 NeoForge가 합니다. 26.3의 저장 데이터 검사(`MinecraftServerMixin`, 클라이언트 `WorldOpenFlowsMixin` 등)는 그대로 둡니다. 게임의 레지스트리 목록을 Fabric 사본으로 바꾸는 `ReloadableServerRegistriesMixin`·`RegistriesDatapackGeneratorMixin`은 NeoForge 옆에서는 게임 목록이 이미 모든 레지스트리를 담으므로 넣지 않습니다.
  - item: 툴팁 순서 API가 `TooltipProvider.Getter`를 받습니다. NeoForge 26.3의 `TooltipAppender.createComponentAppender(type, getter)`로 등록합니다. 새 블록 변환 API(`BlockTransformerEvents`, `BlockTransformerHelper`)와 `FishingHookMixin`은 원본 그대로입니다(NeoForge 26.3 패치에 맞음). `FirstPersonHandsAndItemsMixin`(26.2의 `ItemInHandRendererMixin`)은 FFAPI처럼 넣지 않습니다. `ResourceUtil`은 NeoForge 모드의 데이터 팩도 모드 출처로 봅니다(FFAPI의 조건).
  - object-builder: Fabric API 26.3은 `PoiTypes.register` 접근 확대를 `fabric-transitive-access-wideners-v1`로 옮겼습니다. 이 빌드는 그 모듈에 대해 컴파일하지 않으므로 자기 class tweaker에 그 줄을 남깁니다.
- 라이선스는 `Apache License 2.0`이며, jar는 `LICENSE_fabric-api`를 담습니다.

## 무엇을 소유하는가
- 소스 세트 `src/fabricBlockApiV1`, `src/fabricItemApiV1`, `src/fabricRegistrySyncV0`, `src/fabricObjectBuilderApiV1`과 각 모듈의 `fabric.mod.json`, Mixin 설정, class tweaker.
- `fabric.mod.json`의 `custom` 필드 `plexusmodloader:host-module` (`host` `neoforge`, `minecraft` `26.3`, `replaces` 각 모듈 버전).
- 빌드 산출물 jar(`META-INF/plexusmodloader/host-modules/neoforge-26.3.0.98/`)와 이 모듈의 색인. `plexus-bridge-fabric`이 26.2판의 색인과 합쳐 jar에 하나로 둡니다(`hostModuleIndex` 작업).
- `plexus-bridge-fabric`에 산출물을 전달하는 소모성 구성 `hostModuleElements`.

## 무엇을 하지 않는가
- NeoForge가 없거나 NeoForge 26.3.0.98이 아닌 릴리스에서는 아무것도 하지 않습니다.
- MinecraftForge를 위한 것이 아닙니다.
- Forgified Fabric API를 다운로드하거나 요구하지 않습니다.
- 26.3에서 NeoForge 옆에 아직 빠지는 `fabric-entity-events-v1`, `fabric-resource-conditions-api-v1`, `fabric-content-registries-v0`, `fabric-data-generation-api-v1`, `fabric-menu-api-v1`은 다루지 않습니다.

## 의존성
- Minecraft `26.3` (`plexusCompileMinecraft=26.3`).
- `installNeoForgeClient` 작업이 NeoForge `26.3.0.98` 자체 설치 프로그램(SHA-1 `c3d8feb159c1abeb89af6e10e1be7b6e080d0eb9` 검사)으로 만든 패치된 클라이언트 jar(`~/.gradle/caches/plexus-neoforge/26.3.0.98/client`, 첫 사용 시 네트워크 필요).
- `compileOnly`: NeoForge `26.3.0.98` universal jar, FML 12 loader, NeoForge 이벤트 버스, mergetool API, Fabric Loader, Mixin, MixinExtras, `fabric-api-base`, `fabric-resource-loader-v1`, jspecify, JetBrains annotations. object-builder는 registry-sync 소스 세트에 대해 컴파일합니다.

## 주요 진입점
- 각 모듈의 공개 API(`net.fabricmc.fabric.api.block.v1`, `net.fabricmc.fabric.api.item.v1`, `net.fabricmc.fabric.api.event.registry`, `net.fabricmc.fabric.api.object.builder.v1`).
- NeoForge 연결: `RegistryManagerMixin`, `NewDatapackRegistryEventAccessor`, `GameDataMixin`, `CommonHooksAccessor`, `ItemTooltipHandlerMixin`, `IItemExtensionMixin`, `IBlockExtensionMixin`, `IBlockStateExtensionMixin`.
- 진입점: `FabricRegistryInit`(`main`), `ClientItemEventHooks`(`client`).

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-fabric-api-neoforge-v26_3:assemble
.\gradlew.bat -PplexusMinecraft=26.3 :plexus-bootstrap:fabricApiGameTest :plexus-bootstrap:neoForgeFabricApiGameTest
.\gradlew.bat -PplexusMinecraft=26.3 :plexus-bootstrap:corpusFabricNeoForge
```
- 26.3의 `fabricApiGameTest`(Fabric 단독)와 `neoForgeFabricApiGameTest`(NeoForge 옆)는 테스트 모드 `fabric-block-item`, `fabric-dynamic-registry`, `fabric-object-builder`가 같은 11줄을 내는지 봅니다. 26.3에서는 `fabric-api-events`를 쓰지 않습니다(NeoForge 옆에서 그 모드가 필요한 resource-conditions·events-interaction 쪽이 아직 빠짐).

## 대표 흐름
`plexus-fabric-api-neoforge-v26_2`와 같습니다. 호스트 키가 `neoforge-26.3.0.98`입니다.

## 알려진 제한
- `NeoForge 26.3.0.98` 전용입니다. 새 NeoForge 릴리스가 나오면 다시 빌드하고 검증해야 합니다.
- FFAPI의 26.3판이 없어, 26.3에서 바뀐 부분은 Plexus Mod Loader가 직접 맞췄습니다. 창(클라이언트)에서 툴팁·저장 데이터 검사 화면은 확인하지 않았습니다.
