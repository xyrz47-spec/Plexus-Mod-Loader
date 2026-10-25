# ADR-0020: Fabric 모드를 Fabric Loader처럼 실행 — Mixin, 인터페이스 주입, 환경 제거, 초기화 시점, 라이브러리

- 상태: 채택 (2026-10-22)
- 관련: ADR-0003(게임 클래스로더), ADR-0005(공유 라이브러리), ADR-0009(로더 브리지), ADR-0013(변환 순서), ADR-0014(로더를 넘는 의존성·링크 검사), ADR-0016(Fabric Loader API), ADR-0018(class tweaker)
- 고친 결정: ADR-0003의 "라이브러리는 변환하지 않는다", ADR-0014의 "의존 대상의 엔트리포인트가 먼저 돈다"(로더가 다를 때), ADR-0018의 "없는 대상은 막는다"

## 배경

2026-10-21까지 Poly 모드 로더는 Mixin이 없어서 실제 인기 Fabric 모드 52개 가운데 3개(라이브러리)만 로드했습니다. Fabric API 자체도 44개 하위 모듈 중 2개만 로드됐습니다. 모드가 Fabric에서처럼 동작하려면 Mixin만으로는 부족했습니다. 실제 모드 묶음을 게임 테스트 서버에서 차례로 실행하며 다음이 모두 필요하다는 것을 확인했습니다.

1. Mixin과 MixinExtras(Fabric API 모듈 42개가 씀)
2. class tweaker의 `inject-interface`(Fabric API 모듈 40개)
3. `@Environment` 환경 제거(architectury의 진입점 클래스가 클라이언트 전용 메서드를 가짐)
4. Fabric Loader와 같은 초기화 시점(Balm 등은 `onInitialize`에서 레지스트리에 접근하므로 Minecraft bootstrap 뒤여야 함)
5. 게임 라이브러리 변환(Fabric API `fabric-dimensions-v1`이 DataFixerUpper의 `TaggedChoice`를 Mixin으로 바꿈)
6. Poly 모드 로더 내장 규칙이 바닐라 호출을 없애지 않을 것(Fabric API `fabric-lifecycle-events-v1`이 `ServerLevel.close()` 호출 앞에 주입함)

모든 근거는 `javap -c`로 확인한 Fabric Loader 0.19.5 소스(Apache-2.0)와 26.1·26.2 jar, 그리고 실제 실행 결과입니다.

## 결정

