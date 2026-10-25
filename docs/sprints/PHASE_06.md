# Phase 6 결과 — 혼합 실행 및 회귀 안정화 (1단계)

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 6, §15.4
- 실행: `.\gradlew.bat :poly-bootstrap:compatibilityMatrix` (게임 테스트 서버, 헤드리스, 시나리오마다 새 디렉터리)

## 혼합 실행 행렬 (Minecraft 26.2, Poly 모드 로더 0.1.0-alpha.1)

| 조합 | 모드 | 결과 | 상태 |
|---|---|---|---|
| Native 단독 | `minimal_native_mod` | 통과 | Verified (정의된 시나리오 범위) |
| Fabric 단독 | `fabric_hello`(엔트리포인트만 사용) | 통과 | Experimental |
| NeoForge 단독 | `blocked_example`(메타데이터만 있음) | 통과: 기능별 진단 후 미로드, 게임은 정상 | Blocked |
| Native + Fabric | 위 두 모드 | 통과 | Experimental |
| Native + Fabric + NeoForge | 위 세 모드 | 통과: NeoForge만 차단, 나머지 실행 | Partial |
| Forge 단독 / Fabric + Forge | — | 실행 시나리오 없음 | Untested (FML 미지원으로 Blocked 예상) |

## 회귀 시나리오

| 시나리오 | 기대 동작 | 결과 |
|---|---|---|
| `regressionTransformConflict` | 모드 규칙과 Poly 모드 로더 내장 규칙이 `MinecraftServer.getServerModName`을 함께 변환 → 둘 다 `POLY-TRANSFORM-004`로 거부(`disable_rule`), 다른 규칙과 모드는 정상 | 통과 |

## 완료 기준

| 기준 | 상태 |
|---|---|
| 지정된 조합의 통합 테스트 통과 | ✅ 위 5개 조합 + 회귀 1개 |
| 실패하는 조합의 원인·범위·상태 기록 | ✅ NeoForge·Forge는 `POLY-COMPAT-201..204`로 원인 기록, 상태 `Blocked`/`Untested` |
| 기능 범위가 확인되지 않은 모드를 완전 호환으로 표기하지 않음 | ✅ Fabric은 `Experimental`. `Verified`는 Native의 정의된 시나리오에만 사용 |

## 실행하지 않은 것
- 멀티플레이(전용 서버 + 클라이언트 접속): 전용 서버는 EULA 동의가 필요해 사람이 확인해야 합니다. 대신 통합 서버 접속(`clientWorldReloadTest`)은 검증했습니다.
- 리소스 리로드, 네트워크 동기화: 해당 API가 아직 없습니다.
- 실제 서드파티 Fabric/Forge 모드 혼합: 브리지 범위 밖입니다(ADR-0009).
