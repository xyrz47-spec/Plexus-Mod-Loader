# Poly 모드 로더 개발 현황과 소스 빌드

Poly 모드 로더를 소스에서 빌드하고 고치는 사람을 위한 문서입니다. 플레이어와 모드 제작자는 저장소의 [README](../README.md)와 문서 웹사이트를 보세요. 전체 목표와 설계는 [`PROJECT_PLAN_AND_DESIGN.md`](PROJECT_PLAN_AND_DESIGN.md)에 있습니다.

## 문서 사이트

모드 제작 안내와 API 레퍼런스는 문서 웹사이트 [www.polymc.net](https://www.polymc.net/docs/)에 있습니다. 웹사이트 소스(`site/`, Docusaurus)는 공개 저장소에 들어 있지 않습니다. API 레퍼런스는 `./gradlew apiReference`로 다시 만듭니다.

## 현재 상태 — 0.1.0-alpha.10 (알파) (Phase 0–7 1단계 + Native API 4단계)

| 항목 | 상태 |
|---|---|
| 대상 게임 | Minecraft 26.2 (Java 25) — [ADR-0001](adr/0001-target-minecraft-26-2.md). 버전 목록 하나로 여러 버전을 지원하는 구조([ADR-0011](adr/0011-multiple-minecraft-versions.md)) |
| 클라이언트 실행 | ✅ Poly 모드 로더를 거쳐 타이틀 화면까지 실행, 창 제목 `Minecraft* 26.2`(모드 적용 표시) |
| 전용 서버 | ✅ Poly 모드 로더를 거쳐 부팅, EULA 확인 단계까지 자동 검증(`serverBootSmokeTest`) |
| 월드 저장·재불러오기 | ✅ 게임 테스트 서버에서 생성·저장(`worldCreateSaveTest`), 클라이언트에서 다시 열기(`clientWorldReloadTest`) |
| 종료 생명주기 | ✅ `ModContext.onShutdown` 작업을 등록 역순으로 실행 |
| Poly 모드 | ✅ `poly.mod.json` 검증, 의존성 해석, common/client/server 엔트리포인트 |
| 클래스 변환 | ✅ 사전 계획·descriptor/바이트코드 사전조건 검증·충돌 거부·전후 해시 보고, 모드 선언 변환(`transforms`), 무효화·손상 감지·크기 상한이 있는 변환 캐시 ([ADR-0007](adr/0007-transform-cache-and-mod-transforms.md)). 시작·반환 훅, call-site 변경, 접근 확대, 같은 메서드 규칙의 순서 계약(`after`) ([ADR-0013](adr/0013-transform-order-callsite-access.md)) |
| 시스템 교체 | ✅ `poly:game_brand`를 모드가 교체하고, 충돌을 시작 전에 진단합니다 ([ADR-0006](adr/0006-system-replacement.md)) |
| 진단 보고서 | ✅ `.poly/startup-report.json`(모드, 차단 파일, 활성 시스템, 변환 계획), `.poly/transformation-report.json`(실제 변환, 전후 해시, 캐시 여부) |
| 이벤트 API | ✅ 서버 생명주기(STARTED/TICK/STOPPING), 플레이어 접속·퇴장, 블록 파괴, 클라이언트 틱 |
| Native 콘텐츠 API | ✅ 블록·아이템·음식·창작 탭 등록, 모드 jar의 `assets/`·`data/`를 리소스·데이터 팩으로 자동 추가(레시피·전리품·태그·모델·번역) ([NATIVE_API.md](NATIVE_API.md)) |
| 월드·명령어·네트워크·클라이언트 API | ✅ 월드·플레이어 뷰, Brigadier 기반 명령어, 페이로드 채널, 키 바인딩, HUD 레이어 ([ADR-0010](adr/0010-native-api-game-layer.md)) |
| 블록 상태·블록 엔티티·인벤토리 | ✅ 상태 속성, 저장되는 블록 데이터와 틱, 상자형 인벤토리, 월드 저장소, 작업 예약 |
| 몹·효과·규칙·사운드 | ✅ AI 몹·생성 알·렌더러, 상태 효과, 게임 규칙, 사운드·파티클, 피해 취소·사망 이벤트 |
| 월드 생성·차원 | ✅ 모드 데이터 팩의 월드 생성·차원·피해 유형을 "실험적" 판정 없이 사용, 바이옴 기능 추가, 차원 이동 |
| 클라이언트 화면 | ✅ 모드 화면(라벨·버튼·입력칸), 키 바인딩, HUD |
| 콘텐츠 심화 | ✅ 이름 속성, 상태별 빛, 칸 배치 메뉴, 아이템 데이터 컴포넌트, 투사체, 자연 스폰 |
| 게임플레이 | ✅ AI 목표, 이동·탑승, 나는 몹·헤엄치는 몹, 블록 모양·충돌, 키프레임 애니메이션, 피해량 수정, 발전 과제 |
| 코드 월드 생성 | ✅ 코드 바이옴·지형 생성기, 코드 배치 기능·구조물, 노이즈 지형의 코드 density function, 청크 강제 로드·이벤트 |
| 클라이언트 그리기 | ✅ 몹 상자 모델, 블록 엔티티 그리기(상태별 블록 모델 포함), 월드에 선·상자·글자 그리기, 모드 코어 셰이더 파이프라인(텍스처·모드 유니폼), 파티클, 시야각·시점, 후처리 셰이더, 동적 텍스처 |
| 리소스·동기화·운영 | ✅ 리로드 리스너, 엔티티 데이터, 저장소 버전·동기화, 권한, 접속 이벤트, 접속 준비 단계 교환, 모드 변경 시 월드 백업 |
| 게임 내 테스트 | ✅ 모드가 등록한 테스트를 Mojang 게임 테스트 서버에서 실행(`gameTests`, `nativeApiGameTests` 52개 통과) |
| 모드 개발 SDK | ✅ `dev.polymodloader.mod` Gradle 플러그인 + [`templates/poly-mod-template`](../templates/poly-mod-template). 명령 한 줄로 새 모드(`newPolyMod`), 블록·아이템 리소스 JSON 자동 생성(`polyResources`), 게임 실행 전 리소스 실수 검사(`checkPolyResources`) |
| Fabric 모드 | 🧪 엔트리포인트와 Fabric Loader API(`FabricLoader.getInstance()`, [ADR-0016](adr/0016-fabric-loader-api.md))를 쓰는 모드는 `Experimental`로 실행하고, Poly 모드·Fabric 모드끼리 서로 의존할 수 있습니다. 중첩 jar(jar-in-jar)도 풀어서 판정합니다([ADR-0015](adr/0015-fabric-nested-jars.md)). Mixin·MixinExtras, class tweaker의 `inject-interface`, `@Environment` 환경 제거를 Fabric Loader와 같은 순서로 적용하고, 진입점을 Fabric Loader와 같은 게임 내 지점에서 실행합니다([ADR-0020](adr/0020-fabric-mixin-and-runtime-fidelity.md)). class tweaker의 `extend-enum` 등은 기능별 진단 후 차단 ([ADR-0009](adr/0009-loader-bridges.md), [ADR-0014](adr/0014-cross-loader-dependencies-and-link-check.md)) |
| 혼합 실행 행렬 | ✅ Native·Fabric·NeoForge 조합 5종, 로더를 넘는 의존·차단 연쇄·망가진 파일, 변환 충돌 회귀를 `compatibilityMatrix`로 자동 검증 ([PHASE_06](sprints/PHASE_06.md)) |
| 시작 전 안전 판정 | ✅ 의존성 연쇄 판정과 바이트코드 링크 검사로, 실행할 수 없는 외부 모드를 게임 코드가 돌기 전에 이유와 함께 제외. 실제 인기 모드 124개(Fabric·NeoForge·Forge)와 그 중첩 모드에서 판정 예외·크래시 0건. Mixin 지원 뒤 함께 로드 가능한 직접 설치 Fabric 모드는 52개 중 클라이언트 42개·전용 서버 28개이고, 99개가 실제 전용 서버 시작을 통과합니다. NeoForge·Forge 모드는 FML 런타임이 필요해 아직 막힘 ([COMPATIBILITY.md](COMPATIBILITY.md)) |
| NeoForge 모드 | ⛔ `Blocked`. 필요한 FML 기능을 진단 |
| Forge 모드 | ⛔ `Blocked`. 필요한 FML 기능을 진단 |
| 성능 | 시작 시간(바닐라 7.14초 대 Poly 모드 로더 7.23초)과 같은 부하의 서버 틱 시간(p95 22.1ms 대 21.6ms)·힙·GC 모두 차이가 실행 간 편차 안에 있어 우위도 저하도 주장하지 않습니다. 클라이언트 프레임 시간은 개발용 모드를 함께 넣은 Poly 모드 로더 쪽이 p50 0.14ms 깁니다(0.66 대 0.80ms, 측정된 저하. 로더와 모드의 몫은 아직 나누지 않음) ([PERFORMANCE.md](PERFORMANCE.md)) |
| 설치 프로그램 | ✅ `PolyModLoader-<버전>.jar`/`.exe`가 Minecraft Launcher에 프로필을 추가하고, 런처와 같은 방식의 실행을 `launcherProfileTest`로 검증 ([ADR-0017](adr/0017-launcher-profile-installation.md)) |
| 설치·복구 | [INSTALL.md](INSTALL.md), [CHANGELOG.md](../CHANGELOG.md) |

Phase별 결과와 알려진 제한은 [`docs/sprints/`](sprints/)에 정리돼 있습니다.

## 빠른 시작 (Windows PowerShell)

필요한 것:
- JDK 25: 컴파일과 Minecraft 26.2 실행용. Gradle toolchain이 자동으로 찾습니다.
- Gradle을 실행할 JDK: 17 이상이면 됩니다. 검증은 JDK 25로 했습니다.
- Git
- 인터넷 연결: 첫 실행 때 Mojang에서 게임을 받습니다.

```powershell
git clone <this repository> polymodloader
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
| `poly-version-adapter-v26_2` | Minecraft 26.2 어댑터(변환 규칙). 지원 버전마다 하나(ADR-0011) |
| `poly-game-v26_2` | Minecraft 26.2 게임 계층: Native API를 게임 클래스로 구현. 지원 버전마다 하나 |
| `poly-runtime-impl` | 로딩 파이프라인, 게임 클래스로더, 엔트리포인트 호출 |
| `poly-bridge-api` | 로더 브리지 SPI, 호환성 상태·발견 항목 |
| `poly-bridge-fabric` | Fabric 메타데이터 분석, 엔트리포인트 실행 |
| `poly-bridge-forge` | NeoForge·MinecraftForge 메타데이터 분석(진단 전용) |
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