1. **Mixin 서비스.** Fabric Loader 0.19.5가 쓰는 `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`(MIT, SHA-1이 Fabric 설치 메타데이터와 일치)과 MixinExtras 0.5.5(MIT)를 Poly 모드 로더 클래스 경로에 둡니다. `PolyMixinService`는 Fabric의 `MixinServiceKnot`을 옮긴 것으로, 클래스 바이트를 게임 클래스로더에서 읽습니다. 시작 순서는 Fabric과 같습니다(`MixinBootstrap.init` → MixinExtras → 모드 순서대로 설정 추가 → 모드 ID·호환 수준 표시 → `INIT`·`DEFAULT` 단계). 호환 수준은 Fabric처럼 모드의 `fabricloader` 요구에서 정합니다. Mixin은 Mixin 설정이 있는 Fabric 모드가 있을 때만 시작합니다(JVM당 한 번).
2. **브리지 변환 단계(SPI).** `ModLoaderBridge.classTransformer`가 돌려준 `BridgeClassTransformer`를 게임 클래스로더가 Poly 모드 로더 변환 뒤에 모든 클래스에 적용하고, Mixin의 합성 클래스도 정의합니다. 브리지는 게임 클래스가 하나라도 정의되기 전에 이것을 설치합니다. 브리지가 공유하는 라이브러리(`org.spongepowered.`, `com.llamalad7.mixinextras.`)는 모드 jar에 사본이 있어도 부모에서만 가져옵니다.
3. **Fabric 단계의 순서**는 Fabric Loader와 같습니다. 먼저 Fabric Loader 자신의 변환(`FabricTransformer`)을 합니다. 모드·라이브러리 클래스에는 환경 제거를, Minecraft 클래스에는 `inject-interface`를 적용합니다. 그다음 Mixin을 적용합니다. Mixin이 읽는 클래스도 이 첫 단계를 거친 모습입니다.
4. **인터페이스 주입**은 Fabric의 `InterfaceInjectionClassVisitor`와 같은 뜻입니다. 중복 없이 인터페이스를 붙이고, 제네릭이면 시그니처에 `L<인자>;`를 붙이고, 중첩 인터페이스는 `InnerClasses` 항목을 더합니다. 제네릭 타입 변수 검사는 하지 않습니다(리플렉션에만 영향). `extend-enum`은 여전히 막습니다(`127`).
5. **환경 제거**는 Fabric의 `EnvironmentStrippingData`·`ClassStripper`와 같습니다. 반대쪽 클래스는 로드를 거부하고, 멤버·인터페이스는 지우고, 생성자·정적 초기화의 해당 필드 쓰기도 지웁니다.
6. **초기화 시점.** Fabric 모드의 `main`·`client`·`server` 진입점은 Fabric의 `EntrypointPatch`가 고르는 자리에서 실행합니다. 버전 어댑터가 `InvokeHookAtCall` 규칙으로 그 자리를 표시하고(`GameVersionAdapter.bindModInitialization`), 런타임이 그 시점에 외부 모드를 의존 순서대로 초기화합니다. 자리는 다음과 같습니다.
   - 전용 서버: `Main.main`의 `Util.startTimerHackThread()` 직전(`Bootstrap.bootStrap()` 뒤)
   - 게임 테스트 서버: `GameTestMainUtil.runGameTestServer`의 같은 호출 직전
   - 클라이언트: `Minecraft` 생성자의 `Thread.currentThread()` 직전

   Poly 모드는 지금처럼 게임 시작 전에 초기화합니다. 그 선언이 Minecraft bootstrap 안에서(`BuiltInRegistries.freeze` 시작) 적용되기 때문입니다. **ADR-0014를 고칩니다:** Poly 모드가 Fabric 모드에 의존하면 로드 순서와 클래스는 보장하지만, 그 Fabric 모드의 초기화가 먼저 끝나는 것은 보장하지 않습니다. `preLaunch`는 Mixin 준비 직후, 게임 전에 실행합니다(`ModLoaderBridge.beforeGame`).
7. **게임 라이브러리.** 외부 모드가 하나라도 로드되면, 게임 클래스로더가 Minecraft 라이브러리 jar도 맡아 변환할 수 있게 합니다. Fabric의 Knot이 하는 일과 같습니다. 단, Poly 모드 로더가 직접 쓰는 라이브러리는 부모에 남깁니다(gson, slf4j, log4j, Mojang logging, ASM, Mixin, MixinExtras, tomlj, Fabric API 클래스). jar는 통째로 옮겨 한 라이브러리의 클래스가 두 로더에 나뉘지 않습니다. **ADR-0003을 고칩니다:** 외부 모드가 없는 실행은 지금처럼 라이브러리를 변환하지 않습니다.
8. **바닐라 호출을 남기는 내장 규칙.**
   - 새 동작 `InvokeHookAtCall`은 호출 앞이나 뒤에서 훅을 부르고 호출은 그대로 둡니다. 피연산자 2칸까지 `DUP`/`DUP2`로 복사하며, 뒤에 붙는 훅은 반환형이 `void`인 호출에만 씁니다. `poly:world_unloaded`, `poly:entity_joined_world`는 이 동작으로 바꿉니다.
   - 호출을 실제로 바꿔야 하는 `RedirectCall`(`poly:block_place`, 크리에이티브 탭 페이지)은, 브리지 단계가 있을 때 그 단계 **뒤에** 적용합니다(`ClassTransformExecutor.transformEarly`/`transformLate`). 그래서 Mixin이 바닐라 호출을 찾아 주입할 수 있습니다.
   - Mixin `@Redirect`/`@WrapOperation`이 이미 그 호출을 가져갔으면 그 클래스에서만 Poly 모드 로더 규칙을 빼고 경고합니다(`POLY-TRANSFORM-011`).
   - 브리지 단계가 없으면 한 번에 적용하므로 결과가 이전과 같습니다.
