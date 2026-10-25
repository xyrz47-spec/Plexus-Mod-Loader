# Phase 4 결과 — IntelliJ SDK 경험과 Native API 확대

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 4
- 결정: [ADR-0008](../adr/0008-mod-sdk-distribution.md), [API_BOUNDARIES.md](../architecture/API_BOUNDARIES.md)

## 작업 항목

| 항목 | 상태 | 내용 |
|---|---|---|
| Gradle 템플릿과 예제 모드 | ✅ | `dev.polymodloader.mod` 플러그인, `templates/poly-mod-template`, 예제 3종(minimal, custom-system, transform-demo) |
| 콘텐츠·월드·엔티티·게임플레이 API | ⚠️ 부분 | 첫 게임 연동 API로 **서버 생명주기 이벤트**(`poly-event-api`: STARTED/TICK/STOPPING, `ServerView`)를 구현했고, 그 기반인 메서드 진입 후크 변환(`InjectStaticCallAtHead`)을 추가했습니다. 블록·아이템 등록, 월드·엔티티 API는 아직 없습니다 |
| 렌더링·UI·네트워크·저장 API의 경계 설계 | ✅ 설계 | [API_BOUNDARIES.md](../architecture/API_BOUNDARIES.md) |
| 자동 문서 생성 | ✅ | 모든 모듈이 JavaDoc jar를 만들고, `check`에서 doclint 오류를 실패로 처리합니다. 소스·JavaDoc jar를 Maven에 발행합니다 |
| 정적 검사, 테스트, 실행 구성 정리 | ✅ | `check` = 컴파일(`-Werror`) + Spotless + JavaDoc + 테스트. 저장소 안과 템플릿이 같은 실행 작업을 씁니다(`MinecraftRunTasks`) |

## 완료 기준

| 기준 | 상태 | 근거 |
|---|---|---|
| 개발자가 IntelliJ에서 새 모드 프로젝트를 만들고 빌드·실행·디버깅 | ⚠️ CLI 검증 / IntelliJ 사람 확인 필요 | 템플릿을 저장소 밖에 복사해 `build worldCreateSaveTest` 성공: 메타데이터 검증 → 컴파일 → 26.2 게임 테스트 서버에서 `[my_mod] Hello…`, `[my_mod] Server started at tick 0`. IntelliJ 실행 구성은 포함했지만, IntelliJ 화면에서 직접 확인하지는 않았습니다 |
| API 문서와 예제의 컴파일 가능성 검증 | ✅ | `check`가 JavaDoc을 생성하고, 예제 모드 3종과 템플릿이 실제로 컴파일·실행됩니다 |
| 모듈별 책임이 README와 아키텍처 맵으로 설명됨 | ✅ | 모든 모듈 README, `docs/ARCHITECTURE.md` |

## 실행한 검증 (2026-10-19)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | 성공. 테스트 92개 통과(`EventBusTest` 4개, `HeadInjectionTest` 3개 추가) |
| `serverBootSmokeTest`, `worldCreateSaveTest`, `clientWorldReloadTest` | 통과. 서버 시작·종료 이벤트가 게임 테스트 서버와 통합 서버에서 발생 |
| 템플릿 복사본에서 `build worldCreateSaveTest` | 성공 |

## 남은 제한
- 콘텐츠 등록(블록·아이템), 월드·엔티티·게임플레이 API는 구현하지 않았습니다. 26.2의 레지스트리 동결 시점과 데이터 생성 경로를 조사한 뒤 진행해야 하고, 그 범위는 이 Phase보다 큽니다.
- SDK 배포는 Maven Local 기준입니다.
