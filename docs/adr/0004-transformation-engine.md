# ADR-0004: 변환 엔진 — 선언형 규칙, 사전 계획, ASM 트리 API, 닫힌 동작 집합

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §5.7, §12, §16 Phase 3, §21-13

## 결정

1. **규칙은 데이터입니다.** `TransformRuleDefinition`에는 다음이 담깁니다.
   - 규칙 ID(`namespace:path`)와 소유 모드
   - 정확한 게임 버전과 적용 측(side)
   - `MethodTarget`(owner 내부 이름, 메서드 이름, **JVM descriptor**)
   - 동작과 실패 정책
2. **계획은 게임 시작 전에 확정합니다.** `ClassTransformPlanner`가 아래를 순서대로 검사합니다. 하나라도 실패한 규칙은 `TransformProblem`으로 거부되며, 일부만 적용되는 일은 없습니다.
   1. 규칙 ID 중복
   2. 게임 버전 일치
   3. 같은 메서드를 고치는 다른 규칙과의 충돌
   4. 대상 클래스 존재
   5. 정확한 name+descriptor 일치
   6. 동작별 **바이트코드 사전조건**
3. **충돌은 추측하지 않습니다.** 순서·협력 계약이 아직 없으므로, 같은 메서드를 고치는 규칙이 둘 이상이면 모두 거부합니다(`POLY-TRANSFORM-004`).
4. **동작은 닫힌 집합입니다.** `MethodTransformAction`은 `sealed`입니다. 동작마다 엔진에 검토된 구현과 사전조건이 하나씩 있습니다. 첫 동작은 `ReplaceReturnedStringConstant`이고, 본문이 정확히 `LDC <expected>; ARETURN`일 때만 적용됩니다.
5. **ASM 9.11.1 트리 API**로 변환합니다.
   - 쓰기는 `ClassWriter(reader, 0)`로 원본 상수 풀을 복사하고, 프레임을 다시 계산하지 않습니다. `COMPUTE_FRAMES`는 공통 상위 타입을 찾느라 클래스 로딩 중에 다른 클래스를 로드할 수 있어서, 재귀 로딩 위험(§12.4)이 있기 때문입니다.
   - 결과는 `CheckClassAdapter`로 구조 검증하고, 정의 시점에 JVM verifier가 한 번 더 검증합니다.
6. **입력 바이트는 수정하지 않고** 새 배열을 반환합니다. 변환한 클래스마다 적용 규칙과 전후 SHA-256을 보고하고, 선택적으로 전후 클래스 파일을 덤프합니다(`-Dpoly.transformDumpDir`).
7. 실패 정책은 `ABORT_STARTUP`과 `DISABLE_RULE` 두 가지입니다. 실패는 어떤 경우에도 "적용됨"으로 기록하지 않습니다.

## 첫 PoC 규칙 (`poly-version-adapter-v26_2`)

| 규칙 | 대상 | 측 | 정책 |
|---|---|---|---|
| `poly:client_brand` | `net/minecraft/client/ClientBrandRetriever.getClientModName()Ljava/lang/String;` | CLIENT | DISABLE_RULE |
| `poly:server_brand` | `net/minecraft/server/MinecraftServer.getServerModName()Ljava/lang/String;` | 양쪽 | DISABLE_RULE |

Phase 2부터 두 규칙은 상수를 바꾸지 않고 `ReplaceConstantReturnWithStaticCall`로 `Minecraft26_2Hooks`를 호출해 `poly:game_brand` 시스템 값을 돌려줍니다(ADR-0006).

결과: 26.2 클라이언트 창 제목이 `Minecraft* 26.2`가 됩니다(바닐라는 `*` 없음). 변환 전후 해시는 두 번의 실행에서 같았습니다(결정론적).

## 검증

`ClassTransformPlannerTest`(13개)가 다음을 다룹니다.
- 정상 변환, 그리고 변환된 클래스를 실제로 로드해 호출하는 검증
- descriptor 불일치(존재하는 overload 목록을 메시지에 포함)
- 클래스 누락, 사전조건 불일치, 충돌, ID 중복, 버전 불일치
- 다른 측(side)의 규칙 건너뛰기, `ABORT_STARTUP` 보고
- 계획 후 바이트가 바뀐 경우의 실패, 덤프

## 아직 없는 것 (Phase 3 범위)

> 2026-10-21: 순서 계약, 메서드 진입/종료 후크, call-site 변경, 접근 확대, 캐시 크기 상한은 이후 추가됐습니다([ADR-0013](0013-transform-order-callsite-access.md), ADR-0007). 아래는 이 ADR을 채택할 때의 기록입니다.

- 모드가 선언하는 변환(`poly.mod.json`의 `transforms`). 지금은 `POLY-METADATA-010`으로 거부합니다.
- 우선순위·순서 계약, 메서드 진입/종료 후크, call-site 변경, 접근 확대
- 변환 결과 캐시(키: Minecraft·Java·Poly 모드 로더·모드·규칙 버전)
- Mixin 의미 재현. Fabric/NeoForge 브리지에 필요하며, ASM 도입만으로 Mixin 호환이 되지는 않습니다(§12.2).
