# plexus-version-adapter-v26_1

## 이 모듈이 하는 일
Minecraft 26.1 전용 지식을 담습니다. 클라이언트/서버 진입점과 내장 변환 규칙(브랜드, 타이틀 화면 모드 수, 레지스트리·페이로드 처리, Native API 이벤트 훅 등 수십 개)이 있습니다.

## 무엇을 소유하는가
`dev.plexusmodloader.game.version.v26_1.Minecraft26_1Adapter`와 `META-INF/services` 등록

## 무엇을 하지 않는가
다른 버전을 지원하지 않습니다. 이 모듈 밖에 Minecraft 이름을 두지 않습니다.

## 의존성
`plexus-version-adapter-api`. Minecraft jar에는 컴파일 의존하지 않습니다. 이름은 `javap`로 검증한 문자열입니다.

## 주요 진입점
`Minecraft26_1Adapter`

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-bootstrap:serverBootSmokeTest
```

실제 26.1에서 규칙 승인과 적용을 확인합니다. 클라이언트는 `:plexus-bootstrap:runClient`로 확인합니다.

## 대표 흐름
`poly:client_brand`(CLIENT)와 `poly:server_brand`(양쪽)가 `"vanilla"`를 `"poly"`로 바꿉니다. 실패 정책은 DISABLE_RULE입니다.

`poly:client_title_mod_counts`(CLIENT, DISABLE_RULE)는 타이틀 화면 `TitleScreen.extractRenderState`가 버전 글자를 그리는 유일한 `GuiGraphicsExtractor.text(Font, String, int, int, int)` 호출을 게임 계층의 `client.TitleScreenModCounts.onVersionText`로 바꿉니다(`RedirectCall`, ADR-0013). 훅은 그 줄을 그리고 그 위에 로더별 모드 수를 그립니다. MinecraftForge의 패치에는 이 호출이 없어서 Forge에서는 이 규칙이 꺼지고, `plexus-bridge-forge`의 `poly:client_title_mod_counts_forge`가 대신 Forge 브랜딩 위에 그립니다.

`poly:builtin_registries_refreeze`(모든 쪽, DISABLE_RULE, `poly:content_registration` 뒤)는 `BuiltInRegistries.freeze()`의 유일한 `bindBootstrappedTagsToEmpty(Registry)` 호출을 게임 계층의 `BuiltInRegistryFreeze.bindBootstrappedTagsToEmpty`로 바꿉니다(`RedirectCall`, ADR-0026 38). 훅은 얼지 않은 레지스트리에는 바닐라처럼 태그를 묶고, 이미 언 레지스트리에서는 묶기를 건너뜁니다. Fabric API는 모드 초기화 뒤 내장 레지스트리를 한 번 더 얼리는데, NeoForge 아래에서는 NeoForge의 `GameData.freezeData`가 이미 묶고 얼렸기 때문입니다. 레지스트리를 한 번만 얼리는 실행(바닐라, Fabric만, NeoForge만)에서는 동작이 같습니다. 확인: 26.1·26.1.2 클라이언트 jar와 NeoForge 26.1.0.19-beta·26.1.2.122가 패치한 클라이언트 jar(`javap`).

`poly:custom_payload_encoding`(모든 쪽, DISABLE_RULE)은 `CustomPacketPayload$1.writeCap`의 유일한 `StreamCodec.encode` 호출을 게임 계층의 `HostPayloadHooks.encode`로 바꿉니다(`RedirectCall`, Mixin 단계 뒤, ADR-0026 40). 호스트 로더가 없으면 원래 호출과 같고, NeoForge가 호스트이면 NeoForge 자기 페이로드를 NeoForge 코덱으로 씁니다.

## 26.2와 다른 점
`plexus-version-adapter-v26_2`를 복사했습니다. 규칙 67개 중 두 개의 대상이 다릅니다(26.1 jar를 `javap`로 확인). Native API 확장에서 더한 규칙 21개(`poly:block_break`, `poly:use_block`, `poly:use_item`, `poly:use_entity`, `poly:attack_entity`, `poly:block_place`, `poly:block_placed`, `poly:player_death`, `poly:world_loaded`, `poly:world_saving`, `poly:world_unloaded`, `poly:server_stopped`, `poly:player_respawned`, `poly:entity_joined_world`, `poly:creative_tab_pages`, `poly:client_creative_tabs_clicked`/`_released`/`_hovered`/`_drawn`, `poly:client_item_tooltip`, `poly:client_creative_page_buttons`)는 대상·디스크립터·사전조건이 26.2와 같습니다(`plexus-game-v26_1` README).
- `poly:client_hud`: `net/minecraft/client/gui/Hud` 대신 `net/minecraft/client/gui/Gui.extractRenderState`(디스크립터 같음).
- `poly:client_world_painters`: `LevelRenderer.submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)` 대신 `LevelRenderer.submitEntities(PoseStack, LevelRenderState, SubmitNodeCollector)`.

## 핫픽스
`gameVersions()`는 `26.1`, `26.1.1`, `26.1.2`입니다(ADR-0011 4번, `gradle/minecraft-versions.properties`의 `hotfixes.26.1`). 세 클라이언트 jar를 클래스마다 비교했습니다.
- 26.1 → 26.1.1: `SharedConstants`(월드·스냅숏 프로토콜 버전 상수), `DetectedVersion`, `PlayerEntry`(소셜 화면)
- 26.1.1 → 26.1.2: 위 두 클래스, `Checkbox`, `AbstractReportScreen`, `ServerGamePacketListenerImpl.handleAttack`

규칙 대상과 게임 계층이 쓰는 멤버는 바뀌지 않았습니다(`ServerGamePacketListenerImpl.handleCustomPayload`는 같음). 새 규칙 21개의 대상 클래스(`ServerPlayerGameMode`, `Player`, `BlockItem`, `ServerPlayer`, `MinecraftServer`, `ServerLevel`, `ServerLevel$EntityCallbacks`, `PlayerList`, `CreativeModeTabs`, `CreativeModeInventoryScreen`, `ItemStack`)와 게임 계층이 반사로 읽는 `AbstractContainerScreen`, `Screen`, `GameTestHelper`도 세 버전의 클라이언트 jar와 서버 jar에서 `javap -c`(상수 풀 번호 제외)로 같습니다. 게임 계층은 버전 상수를 컴파일 시점에 넣지 않고 실행 중에 읽습니다. 실행할 때 런타임은 이 어댑터의 규칙을 실행 중인 버전으로 계획합니다(`AdapterTransformRules`).

## 알려진 제한
대상 jar SHA-1은 client `19177183…`(26.1), `377031a9…`(26.1.1), `4e618f09…`(26.1.2)입니다. 클라이언트 전용 규칙은 창 없이 변환 계획·사전조건·바이트코드 검증·훅 시그니처까지만 확인했습니다.
