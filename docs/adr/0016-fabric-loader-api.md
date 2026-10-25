# ADR-0016: Fabric Loader 런타임 API (`FabricLoader.getInstance()`)

- 상태: 채택 (2026-10-21)
- 관련: ADR-0009(로더 브리지), ADR-0014(링크 검사), ADR-0015(중첩 jar)

## 배경
Fabric 모드와 라이브러리는 대부분 `FabricLoader.getInstance()`를 씁니다. 다른 모드가 있는지, 설정 폴더가 어디인지, 다른 모드가 등록한 진입점이 무엇인지 묻기 위해서입니다. 지금까지 Poly 모드 로더는 Fabric Loader jar를 통째로 클래스 경로에 두기만 하고 시작하지 않았습니다. 그래서 이런 모드는 게임 도중에 실패할 수 있었고, ADR-0014부터는 링크 검사가 시작 전에 막았습니다(`POLY-COMPAT-111`). 실제 모드 묶음에서는 `placeholder-api`, `fabric-language-kotlin`이 이 이유 하나로 막혔습니다.

0.19.5 jar를 `javap`로 확인한 결과는 이렇습니다.
- `FabricLoader.getInstance()`는 내부 클래스 `net.fabricmc.loader.impl.FabricLoaderImpl`의 `public static final INSTANCE`를 읽습니다.
- `LanguageAdapter.getDefault()`는 `net.fabricmc.loader.impl.util.DefaultLanguageAdapter.INSTANCE`를 읽습니다.
- 공개 API가 쓰는 나머지 내부 코드는 버전 해석기와 순수 유틸리티뿐입니다.

## 결정
1. Fabric 브리지 jar에는 Fabric Loader(Apache-2.0)에서 공개 API와 그 닫힘만 담습니다. 대상은 `net.fabricmc.api`, `net.fabricmc.loader.api`, 옛 `net.fabricmc.loader.util`, `language.LanguageAdapter`, 버전 해석기, 유틸리티 4개이고, 라이선스 파일을 함께 넣습니다. Fabric Loader 본체는 들어가지 않습니다.
2. 브리지가 `FabricLoaderImpl`과 `DefaultLanguageAdapter`를 같은 이름·같은 필드 모양으로 직접 제공합니다. 둘 다 동작은 Poly 모드 로더의 `FabricLoaderRuntime`에 맡깁니다. 이 대체본은 Fabric Loader의 구현이 아니며, 모드가 이 내부 클래스의 다른 멤버를 쓰면 링크 검사가 막습니다.
3. 브리지 SPI에 `prepareLaunch(BridgeLaunchContext)`를 더합니다. 런타임은 로드할 모드와 게임 클래스로더가 정해진 뒤, 진입점보다 먼저 이것을 부릅니다. 브리지는 여기서 런타임을 만들어 연결합니다.
4. 구현 범위는 Fabric과 동작이 분명한 것으로 한정합니다.
   - **모드 목록:** `provides`를 포함한 모드 조회와 `isModLoaded`. 모든 로더의 로드된 모드가 보이고, Poly 모드는 `getType() = "poly"`, `minecraft`·`java`·`fabricloader`는 `builtin`입니다.
   - **`ModMetadata`:** 작성자, 연락처, 라이선스, 아이콘 크기 선택, `custom` 값(형식이 틀리면 Fabric처럼 `ClassCastException`), 의존성.
   - **`ModContainer`:** jar 루트를 zip 파일 시스템으로 엶. 중첩 모드의 `ModOrigin`(바깥 모드 ID와 경로).
   - **진입점:** 모든 키에 대해 `getEntrypoints`·`getEntrypointContainers`·`invokeEntrypoints`를 제공합니다. 모드·선언당 인스턴스는 하나이고, Poly 모드 로더가 부르는 `main`/`client`/`server` 인스턴스도 같은 것입니다.
   - **기타:** `ObjectShare`(Fabric과 같은 `modid:subkey` 검사), 게임·설정 폴더, 실행 환경, 개발 환경 여부(`fabric.development`), 실행 인수(`sanitize`면 `--accessToken` 값 제외).
5. 기본 언어 어댑터는 클래스, 정적 필드, 정적 메서드(함수형 인터페이스로 감쌈)를 지원합니다. 다른 어댑터는 그것을 `languageAdapters`로 선언한 로드된 모드가 제공합니다.
   - 이 관계는 의존성으로 다룹니다: 선언한 모드는 `fabric-language-adapter:<이름>`을 제공(`provides`)하고, 그 어댑터를 쓰는 모드는 이것을 필요로 합니다. 제공 모드가 없거나 막히면 이유와 함께 막힙니다(`114`).
   - 그래서 `POLY-COMPAT-106`은 이제 기본 어댑터의 `::` 값에만 붙습니다.
6. 뜻이 분명하지 않은 두 멤버는 추측해서 구현하지 않습니다: `getGameInstance`, `getMappingResolver`. 브리지가 이 둘을 `unsupportedMembers()`로 선언하고, 이것을 부르는 모드는 링크 검사가 막습니다(`111`).
   - 같은 맥락에서, 로더 런타임 패키지의 클래스가 제공 범위 안이더라도 실제로 없으면 이제 막습니다. 이전에는 경고(`113`)뿐이었습니다.
7. 브리지의 `prepareLaunch`가 실패하면 진입점 전에 시작을 멈춥니다(`POLY-COMPAT-003`).

## 근거(실험, 2026-10-21)
- `FabricLoaderRuntimeTest` 8개: 모드 조회, 메타데이터, 중첩 출처, 파일 찾기, 진입점 생성·캐시·형 검사, 공유 객체, 폴더·인수, 미지원 메서드.
- `fabricLoaderApi` 시나리오(게임 테스트 서버):
  - Fabric 모드가 Poly 모드 `native_lib`와 Minecraft 버전, `custom` 값, 사용자 진입점, 자신의 `main` 인스턴스, 공유 객체, 설정 폴더를 확인합니다.
  - 다른 모드가 제공한 언어 어댑터(`poly_test`)가 생성자 없는 클래스를 진입점으로 만듭니다.
- 실제 모드 묶음: 로드 가능한 외부 모드가 3개에서 17개로 늘었습니다. `placeholder-api`, `fabric-language-kotlin`과 그 Kotlin 라이브러리 13개, `fabric-api`(일부, `124`)입니다. 이 상태로 게임 테스트 서버가 정상 시작했고, 예외와 크래시는 0건입니다.

## 결과
- Fabric Loader API를 쓰는 라이브러리는 동작합니다. 실제 기능 모드는 대부분 여전히 Mixin(`101`)이나 Fabric API 하위 모듈에서 막힙니다.
- 브리지 jar 안에 Fabric Loader의 API 클래스가 들어 있습니다. Fabric Loader 버전을 올리면 닫힘을 다시 확인해야 합니다(`javap` 절차는 `poly-bridge-fabric/build.gradle.kts` 주석 참고).
- `getGameInstance`·`getMappingResolver`를 쓰는 모드는 막힙니다. 게임 계층과 연결해 제공하는 것은 다음 단계입니다.
