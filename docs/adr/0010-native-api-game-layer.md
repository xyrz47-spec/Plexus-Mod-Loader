# ADR-0010: Native API는 게임 계층(game layer)이 Minecraft 클래스로 직접 구현한다

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §5.1 Level 1–2, §5.3 D–J, §7.7, §8.1, ADR-0003, ADR-0004

## 배경
Phase 0~7에서는 Minecraft 이름을 어댑터의 문자열 상수로만 두고, 훅은 `MethodHandle`로 게임 객체를 다뤘습니다. 이 방식으로는 블록·아이템 등록, 리소스 팩, 명령어, 네트워크처럼 게임 타입을 많이 쓰는 기능을 구현할 수 없습니다. 반사 호출 코드가 커지고, 타입 검사도 받을 수 없습니다.

Minecraft는 26.1부터 난독화하지 않으므로 공개 jar에 그대로 컴파일할 수 있습니다.

## 결정
1. **모드용 API는 게임 타입을 노출하지 않습니다.**
   - `poly-game-api`가 콘텐츠, 월드·엔티티 뷰, 이벤트, 명령어, 네트워크, 클라이언트, 게임 테스트 계약을 담습니다.
   - 모드는 이 API만으로 컴파일합니다.
2. **게임 계층은 Minecraft jar에 컴파일합니다.**
   - 모듈은 `poly-game-v26_2`, 패키지는 `dev.polymodloader.game.layer.v26_2`입니다.
   - `poly.minecraft-compile` 플러그인이 내려받은 클라이언트 jar와 라이브러리를 `compileOnly`로 붙입니다. 그래서 Minecraft jar는 패키징되지도, 발행되지도 않습니다.
   - 클라이언트 jar에는 서버 클래스도 들어 있습니다. 클라이언트 전용 코드는 `client` 하위 패키지에만 둡니다. 전용 서버는 이 패키지를 로드하지 않습니다.
3. **게임 계층은 게임 클래스로더가 정의합니다.**
   - `GameClassLoader`는 Poly 모드 로더 패키지를 부모 로더에서만 가져옵니다. 예외는 `dev.polymodloader.game.layer.` 하나입니다.
   - 런타임은 어댑터가 알려 준 `gameLayerClass()`로 Poly 모드 로더 클래스 경로에서 그 jar를 찾고, 게임 jar 다음 순서로 게임 클래스로더에 넣습니다(`POLY-BOOTSTRAP-004`).
   - 게임 계층은 모든 엔트리포인트가 끝난 뒤, 게임 `main` 전에 `GameLayer.start`로 시작합니다(`POLY-BOOTSTRAP-005`).
4. **선언과 실행을 나눕니다.**
   - 모드는 엔트리포인트에서 선언만 합니다. 런타임의 `NativeRegistrations`가 버전 독립적으로 즉시 검증합니다(`POLY-CONTENT/COMMAND/NETWORK/GAMETEST/CLIENT-xxx`).
   - 게임 계층은 게임이 준비된 시점에 그 선언을 실제 게임 객체로 만듭니다.
   - 등록은 `BuiltInRegistries.freeze()` 직전에 닫힙니다.
5. **게임과의 연결은 모두 선언형 변환 규칙입니다.** 변환 동작 2개를 추가했습니다(ADR-0004의 닫힌 집합 확장).
   - `InvokeHookAtHead`: 수신 객체와 모든 인수를 훅에 넘깁니다. `CONTINUE`, `CANCEL_IF_TRUE`, `RETURN_IF_NON_NULL` 중 하나로 동작합니다. 생성자에는 쓸 수 없습니다.
   - `InvokeHookBeforeReturn`: 모든 반환 직전에 수신 객체, 인수, 반환값을 넘기고, 훅의 결과를 반환합니다. **사전조건**은 메서드가 매개변수 슬롯에 쓰지 않는 것입니다. 쓰면 원래 인수를 넘긴다고 보장할 수 없기 때문입니다.
   - 분기가 생기는 동작은 초기 프레임과 정확히 같은 `F_SAME`/`F_SAME1` 프레임, 그리고 `NOP`만 추가합니다. 프레임은 다시 계산하지 않습니다(ADR-0004 유지).
   - javac로 컴파일한 클래스를 변환한 뒤 JVM 검증기로 실행하는 테스트로 확인했습니다(`HookActionsTest`).
6. **Native 규칙은 필수 기능입니다.** 실패 정책은 `ABORT_STARTUP`입니다. 26.2 jar에서 14개 규칙 모두 사전조건을 통과했습니다.

## 26.2 연결 지점 (javap로 확인)

