# poly-runtime-impl

## 이 모듈이 하는 일
Poly 모드 로더 로딩 파이프라인을 실행합니다. 순서는 부트스트랩 → 검색·분류 → 메타데이터 검증 → 의존성 해석 → 변환 계획 → 게임 클래스로더 → 엔트리포인트 → 게임 `main`입니다.

## 무엇을 소유하는가
`dev.polymodloader.runtime`(`PolyRuntime`, `LaunchConfiguration`, `LaunchedGame`)와 하위 패키지 `.discovery`, `.dependency`, `.compat`(`CrossLoaderModResolver` 로더를 넘는 의존성 판정, `ModLinkVerifier` 시작 전 바이트코드 링크 검사, `CrossLoaderLoadOrder` 엔트리포인트 순서, `HostBuildMatcher` 호스트 로더가 실행하는 같은 모드의 다른 빌드 찾기), `.classloading`(`GameClassLoader`), `.lifecycle`, `.diagnostics`

## 무엇을 하지 않는가
JVM 진입점과 시스템 속성 해석(그것은 `poly-bootstrap`), 버전별 지식(그것은 어댑터)은 다루지 않습니다.

## 의존성
`poly-lifecycle-api`, `poly-version-adapter-api`(api); `poly-mod-metadata`, `poly-transform-engine`, slf4j-api, gson

## 주요 진입점
`new PolyRuntime().launch(LaunchConfiguration, String[] gameArgs)`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-runtime-impl:check
```

`ModDependencyResolverTest` 12개, `CrossLoaderModResolverTest` 8개, `ModLinkVerifierTest` 7개, `CrossLoaderLoadOrderTest` 3개, `ModFileScannerTest` 3개, `ShutdownCoordinatorTest` 4개, `PolyRuntimeTest` 9개 등. `PolyRuntimeTest`는 합성 게임 jar와 모드 jar로 전체 파이프라인을 검증합니다.

## 대표 흐름
`docs/ARCHITECTURE.md`의 "실행 흐름"을 참고하세요. 단계마다 `[Poly 모드 로더] Phase …` 로그를 남깁니다.

의존성 해석 뒤 `LoadedModCounter`가 로더별로 로드한 모드 수를 셉니다(`Loaded mods per loader: N Poly, N Fabric, N Quilt, N Forge` 로그). 규칙: 실제로 로드하는 모드만, 모드 ID마다 한 번 셉니다. 플레이어가 설치한 모드와 그 모드가 묶어 온 모드(중첩 jar)를 모두 세고, Poly 모드 로더 자신과 `minecraft`·`java`·`fabricloader`·`quilt_loader`·`forge`는 세지 않습니다. Quilt 모드는 Quilt 브리지가 로드하는 모드로, Fabric 모드와 따로 셉니다(ADR-0027). Forge 모드는 호스트(`GameHost.modIds()`)가 FML로 로드하는 모드입니다. 이 값은 `GameLayerContext.modCounts()`로 게임 계층에 가고, 타이틀 화면에 나옵니다.

호스트 로더가 있으면 `GameHost.transformRules()`의 규칙도 어댑터 규칙과 함께 계획하고, 게임 계층의 `brandingLines()`를 `GameHost.addBrandingLines`로 넘깁니다. 호스트가 대상을 패치해서 맞지 않는 Poly 모드 로더의 선택 규칙(`DISABLE_RULE`)은 경고 대신 INFO로 "patched the target of Poly Mod Loader's optional rule …; that rule is off"를 남깁니다.

게임 클래스로더를 만든 뒤, 모드를 하나도 실행하지 않는 브리지의 `runtimePackages()`(Poly 모드 로더 클래스 경로의 Fabric Loader·Quilt Loader API)를 `GameClassLoader.hideFromParent`로 게임·모드 코드에서 숨깁니다. 모드를 실행하는 브리지도 가진 접두사는 남깁니다. 그 로더가 실제로 없는 실행(NeoForge·Forge만)에서 여러 로더용 라이브러리가 클래스 존재로 로더를 잘못 알아내지 않게 하기 위함입니다(ADR-0026 27번, "Loader APIs of bridges without mods are not visible to game and mod code" 로그).

## 알려진 제한
시스템 등록·교체와 런타임 모니터링은 아직 없습니다. 종료 단계는 JVM 종료 훅으로만 연결됩니다. 다른 로더의 모드는 엔트리포인트만 쓰는 Fabric 모드만 로드합니다. 링크 검사는 상수 풀의 참조만 보며, 리플렉션으로 찾는 클래스와 실제 실행 경로는 분석하지 않습니다.
