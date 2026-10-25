# poly-game-v26_2

## 이 모듈이 하는 일
Minecraft 26.2의 게임 계층입니다([ADR-0010](../docs/adr/0010-native-api-game-layer.md)). Native API 선언을 실제 게임 객체로 만들고, 변환 규칙이 호출하는 훅을 제공합니다.

## 무엇을 소유하는가
- `GameLayer26_2`(진입점), `LayerState`
- `HostPayloadHooks`: 호스트 로더와 다른 로더의 커스텀 페이로드가 한 연결에 있을 때, 호스트 자기 페이로드를 호스트 코덱으로 쓰고(`poly:custom_payload_encoding`) 호스트의 채널 검사가 다른 로더의 페이로드를 건너뛰게 하며, 호스트의 묶음 패킷 걸러내기가 뺀 다른 로더의 페이로드를 제자리에 되돌립니다(NeoForge 호스트 규칙, ADR-0026 40·41).
- `BuiltInRegistryFreeze`: 내장 레지스트리를 두 번째로 얼릴 때, 다른 로더가 이미 얼린 레지스트리의 태그 묶기를 건너뜁니다(`poly:builtin_registries_refreeze`, ADR-0026 38).
- `GameHooks`(공통 훅), `InteractionHooks`(상호작용 이벤트와 취소 가능한 블록 이벤트), `LifecycleHooks`(서버·월드·리스폰·엔티티 생명주기 이벤트), `client.ClientHooks`(클라이언트 전용 훅)
- `content`(`PolyBlock`, `PolyItem`, `ContentInstaller`, `ModCreativeTabs`), `GameTags`, `client.CreativeTabPaging`, `commands`, `Networking`, `ModPacks`, `GeneratedDataPack`, `gametest`
- `Views`: 게임 객체를 API 뷰로 바꿉니다.
- `client.TitleScreenModCounts`: 타이틀 화면 왼쪽 아래, 버전 글자 위에 로더별로 로드한 모드 수(`ModCountLines`)를 그립니다. 글자는 게임의 현재 언어에서 먼저 찾고(리소스 팩이 바꿀 수 있음), 없으면 Poly 모드 로더의 언어 파일(`poly-version-adapter-api`의 `assets/poly/lang/<언어>.json`)에서 찾습니다. Forge가 그 자리를 그릴 때는 `GameLayer26_2.brandingLines()`로 줄만 넘깁니다.

## 무엇을 하지 않는가
- 변환 규칙을 선언하지 않습니다. 규칙은 `poly-version-adapter-v26_2`의 `Minecraft26_2Adapter`에 있습니다.
- 선언을 검증하지 않습니다. 검증은 `poly-runtime-impl`이 합니다.

## 의존성
- `poly-version-adapter-api`(그리고 그것이 노출하는 공개 API)
- slf4j
- Minecraft 26.2 클라이언트 jar와 라이브러리. `compileOnly`로만, `poly.minecraft-compile` 플러그인이 붙입니다.

## 로딩
- 이 jar는 Poly 모드 로더 클래스 경로에 있지만, 클래스는 **게임 클래스로더**가 정의합니다(`GameClassLoader.GAME_LAYER_PREFIX`).
- 부모 로더의 코드는 이 패키지의 클래스를 직접 참조하면 안 됩니다.
- `client` 하위 패키지는 클라이언트 측 규칙에서만 호출되므로, 전용 서버에서는 로드되지 않습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-game-v26_2:check   # BuiltInRulesTest: 내장 변환 규칙을 실제 26.2 클래스에 양쪽(클라이언트·서버)으로 적용하고 훅 시그니처 확인
.\gradlew.bat :poly-bootstrap:nativeApiGameTests :poly-bootstrap:clientWorldReloadTest
```

## 알려진 제한
ADR-0010의 "실험에서 확인한 제약"을 보세요.
