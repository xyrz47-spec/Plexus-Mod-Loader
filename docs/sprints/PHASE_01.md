# Phase 1 결과 — Bootstrap 및 Runtime Core

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 1
- 이전 작업: [SPRINT_01.md](SPRINT_01.md)(Phase 0 + 첫 스프린트)

## 작업 항목

| 항목 | 상태 | 구현 |
|---|---|---|
| 런타임 진입점 | ✅ | `PolyLauncherMain` → `PolyRuntime`. 실행 대상은 `GameLaunchTarget`(`client`, `dedicated_server`, `game_test_server`) |
| 로깅과 진단 | ✅ | 단계별 `[Poly 모드 로더] Phase …` 로그와 오류 코드 체계(`docs/ARCHITECTURE.md`) |
| 모드 검색과 메타데이터 검증 | ✅ | `ModFileScanner`, `ModMetadataReader` |
| 의존성 해석 | ✅ | `ModDependencyResolver` — 누락, 버전, 순환, 중복, 충돌, 환경, 게임 버전 |
| 초기화/종료 생명주기 | ✅ | 엔트리포인트 순서, `ModContext.onShutdown` + `ShutdownCoordinator`(등록 역순, 실패 격리, 1회 실행) |
| 최소 Poly 모드 로더 Native 샘플 모드 실행 | ✅ | `examples/minimal-native-mod`(common, client, 종료 작업) |

## 완료 기준

| 기준 | 상태 | 근거 |
|---|---|---|
| Poly 모드 로더 샘플 모드가 선택한 게임 버전에서 동작 | ✅ | 26.2 클라이언트, 전용 서버, 게임 테스트 서버에서 엔트리포인트와 종료 작업 로그 확인 |
| 누락 의존성·중복 ID·잘못된 메타데이터가 구체적인 오류로 보고 | ✅ | `ModDependencyResolverTest`, `ModMetadataReaderTest`, `PolyRuntimeTest`(실제 jar로 파이프라인 실행: `POLY-DEPENDENCY-003`, `POLY-METADATA-002`, `POLY-BOOTSTRAP-002`) |
| 게임 월드를 저장하고 다시 불러오는 기본 통합 테스트 통과 | ✅ | `worldCreateSaveTest` + `clientWorldReloadTest` (아래) |

## 월드 저장·재불러오기 테스트 설계

전용 서버로 월드를 만들려면 Minecraft EULA 동의가 필요하고, Poly 모드 로더는 사용자 대신 동의하지 않습니다. 대신 두 단계로 나눴습니다.

1. **`worldCreateSaveTest`(헤드리스)**
   - Poly 모드 로더로 Mojang의 공식 게임 테스트 서버 `net.minecraft.gametest.Main`(server jar에 포함)을 실행합니다. 이 도구는 Mojang이 CI용으로 제공하며 `eula.txt`를 확인하지 않습니다.
   - 월드를 만들고, 내장 GameTest `minecraft:always_pass`를 실행한 뒤 정상 종료하며 저장합니다.
   - 검증 항목: `All 1 required tests passed`, `ThreadedAnvilChunkStorage: All dimensions are saved`, `poly:server_brand` 변환 적용, 종료 작업 실행, 그리고 `level.dat`·`world_gen_settings.dat`·오버월드 `region/`의 존재
2. **`clientWorldReloadTest`(창이 열림)**
   - 1단계에서 저장된 월드를 새 클라이언트 디렉터리의 `saves/poly_roundtrip`으로 복사합니다.
   - Poly 모드 로더로 클라이언트를 `--quickPlaySingleplayer poly_roundtrip`으로 실행합니다. Quick Play는 존재하는 월드만 열 수 있으므로(26.2 `QuickPlay.joinSingleplayerWorld`가 `levelExists` 확인), 이 테스트는 디스크에서 다시 읽기를 증명합니다.
   - `PolyDev joined the game`이 나오면 통과하고 클라이언트를 종료합니다.

## 실행한 검증 (2026-10-19)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | 성공, 테스트 51개 통과(Phase 0 대비 `ShutdownCoordinatorTest` 4개 추가) |
| `serverBootSmokeTest` | 통과(로그 10줄, 종료 작업 포함) |
| `worldCreateSaveTest` | 통과(로그 7줄, 파일 3개). GameTest 1개 통과, 세 차원 저장 |
| `clientWorldReloadTest` | 통과. 통합 서버가 저장된 월드를 열었고(`Loading 48 chunks for player spawn`), `PolyDev joined the game` 확인 |

## 작업 중 발견한 문제

- **Quick Play가 멈춤:** 새 게임 디렉터리에서는 첫 실행 접근성 안내 화면(`onboardAccessibility:true`)이 Quick Play를 막았습니다. 재불러오기 테스트 디렉터리에는 `onboardAccessibility:false`를 미리 써 둡니다.
- **종료 작업 중 로그 유실 위험:** log4j의 자체 종료 훅이 Poly 모드 로더 종료 작업보다 먼저 로깅을 끌 수 있습니다. 개발용 log4j 설정에 `shutdownHook="disable"`을 지정했습니다. 실제 배포 런처의 log4j 설정에서는 아직 확인하지 않았습니다.

## 남은 제한

- 종료 작업은 JVM 종료 시점에만 연결돼 있습니다. "월드 닫힘"·"서버 정지" 같은 게임 내부 종료 시점에 맞춘 생명주기 이벤트는 Native API 확대(Phase 4) 때 다룹니다.
- 전용 서버에서 월드를 실제로 여는 것은 사용자가 EULA에 동의해야 확인할 수 있습니다(`runServer`).
- `clientWorldReloadTest`는 그래픽 환경이 필요해 CI 서버에서 바로 돌리기 어렵습니다.
