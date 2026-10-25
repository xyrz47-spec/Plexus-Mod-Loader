# poly-game-v26_3

## 이 모듈이 하는 일
Minecraft 26.3의 게임 계층입니다(ADR-0010). Native API 선언을 실제 게임 객체로 만들고, 변환 규칙이 호출하는 훅을 제공합니다.

## 무엇을 소유하는가
- `GameLayer26_3`(진입점), `LayerState`
- `HostPayloadHooks`: 호스트 로더와 다른 로더의 커스텀 페이로드가 한 연결에 있을 때, 호스트 자기 페이로드를 호스트 코덱으로 쓰고(`poly:custom_payload_encoding`) 호스트의 채널 검사가 다른 로더의 페이로드를 건너뛰게 하며, 호스트의 묶음 패킷 걸러내기가 뺀 다른 로더의 페이로드를 제자리에 되돌립니다(NeoForge 호스트 규칙, ADR-0026 40·41).
- `BuiltInRegistryFreeze`: 내장 레지스트리를 두 번째로 얼릴 때, 다른 로더가 이미 얼린 레지스트리의 태그 묶기를 건너뜁니다(`poly:builtin_registries_refreeze`, ADR-0026 38).
- `GameHooks`(공통 훅), `InteractionHooks`(상호작용 이벤트와 취소 가능한 블록 이벤트), `LifecycleHooks`(서버·월드·리스폰·엔티티 생명주기 이벤트), `client.ClientHooks`(클라이언트 전용 훅)
- `content`(`PolyBlock`, `PolyItem`, `ContentInstaller`, `ModCreativeTabs`), `GameTags`, `client.CreativeTabPaging`, `commands`, `Networking`, `ModPacks`, `GeneratedDataPack`, `gametest`
- `Views`: 게임 객체를 API 뷰로 바꿉니다.
- `client.TitleScreenModCounts`: 타이틀 화면 왼쪽 아래, 버전 글자 위에 로더별로 로드한 모드 수(`ModCountLines`)를 그립니다. 글자는 게임의 현재 언어에서 먼저 찾고(리소스 팩이 바꿀 수 있음), 없으면 Poly 모드 로더의 언어 파일(`poly-version-adapter-api`의 `assets/poly/lang/<언어>.json`)에서 찾습니다. Forge가 그 자리를 그릴 때는 `GameLayer26_3.brandingLines()`로 줄만 넘깁니다.

## 무엇을 하지 않는가
- 변환 규칙을 선언하지 않습니다. 규칙은 `poly-version-adapter-v26_3`의 `Minecraft26_3Adapter`에 있습니다.
- 선언을 검증하지 않습니다. 검증은 `poly-runtime-impl`이 합니다.

## 의존성
- `poly-version-adapter-api`(그리고 그것이 노출하는 공개 API)
- slf4j
- Minecraft 26.3 클라이언트 jar와 라이브러리. `compileOnly`로만, `poly.minecraft-compile` 플러그인이 붙입니다.

## 로딩
- 이 jar는 Poly 모드 로더 클래스 경로에 있지만, 클래스는 **게임 클래스로더**가 정의합니다(`GameClassLoader.GAME_LAYER_PREFIX`).
- 부모 로더의 코드는 이 패키지의 클래스를 직접 참조하면 안 됩니다.
- `client` 하위 패키지는 클라이언트 측 규칙에서만 호출되므로, 전용 서버에서는 로드되지 않습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-game-v26_3:check   # BuiltInRulesTest: 내장 변환 규칙을 실제 26.3 클래스에 양쪽(클라이언트·서버)으로 적용하고 훅 시그니처 확인
.\gradlew.bat :poly-bootstrap:nativeApiGameTests :poly-bootstrap:clientWorldReloadTest
```

## 26.2와 다른 점

1.2.0부터 지원 버전입니다(`gradle/minecraft-versions.properties`의 `supported`). `poly-game-v26_2`를 복사한 뒤 26.3에 맞게 고쳤습니다. 고친 곳과 이유는 `docs/plans/MINECRAFT_26_3.md`의 "진행 상황"에 있습니다. 요약하면 다음과 같습니다.

- 키 바인딩: `client.GameKeyCodes`가 Poly 모드 로더 키 코드(GLFW 값)를 26.3의 SDL 스캔코드로 바꿉니다(ADR-0029).
- 몹 스폰: 바이옴의 환경 속성 `NATURAL_MOB_SPAWNS`(`WorldgenModifier.applySpawns`, `Views`, `GeneratedDataPack`).
- 월드 생성: `ChunkGenerator.buildTerrain`, 설정을 품은 `Feature`(`FEATURE_TYPE`, `worldgen/feature`), 컴파일되는 밀도 함수(`worldgen.CodeWorldgen`).
- 렌더링: `com.mojang.renderpearl.api`(`client.ModShaderPipelines`), 유니폼 버퍼는 파이프라인 위치로 찾음, 후처리 효과는 `spectatedEntityPostEffect`.
- 테스트: `GameKeyCodesTest`, `ModPacksTest`(팩 오버레이), `ModShaderSourcesTest`(예제 셰이더를 게임의 shaderc 컴파일러로 창 없이 컴파일).

## 알려진 제한
ADR-0010의 "실험에서 확인한 제약"을 보세요.
