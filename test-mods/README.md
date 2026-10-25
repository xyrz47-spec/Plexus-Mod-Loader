# test-mods

## 이 모듈들이 하는 일
호환성 시나리오(`:poly-bootstrap:compatibilityMatrix`)에 넣는 작은 모드들입니다. 하나하나가 Poly 모드 로더가 다뤄야 하는 경우를 하나씩 재현합니다. 배포용 모드가 아닙니다.

## 무엇을 소유하는가

| 모듈 | 로더 | 재현하는 경우 | 기대 결과 |
|---|---|---|---|
| `fabric-entrypoint-only` | Fabric | 엔트리포인트만 쓰는 모드 | 로드 (`Experimental`) |
| `neoforge-blocked` | NeoForge | Mixin을 쓰는 FML 모드 | NeoForge가 실행되지 않는 실행(Java agent 없음: `216`)에서 차단 (`201`, `202`), 다른 모드는 계속 |
| `transform-conflict` | Native | 내장 규칙과 같은 메서드를 고치는 변환 | 두 규칙 모두 거부 (`POLY-TRANSFORM-004`) |
| `native-library` | Native | 다른 로더의 모드가 의존하는 Native 라이브러리 | 로드 |
| `fabric-library` | Fabric | 다른 로더의 모드가 의존하는 Fabric 라이브러리(`provides` 별칭 포함) | 로드 |
| `native-addon` | Native | Fabric 모드에 의존하는 Poly 모드 | 로드. Poly 모드는 게임 시작 전, Fabric 모드는 Minecraft 안 초기화 지점에서 초기화되므로(ADR-0020) Fabric 라이브러리의 클래스는 보지만 초기화 전 값(`not ready`)을 봄 |
| `fabric-addon` | Fabric | Fabric 별칭과 Poly 모드에 의존하는 Fabric 모드(`breaks` 포함) | 로드, 두 라이브러리 다음에 실행 |
| `fabric-wrong-version` | Fabric | `minecraft: ~1.21.4` 모드 | 차단 (`114`) |
| `fabric-chain` | Fabric | 차단된 Fabric·NeoForge 모드와 설치되지 않은 모드에 의존 | 차단 (`114`, 이유 3개) |
| `fabric-stale` | Fabric | 26.2에 없는 메서드·클래스를 부르는 코드(컴파일용 스텁 `src/stubs`, jar에는 없음) | 차단 (`112`) |
| `fabric-launcher-user` | Fabric | Fabric Loader 내부 `FabricLauncherBase.getLauncher().addToClassPath`로 모드 안의 jar를 꺼내 넣고 그 클래스를 씀(CustomSkinLoader 방식), `FabricLoaderImpl.getGameProvider()`로 게임 버전을 읽음(veinminer 방식) | 로드, 게임 클래스로더가 그 클래스를 정의, 버전을 읽음 |
| `fabric-loader-user` | Fabric | Poly 모드 로더가 제공하지 않는 Fabric Loader 내부 클래스(`Knot`) 사용 | 차단 (`111`) |
| `fabric-api-user` | Fabric | `FabricLoader.getInstance()`로 모드 조회·메타데이터·사용자 진입점·공유 객체·설정 폴더 사용. 같은 게임의 Forge 모드 `forge_hello`도 조회 | 로드, 모두 동작. `forgeFabricGameTest`에서 `forge_hello`가 보임(ADR-0025) |
| `fabric-adapter-provider` | Fabric | `languageAdapters`로 어댑터 `poly_test` 제공 | 로드 |
| `fabric-adapter-user` | Fabric | 진입점을 `poly_test` 어댑터로 생성 | 로드, 어댑터가 진입점을 만듦 |
| `fabric-malformed` | Fabric | 값의 JSON 형식이 틀린 `fabric.mod.json` | 차단 (`100`), 예외 없음 |
| `fml-library` | NeoForge | 매니페스트 `FMLModType`만 있는 라이브러리 jar | 차단 (`205`) |
| `neoforge-hello` | NeoForge | NeoForge 모드를 NeoForge에서처럼 씀: `@Mod`, 생성자의 모드 이벤트 버스와 `ModContainer`, `FMLCommonSetupEvent`, `DeferredRegister.Items`로 아이템 하나, `ModList.get().isLoaded`, Poly 모드 `native_lib`를 `ModList.get().isLoaded`·`getModFileById`·`getModContainerById`로 조회. 실제 26.2 게임 클래스와 NeoForge API로 컴파일(`gradle.properties`의 `polyCompileMinecraft`) | FML 11이 로드(ADR-0026, `neoForgeServerBoot`, `neoForgeGameTest`, `neoForgeClient`). Poly 모드가 FML 모드 목록에 보임(ADR-0025) |
| `neoforge-mixin` | NeoForge | `neoforge.mods.toml`의 `[[mixins]]` 두 개: 하나는 NeoForge가 패치한 `MinecraftServer.tickServer`에 `@Inject`, `getServerVersion`에 MixinExtras `@ModifyReturnValue`, 다른 하나는 설치되지 않은 모드를 `requiredMods`로 요구(jar에 그 파일 없음). 실제 26.2 게임 클래스로 컴파일 | FML이 첫 설정만 적용하고 둘째는 FML처럼 건너뜀. Fabric 모드와 함께면 Fabric의 Mixin이 적용(ADR-0026 30, `neoForgeFabricGameTest`) |
| `fabric-registry` | Fabric | `onInitialize`에서 `Registry.register(BuiltInRegistries.ITEM, …)`로 아이템을 바로 등록하고, NeoForge·Forge가 패치하는 `MinecraftServer.tickServer`의 Mixin에서 첫 틱에 그 아이템이 레지스트리에 남았는지 적음 | 로드, 등록되고 남음. NeoForge와 함께면 NeoForge의 레지스트리를 Fabric 초기화 동안 엶(ADR-0026 31, `neoForgeFabricGameTest`) |
| `multi-loader-fallback` | Fabric, NeoForge | `fabric.mod.json`과 `neoforge.mods.toml`을 다 든 jar. NeoForge 쪽은 없는 NeoForge(`[99,)`)를 요구 | NeoForge가 Fabric 모드와 함께 실행될 때 NeoForge 쪽은 `217`로 빠지고 Fabric 쪽이 실행됨(ADR-0026 35, `neoForgeFabricGameTest`) |
| `fabric-twin` | Fabric | ID `twin-mod`(제공 ID `twin-mod-api`). `onInitialize`에서 공유 클래스 `TwinConfig`로 설정 `example`을 등록. 같은 설정을 두 번 등록하면 멈춤(Cloth Config의 `AutoConfig`처럼) | NeoForge와 함께면 `neoforge-twin`과 같은 모드로 보고 `139`로 빠짐(ADR-0026 37, `neoForgeFabricGameTest`) |
| `neoforge-twin` | NeoForge | ID `twin_mod`. `fabric-twin`과 같은 `TwinConfig`를 갖고, 모드 생성자에서 같은 설정을 등록 | 이 빌드만 실행됨. 고치기 전에는 `Config 'example' already registered`로 멈춤 |
| `fabric-client-bootstrap-probe` | Fabric(클라이언트) | `-Dfabric_client_bootstrap_probe.stop=true`이면 `Minecraft.<init>`이 게임 창(`new Window`)을 만들기 바로 앞에서 레지스트리 상태를 적고 클라이언트를 끝냄. NeoForge가 치명적 오류를 알리면 FML의 오류 대화 상자 대신 종료(`@Pseudo` mixin) | 진단 하네스, Poly 모드 로더 기능이 아님(ADR-0026 38, `corpusFabricNeoForgeClientBootstrap`) |
| `fabric-network-probe` | Fabric | 게임 테스트 서버의 메모리 채널로 클라이언트처럼 접속해 로그인·설정·놀이 단계를 지나고, 같은 모드를 가진 클라이언트처럼 답함(채널 등록, `c:version`·`c:register`, NeoForge 질의, 바닐라·Fabric API·NeoForge의 설정 작업). 놀이 단계에서 자기 Fabric API 채널(`fabric_network_probe:echo`)로 보내고 받으며, `neoforge-network-probe`의 페이로드를 되돌려 보내고, 서버 쪽에서 Fabric 페이로드 묶음을 보냄. 받은 페이로드와 연결 유지 여부를 적음. 보고할 때까지 게임 테스트 서버가 멈추지 않게 함 | 진단 하네스(ADR-0026 40·41, `corpusFabricNeoForgeJoin`) |
| `neoforge-network-probe` | NeoForge | NeoForge 모드처럼 놀이 단계 페이로드 `neoforge_network_probe:echo`를 `playBidirectional`로 등록하고, 접속한 플레이어에게 `hello`를 보내며, 받은 것에 `pong: <내용>`으로 답함. 실제 26.2 게임 클래스와 NeoForge API로 컴파일 | 진단 하네스(ADR-0026 41, `corpusFabricNeoForgeJoin`) |
| `fabric-twin-user` | Fabric | `twin-mod`가 필요하고 `TwinConfig`를 씀. `isModLoaded("twin-mod")`, `isModLoaded("twin-mod-api")`를 적음 | NeoForge 빌드가 대신 충족해 로드됨, 둘 다 `true` |
| `neoforge-gametest-harness` | NeoForge | 게임 테스트 서버에서만 쓰는 하네스. NeoForge 26.2.0.93은 운영 환경(설치한 NeoForge, NeoForge 자신의 실행기 포함)에서 Mojang의 `GameTestTicker`를 돌리지 않아(`GameTestHooks.isGametestEnabled`) 테스트가 시작되지 않음. 게임 테스트 서버이고 NeoForge가 직접 돌리지 않을 때만 서버 틱 끝에 돌림. NeoForge 26.1 이상을 요구 | 로드. Poly 모드 로더 기능이 아님 (`neoForgeGameTest`, `neoForgeWorldTest`, `corpusNeoForge`, `serverInstallTest`, NeoForge 벤치마크) |
| `neoforge-client-session` | NeoForge | `fabric-client-session`의 NeoForge 판: NeoForge 이벤트(`ClientTickEvent.Post`, `ScreenEvent.Init.Post`)만으로 월드에서 200틱을 보낸 뒤 "저장 후 타이틀로"처럼 나가고, 타이틀 화면에서 40틱 뒤 클라이언트를 끔. 월드가 백업을 물으면 백업 없이 들어감. `@Mod(dist = CLIENT)` | 클라이언트에서 로드, 틱·저장·종료까지 (`neoForgeClient`, `corpusNeoForgeClient`, `launcherProfileNeoForgeTest`) |
| `neoforge-early-service` | NeoForge | Sodium의 NeoForge 빌드와 같은 구조: 설치하는 jar는 초기 서비스(`GraphicsBootstrapper`)가 든 `FMLModType: LIBRARY`이고, 모드는 안의 JarJar(`META-INF/jarjar/`, `metadata.json`)에 있음 | FML이 바깥 jar를 초기 서비스로 싣고 안의 모드를 생성 (`neoForgeEarlyService`, `serverInstallTest`의 mods 폴더) |
| `neoforge-environment-probe` | NeoForge | 여러 로더용 라이브러리가 보는 것을 로그에 적음: Fabric Loader·Quilt Loader API 클래스가 보이는지, Lambda Dynamic Lights가 있으면 그 기본 설정 파일을 Yumi의 `ModContainer.findPath`로 찾음(Lambda Dynamic Lights가 클라이언트에서 하는 조회를 어느 쪽에서나). 공통 설정에서 실행 | 로드, API는 보이지 않고 Yumi는 NeoForge 쪽 컨테이너로 파일을 찾음 (`corpusNeoForge`, `corpusNeoForgeClient`) |
| `neoforge-unmet` | NeoForge | `neoforge.mods.toml` 요구 조건이 맞지 않는 모드(`neoforge` `[27,)`, 설치되지 않은 필수 모드). 메타데이터만 있음 | FML 시작 전에 빠짐 (`217`), 나머지는 실행 (`neoForgeGameTest`) |
| `forge-hello` | MinecraftForge | Forge 모드를 Forge에서처럼 씀: `@Mod`, 모드 이벤트 버스, `FMLCommonSetupEvent`, `DeferredRegister`로 아이템 하나. 공통 설정에서 `ModList.isLoaded`·`getModFileById`·`getModContainerById`로 Fabric 모드 `fabric_lib`(별칭 포함)와 Poly 모드 조회. 실제 26.2 게임 클래스와 Forge API로 컴파일(`gradle.properties`의 `polyCompileMinecraft`) | FML이 로드, 모두 동작 (`forgeGameTest`). 다른 로더의 모드가 FML 모드 목록에 보임(ADR-0025, `forgeFabricGameTest`) |
| `fabric-bundle` | Fabric | 라이브러리를 중첩 jar로 담은 모드 | 로드, `124` 경고(안쪽 하나가 막힘) |
| `fabric-nested-lib` | Fabric | `fabric-bundle` 안의 라이브러리. 자신도 `fabric-nested-deep`을 담음 | 로드, 바깥 모드보다 먼저 실행 |
| `fabric-nested-deep` | Fabric | 두 겹 안쪽 중첩 jar | 로드 |
| `fabric-nested-mixin` | Fabric | 더 새 Mixin(9.0)이 필요한 `required` Mixin 설정을 가진 중첩 모드 | 이것만 차단 (`132`) |
| `fabric-class-tweaker` | Fabric | class tweaker로 26.2의 private 필드(`Mth.SIN`, accessible·mutable)와 private 메서드(`SharedConstants.prefixDebugFlagName`)를 넓히고 진입점에서 씀(컴파일용 스텁 `src/stubs`, jar에는 없음) | 로드, 두 멤버 모두 동작 |
| `fabric-class-tweaker-stale` | Fabric | 26.2에 없는 필드·클래스를 넓히는 class tweaker | 로드, 그 줄은 적용하지 않음 (`137`, 줄 번호 포함). Fabric Loader도 그런 줄은 아무것에도 적용하지 않음 |
| `fabric-mixin` | Fabric | Mixin(`@Inject`·`@Invoker`·`@Accessor`·`@ModifyArg`)·MixinExtras(`@WrapOperation`)·설정 플러그인·클라이언트 전용 설정으로 26.2 클래스와 자기 클래스를 바꾸고, class tweaker로 일반·중첩·제네릭 인터페이스를 `MinecraftServer`에 주입(컴파일용 스텁 `src/stubs`) | 로드, 모두 동작. Poly 모드 로더가 이미 변환한 메서드 위에 Mixin이 쌓임 |
| `fabric-client-session` | Fabric | 클라이언트 Mixin으로 월드에서 200틱을 보낸 뒤 "저장 후 타이틀로"처럼 나가고 클라이언트를 끔(실제 클라이언트 jar로 컴파일). `corpusFabricClient`에서 실제 모드와 함께 | 로드, 틱·저장·종료까지 |
| `fabric-mixin-broken` | Fabric | jar에 없는 Mixin 설정과 더 새 Mixin이 필요한 `required` 설정 | 차단 (`132`), 없는 설정은 `136` 경고 |
| `fabric-api-events` | Fabric | 실제 Fabric API(모드 묶음의 `fabric-api` jar)를 Fabric 모드처럼 씀: 서버가 시작되면 가짜 플레이어가 블록을 부숨(생존, 블록 엔티티, 리스너가 취소, 창작) — `PlayerBlockBreakEvents` BEFORE·AFTER·CANCELED, 데이터 팩 레시피 두 개가 Fabric 리소스 조건 뒤에 있음, 게임의 조합 코드로 용암 양동이의 남는 아이템을 읽음(컴파일용 스텁 `src/stubs`, jar에는 없음) | 로드, 모두 동작. `fabricApiGameTest`(바닐라)와 `forgeFabricApiGameTest`(MinecraftForge, ADR-0021 G8)에서 같은 줄 |
| `fabric-host-patch-probe` | Fabric | Forge 패치가 더하거나 바꾼 멤버와 Fabric API mixin이 겹치는 곳을 Fabric API처럼 씀: 서버 패킷 리스너를 네트워킹 접근자로 캐스트해 `getConnection()`·`getServer()` 호출, Forge의 `remove`와 Fabric의 `fabric:remove`가 함께 있는 아이템 태그, 속성 빌더 접근자 호출, 발전 과제 빌더의 인터페이스. 게임과 Fabric API는 리플렉션으로 씀. `corpusFabricForge`에서 | 접근자 동작, 태그에 `minecraft:stone`만 남음, 속성 빌더 접근자는 `214`, 발전 과제 빌더는 인터페이스 구현 |
| `quilt-hello` | Quilt | `quilt.mod.json`만 든 Quilt 모드(ADR-0027 Q3): Quilt Loader API(`QuiltLoader`, `MinecraftQuiltLoader`, 모드 조회·소스 종류·그룹, 자기 메타데이터와 사용자 값, `getModContainer(Class)`, 사용자 진입점 `quilt_hello:custom`), 진입점 `pre_launch`·`preLaunch`·`main`·`client`·QSL `init`, `provides`(버전 있는 것 포함), Quilt 뜻의 `depends`(`optional`, `unless`, ANY 배열, `versions` 객체, 연산자 없는 `1.0.0` = `^1.0.0`)와 `breaks`(ALL 배열), 중첩 jar 셋(`fabric-nested-lib`, `quilt-nested-lib`, `quilt-nested-unused`), `mixin`(`MinecraftServer`에 `@Inject`), `access_widener` 배열(파일 둘), `@ClientOnly`·`@DedicatedServerOnly`·`@Requires` 제거 | 로드, 모두 동작. `pre_launch` → `preLaunch` → `main` 순서, `init`은 실행하지 않음(`303`), 사용자 키 `304` (`quiltOnly`, `quiltFabricPoly`, `quiltBlocked`) |
| `quilt-nested-lib` | Quilt | `quilt-hello` 안의 Quilt 라이브러리, `load_type` 기본값(`if_required`). `quilt_hello`가 필요로 함 | 로드, 소스 경로가 `[[바깥 jar, 안의 경로]]` |
| `quilt-nested-unused` | Quilt | `quilt-hello` 안의 Quilt 모드, `load_type` 기본값, 아무도 필요로 하지 않음 | 로드하지 않음 (`307`), 바깥 모드의 `124`에 세지 않음 |
| `fabric-quilt-dual` | Fabric, Quilt | `fabric.mod.json`과 `quilt.mod.json`을 다 든 jar(같은 ID, 다른 진입점). Fabric 진입점이 `FabricLoader`로 `quilt_hello`를 봄 | Fabric 모드로 로드(`310`), Fabric 쪽 진입점만 실행 (`quiltFabricPoly`) |
| `quilt-qsl-resource` | Quilt | 클래스 없이 리소스만 있고 QSL의 `quilt_resource_loader`가 필요한 모드(26.x Quilt 전용 판 대부분과 같음) | 차단 (`114` + `308`), 나머지는 실행 (`quiltBlocked`) |
| `quilt-blocked` | Quilt | jar 넷을 만드는 한 프로젝트: `quilt-plugin`(`experimental_quilt_loader_plugin`), `quilt-malformed`(`intermediate_mappings` 없음), `quilt-client-only`(`minecraft.environment: client`), `quilt-init-only`(QSL `init`만 있음) | 차단 `301`, `300`, 서버에서 `305`. `quilt_init_only`는 로드되고 `303` (`quiltBlocked`) |
| `fabric-gametest-harness` | Fabric | 실제 모드 묶음을 Mojang 게임 테스트 서버에서 돌릴 때만 쓰는 하네스. Fabric API가 `net.minecraft.server.Main`에서 하는 레지스트리 동결을 게임 테스트 시작에서도 함 | 로드. Poly 모드 로더 기능이 아님 |
| `fabric-mixin-audit` | Fabric | `-Dpoly.mixinAudit=true`일 때 `preLaunch`에서 이 쪽의 모든 Mixin 설정이 겨냥하는 클래스를 초기화 없이 로드해 Mixin을 모두 적용해 보고, 실패한 클래스를 알린 뒤 게임 시작 전에 JVM을 멈춤(클라이언트도 창이 열리지 않음). 진단용 | `mixinAuditQuiltClient`·`mixinAuditQuiltServer`. Poly 모드 로더 기능이 아님 |

깨진 jar(`POLY-DISCOVERY-003`)는 `poly-bootstrap`의 `corruptTestJar` 작업이 만듭니다.

## 무엇을 하지 않는가
실제 배포 모드의 호환성을 대신하지 않습니다. 실제 모드는 `:poly-bootstrap:compatibilityCorpus`가 Modrinth에서 받아 검증합니다.

## 의존성
`poly-lifecycle-api`(Native), `net.fabricmc:fabric-loader`(Fabric, `compileOnly`, 비전이), `org.quiltmc:quilt-loader`(Quilt, `compileOnly`, 비전이; Fabric Loader보다 뒤에 둠 — Quilt Loader jar에 Fabric Loader 클래스의 사본이 있음), slf4j-api

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:compatibilityMatrix
```

## 대표 흐름
`poly-bootstrap/build.gradle.kts`의 시나리오가 모드 묶음과 기대 로그 줄을 정합니다. 시나리오마다 새 게임 테스트 서버를 띄워 확인합니다.

## 알려진 제한
모두 전용 게임 테스트 서버에서만 실행합니다. 클라이언트 쪽 판정은 `corpusAssessment`가 게임을 띄우지 않고 확인합니다.
