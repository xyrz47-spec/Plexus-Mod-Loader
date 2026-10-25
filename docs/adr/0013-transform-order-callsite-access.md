# ADR-0013: 변환 규칙의 순서 계약, call-site 변경, 접근 확대, 캐시 크기 상한

- 상태: 채택 (2026-10-21). 결정 5의 생성자 `extendable` 금지와 인터페이스 필드 `mutable`은 ADR-0018이 고쳤습니다
- 관련: ADR-0004(변환 엔진), ADR-0007(변환 캐시와 모드 선언 변환), ADR-0009(로더 브리지), 설계서 §5.4.1, §5.7, §12

## 배경
ADR-0004는 같은 메서드를 고치는 규칙을 모두 거부했습니다(`POLY-TRANSFORM-004`). 순서·협력 계약이 없었기 때문입니다. 그러다 보니 Poly 모드 로더가 이미 훅을 건 메서드(`MinecraftServer.tickServer` 등)는 모드가 건드릴 수 없었습니다. 또 동작 집합에 메서드 안의 특정 호출만 바꾸는 방법(call-site 변경)이 없었고, private 멤버에 접근하는 방법(접근 확대)도 없었습니다. 둘 다 Fabric 브리지가 Mixin `@Redirect`와 access widener를 재현할 때도 필요합니다. 변환 캐시는 크기 제한 없이 계속 커졌습니다.

## 결정
1. **순서 계약은 `after`입니다.** `TransformRuleDefinition.after`에 같은 메서드에서 먼저 실행돼야 하는 규칙 ID를 적습니다.
   - 같은 메서드의 규칙들은 `after`가 **하나의 순서**를 정할 때만 함께 받아들입니다. 매 단계에 놓을 수 있는 규칙이 정확히 하나여야 하며, 순서가 둘 이상 가능하거나 순환이면 모두 거부합니다(`POLY-TRANSFORM-004`).
   - 없는 규칙이나 다른 메서드를 고치는 규칙 ID는 무시합니다. 순서만 정하고 존재를 요구하지 않습니다.
   - 숫자 우선순위는 두지 않습니다. 서로 모르는 두 모드가 같은 숫자를 고르면 결국 추측해야 하기 때문입니다(설계서 §4.4).
2. **함께 쓸 수 있는 동작만 합칩니다.**
   - 호출 바꾸기(`RedirectCall`), 시작 훅(`InvokeHookAtHead`, `InjectStaticCallAtHead`), 반환 직전 훅(`InvokeHookBeforeReturn`)은 함께 쓸 수 있습니다.
   - 상수 교체 두 가지는 본문 전체 모양(`LDC; ARETURN`)에 의존하므로 다른 규칙과 함께 쓸 수 없습니다.
   - 같은 호출을 바꾸는 `RedirectCall` 둘도 함께 쓸 수 없습니다.
3. **실행 순서가 선언 순서와 같도록 적용합니다.** 사전조건은 모두 원본 메서드에서 검사합니다. 적용은 다음 순서로 합니다.
   1. 호출 바꾸기
   2. 반환 직전 훅: 선언 순서대로 실행되고, 앞 훅의 결과가 다음 훅에 전달됩니다.
   3. 시작 훅: 역순으로 앞에 끼워 넣으므로 선언 순서대로 실행됩니다.

   시작 훅이 취소하거나 값을 돌려주면 반환 직전 훅은 건너뜁니다. 메서드 자신의 반환에만 붙기 때문입니다.
4. **`RedirectCall`**
   - 호출 명령의 owner·name·descriptor가 정확히 같은 호출을 모두 또는 n번째(0부터) 하나만 정적 훅 호출로 바꿉니다.
   - 훅은 받는 쪽(정적 호출이면 없음)과 인자를 받고 같은 형식을 돌려줍니다. 스택 모양이 같으므로 프레임은 바뀌지 않습니다.
   - `invokespecial`(super·private 호출), 생성자, `invokedynamic`은 거부합니다.
