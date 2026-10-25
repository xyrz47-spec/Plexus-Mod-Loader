# transform-demo-mod

## 이 모듈이 하는 일
`poly.mod.json`의 선언으로 Minecraft 26.2를 변환합니다.

- `transform_demo_mod:storage_format_name`: `LevelStorageSource.getName()` 반환값을 `"Anvil"`에서 `"Anvil (Poly 모드 로더 transform demo)"`로 바꿉니다. Java 코드 없이 선언만으로 동작합니다.
- `transform_demo_mod:tick_start_nanos`: `MinecraftServer.tickServer`의 첫 `Util.getNanos()` 호출을 `TickHooks.tickStartNanos()`로 바꿉니다(`redirect_call`). Poly 모드 로더 내장 규칙 `poly:server_tick_events`가 같은 메서드에 시작 훅을 걸기 때문에 `"after": ["poly:server_tick_events"]`로 순서를 선언합니다.
- 접근 확대: `MinecraftServer.tickCount`(private 필드)를 public으로 만듭니다(`accessWidenings`). 훅이 처음 불릴 때 리플렉션으로 확인해 기록합니다.

## 무엇을 소유하는가
`poly.mod.json`(변환 규칙 둘, 접근 확대 하나), `TickHooks`(호출 바꾸기 훅)

## 무엇을 하지 않는가
게임 진행에 영향을 주지 않습니다. 저장 형식 이름은 표시용 문자열입니다. 훅은 원래와 같은 시계(`System.nanoTime`)를 돌려줍니다.

## 의존성
SLF4J(`compileOnly`). 권한 `internal.bytecode.transform`, `internal.callsite.intercept`, `internal.member.access`를 선언합니다. Minecraft jar에 대고 컴파일하지 않으므로, 훅 시그니처에 게임 타입이 없는 호출(`()J`)을 골랐습니다.

## 주요 진입점
없음(선언형 규칙과 훅 클래스만 있음)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:worldCreateSaveTest
```

게임 테스트 서버 로그에서 다음을 확인합니다. 두 번째 실행부터는 `from cache`가 붙습니다.

- `Transformed net/minecraft/world/level/storage/LevelStorageSource with [transform_demo_mod:storage_format_name]`
- `Transformed net/minecraft/server/MinecraftServer with [poly:server_brand, poly:server_tick_events, transform_demo_mod:tick_start_nanos, …, access widening by transform_demo_mod]`
- `[transform_demo_mod] tickServer start time comes from the mod hook; MinecraftServer.tickCount is public: true`

## 대표 흐름
메타데이터 검증(권한·이름 공간·동작·순서) → `ModTransformRules`가 엔진 규칙·접근 확대로 변환 → 계획 단계에서 실제 바이트코드와 대조하고 같은 메서드의 규칙 순서를 정함 → 클래스 로드 시 적용, 또는 캐시에서 재사용

## 알려진 제한
- 대상 메서드가 바뀌면 규칙은 비활성화됩니다(`disable_rule`). 접근 확대는 `skip`입니다.
- 대상은 Minecraft 26.2에 고정돼 있습니다. 다른 버전에서는 `POLY-TRANSFORM-006`으로 건너뜁니다.
