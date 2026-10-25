# Native API 1단계 결과

- 완료일: 2026-10-19
- 기준: 설계서 §5.1 Level 1–2, §5.3 D·E·F·G·H·I·J 중 기본 경로, §5.9 API 완료 기준
- 결정: [ADR-0010](../adr/0010-native-api-game-layer.md), 사용법: [NATIVE_API.md](../NATIVE_API.md)

## 범위
일반적인 Native 모드 하나가 Minecraft 타입 없이 다음을 할 수 있게 하는 것을 목표로 잡았습니다.
- 콘텐츠·데이터 추가
- 상호작용
- 명령어
- 클라이언트·서버 통신
- 클라이언트 UI 요소
- 자동 테스트

§5.3의 전체 카탈로그(수백 항목)를 이번에 완성하지는 않았습니다. 남은 항목은 NATIVE_API.md의 "아직 없는 것"에 적었습니다.

## §5.9 완료 기준 대비

| 기준 | 상태 |
|---|---|
| 공개 계약과 안정성 등급 | ✅ 모든 타입 `@ApiStability(PREVIEW)`, API_CATALOG에 기재 |
| JavaDoc(목적·시점·스레드·실패) | ✅ `javadoc`이 `check`에 포함(doclint) |
| 허용 환경과 스레드 규칙 | ✅ JavaDoc과 카탈로그 표 |
| 정상·오류 경로 단위 테스트 | ✅ `NativeRegistrationsTest` 8개, `HookActionsTest` 6개 |
| 최소 Native 샘플 모드 | ✅ `examples/native-showcase-mod`, 템플릿의 아이템·게임 테스트 |
| IntelliJ 자동완성·소스 탐색 | ⚠️ 사용자 확인 필요(`docs/DEVELOPMENT_IN_INTELLIJ.md`) |
| 버전별 지원 매트릭스 | ✅ 26.2 단일 버전(ADR-0010 연결 지점 표) |
| 저수준 API: 대상 시그니처·충돌 테스트·진단 | ✅ 규칙 14개를 javap로 확인, 사전조건 거부 테스트, `ABORT_STARTUP` |
| 마이그레이션 기록 | ✅ 첫 버전이라 해당 없음, CHANGELOG에 기록 |

## 실행한 검증 (2026-10-19, 실제 Minecraft 26.2)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | 성공. 테스트 112개 통과(14개 추가) |
| `:poly-bootstrap:nativeApiGameTests` | **게임 테스트 9개 통과**. 아래 9가지를 게임 안에서 확인 |
| `:poly-bootstrap:clientWorldReloadTest` | 통과. 아래 6가지를 클라이언트에서 확인 |
| `serverBootSmokeTest`, `worldCreateSaveTest`, `compatibilityMatrix`(6개) | 통과. 전용 서버에서 변환 규칙 14개 수락 |
| 템플릿 복사본(Maven Local)에서 `build gameTests -PpolyTests=my_mod:*` | 성공. 외부 프로젝트에서 게임 계층을 찾고, 모드 테스트 1개 통과 |

`nativeApiGameTests`가 게임 안에서 확인한 것:
- 콘텐츠 등록, 블록 배치
- 블록 사용 시 아이템 지급(모의 플레이어)
- 음식 아이템 지급
- 월드 뷰 읽기·쓰기
- 명령어 결과값(`/showcase add 2 3` = 5)
- 모드 jar의 전리품 테이블(`/loot`)과 태그(`execute if block … #minecraft:mineable/pickaxe`)
- 지연 검증

`clientWorldReloadTest`가 클라이언트에서 확인한 것:
- 키 바인딩 추가
- 리소스 팩 `poly/native_showcase` 로드
- 플레이어 접속 이벤트
- 서버 → 클라이언트 → 서버 페이로드 왕복
- HUD 레이어(427×240)

## 발견하고 고친 문제
1. **생성한 테스트 인스턴스 데이터 때문에 클라이언트가 월드를 열지 못했습니다.**
   - 원인: 알려진 팩 정보가 없는 팩이 데이터 팩 레지스트리 항목을 넣어, 월드가 "실험적 설정"으로 판정됐습니다. 그래서 확인 화면에서 멈췄습니다.
   - 조치: 생성 팩은 게임 테스트 서버에서만 만듭니다.
2. **템플릿 프로젝트가 `net.fabricmc:fabric-loader`를 찾지 못했습니다.**
   - Phase 5부터 있던 문제입니다.
   - 조치: 모드 플러그인이 `net.fabricmc` 그룹에만 FabricMC 저장소를 씁니다.

## 남은 제한
- **API 범위:** 블록 상태 속성, 블록 엔티티, 엔티티 타입, 인벤토리·화면, 월드 생성, 사운드 등록은 없습니다.
- **멀티플레이 검증:** 전용 서버와 원격 클라이언트 사이의 멀티플레이는 EULA 동의가 필요해 검증하지 않았습니다.
- **키 바인딩 구현:** final 필드를 반사로 교체하는 방식입니다(ADR-0010).

