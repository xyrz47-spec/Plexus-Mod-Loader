# 모듈 구성

모드는 공개 API 모듈에만 의존합니다. 모드 플러그인 `dev.polymodloader.mod`가 `poly-lifecycle-api`를 `compileOnly`로 붙이며, 이 모듈이 이벤트·시스템·게임 API를 함께 가져옵니다. 실행할 때는 Poly 모드 로더와 Minecraft가 제공합니다.

## 공개 API (모드가 의존)

| 모듈 | 내용 |
|---|---|
| `poly-runtime-api` | 공개 API 기반: 안정성 등급 표시(`@ApiStability`), `GameSide` |
| `poly-lifecycle-api` | 모드 엔트리포인트와 `ModContext` |
| `poly-event-api` | 이벤트 키·등록, 서버 생명주기 이벤트 |
| `poly-system-api` | 교체 가능한 시스템의 계약(`SystemProvider`, `GameBrandSystem`) |
| `poly-game-api` | Native API: 콘텐츠, 월드·엔티티 뷰, 명령어, 네트워크, 클라이언트, 게임 테스트(게임 타입 없음) |

## 버전별 구현 (Minecraft 26.2)

| 모듈 | 내용 |
|---|---|
| <code>poly-version-adapter-v26_2</code> | Minecraft 26.2 버전 어댑터: 진입점과 내장 변환 규칙 |
| <code>poly-game-v26_2</code> | Minecraft 26.2 게임 계층: Native API를 게임 클래스로 구현 |

Minecraft 이름은 이 버전별 모듈에만 있습니다. 다른 Minecraft 버전 지원은 버전별 모듈을 추가하는 방식입니다. 진행 상황은 [새 Minecraft 버전 지원하기](../contributing/new-minecraft-version.md)에 있습니다.

버전별 모듈의 이름은 ADR-0011의 규칙을 따릅니다.

| 모듈 이름 | 내용 |
|---|---|
| `poly-version-adapter-v<id>` | 그 버전의 실행 대상과 변환 규칙 |
| `poly-game-v<id>` | 그 버전의 게임 클래스로 구현한 Native API |

- `<id>`는 Minecraft 버전의 `.`과 `-`를 `_`로 바꾼 값입니다(예: `26.2` → `26_2`). 패키지와 클래스 이름도 같은 규칙을 따르므로, 빌드가 이름만으로 모듈과 클래스를 찾습니다.
- 지원 버전 목록은 저장소의 `gradle/minecraft-versions.properties` 한 곳에 있고, `settings.gradle.kts`가 목록의 버전마다 두 모듈을 포함합니다. 목록에 있는데 모듈 디렉터리가 없으면, 만드는 명령을 알려 주고 실패합니다.
- `poly-bootstrap`은 모든 버전의 어댑터를 담고, 실행할 때 게임 jar의 버전으로 어댑터를 고릅니다.

구조 전체는 [아키텍처](../ARCHITECTURE.md)의 "여러 Minecraft 버전" 절에 있습니다.

## 내부 구현 (모드가 의존하면 안 됨)

| 모듈 | 내용 |
|---|---|
| `poly-system-registry` | 시스템 정의, 교체 충돌 해결, 제공자 생성·검증 |
| `poly-mod-metadata` | `poly.mod.json` 파서·검증기, SemVer 범위 |
| `poly-transform-api` | 변환 규칙 선언(대상·동작·실패 정책·오류 코드) |
| `poly-transform-engine` | ASM 기반 변환 계획기·실행기 |
| `poly-version-adapter-api` | Minecraft 버전 어댑터 계약 |
| `poly-runtime-impl` | 로딩 파이프라인, 게임 클래스로더, 엔트리포인트 호출 |
| `poly-bridge-api` | 로더 브리지 SPI, 호환성 상태·발견 항목 |
| `poly-bridge-fabric` | Fabric 메타데이터 분석, 엔트리포인트 실행 |
| `poly-bridge-forge` | NeoForge·MinecraftForge 메타데이터 분석(진단 전용) |
| `poly-bootstrap` | JVM 진입점과 개발 실행 작업 |
| `poly-installer` | 플레이어용 설치 프로그램(`PolyModLoader-<버전>.jar`, `.exe`). Minecraft Launcher 프로필을 씁니다(ADR-0017) |

`GameClassLoader`는 내부 패키지를 모드 쪽 로더에서 정의하지 않고 항상 부모 로더에서 가져옵니다.

## 개발 도구와 예제

| 경로 | 내용 |
|---|---|
| `build-logic` | Gradle 규약 플러그인, Minecraft 개발 환경, 모드 개발 플러그인(`dev.polymodloader.mod`, `poly-gradle-plugin`으로 발행) |
| `templates/poly-mod-template` | 새 모드 프로젝트 템플릿 |
| `examples/minimal-native-mod` | 가장 작은 Poly 모드(엔트리포인트 호출만 로그로 남김) |
| `examples/native-showcase-mod` | Native API 전 영역 예제와 게임 내 테스트 |
| `examples/custom-system-mod` | `poly:game_brand` 시스템을 교체하는 예제 |
| `examples/transform-demo-mod` | `poly.mod.json`만으로 Minecraft 메서드를 변환하는 예제 |
| `tools/api-reference` (`tools:api-reference`) | 공개 API 모듈(`poly-runtime-api`, `poly-system-api`, `poly-event-api`, `poly-game-api`, `poly-lifecycle-api`)의 Javadoc에서 이 사이트의 [API 레퍼런스](../api-reference/index.md) 페이지를 만드는 빌드 도구. `./gradlew apiReference`로 실행합니다. 발행하지 않으며 Poly 모드 로더에 포함되지 않습니다 |

## 발행 좌표

모든 Poly 모드 로더 모듈은 `dev.polymodloader:<모듈명>:<버전>`으로 발행되며(빌드 도구 `tools:api-reference`는 제외), 소스 jar와 JavaDoc jar를 함께 발행합니다. 공개 저장소가 정해지기 전까지는 Maven Local을 씁니다.