| 규칙 | 대상 | 동작 |
|---|---|---|
| `poly:content_registration` | `BuiltInRegistries.freeze()` | 머리 훅: 블록 → 아이템 → 게임 테스트 함수 등록, 등록 종료 |
| `poly:mod_resource_packs` | `BuiltInPackSource.loadPacks(Consumer)` | 머리 훅: 모드 jar를 필수 팩 `poly/<mod id>`로 추가(클라이언트는 `assets/`, 서버는 `data/`) |
| `poly:commands` | `Commands.<init>(CommandSelection, CommandBuildContext)` | 반환 훅: 명령어 트리 추가(서버 시작·`/reload` 모두) |
| `poly:payload_codecs` | `CustomPacketPayload.codec(FallbackProvider, List)` | 머리 훅(`RETURN_IF_NON_NULL`): 채널을 더한 목록으로 같은 메서드를 재호출 |
| `poly:server_payloads` | `ServerGamePacketListenerImpl.handleCustomPayload` | 머리 훅(`CANCEL_IF_TRUE`): Poly 모드 로더 페이로드를 서버 스레드로 전달 |
| `poly:client_payloads` | `ClientPacketListener.handleCustomPayload(CustomPacketPayload)` | 클라이언트 전용, 같은 방식 |
| `poly:player_joined` / `poly:player_leaving` | `PlayerList.placeNewPlayer` 반환 / `PlayerList.remove` 머리 | 이벤트 |
| `poly:block_break_capture` + `poly:block_broken` | `Block.playerWillDestroy` 머리 + `ServerPlayerGameMode.destroyBlock` 반환 | 파괴 전 상태를 기록하고, 실제로 제거됐을 때만 `BlockEvents.BROKEN` 발생 |
| `poly:creative_tabs` | `CreativeModeTab.buildContents` | 반환 훅: 지정한 탭 끝에 모드 아이템 추가 |
| `poly:client_key_bindings` | `Options.load()` | 머리 훅: 저장된 키를 읽기 전에 `keyMappings`에 추가 |
| `poly:client_tick` / `poly:client_hud` | `Minecraft.tick()` 반환 / `Hud.extractRenderState` 반환 | 클라이언트 이벤트, HUD 레이어 |

## 실험에서 확인한 제약
- **게임 테스트 인스턴스는 데이터 팩 레지스트리 항목입니다.**
  - 알려진 팩 정보가 없는 팩이 이 항목을 넣으면, 월드의 `worldGenSettingsLifecycle`이 stable이 아니게 됩니다.
  - 그러면 클라이언트가 "실험적 설정" 확인 화면에서 멈춥니다. `clientWorldReloadTest`에서 재현했습니다.
  - 그래서 Poly 모드 로더가 생성하는 `poly/generated` 팩은 **게임 테스트 서버에서만** 만듭니다.
- **`Block.playerWillDestroy`를 재정의하면서 `super`를 부르지 않는 블록**은 파괴 이벤트가 발생하지 않습니다.
- **키 바인딩은 `Options.keyMappings` final 필드를 반사로 교체합니다.** 이후 JDK에서 final 필드 쓰기가 제한되면 다른 연결 지점이 필요합니다(`POLY-CLIENT-004`로 보고).

## 대안
- **반사만 쓰는 어댑터 유지:** 기능마다 수십 개의 `MethodHandle`이 필요하고 타입 검사가 없습니다. 기각했습니다.
- **Minecraft 타입을 모드 API에 노출:** 버전 결합이 모드까지 퍼집니다. 설계서 §7.7 패키지 경계 규칙에 어긋나 기각했습니다. 원하는 모드는 Level 3–4(변환)를 씁니다.
- **Mixin 도입:** 별도 서비스와 의미 재현이 필요합니다(ADR-0009 다음 단계). Native API에는 닫힌 동작 집합으로 충분했습니다.

## 추가 결정 (2단계, 2026-10-19)
- **모드 팩은 `KnownPack("poly", <mod id>, <버전>)` 정보를 가집니다.**
  - 26.2는 알려진 팩에서 온 레지스트리 항목만 stable로 등록합니다(`ResourceManagerRegistryLoadTask`).
  - 그래서 모드의 월드 생성, 피해 유형, 차원 데이터가 월드를 "실험적"으로 만들지 않습니다.
  - 생성 게임 테스트 팩도 알려진 팩이 되어, 일반 월드에서도 `/test`로 실행할 수 있습니다.
- **로드된 모드 이름공간의 차원은 안정으로 판정합니다.**
  - 26.2는 바닐라가 아닌 차원을 모두 실험적으로 판정합니다(`WorldDimensions.checkStability`).
  - Poly 모드 로더는 같은 이름의 모드가 로드되어 있을 때만 안정으로 바꿉니다(`poly:mod_dimension_stability`, `RETURN_IF_NON_NULL`).
  - 다른 데이터 팩의 차원에는 바닐라 판정이 그대로 적용됩니다.
- **원시형 반환 메서드의 조기 반환을 지원합니다.** `RETURN_IF_NON_NULL`은 원시형을 반환하는 메서드에서 박스 타입을 받아 박싱을 풀고 반환합니다. 이 방식으로 `DefaultAttributes.hasSupplier`와 `LivingEntity.hurtServer`에 연결합니다.
- **바이옴 기능 추가는 `MinecraftServer.createLevels()` 머리에서 합니다.** 서버의 바이옴 객체에 있는 `BiomeGenerationSettings.features` final 필드를 반사로 교체합니다. 청크 생성기가 단계별 기능 목록을 처음 계산하기 전입니다.