## 2단계 묶음 A — 블록 상태·블록 엔티티·인벤토리·저장 (2026-10-19)

| 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **15개 통과**(6개 추가) |
| `worldCreateSaveTest` → `clientWorldReloadTest` | 월드 저장소 값이 저장·재불러오기 후에도 남음(`load count 1` → `2`) |
| `NbtTest` | `DataTree` ↔ NBT 왕복(모든 값 타입, 중첩) |
| 변환 규칙 | 15개 수락(`poly:level_tick` 추가) |

새로 추가한 6개 테스트가 확인한 것:
- 속성 읽기·쓰기와 잘못된 속성 거부
- 바닐라 화로의 `facing` 읽기
- 사용 시 상태 전환과 블록 엔티티 틱 누적
- 27칸 인벤토리에 70개 분할 저장
- 월드 저장소
- 예약 작업이 정확히 1번, 일찍 실행되지 않음

## 2단계 묶음 B — 몹·효과·게임 규칙·사운드·파티클·피해/사망 이벤트 (2026-10-19)

| 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **22개 통과**(7개 추가) |
| `clientWorldReloadTest` | 모드 몹 렌더러 등록, 접속 시 생성된 골렘 친구 렌더링 중 오류 없음 |
| `HookActionsTest` | 원시형 반환 메서드의 조기 반환(박싱 해제) 추가, JVM 검증 통과 |
| 변환 규칙 | 서버 19개 수락(속성 2, 피해, 사망 추가), 클라이언트 렌더러 규칙 1개 |

새로 추가한 7개 테스트가 확인한 것:
- `/summon`으로 모드 몹 생성
- 몹 속성(최대 체력 20)
- 피해 취소 이벤트(500 피해 취소, 5 피해는 적용)
- 사망 이벤트가 정확히 1번 발생
- 모드 효과가 체력을 회복
- 불·정수 게임 규칙의 기본값과 `/gamerule` 변경
- 생성 알 아이템, 사운드, 단순 파티클, 옵션이 필요한 파티클 거부

발견하고 고친 문제:
- **속성 홀더 바인딩:** `freeze()` 직전에는 속성 홀더가 아직 바인딩되지 않습니다. 그래서 몹 속성 표는 처음 요청될 때 만듭니다.
- **유인 목표 속성:** 유인 목표에는 `tempt_range` 속성이 필요해 추가했습니다.

## 2단계 묶음 C — 데이터 레지스트리·월드 생성·차원 (2026-10-19)

| 검증 | 결과 |
|---|---|
| `nativeApiGameTests` | **25개 통과**(3개 추가) |
| `clientWorldReloadTest` | 모드 데이터 차원 `native_showcase:pocket`이 실제 클라이언트 월드에 로드됨. 모드 데이터 팩의 레지스트리 항목이 있어도 "실험적 설정" 화면 없이 열림 |
| 변환 규칙 | 서버 21개 수락(`poly:worldgen_features`, `poly:mod_dimension_stability` 추가) |
| 기능 추가 로그 | `Added 1 feature(s) to 55 biome(s)`(`#minecraft:is_overworld`) |

새로 추가한 3개 테스트가 확인한 것:
- 데이터 피해 유형 `native_showcase:zap`을 이벤트가 알아봄
- 모드 데이터의 설정 기능을 `/place feature`로 배치
- 네더로 차원 이동, 모르는 차원은 비어 있음

발견하고 고친 문제:
- **차원 이동 결과:** 엔티티는 다른 차원으로 옮길 때 새 객체로 바뀝니다. 그래서 `TeleportTransition`이 돌려주는 엔티티로 뷰를 만듭니다.
- **실험적 판정:** 26.2는 바닐라가 아닌 차원을 실험적으로 판정합니다. 모드 차원만 안정으로 판정하는 규칙을 추가했습니다.

## 2단계 묶음 D — 클라이언트 화면·설정 디렉터리 (2026-10-19)

| 검증 | 결과 |
|---|---|
| `clientWorldReloadTest` | 모드 화면이 열리고(427×240) 그려지며, 40틱 뒤 닫힐 때 입력칸 값 `PolyDev`를 읽음. 설정 파일 생성 |
| `check` | 단위 테스트 114개 통과 |
| 전체 실제 게임 검증 | `nativeApiGameTests` 25/25, `serverBootSmokeTest`, `worldCreateSaveTest`, `clientWorldReloadTest`, `compatibilityMatrix` 6/6 |
| 템플릿 외부 프로젝트(Maven Local) | `build gameTests` 성공 |

발견하고 고친 문제: `setScreen(null)`로 닫으면 게임은 `onClose`가 아니라 `removed`를 부릅니다. 그래서 닫힘 콜백을 `removed`에 연결했습니다.
