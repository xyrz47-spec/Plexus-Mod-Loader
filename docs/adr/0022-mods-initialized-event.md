# ADR-0022: 모든 모드 초기화 뒤의 공개 이벤트 `LifecycleEvents.MODS_INITIALIZED`

- 상태: 채택 (2026-10-22)
- 관련: ADR-0014(로더를 넘는 의존성), ADR-0020(Fabric 모드의 초기화 시점)
- 계획: `docs/plans/FABRIC_FULL_SUPPORT.md` F6

## 배경

ADR-0020 이후 Poly 모드와 Fabric 모드는 초기화되는 때가 다릅니다.

- Poly 모드의 진입점은 Minecraft가 시작하기 전에 돕니다. 이때는 선언만 합니다.
- Fabric 모드의 진입점은 Fabric Loader가 부르는 자리에서 돕니다. Minecraft 부트스트랩 뒤, 클라이언트 창이나 서버가 시작하기 전입니다.

그래서 Fabric 라이브러리에 의존하는 Poly 모드는 자기 진입점에서 그 라이브러리의 클래스는 보지만, 라이브러리가 초기화한 값은 보지 못합니다(`native-addon` 테스트 모드의 "not ready"). ADR-0014는 "의존 대상의 진입점이 먼저 돈다"고 정했지만, 로더가 다르면 ADR-0020 뒤로는 이것을 지킬 수 없게 됐습니다. Poly 모드에게는 "모든 모드가 초기화된 뒤"라는 시점이 필요합니다.

## 결정

1. 공개 이벤트 `dev.polymodloader.api.event.lifecycle.LifecycleEvents.MODS_INITIALIZED`(`ModsInitializedListener`, `PREVIEW`)를 둡니다.
   - 시점: Minecraft의 모드 초기화 자리(ADR-0020의 `bindModInitialization`). 외부 모드의 진입점을 의존 순서대로 다 부른 바로 뒤, 한 번.
   - 스레드: 게임의 메인 스레드.
   - 외부 모드가 없어도 같은 자리에서 옵니다. 설치된 모드에 따라 Poly 모드가 받는 시점이 달라지지 않게 하기 위해서입니다. 버전 어댑터에 그 자리가 없으면 진입점 직후에 옵니다.
   - 실패: 다른 이벤트와 같습니다. 던진 리스너는 `POLY-EVENT-001`로 보고하고 끕니다.
2. Poly 모드의 진입점 시점은 바꾸지 않습니다. 진입점은 계속 선언만 하는 자리입니다. 진입점을 Fabric 자리로 옮기면, 선언을 게임 객체로 바꾸는 시점(레지스트리 동결 직전)과의 관계가 바뀌고 기존 Poly 모드의 가정이 깨집니다.
3. 이벤트 이름은 기존 `ServerEvents`·`WorldEvents`처럼 `<영역>Events.<상태>` 꼴입니다. 상태는 과거형(`INITIALIZED`)입니다. 일이 끝난 뒤의 알림이기 때문입니다.

## 고르지 않은 것

- **로더별 이벤트(`FABRIC_MODS_INITIALIZED`)**: Forge·NeoForge·Quilt가 붙으면 이벤트가 늘어나고, Poly 모드가 설치된 로더를 알아야 합니다.
- **Poly 모드 진입점을 Fabric 자리로 옮기기**: 위 2번의 이유.

## 결과

- `dependencyCrossLoader` 시나리오: `native_addon`이 진입점에서는 "not ready"를, 이 이벤트에서는 "hello-from-fabric-lib"를 봅니다.
- `nativeApiGameTests`: 예제 `native-showcase-mod`가 이 이벤트를 씁니다(외부 모드 없이도 옴).
- Forge(ADR-0021)에서는 Forge의 패치된 jar에서도 초기화 자리 규칙이 적용됩니다(`forgeGameTest` 통과). FML의 로딩 단계와의 순서는 아직 정하지 않았습니다.
