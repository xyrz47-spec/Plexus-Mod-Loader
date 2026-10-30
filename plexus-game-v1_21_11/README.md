# poly-game-v1_21_11

## 이 모듈이 하는 일
Minecraft 1.21.11의 게임 계층입니다(ADR-0010). Native API 선언을 실제 게임 객체로 만들고, 변환 규칙이 호출하는 훅을 제공합니다.

## 무엇을 소유하는가
- `GameLayer1_21_11`(진입점), `LayerState`
- `GameHooks`(공통 훅), `InteractionHooks`(상호작용 이벤트와 취소 가능한 블록 이벤트), `LifecycleHooks`(서버·월드·리스폰·엔티티 생명주기 이벤트), `client.ClientHooks`(클라이언트 전용 훅)
- `content`(`PolyBlock`, `PolyItem`, `ContentInstaller`, `ModCreativeTabs`), `GameTags`, `client.CreativeTabPaging`, `commands`, `Networking`, `ModPacks`, `GeneratedDataPack`, `gametest`
- `Views`: 게임 객체를 API 뷰로 바꿉니다.
- `client.TitleScreenModCounts`: 타이틀 화면 왼쪽 아래, 버전 글자 위에 로더별로 로드한 모드 수(`ModCountLines`)를 그립니다. 글자는 게임의 현재 언어에서 먼저 찾고(리소스 팩이 바꿀 수 있음), 없으면 Poly 모드 로더의 언어 파일(`poly-version-adapter-api`의 `assets/poly/lang/<언어>.json`)에서 찾습니다. Forge가 그 자리를 그릴 때는 `GameLayer1_21_11.brandingLines()`로 줄만 넘깁니다.

## 무엇을 하지 않는가
- 변환 규칙을 선언하지 않습니다. 규칙은 `poly-version-adapter-v1_21_11`의 `Minecraft1_21_11Adapter`에 있습니다.
- 선언을 검증하지 않습니다. 검증은 `poly-runtime-impl`이 합니다.

## 의존성
- `poly-version-adapter-api`(그리고 그것이 노출하는 공개 API)
- slf4j
- Mojang 이름으로 다시 매핑한 Minecraft 1.21.11 클라이언트 jar(ADR-0019, Gradle 캐시에만 있음)와 라이브러리. `compileOnly`로만, `poly.minecraft-compile` 플러그인이 붙입니다.

## 로딩
- 이 jar는 Poly 모드 로더 클래스 경로에 있지만, 클래스는 **게임 클래스로더**가 정의합니다(`GameClassLoader.GAME_LAYER_PREFIX`).
- 부모 로더의 코드는 이 패키지의 클래스를 직접 참조하면 안 됩니다.
- `client` 하위 패키지는 클라이언트 측 규칙에서만 호출되므로, 전용 서버에서는 로드되지 않습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-game-v1_21_11:check   # BuiltInRulesTest: 내장 변환 규칙을 실제 1.21.11 클래스에 양쪽(클라이언트·서버)으로 적용하고 훅 시그니처 확인
.\gradlew.bat :poly-bootstrap:nativeApiGameTests :poly-bootstrap:clientWorldReloadTest
```

## 26.1과 다른 점

**실험 버전입니다**(ADR-0019, `gradle/minecraft-versions.properties`의 `experimental`). 지원 버전이 아니므로 설치 프로그램·SDK에 들어가지 않습니다.

`poly-game-v26_1`을 `newMinecraftVersion -Pexperimental=true`로 복사한 뒤 1.21.11에 맞게 고쳤습니다. 다시 매핑한 jar를 `javap`로 확인했습니다. 고친 곳은 `docs/plans/MINECRAFT_1_21.md`의 "1.21.11 서버 PoC"에 있습니다(`docs/`는 이 공개 저장소에 없습니다). 요약하면 다음과 같습니다.

- GUI는 `GuiGraphics`(`drawString`, `render`, 컨테이너 화면 `renderBg`)입니다.
- 렌더 상태는 `net.minecraft.client.renderer.state` 패키지에 있습니다.
- 파이프라인은 `withDepthTestFunction`·`withDepthWrite`·`withBlend`로 만들고, 블록은 `SubmitNodeCollector.submitBlock`으로 그립니다.
- 월드 저장소 파일 이름은 `data/poly_<namespace>__<path>.dat`입니다(1.21.11의 `SavedDataType`은 식별자 대신 파일 이름 하나를 받음).
- `Player.interactOn`에 클릭 위치가 없습니다. 메시지는 `displayClientMessage`로 보냅니다.
- 코드 주석의 "1.21.11"은 대부분 복사할 때 26.1을 바꾼 것입니다. 1.21.11에서 확인한 사실은 위 문서와 `BuiltInRulesTest`가 기준입니다.

## 알려진 제한
클라이언트 쪽 변경(HUD, 월드 페인터, 셰이더 파이프라인, 화면)은 변환 계획과 훅 시그니처까지만 창 없이 확인했습니다. 창을 여는 `clientWorldReloadTest`는 실행하지 않았습니다.

ADR-0010의 "실험에서 확인한 제약"을 보세요.
