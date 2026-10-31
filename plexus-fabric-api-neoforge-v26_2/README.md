# plexus-fabric-api-neoforge-v26_2 — NeoForge 26.2용 Fabric API 호스트 모듈

Plexus Mod Loader의 Mixin 브리지로도 NeoForge 옆에서 원래대로 실행할 수 없는 Fabric API 모듈을 위해, `NeoForge 26.2.0.93`의 훅 위에서 동작하는 자체 Fabric API 구현체("호스트 모듈", `ADR-0031`)를 빌드합니다.

## 이 모듈이 하는 일
- `NeoForge 26.2.0.93`의 훅 위에서 Plexus Mod Loader 자체의 Fabric API 모듈("호스트 모듈", `ADR-0031`)을 빌드합니다.
- Plexus Mod Loader의 Mixin 브리지로도 NeoForge 옆에서 원래대로 실행할 수 없는 모듈을 대체합니다.
- 현재는 Fabric API `0.161.0+26.2`의 같은 모듈과 공개 API가 같은 네 모듈을 포함합니다: `fabric-block-api-v1`(`3.1.0+plexus.neoforge.26.2`), `fabric-item-api-v1`(`14.5.0+plexus.neoforge.26.2`), `fabric-registry-sync-v0`(`7.1.1+plexus.neoforge.26.2`), `fabric-object-builder-api-v1`(`24.1.1+plexus.neoforge.26.2`).
- 소스는 Sinytra Forgified Fabric API(https://github.com/Sinytra/ForgifiedFabricAPI, 브랜치 `26.2.x`, 커밋 `40f8ce0`)의 같은 이름 모듈입니다. 이는 Fabric API(`FabricMC`, https://github.com/FabricMC/fabric) 모듈에 NeoForge 전용 변경을 적용한 것입니다.
- 블록 모듈 변경 사항은 `Block`/`BlockState` 대신 NeoForge의 `IBlockExtension` 및 `IBlockStateExtension` 대상 Mixin, Fabric과 NeoForge 마법 부여 위력 간의 `EnchantmentPowerBridge`, NeoForge `FluidInteractionRegistry.canInteract` 대상 `LiquidBlockMixin`, NeoForge `EventHooks.onEnchantmentLevelSet` 대상 `EnchantmentMenuMixin`입니다.
- 아이템 모듈의 Forgified Fabric API 자체 변경 사항은 NeoForge의 `IItemExtension` 및 `IItemStackExtension` 대상 Mixin(`IItemExtensionMixin`, 클라이언트 `IItemExtensionClientMixin`), NeoForge `ItemTooltipHandler`를 통한 툴팁 처리, NeoForge 훅 기반 모루 및 마법 부여 검사(`AnvilMenuMixin`, `EnchantCommandMixin`, `EnchantRandomlyFunctionMixin`)입니다.
- 아이템 모듈에 대한 Plexus Mod Loader의 변경 사항(서식 정리 제외):
  - Forgified Fabric API의 `FabricItemImpl`(NeoForge `@Mod` 클래스)을 제거하고, 새 Mixin `ItemTooltipHandlerMixin`으로 NeoForge `ItemTooltipHandler.init` 내부의 `ModLoader.postEvent` 호출을 감싸 이벤트 게시 후 `ItemComponentTooltipProviderRegistryImpl`을 등록합니다.
  - `ClientItemEventHooks`는 `NeoForge.EVENT_BUS`에 리스너를 등록하는 Fabric `ClientModInitializer`(클라이언트 진입점)입니다.
  - `DataComponentMapBuilderMixin`은 NeoForge의 `IDataComponentMapBuilderExtensions`와 `DataComponentGetter`가 모두 기본 메서드를 제공하므로 `getOrDefault`를 직접 선언합니다.
  - `IItemExtensionMixin.isPrimaryItemFor`는 명시적인 `EnchantmentEvents.ALLOW_ENCHANTING` 응답을 먼저 반영합니다.
- 각 파일의 헤더에 소스 파일과 커밋, Plexus Mod Loader의 변경 내용이 명시되어 있습니다.
- 라이선스는 `Apache License 2.0`이며, jar는 `LICENSE_fabric-api`를 담습니다(두 프로젝트의 텍스트가 같으며 `NOTICE` 파일은 없음).

- registry-sync 모듈: FFAPI의 같은 모듈에서 `FabricRegistryInit`의 NeoForge 모드 버스 리스너를 빼고, 새 `RegistryManagerMixin`이 NeoForge `RegistryManager.postNewRegistryEvent` 안에서 `DataPackRegistryEvent.NewRegistry.process()`와 `ModifyRegistriesEvent` 게시를 감쌉니다.
- object-builder 모듈: FFAPI의 `FabricObjectBuilderImpl`(NeoForge `@Mod` 클래스, `FMLCommonSetupEvent`·`EntityAttributeModificationEvent` 리스너)을 빼고, 새 `GameDataMixin`이 NeoForge 등록(`GameData.postRegisterEvents`)과 Fabric 모드 초기화가 모두 끝난 뒤의 `GameData.freezeData`에서 속성 수정(`FabricDefaultAttributeRegistry.MODIFY`)과 추가된 유효 블록(`FabricBlockEntityType.addValidBlock`)을 적용합니다. 서버에서는 Fabric 모드 초기화가 NeoForge 등록보다 먼저이고, 클라이언트에서는 나중입니다. `FabricDefaultAttributeRegistryImpl`은 `DefaultAttributes.getSupplier`가 먼저 읽는 NeoForge 맵(`CommonHooks.FORGE_ATTRIBUTES`, 새 `CommonHooksAccessor`)의 값을 고칩니다. 원본의 표지판 편집 화면 클라이언트 mixin 2개는 FFAPI처럼 넣지 않습니다(NeoForge 패치가 `Identifier.withDefaultNamespace` 대신 `Identifier.parse`를 써서 모드 나무 종류를 이미 처리함).
- 원본 object-builder는 NeoForge 옆에서 `224` 없이 로드되지만 속성 수정이 게임에 닿지 않습니다(테스트 모드 `fabric-object-builder`: 좀비 최대 체력 20.0, Fabric 단독은 42.0). 그래서 색인에 `always`로 표시해 원본이 로드되더라도 대체합니다.

## 무엇을 소유하는가
- 소스 세트 구조: `src/fabricBlockApiV1`, `src/fabricItemApiV1`, `src/fabricRegistrySyncV0`, `src/fabricObjectBuilderApiV1`. object-builder는 registry-sync 소스 세트의 출력에 대해 컴파일합니다(`HostModule.dependsOn`).
- Fabric 모드 jar: 각 모듈의 `fabric.mod.json`, Mixin 설정(`fabric-block-api-v1.mixins.json`, `fabric-item-api-v1.mixins.json`, `fabric-item-api-v1.client.mixins.json`), 클래스 트위커(`fabric-block-api-v1.classtweaker`, `fabric-item-api-v1.classtweaker`).
- `fabric.mod.json`의 `custom` 필드 `plexusmodloader:host-module` (`host` `neoforge`, `minecraft` `26.2`, `replaces` 각 모듈 버전).
- NeoForge 훅에 맞춘 구현 소스 코드: `fabric-block-api-v1` 및 `fabric-item-api-v1` 구현 코드(`net.fabricmc.fabric.api.block.v1.*`, `net.fabricmc.fabric.api.item.v1.*`, `net.fabricmc.fabric.api.client.item.v1.*`, `net.fabricmc.fabric.impl.*`, `net.fabricmc.fabric.mixin.*`).
- Gradle 빌드 작업: `installNeoForgeClient`, `<sourceSet>CompileGame`, `<sourceSet>Jar`, `hostModuleIndex`, `hostModuleResources`.
- 빌드 산출물 jar(`META-INF/plexusmodloader/host-modules/neoforge-26.2.0.93/` 아래의 네 jar)와 색인 파일 `META-INF/plexusmodloader/host-modules.properties`(키 `neoforge-26.2.0.93.<모드 ID>`, 원본이 로드돼도 대체하는 모듈은 `neoforge-26.2.0.93.<모드 ID>.always=true`).
- `plexus-bridge-fabric`에 산출물을 전달하는 소모성 구성 `hostModuleElements`.

## 무엇을 하지 않는가
- NeoForge가 없거나 다른 NeoForge 릴리스에서는 아무것도 하지 않습니다.
- `MinecraftForge`를 위한 것이 아닙니다.
- Forgified Fabric API를 다운로드하거나 요구하지 않습니다.
- 브리지로 이미 실행되는 모듈은 다시 구현하지 않습니다.
- Plexus Mod Loader의 클래스 경로에 jar를 직접 올리지 않습니다.

## 의존성
- Minecraft `26.2` (`plexus.minecraft-compile`, 모듈의 `gradle.properties`에서 `plexusCompileMinecraft=26.2`로 고정).
- NeoForge의 패치된 클라이언트 jar: `installNeoForgeClient` 작업(`NeoForgeClientInstallTask`)이 `NeoForge 26.2.0.93` 자체 설치 프로그램(SHA-1 `97205829a020fc9389f504a77f590b54157c8dde` 검사)을 `--installClient`로 `~/.gradle/caches/plexus-neoforge/26.2.0.93/client`에 실행(첫 사용 시 네트워크 필요)하여 생성되는 `libraries/net/neoforged/minecraft-client-patched/26.2.0.93/minecraft-client-patched-26.2.0.93.jar`를 기반으로 합니다.
- `ClassTweakedJarTask`(`<sourceSet>CompileGame`) 작업이 컴파일 시점에만 모듈의 클래스 트위커(`accessible`, `extendable`, `mutable`, `inject-interface`)를 이 jar에 적용합니다.
- 기타 `compileOnly` 입력: NeoForge universal jar, Fabric Loader, Mixin, MixinExtras, `fabric-api-base` `2.0.4+ece063239e`, `fabric-resource-loader-v1` `2.0.13+9edec1269e`, `jspecify`, JetBrains annotations, NeoForge의 이벤트 버스, FML loader 및 mergetool API.

## 주요 진입점
- `fabric-block-api-v1` 공개 API 진입점: `net.fabricmc.fabric.api.block.v1` 패키지의 `BlockFunctionalityTags`, `FabricBlock`, `FabricBlockState`, `FluidFlowEvents`.
- `fabric-item-api-v1` 공개 API 진입점: `net.fabricmc.fabric.api.item.v1` 패키지의 `FabricItem`, `FabricItemStack`, `EnchantmentEvents`, `DefaultItemComponentEvents`, `CustomDamageHandler`, `ItemComponentTooltipProviderRegistry`, 그리고 `net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback`.
- 클라이언트 진입점: `ClientItemEventHooks`(`ClientModInitializer`).
- 내부 훅 브리지: `EnchantmentPowerBridge`.
- registry-sync·object-builder 공개 API 진입점: `net.fabricmc.fabric.api.event.registry`(`DynamicRegistries`, `FabricRegistryBuilder` 등), `net.fabricmc.fabric.api.object.builder.v1`(`FabricDefaultAttributeRegistry`, `FabricEntityDataRegistry`, `FabricBlockEntityType` 등).
- NeoForge 연결 mixin: `RegistryManagerMixin`, `GameDataMixin`, `CommonHooksAccessor`.
- 패키징 및 색인 진입점: `hostModuleResources` 작업, 색인 파일 `META-INF/plexusmodloader/host-modules.properties`.

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-fabric-api-neoforge-v26_2:jar
.\gradlew.bat :plexus-bootstrap:neoForgeFabricApiGameTest
.\gradlew.bat :plexus-bootstrap:corpusFabricNeoForge
```
- `.\gradlew.bat :plexus-fabric-api-neoforge-v26_2:jar`: 호스트 모듈 jar를 빌드합니다.
- `.\gradlew.bat :plexus-bootstrap:neoForgeFabricApiGameTest`: `fabric_api_events` 테스트 모드가 블록 API 출력(`FabricBlockState.getAppearance(minecraft:stone) = minecraft:stone`, `FabricBlockState.getProvidedEnchantmentPower(minecraft:bookshelf) = 1.0`) 및 아이템 API 출력(`enchanting table offers minecraft:sharpness for minecraft:diamond_sword: false`, `enchanting table offers minecraft:sharpness for minecraft:iron_sword: true`, `default max stack size of minecraft:stick: 16`)을 출력하는지 확인합니다. 테스트 모드 `fabric-dynamic-registry`(동적 레지스트리 두 줄)와 `fabric-object-builder`(좀비 최대 체력 42.0, 화로 블록 엔티티가 훈연기를 받음, 엔티티 데이터 직렬화기 등록)의 줄도 확인합니다. 이는 Fabric 단독 실행인 `fabricApiGameTest` 및 `forgeFabricApiGameTest`와 동일한 출력입니다.
- `.\gradlew.bat :plexus-bootstrap:corpusFabricNeoForge`: 실제 모드 묶음의 Fabric 모드를 NeoForge 옆에서 실행하여 검증합니다.
- `plexus-runtime-impl`의 단위 테스트 `HostModulesTest`로 대체 로직을 검증합니다.

## 대표 흐름
1. `hostModuleResources` 작업이 jar를 `META-INF/plexusmodloader/host-modules/neoforge-26.2.0.93/`에 배치하고 `META-INF/plexusmodloader/host-modules.properties` 색인을 작성합니다. 소모성 구성 `hostModuleElements`가 이를 `plexus-bridge-fabric`에 전달하여 해당 jar에만 포함합니다.
2. 런타임(`plexus-runtime-impl`)에서 `NeoForge 26.2.0.93`이 호스트일 때, `HostPatchCheck`가 패치에 맞지 않는 원래 모듈을 먼저 `PLEXUS-COMPAT-224`로 표시합니다.
3. `HostModules.replaceLeftOut`이 `224`로 빠졌거나 `always`로 표시된 모듈마다 해당 호스트 릴리스용으로 색인된 호스트 모듈을 추출하고, 일반 모드 파일처럼 평가하여 원래 모듈 자리에 넣은 뒤 교체 내역을 로그에 남깁니다.
4. 교체된 결과에 대해 `HostPatchCheck`를 다시 실행하여 호스트 모듈 역시 패치에 맞는지 확인합니다.
5. 링크 검증(`ModLinkVerifier`) 단계에서 바닐라 jar보다 먼저 호스트의 패치된 게임 및 로더 jar를 읽어 링크를 검증합니다.

## 알려진 제한
- `NeoForge 26.2.0.93` 전용으로만 빌드되었습니다.
- 새로운 NeoForge 릴리스가 나오면 다시 빌드하고 검증해야 합니다.
- 실행 중인 클라이언트에서 툴팁 표시는 아직 확인하지 않았습니다. object-builder의 클라이언트 쪽 순서(NeoForge 등록 뒤 Fabric 초기화)도 창 작업으로는 확인하지 않았습니다.
- 각 모듈의 버전보다 최신 버전의 모듈을 요구하는 Fabric 모드는 일반 버전 검사에서 처리됩니다.
