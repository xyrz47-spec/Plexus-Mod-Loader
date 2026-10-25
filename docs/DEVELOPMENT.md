# Poly 모드 로더 개발 현황과 소스 빌드

Poly 모드 로더를 소스에서 빌드하고 고치는 사람을 위한 문서입니다. 플레이어와 모드 제작자는 저장소의 [README](../README.md)와 문서 웹사이트를 보세요. 전체 목표와 설계는 [`PROJECT_PLAN_AND_DESIGN.md`](PROJECT_PLAN_AND_DESIGN.md)에 있습니다.

## 문서 사이트

모드 제작 안내와 API 레퍼런스는 문서 웹사이트 [www.polymc.net](https://www.polymc.net/docs/)에 있습니다. 웹사이트 소스(`site/`, Docusaurus)는 공개 저장소에 들어 있지 않습니다. API 레퍼런스는 `./gradlew apiReference`로 다시 만듭니다.

## 현재 상태 — 1.1.0 (정식)

| 항목 | 상태 |
|---|---|
| 대상 게임 | Minecraft 26.1, 26.1.1, 26.1.2, 26.2(기본 26.2), Java 25. 지원 목록은 `gradle/minecraft-versions.properties` 한 곳에서 관리합니다([ADR-0011](adr/0011-multiple-minecraft-versions.md)). 1.21.11은 개발용 실험 버전이며 지원 버전이 아닙니다([ADR-0019](adr/0019-obfuscated-minecraft-versions.md)) |
| 실행 | 클라이언트, 전용 서버, Mojang 게임 테스트 서버에서 Poly 모드 로더를 거쳐 실행. 월드 생성·저장·다시 열기를 자동 검증(`serverBootSmokeTest`, `worldCreateSaveTest`, `clientWorldReloadTest`) |
| Poly 모드 | **Verified**. `poly.mod.json` 검증, 의존성 해석, 진입점, Native API(콘텐츠, 월드 생성, 명령어, 네트워크, 저장 데이터, 클라이언트 그리기, 게임 테스트, 시스템 교체), 선언형 바이트코드 변환. API는 모두 미리보기 단계입니다([NATIVE_API.md](NATIVE_API.md)) |
| Fabric 모드 | **Verified**. Fabric Loader API, Mixin·MixinExtras, class tweaker, 중첩 jar를 Fabric Loader와 같은 순서로 적용([ADR-0016](adr/0016-fabric-loader-api.md), [ADR-0020](adr/0020-fabric-mixin-and-runtime-fidelity.md)) |
| Quilt 모드 | **Verified**. `quilt.mod.json`과 Quilt Loader API. Quilt 표준 라이브러리(QSL)가 필요한 모드는 이유와 함께 빠짐([ADR-0027](adr/0027-quilt-mods-on-poly-mod-loader.md)) |
| Forge 모드 | **Verified**. Forge와 FML 자체를 Poly 모드 로더 안에서 실행([ADR-0021](adr/0021-forge-on-polyloader.md)) |
| NeoForge 모드 | **Verified**. NeoForge와 FML 자체를 Poly 모드 로더 안에서 실행([ADR-0026](adr/0026-neoforge-on-poly-mod-loader.md)) |
| 혼합 실행 | Fabric·Quilt 모드는 Forge 모드나 NeoForge 모드와 한 게임에서 실행. Forge 모드와 NeoForge 모드를 한 게임에 넣는 것은 아직 안 됨(`POLY-COMPAT-221`) |
| 시작 전 안전 판정 | 실행할 수 없는 모드는 게임 코드가 돌기 전에 오류 코드와 이유를 남기고 빠지며, 나머지 모드는 그대로 시작 |
| 성능 | 시작 시간, 서버 틱 시간, 메모리는 바닐라와의 차이가 측정 편차 안. 클라이언트 프레임 시간은 개발용 모드를 함께 넣은 측정에서 약간 깁니다([PERFORMANCE.md](PERFORMANCE.md)) |
| 설치 프로그램 | `PolyModLoader-<버전>.jar`/`.exe`가 Minecraft Launcher에 프로필을 추가하거나 전용 서버를 설치([ADR-0017](adr/0017-launcher-profile-installation.md)) |
| 라이선스 | Mozilla Public License 2.0, 1.1.0 다음 버전부터([ADR-0028](adr/0028-open-source-under-mpl-2.0.md)) |

로더별 검증 범위, 실제 모드 묶음의 결과, 오류 코드는 [COMPATIBILITY.md](COMPATIBILITY.md)에 있습니다. 버전별 변경은 [CHANGELOG.md](../CHANGELOG.md), 설치·복구는 [INSTALL.md](INSTALL.md)에 있습니다. 초기 Phase별 결과는 [`docs/sprints/`](sprints/)에 기록으로 남아 있습니다.

## 빠른 시작 (Windows PowerShell)

필요한 것:
- JDK 25: 컴파일과 Minecraft 26.2 실행용. Gradle toolchain이 자동으로 찾습니다.
- Gradle을 실행할 JDK: 17 이상이면 됩니다. 검증은 JDK 25로 했습니다.
- Git
- 인터넷 연결: 첫 실행 때 Mojang에서 게임을 받습니다.

```powershell
git clone https://github.com/xyrz47-spec/Poly-Mod-Loader.git polymodloader
cd polymodloader
.\gradlew.bat check                              # 컴파일 + 포맷 검사 + 테스트
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest # 전용 서버를 Poly 모드 로더로 부팅 (EULA 미동의 상태에서 검증)
.\gradlew.bat :poly-bootstrap:runClient           # 클라이언트 실행 (run/client)
```

Linux/macOS에서는 `./gradlew`를 사용합니다.

- Minecraft 파일은 Poly 모드 로더가 재배포하지 않습니다. 빌드 시 Mojang 서버에서 받아 해시를 검증하고 `~/.gradle/caches/poly-minecraft`에 캐시합니다.
- 개발 실행은 오프라인 계정(`PolyDev`)을 사용합니다. 싱글플레이는 가능하지만 온라인 서버·Realms는 인증 오류가 납니다.
- `runServer`는 Minecraft EULA 동의가 필요합니다. Poly 모드 로더는 사용자를 대신해 동의하지 않습니다. 내용을 읽은 뒤 `run/server/eula.txt`에 `eula=true`를 직접 적으세요.

IntelliJ IDEA 사용법은 [`DEVELOPMENT_IN_INTELLIJ.md`](DEVELOPMENT_IN_INTELLIJ.md)를 보세요.

## 저장소 구조

| 경로 | 역할 |
|---|---|
| `poly-runtime-api` | 공개 API 기반: 안정성 등급 표시, `GameSide` |
| `poly-lifecycle-api` | 공개 API: 모드 엔트리포인트와 `ModContext` |
| `poly-event-api` | 공개 API: 이벤트 키·등록, 서버 생명주기 이벤트 |
| `poly-system-api` | 공개 API: 교체 가능한 시스템의 계약(`SystemProvider`, `GameBrandSystem`) |
| `poly-system-registry` | 시스템 정의, 교체 충돌 해결, 제공자 생성·검증 |
| `poly-mod-metadata` | `poly.mod.json` 파서·검증기, SemVer 범위 |
| `poly-transform-api` | 변환 규칙 선언(대상·동작·실패 정책·오류 코드) |
| `poly-transform-engine` | ASM 기반 변환 계획기·실행기 |
| `poly-version-adapter-api` | Minecraft 버전 어댑터 계약 |
| `poly-game-api` | 공개 API: 콘텐츠, 월드·엔티티 뷰, 명령어, 네트워크, 클라이언트, 게임 테스트 |
| `poly-version-adapter-v26_2` | Minecraft 26.2 어댑터(변환 규칙). 지원 버전마다 하나(ADR-0011): `v26_1`(26.1·26.1.1·26.1.2), 실험 버전 `v1_21_11` |
| `poly-game-v26_2` | Minecraft 26.2 게임 계층: Native API를 게임 클래스로 구현. 지원 버전마다 하나 |
| `poly-runtime-impl` | 로딩 파이프라인, 게임 클래스로더, 엔트리포인트 호출 |
| `poly-bridge-api` | 로더 브리지 SPI, 호환성 상태·발견 항목 |
| `poly-bridge-fabric` | Fabric·Quilt 모드 실행: 메타데이터, Fabric Loader·Quilt Loader API, Mixin, 진입점 |
| `poly-bridge-forge` | Forge·NeoForge를 Poly 모드 로더 안에서 실행: 패치된 게임 jar, FML 시작, 모드 목록 연결 |
| `poly-bootstrap` | JVM 진입점과 개발 실행 작업 |
| `poly-installer` | 플레이어용 설치 프로그램(`PolyModLoader-<버전>.jar`, `.exe`) |
| `examples/crystal-garden` | 튜토리얼 "첫 Poly 모드 만들기"의 완성본(독립 프로젝트, 게임 내 테스트 4개) |
| `examples/minimal-native-mod` | 가장 작은 Poly 모드 |
| `examples/custom-system-mod` | `poly:game_brand` 시스템을 교체하는 예제 |
| `examples/transform-demo-mod` | `poly.mod.json`으로 Minecraft 메서드를 변환하고(call-site 변경, 순서 선언 포함) 필드 접근을 넓히는 예제 |
| `examples/native-showcase-mod` | Native API 전 영역 예제와 게임 내 테스트 52개 |
| `build-logic` | Gradle 규약 플러그인, Minecraft 개발 환경, 모드 개발 플러그인(`dev.polymodloader.mod`, `poly-gradle-plugin`으로 발행) |
| `templates/poly-mod-template` | 새 Poly 모드 로더 모드 프로젝트 템플릿 |
| `docs/` | 설계서, ADR, 아키텍처, 호환성, API 카탈로그, 기여 안내(`contributing/`) |
| `site/` | 문서 웹사이트 (공개 저장소에는 없음) |

모듈 의존 방향과 실행 흐름은 [`ARCHITECTURE.md`](ARCHITECTURE.md)에 있습니다.

## 기여 규칙

기여 규칙은 [`contributing/`](contributing/index.md)에 있습니다.
