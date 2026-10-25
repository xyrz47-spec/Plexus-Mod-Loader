# ADR-0006: 시스템 교체 — 메타데이터 선언, 사전 충돌 해결, 버전 어댑터 훅

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §4.3, §4.4, §5.4.1, §5.6, §11 단계 8–9, Phase 2

## 결정

1. **교체는 `poly.mod.json`에 선언합니다.**
   - 형식: `replacesSystems: [{system, provider, override?}]`
   - 선언하려면 기능 권한 `capabilities: ["system.provider.replace"]`가 필요합니다. 없으면 `POLY-METADATA-012`.
   - 코드에서 실행 중에 교체하는 API는 두지 않습니다. 누가 무엇을 바꾸는지 시작 전에 알 수 있어야 하기 때문입니다.
2. **충돌은 모드 코드가 실행되기 전에 해결합니다**(`CONFLICT_RESOLUTION` 단계, `SystemReplacementResolver`).
   - 교체가 0개면 기본 구현, 1개면 그 구현을 씁니다.
   - 2개 이상이면, `override: true`이면서 다른 교체 모드 모두에 (전이적으로) 의존하는 모드 하나만 이깁니다.
   - 그 밖의 경우는 `POLY-SYSTEM-001`로 시작을 중단하고, 모드 ID와 해결 방법을 함께 보고합니다.
3. **구현체는 생성 즉시 검증합니다**(`SYSTEM_REGISTRATION` 단계, `SystemRegistry`).
   - 다음 경우는 모두 `POLY-SYSTEM-003`으로 시작을 중단합니다: 제공자 생성 실패, `null` 반환, 계약 인터페이스 미구현, 시스템별 검증 실패(브랜드 규칙 위반 등).
   - 기본 구현으로 조용히 되돌아가지 않습니다.
4. **게임 연결은 버전 어댑터의 훅이 맡습니다.**
   - 26.2 어댑터의 변환 규칙이 `ClientBrandRetriever.getClientModName`과 `MinecraftServer.getServerModName` 본문을 `Minecraft26_2Hooks`의 정적 호출로 바꿉니다(새 변환 동작 `ReplaceConstantReturnWithStaticCall`).
   - 훅은 `GameVersionAdapter.bindSystems`로 받은 `SystemLookup`에서 활성 구현을 조회합니다.
   - 그래서 모드 코드는 Minecraft 클래스를 참조하지 않고, 게임 코드는 어느 모드가 교체했는지 알지 못합니다.
5. **진단:** 활성 시스템과 제공자를 로그와 `.poly/startup-report.json`(System Explorer 1단계)에 기록합니다.

## 고려한 대안

- **`ServiceLoader`로 교체 구현 탐색:** 여러 jar가 같은 서비스를 제공하면 순서가 클래스 경로에 좌우됩니다. 그러면 §4.4의 "추측하지 않는다"를 지킬 수 없어 제외했습니다.
- **모드가 엔트리포인트에서 `registry.replace(...)` 호출:** 교체 시점이 모드 초기화 순서에 좌우되고, 시작 전에 충돌을 알 수 없어 제외했습니다.
- **정적 전역 레지스트리:** 설계서 §7.1(신 전역 객체 금지)에 어긋납니다. 대신 정적 상태는 버전 어댑터의 훅 클래스 하나에만 두고, 그 범위를 훅으로 제한했습니다.

## 결과

- 첫 시스템 `poly:game_brand`가 기본 구현(`poly`)과 교체 구현(`examples/custom-system-mod` → `poly/example`)을 모두 실제 26.2에서 보여 줍니다.
- 새 시스템을 추가하려면 `poly-system-api`에 계약을, `BuiltInSystems`에 정의·기본 제공자·검증기를, 버전 어댑터에 훅과 변환 규칙을 추가합니다.

## 아직 없는 것

- 확장(`EXTEND`)과 교체의 구분. 지금은 시스템 전체 교체만 지원합니다.
- 교체 가능 지점이 여러 개인 시스템(예: 레시피의 등록·조회·동기화 분리)
- 시스템 사이의 의존성과 초기화·종료 순서
