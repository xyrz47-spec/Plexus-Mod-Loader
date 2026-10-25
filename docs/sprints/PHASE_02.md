# Phase 2 결과 — 시스템 계약 및 첫 교체 지점

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 2
- 설계: [ADR-0006](../adr/0006-system-replacement.md), [SYSTEM_REPLACEMENT_DEMO.md](../architecture/SYSTEM_REPLACEMENT_DEMO.md)

## 작업 항목

| 항목 | 상태 | 구현 |
|---|---|---|
| `SystemRegistry`와 시스템 수명 관리 | ✅ | `poly-system-registry`: `SystemDefinition`, `BuiltInSystems`, `SystemRegistry`(생성·검증·조회) |
| 기본 구현체와 대체 구현체 계약 | ✅ | `poly-system-api`: `SystemId`, `PolySystem`, `SystemProvider<T>`, `SystemContext`, `SystemLookup`, `GameBrandSystem` |
| 교체 우선순위·충돌·의존성 규칙 | ✅ | `SystemReplacementResolver`: 단일 교체, 충돌(`POLY-SYSTEM-001`), 의존 기반 `override`, 잘못된 override(`POLY-SYSTEM-005`) |
| 대표 시스템의 대체 구현 PoC | ✅ | `poly:game_brand` + `examples/custom-system-mod` |
| 진단 도구와 테스트 샘플 | ✅ | 단계 로그 `[Poly 모드 로더] System …`, `.poly/startup-report.json`(모드·차단 파일·활성 시스템·변환 계획) |

## 완료 기준

| 기준 | 상태 | 근거 |
|---|---|---|
| 기본 구현과 대체 구현의 차이를 테스트로 확인 | ✅ | `SystemRegistryTest`(기본 `poly` 대 교체 `test/brand`), `PolyRuntimeTest.aModReplacingTheBrandSystemChangesWhatTheGameSees`(합성 게임 `main`이 교체 결과를 봄), 실제 26.2에서 `poly:game_brand reported 'poly/example' to Minecraft` (클라이언트·서버) |
| 두 모드가 같은 교체 지점을 요구하면 자동 진단 | ✅ | `SystemReplacementResolverTest`, `PolyRuntimeTest.twoModsReplacingTheSameSystemStopTheLaunchBeforeAnyModCodeRuns`(`CONFLICT_RESOLUTION` 단계에서 `POLY-SYSTEM-001`, 게임 미실행) |
| 모드 코드에서 게임 내부 클래스 직접 참조 없이 교체 시나리오 작성 | ✅ | `ExampleBrandProvider`는 `poly-system-api`만 `compileOnly`로 의존 |

## 실행한 검증 (2026-10-19)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | 성공. 테스트 72개 통과, 실패·스킵 0 |
| `serverBootSmokeTest` | 통과(로그 14줄). 시스템 교체와 시작 보고서 확인 |
| `worldCreateSaveTest` | 통과. 게임 테스트 서버가 교체된 브랜드 `poly/example`을 받음 |
| `clientWorldReloadTest` | 통과. 클라이언트가 교체된 브랜드 `poly/example`을 받음, 저장 월드 재진입 |

## 함께 바뀐 것

- 새 변환 동작 `ReplaceConstantReturnWithStaticCall`: 상수 반환을 정적 훅 호출로 바꿉니다. 사전조건은 기존 동작과 같고, 스택 깊이가 같아 프레임을 다시 계산할 필요가 없습니다.
- 26.2 브랜드 규칙은 이제 상수를 바꾸지 않고 `Minecraft26_2Hooks`를 호출합니다.
- `ModContext.systems()`로 모드가 활성 시스템을 조회할 수 있습니다.
- 메타데이터 오류 코드 `POLY-METADATA-012`(권한 누락), `-013`(알 수 없는 권한, 경고), `-014`(잘못된 교체 선언)를 추가했습니다.

## 남은 제한

- 시스템은 아직 하나(`poly:game_brand`)뿐입니다. 상태를 가진 시스템(레시피·월드 생성 등)의 리로드·동기화·저장 계약은 Phase 4에서 다룹니다.
- 시스템 확장(`EXTEND`)과 부분 교체는 지원하지 않습니다.