9. **Fabric과 같은 판정으로 맞춤.**
   - Mixin 설정:
     - 없는 설정, 읽을 수 없는 설정, 객체가 아닌 설정은 Mixin이 오류를 남기고 건너뜁니다. 그래서 막지 않습니다(`136`).
     - `required` 설정이 더 새 Mixin을 요구하면 Mixin이 게임을 멈춥니다. 이 경우만 막습니다(`132`).
   - class tweaker의 없는 대상은 Fabric이 아무것에도 적용하지 않으므로, 막지 않고 적용에서 뺍니다(`137`). 게임 jar 밖 대상은 여전히 막습니다(`128`).
   - `::` 진입점(인스턴스 메서드·생성자 포함)을 지원합니다(`106` 폐기).
   - `getMappingResolver`는 매핑 없는 Fabric과 같이 `official` 이름 공간과 항등 변환을 돌려줍니다.
   - `preLaunch`를 실행합니다(`105` 폐기).
10. **링크 검사.**
    - 모드가 주입하는 인터페이스와 Mixin 클래스가 대상에 더하는 인터페이스를 상위 형식으로 셉니다.
    - 다른 쪽 전용 모드의 클래스(서버의 Fabric API 클라이언트 모듈)와 JDK incubator 클래스는 알림(`113`)으로만 둡니다.
    - 설치된 jar에 없는 형식을 쓰는 게임 멤버는 다른 로더 코드 경로로 봅니다(`113`).
    - 생성자는 자기 클래스에서만 찾습니다.
    - 아직 읽지 못한 클래스를 "없음"으로 기억하지 않습니다.

## 근거 (2026-10-22, Minecraft 26.2, Java 25)

- 단위 테스트: `FabricBridgeTest`(Mixin 설정 판정, `preLaunch`·`::`, 인터페이스 주입), `ClassTweakerFileTest`, `FabricLoaderRuntimeTest`(매핑 해석기), `CallSiteAndAccessTest`(관찰 훅, 2단계 적용), `ModLinkVerifierTest`(주입 인터페이스, `135`·`137`), `ForeignAccessWideningsTest`, 26.1·26.2 `BuiltInRulesTest`(새 규칙 3개가 실제 jar에서 계획·검증·훅 연결됨).
- `fabricMixin` 시나리오(게임 테스트 서버): `@Inject`(HEAD·RETURN·취소), `@Invoker`·`@Accessor`(정적), `@ModifyArg`, MixinExtras `@WrapOperation`, 설정 플러그인(Mixin 하나 끔), Poly 모드 로더가 이미 변환한 메서드에 쌓인 Mixin(`getServerModName() = poly+fabric_mixin`), 일반·중첩·제네릭 인터페이스 주입과 그 기본 메서드 호출.
- 실제 모드 묶음(Modrinth 26.2 Fabric 모드 52개와 의존 모드, `compatibilityCorpus`):
  - 판정: 함께 로드 가능한 직접 설치 Fabric 모드가 클라이언트 3 → 42, 전용 서버 3 → 28(나머지 24는 클라이언트 전용 `108` 등)입니다.
  - 실제 서버 시작(`net.minecraft.server.Main`): 로드 가능한 모드 99개 전부가 Mixin 적용·초기화·Fabric API 레지스트리 동결을 거쳐 EULA 확인까지 갑니다.
  - 게임 테스트 서버: 97개(Xaero 2개 제외)로 월드 생성, GameTest 통과, 틱, 저장까지 됩니다.

