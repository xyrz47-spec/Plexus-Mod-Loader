# poly-game-v26_1

## 이 모듈이 하는 일
Minecraft 26.1의 게임 계층입니다(ADR-0010). Native API 선언을 실제 게임 객체로 만들고, 변환 규칙이 호출하는 훅을 제공합니다.

## 무엇을 소유하는가
- `GameLayer26_1`(진입점), `LayerState`
- `HostPayloadHooks`: 호스트 로더와 다른 로더의 커스텀 페이로드가 한 연결에 있을 때, 호스트 자기 페이로드를 호스트 코덱으로 쓰고(`poly:custom_payload_encoding`) 호스트의 채널 검사가 다른 로더의 페이로드를 건너뛰게 하며, 호스트의 묶음 패킷 걸러내기가 뺀 다른 로더의 페이로드를 제자리에 되돌립니다(NeoForge 호스트 규칙, ADR-0026 40·41).
- `BuiltInRegistryFreeze`: 내장 레지스트리를 두 번째로 얼릴 때, 다른 로더가 이미 얼린 레지스트리의 태그 묶기를 건너뜁니다(`poly:builtin_registries_refreeze`, ADR-0026 38).
- `GameHooks`(공통 훅), `InteractionHooks`(상호작용 이벤트와 취소 가능한 블록 이벤트), `LifecycleHooks`(서버·월드·리스폰·엔티티 생명주기 이벤트), `client.ClientHooks`(클라이언트 전용 훅)
- `content`(`PlexusBlock`, `PlexusItem`, `ContentInstaller`, `ModCreativeTabs`), `GameTags`, `client.CreativeTabPaging`, `commands`, `Networking`, `ModPacks`, `GeneratedDataPack`, `gametest`
- `Views`: 게임 객체를 API 뷰로 바꿉니다.
- `client.TitleScreenModCounts`: 타이틀 화면 왼쪽 아래, 버전 글자 위에 로더별로 로드한 모드 수(`ModCountLines`)를 그립니다. 글자는 게임의 현재 언어에서 먼저 찾고(리소스 팩이 바꿀 수 있음), 없으면 Poly 모드 로더의 언어 파일(`poly-version-adapter-api`의 `assets/poly/lang/<언어>.json`)에서 찾습니다. Forge가 그 자리를 그릴 때는 `GameLayer26_1.brandingLines()`로 줄만 넘깁니다.

## 무엇을 하지 않는가
- 변환 규칙을 선언하지 않습니다. 규칙은 `poly-version-adapter-v26_1`의 `Minecraft26_1Adapter`에 있습니다.
- 선언을 검증하지 않습니다. 검증은 `poly-runtime-impl`이 합니다.

## 의존성
- `poly-version-adapter-api`(그리고 그것이 노출하는 공개 API)
- slf4j
- Minecraft 26.1 클라이언트 jar와 라이브러리. `compileOnly`로만, `poly.minecraft-compile` 플러그인이 붙입니다.

