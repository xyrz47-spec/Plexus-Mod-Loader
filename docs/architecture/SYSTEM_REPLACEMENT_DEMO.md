# 시스템 교체 최소 데모 — 설계 (구현 전)

- 상태: **구현 완료** (2026-10-19, Phase 2). 구현 결정은 [ADR-0006](../adr/0006-system-replacement.md)을 보세요. 아래 표의 계약과 충돌 규칙은 그대로 구현됐습니다. 단, 오류 코드 체계는 `POLY-SYSTEM-001..005`로 정리됐습니다.
- 관련: 설계서 §4.3, §4.4, §5.6, §21-9

이 문서는 "시스템 교체"를 확인할 가장 작은 데모를 정의합니다. 아래 Java 코드는 **개념 예시이며 아직 컴파일되는 API가 아닙니다.**

## 데모로 증명할 것

1. Poly 모드 로더가 시스템의 **기본 구현**을 제공하고, 모드가 그것을 **명시적으로 교체**할 수 있다.
2. 교체한 구현이 실제 게임 동작에 반영된다. 실제 Minecraft 26.2에서 확인한다.
3. 두 모드가 같은 시스템을 교체하려 하면, 추측하지 않고 시작 전에 진단한다.
4. 모드 코드는 Minecraft 내부 클래스를 직접 참조하지 않는다.

## 대상 시스템: 게임 브랜드 (`poly:game_brand`)

첫 데모 대상은 "게임이 스스로를 무엇이라고 보고하는가"입니다. 고른 이유는 다음과 같습니다.

- 이미 검증된 변환 지점이 있습니다. `ClientBrandRetriever.getClientModName`, `MinecraftServer.getServerModName`(ADR-0004).
- 결과를 눈으로 확인할 수 있습니다. 창 제목, 크래시 리포트, 서버 목록에 나타납니다.
- 상태나 저장 데이터가 없어서 데이터 손상 위험이 없습니다.
- 계약 요소(§4.3) 전부를 작게 연습할 수 있습니다.

레시피 처리(§21-9의 예시)는 등록·검색·리로드·동기화·저장과 엮여 있습니다. 그래서 계약 구조가 검증된 뒤 두 번째 대상으로 삼습니다.

## 계약 (§4.3 항목별)

| 항목 | `poly:game_brand` |
|---|---|
| 시스템 ID / 이름 | `poly:game_brand` / "Game Brand" |
| 역할·소유 | 게임이 보고하는 모드 브랜드 문자열 하나 |
| 공개 인터페이스 | `GameBrandProvider { String brand(GameSide side); }` |
| 기본 구현 | `PolyGameBrandProvider` → `"poly"` |
| 단계 | 시스템 등록(§11-8) → 충돌 해결(§11-9) → 첫 조회 시 값 고정 |
| 의존 시스템 | 없음 |
| 교체 가능 지점 | 제공자 전체(`REPLACE`). 확장(`EXTEND`)은 지원하지 않음 |
| 교체 금지 지점 | 변환 규칙 자체(모드가 바이트코드를 직접 바꾸지 않음) |
| 스레드 | 어느 스레드에서나 호출될 수 있음. 구현은 상태 없는 순수 함수여야 함 |
| 상태·데이터 | 없음 |
| 오류 처리 | 제공자 예외나 `null`이면 기본 구현으로 되돌리지 않고 `POLY-SYSTEM-003`으로 실행 중단 |
| 버전 호환 | 26.2 어댑터의 변환 규칙이 제공자 호출로 바뀜 (VERSION_BOUND 연결부) |
| 테스트 더블 | `FixedGameBrandProvider("test")` |

## 메타데이터 선언 (`replacesSystems` 활성화)

```json
"replacesSystems": [
  { "system": "poly:game_brand", "provider": "com.example.brand.ExampleBrandProvider" }
]
```

교체를 선언하려면 기능 권한 `system.provider.replace`(§5.4.1)가 필요합니다. 시작 보고서에 "누가, 무엇을, 왜 교체했는지"를 표시합니다.

## 충돌 규칙 (§4.4)

| 상황 | 결과 |
|---|---|
| 교체 선언 0개 | 기본 구현 사용 |
| 교체 선언 1개 | 그 제공자 사용, 로그에 `system=poly:game_brand provider=<class> mod=<id>` |
| 교체 선언 2개 이상, 서로 `depends` 관계 없음 | `POLY-SYSTEM-001` 실행 중단. 메시지에 두 모드 ID와 해결 방법(하나 제거, 또는 한쪽이 다른 쪽에 의존하며 `override` 명시)을 적음 |
| 2개 이상이고 한쪽이 다른 쪽에 의존하며 `override: true` | 의존하는 쪽이 이김. 로그에 덮어쓴 사실을 기록 |

## 구현 순서 (Phase 2)

1. `poly-system-api`: `SystemId`, `SystemDescriptor`, `SystemProvider<T>`, `SystemCapabilities`
2. `poly-system-registry`: 등록, 충돌 해결, 진단(`POLY-SYSTEM-*`) + 단위 테스트(교체 없음 / 1개 / 충돌 / override)
3. `ReplaceReturnedStringConstant`를 "제공자 호출로 교체"하는 새 변환 동작으로 확장하고, 사전조건 테스트를 추가
4. `examples/custom-system-mod`, `test-mods/system-replacement`(충돌 쌍)
5. `serverBootSmokeTest`와 같은 방식으로 `runClient` 결과(창 제목 `Minecraft* 26.2`, 브랜드 문자열)를 확인하는 검증 작업

## 완료 기준 (설계서 Phase 2)

- 기본 구현과 교체 구현의 차이가 테스트로 확인된다.
- 두 모드가 같은 교체 지점을 요구하면 자동으로 진단된다.
- 데모 모드 코드에 `net.minecraft` 참조가 없다.