## 결과와 위험

- Fabric 모드가 Fabric Loader에서와 같은 바이트코드와 시점으로 실행됩니다. Mixin 적용 실패는 Fabric처럼 그 클래스를 로드할 때 일어나며, 오류에 Mixin 설정과 모드 ID가 나옵니다(`POLY-COMPAT-133`).
- **버전별 위험:** 초기화 지점과 관찰 규칙은 Minecraft 메서드에 묶여 있습니다. 새 버전 어댑터마다 `javap`로 다시 확인해야 합니다(`BuiltInRulesTest`가 검사). Mixin·MixinExtras·Fabric Loader 버전을 올리면 서비스 인터페이스, 호환 수준 표, 공개 API 닫힘을 다시 확인해야 합니다.
- **유지 비용:** Fabric Loader 소스를 옮긴 부분(`PolyMixinService`, `MixinCompatibility`, `EnvironmentStripper`, `InterfaceInjector`)은 Fabric Loader가 바뀌면 따라 고쳐야 합니다. 각 파일 머리에 출처와 라이선스가 있습니다.
- Mojang의 게임 테스트 서버는 전용 서버도 클라이언트도 아닙니다. 다음 두 가지는 Fabric Loader에서도 같은 진입점으로 실행하면 똑같이 실패합니다. 그래서 하네스 모드(`fabric-gametest-harness`)와 제외 목록으로 다루고, 실제 서버 시작은 따로 검증합니다(`corpusFabricServer`).
  - Fabric API는 `net.minecraft.server.Main`에서만 레지스트리를 동결합니다.
  - Xaero 모드는 `DedicatedServer`일 때만 서버 준비를 마칩니다.
- 다른 로더의 "wrong loader" 안내 모드(JourneyMap Forge jar 안의 Fabric 스텁)는 `preLaunch`에서 일부러 시작을 멈춥니다. Fabric과 같습니다.
- 클라이언트에서의 실제 모드 실행, NeoForge·Forge 모드(FML)는 이 결정 범위 밖입니다.

## 후속 (2026-10-22, 같은 날)

1. **문맥 클래스로더.** 게임 클래스로더를 만든 직후, Mixin과 `preLaunch`보다 먼저 스레드 문맥 클래스로더로 둡니다. Fabric의 Knot과 같습니다. 그 전에는 `preLaunch` 뒤에 바꿨습니다. 그래서 `ServiceLoader.load(Class)`로 서비스를 찾는 Mixin 설정 플러그인(ImmediatelyFast)이 클라이언트 시작을 멈췄습니다.
2. **`getGameInstance`. ADR-0016을 고칩니다.**
   - 버전 어댑터가 게임 객체를 알려 줍니다(`GameVersionAdapter.gameInstance`). 브리지는 `BridgeLaunchContext.gameInstance`로 받습니다.
   - 클라이언트는 `Minecraft.getInstance()`입니다. 생성자 앞부분에서 정해지므로 Fabric 초기화 시점에는 이미 있습니다.
   - 서버는 `MinecraftServer.runServer()`가 시작될 때 기록합니다(새 규칙 `poly:server_running`). 그래서 Fabric처럼 서버의 `onInitialize` 동안에는 `null`입니다.
   - 통합 서버는 돌려주지 않습니다.
   - 이제 Fabric 브리지에 막는 공개 API 멤버가 없습니다. `111`은 Fabric Loader 내부 클래스에만 남습니다.
3. **클라이언트 검증.** 새 시나리오 `corpusFabricClient`는 창이 열리므로 `compatibilityCorpus`에 넣지 않았습니다. 26.2 실제 모드 묶음의 Fabric 모드 137개가 클라이언트에서 월드에 들어갑니다.
