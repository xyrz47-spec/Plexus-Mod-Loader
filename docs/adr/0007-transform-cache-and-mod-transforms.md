# ADR-0007: 변환 캐시와 모드 선언 변환

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §5.4.1, §5.7, §12.3, §12.4, §15.2, Phase 3. ADR-0004를 보완합니다.

## 1. 변환 캐시

### 결정
- **키:** SHA-256 하나로, 결과를 바꿀 수 있는 입력을 모두 포함합니다(`TransformCacheKey`).
  - 원본 클래스 바이트: Minecraft 버전과 이름 공간까지 함께 고정됩니다.
  - 적용 규칙 각각의 정규화된 정의: ID, 소유 모드, 게임 버전, 정렬된 측, 대상, 동작, 실패 정책
  - 엔진 지문: Poly 모드 로더 버전, 엔진 리비전, ASM API 수준과 버전
  - 입력 하나라도 바뀌면 키가 달라지므로, 별도의 무효화 절차가 필요 없습니다.
- **저장 형식:** `<digest>.polyclass` 파일 하나가 하나의 항목이며, `[32바이트 SHA-256(내용)][변환된 클래스 파일]` 순서로 저장합니다. 쓰기는 임시 파일에 쓴 뒤 원자적으로 이동합니다.
- **손상 처리:** 체크섬이 맞지 않는 항목은 삭제하고 미스로 처리한 뒤 다시 변환합니다(`POLY-TRANSFORM-008` 경고). 손상된 바이트는 JVM에 절대 전달되지 않습니다. 쓰기 실패는 `POLY-TRANSFORM-009` 경고만 내고 클래스 로딩은 계속합니다.
- **위치:** 기본값은 `<게임 디렉터리>/.poly/transform-cache`입니다. `-Dpoly.transformCacheDir=none`으로 끌 수 있습니다. 개발 실행 작업은 `poly-bootstrap/build/poly/transform-cache`를 공유합니다.
- **보고:** 변환된 클래스마다 캐시 사용 여부(`fromCache`)를 로그와 `.poly/transformation-report.json`에 기록합니다. 보고서는 게임이 종료될 때 Poly 모드 로더 자체 종료 작업으로 씁니다.

### 발견한 함정
`TransformRuleDefinition.sides`는 `Set.copyOf` 결과입니다. 이 집합의 반복 순서는 JVM 실행마다 바뀝니다(무작위 SALT). 그래서 레코드의 `toString()`을 키 계산에 쓰면 매 실행 키가 달라져 캐시가 한 번도 적중하지 않습니다. 정규화 함수(`TransformCacheKey.canonical`)에서 측을 정렬하도록 했고, 테스트로 고정했습니다.

## 2. 모드 선언 변환

### 결정
- 모드는 `poly.mod.json`의 `transforms`에 규칙을 선언합니다. 권한 `internal.bytecode.transform`이 필요하고, 없으면 `POLY-METADATA-012`.
- **규칙 ID**는 `<모드 ID>:<경로>`여야 합니다. 다른 모드의 이름 공간은 쓸 수 없습니다(`POLY-METADATA-015`).
- **동작:** 엔진이 검토한 닫힌 집합만 쓸 수 있습니다. 현재는 `replace_returned_string_constant` 하나입니다. 모르는 동작은 `POLY-METADATA-015`.
- **검증:** 모드 규칙은 Poly 모드 로더 내장 규칙과 같은 계획 단계를 거칩니다. 대상 존재, descriptor, 바이트코드 사전조건, 게임 버전, 그리고 다른 모든 규칙(Poly 모드 로더 자신의 규칙 포함)과의 충돌을 확인합니다.
- **실패 정책:** `onFailure: abort_startup | disable_rule`. 기본값은 `disable_rule`입니다.

### 결과
- `examples/transform-demo-mod`가 실제 26.2의 `LevelStorageSource.getName()`(`"Anvil"`)을 바꿉니다. 클라이언트와 게임 테스트 서버에서 적용과 캐시 재사용을 확인했습니다.
- 모드가 Poly 모드 로더 내장 규칙과 같은 메서드를 고치면 두 규칙이 모두 거부됩니다. 순서·협력 계약은 아직 없습니다(설계서 §5.7의 "순서·협력 계약"은 Phase 5에서 Mixin 의미를 다룰 때 정합니다).

## 아직 없는 것
- 메서드 진입/종료 후크, call-site 변경, 접근 확대 같은 일반 변환 동작
- 변환 사이의 우선순위·순서 선언
- 캐시 크기 상한과 오래된 항목 정리(현재 항목 수는 규칙 수에 비례해 작습니다)