5. **접근 확대(`AccessWidening`)는 Fabric access widener와 같은 뜻입니다.**
   - `accessible`: public으로 만듭니다. 이전에 private이던 메서드는 final이 되어, 클래스 자신의 호출이 하위 클래스 때문에 바뀌지 않습니다.
   - `extendable`: 클래스는 public·non-final·non-sealed가 되고, 메서드는 non-final·최소 protected가 됩니다. owner 안의 `invokespecial` 호출은 가상 호출로 바꿉니다.
   - `mutable`: 필드의 final을 해제합니다.
   - 접근은 넓히기만 하므로 같은 대상의 확대는 합집합으로 합치고 순서를 요구하지 않습니다.
   - 계획 단계에서 클래스와 정확한 멤버가 있는지 확인합니다(`POLY-TRANSFORM-010`).
6. **모드 선언**
   - `transforms`에 `invoke_hook_at_head`, `invoke_hook_before_return`, `redirect_call`과 `after`를 추가합니다.
   - 새 필드 `accessWidenings`를 추가합니다.
   - 권한은 설계서 §5.4.1에 정의된 것을 씁니다. `redirect_call`은 `internal.callsite.intercept`, `accessWidenings`는 `internal.member.access`가 추가로 필요합니다.
   - 접근 확대의 기본 실패 정책은 시작 중단입니다. 대상이 없으면 그것을 쓰는 모드 코드가 나중에 `IllegalAccessError`로 실패하기 때문입니다.
7. **캐시 크기 상한**
   - 기본값은 256 MiB이고 `-Dpoly.transformCacheMaxMegabytes`로 바꿉니다.
   - 적중하면 그 항목의 수정 시각을 갱신합니다.
   - 쓰기로 한도를 넘으면 가장 오래 쓰지 않은 항목부터 지워 한도의 90%까지 줄입니다. 방금 쓴 항목은 남깁니다.
   - 키가 모든 입력을 담으므로, 지운 항목은 다시 변환하는 비용만 듭니다.
8. 캐시 키에 `after`와 접근 확대(정렬한 텍스트)를 넣고, 엔진 리비전을 2로 올렸습니다.

## 결과
- 모드가 Poly 모드 로더 내장 훅과 같은 메서드를 고칠 수 있습니다. 예제는 `transform-demo-mod`입니다. `tick_start_nanos`가 `poly:server_tick_events` 뒤에서 `tickServer`의 첫 `Util.getNanos()`를 바꾸고, `MinecraftServer.tickCount`를 public으로 만듭니다. 실제 게임(`worldCreateSaveTest`, `serverBootSmokeTest`, `clientWorldReloadTest`)에서 확인했습니다.
- 모드 훅은 descriptor가 정확히 맞아야 합니다. 모드는 Poly 모드 로더 API로만 컴파일되므로, 게임 타입이 들어간 훅 시그니처는 게임 jar에 대고 컴파일해야 합니다. 그 도구(접근 확대를 반영한 컴파일용 jar 포함)는 아직 없습니다.
- 접근 확대는 실행 시에만 적용됩니다. `static final` 상수 필드를 `mutable`로 바꿔도, 그 값을 컴파일 때 복사해 둔 코드에는 반영되지 않습니다.
- Mixin의 다른 의미(`@ModifyVariable`, `@ModifyArg`, 지역 변수 캡처, 인터페이스 추가 등)는 아직 없습니다. Fabric 브리지 단계(ADR-0009)에서 필요한 만큼 추가합니다.

## 검증
- `CallSiteAndAccessTest`(11개): javac로 컴파일한 클래스를 변환해 로드·실행하므로 JVM 검증기가 결과를 검사합니다.
  - 호출 바꾸기(모두·n번째, 정적·가상), 사전조건 실패
  - 순서가 섞인 규칙 5개의 실행 순서, 순서 없음·순환 거부, 합칠 수 없는 동작 거부
  - 접근 확대(public·final 유지, 필드 쓰기, extendable 클래스·메서드와 가상 호출 전환), 없는 멤버 거부, 캐시 키
- `TransformCacheTest`: 크기 상한(LRU 삭제, 한도보다 큰 항목, 잘못된 한도)
- `ModMetadataReaderTest`: `redirect_call`·`after`·`accessWidenings` 읽기와 권한·형식 오류