## 로딩
- 이 jar는 Poly 모드 로더 클래스 경로에 있지만, 클래스는 **게임 클래스로더**가 정의합니다(`GameClassLoader.GAME_LAYER_PREFIX`).
- 부모 로더의 코드는 이 패키지의 클래스를 직접 참조하면 안 됩니다.
- `client` 하위 패키지는 클라이언트 측 규칙에서만 호출되므로, 전용 서버에서는 로드되지 않습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-game-v26_1:check   # BuiltInRulesTest: 내장 변환 규칙을 실제 26.1 클래스에 양쪽(클라이언트·서버)으로 적용하고 훅 시그니처 확인
.\gradlew.bat :poly-bootstrap:nativeApiGameTests :poly-bootstrap:clientWorldReloadTest
```

## 26.2와 다른 점
`poly-game-v26_2`를 복사한 뒤 26.1에 맞게 고친 곳만 다릅니다(26.1 jar를 `javap`로 확인). 같은 코드로 26.1.1과 26.1.2도 실행합니다(어댑터의 `gameVersions()`).
- HUD: 26.1에는 `Hud`가 없습니다. 훅 `ClientHooks.onHudRendered`는 `Gui`를 받고, 숨김 여부는 `Options.hideGui`로 봅니다.
- 화면 열기: `minecraft.gui.setScreen(…)`/`gui.screen()` 대신 `Minecraft.setScreen(…)`/`Minecraft.screen`.
- 월드 페인터: 26.1에는 `LevelRenderer.submitFeatures`가 없습니다. 훅 `onSubmitWorldFeatures`는 `submitEntities(PoseStack, LevelRenderState, SubmitNodeCollector)`의 시작에서 불립니다. 26.2의 `submitFeatures`가 처음 하는 일이고, 프레임마다 한 번, `finalizeGizmoCollection` 전에 불립니다.
- 셰이더 파이프라인(`ModShaderPipelines`): 바인드 그룹(`BindGroupLayout`, `BindGroupLayouts`)과 `PrimitiveTopology`가 없습니다. 유니폼(`Globals`, `DynamicTransforms`, `Projection`, 모드 블록)과 샘플러를 `withUniform`/`withSampler`로 하나씩 선언하고, 정점 형식은 `withVertexFormat(…, VertexFormat.Mode.QUADS)`로 정합니다.
- 블록 엔티티 타입: `BlockEntityType`의 생성자와 `BlockEntitySupplier`가 private입니다. `PlexusMenus`처럼 프록시와 반사로 만듭니다(`ContentInstaller.newBlockEntityType`).
- 발전 과제 트리거: `PlayerTrigger`가 `net.minecraft.advancements.criterion`에 있습니다.
- 이동 제어: `FlyingMoveControl`, `SmoothSwimmingMoveControl`은 제네릭이 아닙니다.
- 게임 테스트: `GameTestHelper.makeMockServerPlayer(GameType)`이 없습니다. 26.2의 그 메서드가 하는 일(월드에 넣지 않은 서바이벌 `ServerPlayer`)을 `GameTestContextView`가 직접 합니다.
- Native API 확장(이벤트 우선순위·결과, 상호작용 이벤트, 취소 가능한 블록 부수기·놓기, 서버·월드·리스폰·엔티티 생명주기 이벤트, 플레이어 죽음 훅, 태그, 모드 크리에이티브 탭 페이지, 아이템 툴팁, `GameTestContext` 도우미)은 26.2와 같은 코드입니다. 새 규칙 21개의 대상 메서드를 26.1과 26.2 jar에서 `javap -c`로 비교했습니다. 다른 곳은 규칙과 무관합니다.
  - `ServerPlayerGameMode.useItemOn`, `Player.interactOn`, `Player.attack`: 본문만 다르고 시작 훅이라 영향이 없습니다.
  - `BlockItem.place`: 발전 과제 트리거 패키지(`advancements.criterion`, 26.2는 `advancements.triggers`)만 다릅니다. `placeBlock` 호출 1번, `areturn` 7개, 매개변수 슬롯에 쓰지 않음은 같습니다.
  - `CreativeModeInventoryScreen.init`: 화면을 `Minecraft.setScreen`으로 엽니다(26.2는 `Gui.setScreen`). `return`은 1개로 같습니다.
  - 크리에이티브 탭은 26.2처럼 14자리(위·아래 줄 각 0~6열)를 모두 게임 탭이 씁니다.

## 알려진 제한
클라이언트 쪽 변경(HUD, 월드 페인터, 셰이더 파이프라인, 화면)은 변환 계획과 훅 시그니처까지만 창 없이 확인했습니다. 창을 여는 `clientWorldReloadTest`는 실행하지 않았습니다.

ADR-0010의 "실험에서 확인한 제약"을 보세요.
