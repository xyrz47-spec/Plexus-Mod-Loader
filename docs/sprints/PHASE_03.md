# Phase 3 결과 — Transformation Engine PoC

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 3
- 결정: [ADR-0004](../adr/0004-transformation-engine.md), [ADR-0007](../adr/0007-transform-cache-and-mod-transforms.md)

## 작업 항목

| 항목 | 상태 | 구현 |
|---|---|---|
| 변환 계획, 클래스/메서드 검증, 변환 실행 | ✅ | `ClassTransformPlanner`(시작 전 계획, descriptor·사전조건·충돌·버전 검증), `ClassTransformExecutor`. 이번 Phase에서 모드 선언 변환(`transforms`)도 같은 경로로 처리 |
| 변환 결과 검사와 디버그 덤프 | ✅ | `CheckClassAdapter` 구조 검증, `-Dpoly.transformDumpDir` 전후 클래스 덤프 |
| 실패 원인과 적용 규칙 보고 | ✅ | `POLY-TRANSFORM-*` 오류 코드, `[Poly 모드 로더] Transformed …` 로그, `.poly/startup-report.json`(계획·거부), `.poly/transformation-report.json`(실제 적용·전후 해시·캐시 여부) |
| 캐시의 정확성과 무효화 검증 | ✅ | `DirectoryTransformCache` + `TransformCacheKey`. 적중, 규칙 변경·엔진 변경·원본 변경 시 무효화, 손상 감지, 반환 배열 독립성 테스트 |

## 완료 기준

| 기준 | 상태 | 근거 |
|---|---|---|
| 의도한 단일 클래스 변환의 전후 차이 검증 | ✅ | `ClassTransformPlannerTest`(변환된 클래스를 실제로 로드해 호출), `PolyRuntimeTest.modDeclaredTransformsAreAppliedAndCachedAcrossLaunches`, 실제 26.2: `LevelStorageSource` SHA-256 `3b7e4ea2… → 71145ad2…` |
| 잘못된 대상은 명확히 실패 | ✅ | descriptor 불일치 `POLY-TRANSFORM-002`(존재하는 overload 목록 포함), 클래스 누락 `-001`, 사전조건 불일치 `-003`. `abort_startup` 모드 규칙은 `TRANSFORMATION_PLANNING` 단계에서 시작 중단(`PolyRuntimeTest`) |
| 변환 실패가 조용히 성공으로 처리되지 않음 | ✅ | 거부된 규칙은 로그 WARN과 시작 보고서 `rejectedTransformations`에 기록되고, 적용 목록에는 나타나지 않음. 계획 후 바이트가 바뀌면 `TransformException` |

## 실행한 검증 (2026-10-19)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | 성공. 테스트 85개 통과, 실패·스킵 0 |
| `worldCreateSaveTest` 두 번 연속 | 첫 실행은 변환 후 캐시 저장, 두 번째는 `LevelStorageSource`·`MinecraftServer` 모두 `from cache`. 전후 해시 동일 |
| `serverBootSmokeTest`, `clientWorldReloadTest` | 통과(규칙 2개/3개 승인, 모드 규칙 1개) |

## 남은 제한

- 변환 동작은 상수 반환 교체와 정적 훅 호출 두 가지뿐입니다. 일반 후크(진입/종료, call-site)는 Fabric/Forge 브리지의 Mixin 의미 재현과 함께 설계합니다.
- 같은 메서드를 고치는 규칙끼리 협력하는 방법은 없습니다. 지금은 모두 거부합니다.
