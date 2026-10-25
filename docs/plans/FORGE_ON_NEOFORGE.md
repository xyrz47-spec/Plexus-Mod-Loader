# Forge on NeoForge 계획 (2026-10-25 작성)

MinecraftForge 모드와 NeoForge 모드를 한 게임에서 실행하기 위한 계획입니다. 이 문서만 읽고 작업을 시작할 수 있게, 지금의 사실·장벽·방안·단계를 모았습니다. 아직 [`ROADMAP.md`](ROADMAP.md)에 없는 일입니다. 시작할 때 ROADMAP에 단계를 더하고(1.0 이후, 26.3 다음 또는 나란히) ADR-0030을 씁니다(아래 "ADR 초안").

원칙은 Fabric·Forge·NeoForge 때와 같습니다.
- **로더의 의미를 추측해 다시 만들지 않습니다.** 쓸 수 있는 곳은 Forge·NeoForge 자신의 코드를 그대로 실행합니다. 다시 만드는 것은 두 로더 사이를 잇는 부분뿐이고, 그 부분은 실제 jar를 `javap`로 확인한 뒤 씁니다(AGENTS.md 원칙 6).
- **실패하는 검증을 먼저 만들고, 그것을 통과시킵니다.**
- **크래시 대신 판정.** 옮길 수 없는 Forge 모드는 게임 코드가 돌기 전에 이유(새 오류 코드)와 함께 뺍니다.

## 목표의 뜻

"Forge on NeoForge"는 **NeoForge가 게임을 갖고(호스트), 같은 게임에서 MinecraftForge 모드도 실행하는 것**으로 봅니다. Sinytra Connector가 NeoForge 위에서 Fabric 모드를 실행하고, Poly 모드 로더가 Forge 위에서 Fabric 모드를 실행하는 것(ADR-0021 G8)과 같은 방향입니다.

이렇게 본 근거:
- [ADR-0026](../adr/0026-neoforge-on-poly-mod-loader.md) 34번이 Forge·NeoForge 모드를 함께 실행하는 세 방안을 견주었고, 실현할 수 있는 것은 "한 로더가 게임을 갖고 다른 로더의 API를 옮겨 주는 층"뿐이라고 결론지었습니다(나머지 둘은 아래 "고르지 않은 것").
- 어느 쪽이 호스트인지: NeoForge를 호스트로 합니다. 실제 묶음에서 NeoForge 모드가 더 많고(Modrinth 표시 수, [`MINECRAFT_26_3.md`](MINECRAFT_26_3.md) "로더의 26.3 빌드": 26.2 NeoForge 6,886 대 Forge 3,426), NeoForge FML에는 다른 형식의 모드 파일을 받아들이는 SPI가 있습니다(아래 "NeoForge FML의 SPI"). 반대 방향(Forge 위의 NeoForge)은 이 계획 밖입니다.
- Forge 모드만 있을 때는 지금처럼 실제 Forge가 호스트입니다(ADR-0021). 이 계획은 **두 로더의 모드가 함께 있을 때**만 바꿉니다.

## 지금 상태 (2026-10-25, 사실)

- Forge 모드와 NeoForge 모드를 함께 넣으면 **Forge가 실행되고 NeoForge 모드가 빠집니다.** `PolyRuntime`(`poly-runtime-impl/src/main/java/dev/polymodloader/runtime/PolyRuntime.java`)이 `forgeJars(files)`가 비어 있지 않으면 NeoForge 호스트를 찾지 않고(`neoForgeJars = forge ? List.of() : …`), `reportNeoForgeBesideForge`가 `POLY-COMPAT-221`을 한 번 알린 뒤 NeoForge 모드를 각각 `200..205`로 진단합니다. [`COMPATIBILITY.md`](../COMPATIBILITY.md)의 NeoForge 줄에도 "MinecraftForge 모드와 한 게임에서는 실행하지 않음(`221`)"으로 적혀 있습니다.
- 호스트 고르기: Forge 모드가 있으면 `gameHost(...)`(MinecraftForge, ADR-0021), 없으면 `neoForgeHost(...)`(NeoForge FML, ADR-0026). NeoForge를 실행할 수 없으면 `216`. `-Dpoly.neoforge=off`로 NeoForge를 끕니다.
- 호스트 SPI: `poly-bridge-api`의 `GameHost`(`prepare(GameModuleLayers, GameClassBytes)`, `launch()`, `sharedMixinConfigs()`, `injectionPointReplacements()`, `transformRules()`, `classTransformer()`, `otherMods(...)` 등)와 `ModLoaderBridge.gameHost(Request, List<Path>)`. Forge 쪽은 `poly-bridge-forge`의 `MinecraftForgeBridge`·`ForgeGameHost`·`ForgeInstallation`·`ForgePatchSet`·`ForgeModListPatch`, NeoForge 쪽은 `NeoForgeBridge`·`NeoForgeGameHost`·`NeoForgeInstallation`이 맡습니다.
- 다른 로더의 모드를 호스트의 모드 목록에 보이는 장치(ADR-0025, NeoForge는 ADR-0026 19번, `218`)와, Fabric mixin을 호스트 패치에 맞추는 장치(`InjectionPointReplacement`, `138`·`212`·`213`)가 이미 있습니다.

## 장벽 (실제 jar로 확인)

확인한 파일: Forge `26.2-65.1.5`와 NeoForge `26.2.0.93`의 설치 결과(`poly-bootstrap/build/poly/scenarios/*/.poly/{forge,neoforge}/<버전>/libraries`, 사용자 PC에서만 만들어짐), 26.2 묶음의 Forge 모드 41개(`poly-bootstrap/build/poly/mod-corpus/26.2/forge`).

### 1. 게임 클래스는 하나 (결정 요인, ADR-0026 34번의 측정)

Minecraft 26.2 서버 jar를 Forge·NeoForge가 각각 패치한 결과를 견주면, 두 쪽이 다 바꾸는 클래스 549개, 메서드 2,342개이고 그중 736개(288개 클래스)는 같은 바닐라 명령을 서로 다르게 바꿉니다. `Entity`·`Level`·`BlockEntity` 등 6개 클래스는 두 로더가 서로 다른 상위 클래스를 둡니다. 그래서 **두 패치를 함께 얹을 수 없고, NeoForge 패치만 얹은 게임에서 Forge 모드가 기대하는 Forge 패치의 부분을 다른 방법으로 채워야 합니다.**

### 2. 라이브러리와 클래스 이름

두 설치의 `libraries`에서 클래스 이름이 겹치는 것을 세었습니다(Python `zipfile`, 패치된 Minecraft jar 제외).

| | Forge 26.2-65.1.5 | NeoForge 26.2.0.93 |
|---|---|---|
| 라이브러리 클래스 수 | 4,936 | 4,072 |
| 로더 자신의 클래스 겹침 | 0 (Forge는 `net.minecraftforge.*`, NeoForge는 `net.neoforged.*`) | |
| 겹치는 제3자 클래스 | 1,213개 | |
| Mixin | `org.spongepowered:mixin` 0.8.7 + `mixinextras-forge` 0.5.4 | `net.fabricmc:sponge-mixin` 0.17.4+mixin.0.8.7 |
| night-config | 3.7.4 | 3.9.0 |
| jline | 3.25.1 | 3.20.0 |
| ASM | 9.10.1 | 9.10.1 |
| 이벤트 버스 | `net.minecraftforge:eventbus` 7.0.5 | `net.neoforged:bus` 8.0.5 |
| Access transformer | `net.minecraftforge:accesstransformers` 8.2.17 | `net.neoforged:accesstransformers` 11.0.2 |
| 그 밖에 Forge에만 | `modlauncher` 10.2.6, `securemodules` 2.2.24, `coremods-api` 5.3.1, `forgespi` 8.0.0, `bootstrap` 2.1.8 | |

뜻: Forge의 API 클래스(이벤트 버스, `forgespi`, 이벤트 클래스)는 이름이 겹치지 않으므로 NeoForge 게임 안에 **라이브러리로 함께 둘 수 있습니다.** 겹치는 제3자 라이브러리(Mixin, night-config, jline)는 NeoForge 쪽 판 하나만 둡니다. Mixin은 이미 하나만 쓰고(ADR-0026 30번), night-config 3.7.4 → 3.9.0 차이는 F-N0에서 Forge의 `ForgeConfigSpec`이 3.9.0에서 링크되는지로 확인합니다.

### 3. 메타데이터와 모드 생성

- NeoForge FML 11(`fancymodloader/loader` 11.0.16)은 `META-INF/neoforge.mods.toml`만 읽습니다(`ModFileParser`, `JarModsDotTomlModFileReader`, `GameLocator`, `InDevJarLocator`에 그 문자열). `META-INF/mods.toml`은 `IncompatibleModReason`에만 있어, Forge 모드를 "호환되지 않음"으로 알리는 데만 씁니다.
- Forge 모드의 진입점은 `net.minecraftforge.fml.common.Mod`이고 NeoForge의 `javafml`은 `net.neoforged.fml.common.Mod`를 찾습니다. 두 쪽 모두 `modLoader = "javafml"`이라 이름이 같지만 다른 언어 공급자입니다.

### 4. NeoForge FML의 SPI (연결 지점)

`loader-11.0.16.jar`의 `net.neoforged.neoforgespi`(javap):
- `locating.IModFileReader.read(JarContents, ModFileDiscoveryAttributes)` → `IModFile`: 다른 형식의 jar를 모드 파일로 읽음
- `locating.IModFileCandidateLocator.findCandidates(ILaunchContext, IDiscoveryPipeline)`
- `language.IModLanguageLoader.loadMod(IModInfo, ModFileScanData, ModuleLayer)` → `ModContainer`: 모드 생성
- `transformation.ClassProcessor`·`ClassProcessorProvider`: 바이트코드 변환(Poly 모드 로더는 이미 브리지 변환 단계에서 FML의 `ClassProcessor`를 부름, ADR-0026)

Sinytra Connector가 NeoForge 위의 Fabric 모드에 쓰는 것과 같은 종류의 지점입니다. 실제 동작은 F-N2에서 확인합니다.

### 5. Forge 모드가 쓰는 Forge API (26.2 묶음 41개)

방법: 각 jar(중첩 jar 포함)의 클래스 바이트에서 `net/minecraftforge/…` 이름을 정규식으로 모았습니다. 상수 풀 문자열까지 세므로 대략값입니다. F-N0에서 상수 풀 파서로 다시 셉니다.

- 서로 다른 Forge 클래스 338개. jar당 중앙값 21개, 가장 많은 것은 Open Parties and Claims(104개), 하나도 없는 것은 Dungeons and Taverns(데이터만).
- 패키지별: `event` 88, `client` 82, `fml` 54, `common` 36, `network` 18, `registries` 16, `eventbus` 11, `forgespi` 10, `server` 10, `fluids` 5.
- 많이 쓰는 것: `Mod` 40, `Dist` 31, `FMLJavaModLoadingContext` 29, `ModList` 23, `FMLEnvironment` 23, `EventBus` 22, `SubscribeEvent` 21, `IExtensionPoint$DisplayTest` 18, `FMLPaths` 16, `BusGroup` 15, `CustomPayloadEvent`·`ChannelBuilder` 13, `TickEvent` 13, `FMLClientSetupEvent` 12, `PacketDistributor` 11, `ConfigScreenHandler` 10, `RegisterKeyMappingsEvent` 10.
- 338개 중 211개는 NeoForge 26.2.0.93 jar들에 같은 짧은 이름의 클래스가 있습니다(뜻이 같다는 보장은 아님). 없는 것의 예: capability 전체(`Capability`, `CapabilityManager`, `AttachCapabilitiesEvent`), `ForgeConfigSpec`, Forge 네트워크(`ChannelBuilder`, `Channel`, `EventNetworkChannel`, `CustomPayloadEvent`), 이벤트 버스 7(`BusGroup`, `CancellableEventBus`), `FMLJavaModLoadingContext`, `ConfigScreenHandler`, `BlockEvent$BreakEvent`.
- Forge `universal` jar는 클래스 1,121개, NeoForge `universal` jar는 1,741개입니다.

### 6. 바닐라 클래스에 더한 확장 메서드

Forge 모드의 `Item`·`Block` 하위 클래스는 Forge가 바닐라에 더한 메서드(`IForgeItem` 등)를 덮어쓰고 부릅니다. 같은 자리의 NeoForge 인터페이스와 견준 결과(javap `-s`, 이름과 설명자):

| Forge | NeoForge | Forge 메서드 | NeoForge 메서드 | 이름 같음 | 이름·설명자 같음 |
|---|---|---|---|---|---|
| `IForgeItem` | `IItemExtension` | 38 | 47 | 28 | 21 |
| `IForgeBlock` | `IBlockExtension` | 52 | 64 | 51 | 40 |
| `IForgeEntity` | `IEntityExtension` | 31 | 29 | 25 | 13 |
| `IForgeItemStack` | `IItemStackExtension` | 30 | 30 | 19 | 16 |
| `IForgeBlockState` | `IBlockStateExtension` | 49 | 61 | 48 | 39 |

이름·설명자가 같은 메서드는 Forge 모드의 덮어쓰기가 NeoForge 게임에서도 그대로 덮어쓰기가 됩니다. 나머지는 설명자에 로더 고유 형(Forge `ToolAction` 대 NeoForge `ItemAbility` 같은 것)이 들어가 옮김(호출·덮어쓰기 다시 쓰기)이 필요합니다. 표에 없는 확장 인터페이스와 capability(`getCapability`, 상위 클래스 `CapabilityProvider`)는 F-N0에서 셉니다.

### 7. 클래스 변환 기능 (26.2 묶음 41개)

Mixin 31개, access transformer(`META-INF/accesstransformer.cfg`) 24개, coremod(`META-INF/coremods.json`) 2개, 중첩 jar 7개, 여러 로더용 jar(`fabric.mod.json`이나 `neoforge.mods.toml`도 있음) 6개.

- Mixin: 이미 하나의 Mixin을 공유합니다. Forge 모드의 mixin이 **Forge 패치에만 있는 코드**를 겨누면 NeoForge 게임에는 그 대상이 없습니다. Fabric mixin을 Forge 패치에 맞출 때와 같은 판정(`138`·`212`·`213`)이 필요합니다.
- Access transformer: 두 로더의 형식이 같은 문법인지 F-N0에서 확인합니다. NeoForge의 AT `ClassProcessor`가 Forge 모드의 파일을 읽게 할지, Poly 모드 로더의 접근 확대(ADR-0013, ADR-0018)로 바꿀지 정합니다.
- Coremod: Forge의 JavaScript coremod(`coremods-api`)는 NeoForge에 없습니다. 처음에는 새 코드로 막습니다.

### 8. 이벤트와 생명주기

Forge 패치는 바닐라 코드에서 `ForgeEventFactory`·`ForgeHooks`를 부르고, NeoForge 패치는 같은 자리에서 `EventHooks`를 부릅니다(ADR-0026 34번의 `LivingEntity` 예). NeoForge 게임에서는 Forge의 훅이 불리지 않으므로, **Forge 이벤트는 NeoForge 이벤트가 일어날 때 옮겨 쏘아야 합니다.** 값을 돌려받거나 취소하는 이벤트(`LivingHurtEvent` 등)는 Forge 쪽 결과를 NeoForge 이벤트에 되돌려야 합니다. 모드 버스의 생명주기 이벤트(`FMLCommonSetupEvent`, `FMLClientSetupEvent`, 등록 이벤트)도 NeoForge의 단계에 맞춰 쏩니다.

## 방안

| 방안 | 하는 일 | 판단 |
|---|---|---|
| A. 두 패치를 합친 게임 하나 | Forge·NeoForge 패치를 한 게임 jar에 함께 얹음 | 불가능. 736개 메서드가 충돌하고 6개 클래스는 상위 클래스가 둘이 될 수 없음(ADR-0026 34번) |
| B. 로더마다 게임 사본 | Forge·NeoForge가 각자 패치된 Minecraft와 레지스트리를 가짐 | 고르지 않음. 한 월드에 두 로더의 블록이 있을 수 없고, 창은 하나뿐이며, 비용이 두 게임의 합(ADR-0026 34번) |
| C1. Forge API를 NeoForge 위에 처음부터 다시 구현 | `net.minecraftforge.*` 전체를 새 코드로 | 고르지 않음. Forge의 의미를 추측해 다시 만드는 일(원칙 위반), 규모가 Forge `universal` 전체 |
| **C2. 섞어 쓰기(추천)** | NeoForge가 호스트. Forge 자신의 클래스 중 Forge 패치에 기대지 않는 것(이벤트 버스 7, 이벤트 클래스, `forgespi`, 설정 명세 등)은 그대로 라이브러리로 싣고, Forge 패치·Forge 로더에 기대는 부분만 NeoForge로 잇는 층을 둠 | 추천. 아래 |

C2를 고르는 이유:
- 로더 고유 클래스 이름이 겹치지 않아(장벽 2) Forge의 API jar를 NeoForge 게임에 함께 실을 수 있습니다.
- Forge 모드가 부르는 클래스 대부분이 이벤트·FML·등록(장벽 5)이고, 이벤트 클래스와 버스는 Forge 자신의 것을 쓰면 의미를 추측하지 않습니다. 새로 쓰는 것은 "NeoForge 이벤트가 일어나면 Forge 이벤트를 쏜다", "Forge 등록을 NeoForge의 `RegisterEvent`에서 한다" 같은 연결뿐입니다.
- 확장 메서드는 상당수가 이름·설명자까지 같습니다(장벽 6). 다른 것만 다시 씁니다.
- 연결할 수 없는 Forge API를 쓰는 모드는 링크 검사(`ModLinkVerifier`, ADR-0014)를 NeoForge 패치 jar + 실은 Forge 클래스 + 연결 층에 대해 돌려 시작 전에 뺍니다.

어느 Forge 클래스를 그대로 실을 수 있는지는 F-N0의 측정으로 정합니다. 그대로 실을 수 없는(Forge 패치 멤버를 부르는) 클래스는 연결 층이 같은 이름으로 대신하거나, 그것을 쓰는 모드를 막습니다.

## 완료 기준

"Forge on NeoForge"를 `Verified`로 쓰려면 지원하는 모든 Minecraft 버전에서 다음이 참이어야 합니다. 그 전까지는 `Experimental`이고, 기본값은 꺼짐입니다(`-Dpoly.forgeOnNeoForge=on`).

1. Forge 모드 묶음과 NeoForge 모드 묶음을 함께 넣은 게임이 **클라이언트**에서 저장된 월드에 들어가 틱이 돌고 저장하고 나온다.
2. 같은 조합이 게임 테스트 서버(월드 생성·틱·저장)와 전용 서버 시작(EULA 확인까지)을 통과한다.
3. 설치한 런처 프로필과 서버에서 함께 시작된다.
4. 빠지는 Forge 모드는 이유(오류 코드)와 함께 시작 전에 빠지고, 그 수와 이유를 [`COMPATIBILITY.md`](../COMPATIBILITY.md)에 적는다. Forge 모드만 넣었을 때 실제 Forge로 실행되던 결과는 바뀌지 않는다.
5. NeoForge 모드만 넣었을 때의 결과(`corpusNeoForge` 등)가 바뀌지 않는다.
6. 함께 넣은 상태의 시작 시간·틱을 측정해 [`PERFORMANCE.md`](../PERFORMANCE.md)에 남긴다.

## 단계

각 단계는 작은 PoC로 시작하고, 끝나면 이 문서의 "진행"에 결과(실행한 작업, 통과/실패 수)를 적습니다. 검증은 모두 헤드리스로 먼저 하고, 창이 열리는 작업은 마지막에 하나씩 합니다(AGENTS.md).

### F-N0. 측정 (코드 변경 없음, 결과는 이 문서에)

- 목표: C2의 범위를 숫자로 정합니다.
- 할 일:
  1. 묶음의 Forge 모드(26.1·26.1.2·26.2, 26.3이 지원되면 26.3)가 부르는 Forge 클래스·멤버를 상수 풀로 셉니다. 확장 메서드와 capability 호출(`getCapability` 등 바닐라 형에 Forge가 더한 멤버)도 셉니다. 도구는 `build-logic`의 작업으로 둘지, 일회성 스크립트로 둘지 정합니다(일회성이면 결과만 문서에).
  2. Forge `universal`·`fmlcore`·`javafmllanguage`·`eventbus`·`forgespi`의 클래스마다 "NeoForge 패치 jar + NeoForge 라이브러리 위에서 링크되는가"를 검사합니다. 기존 `ModLinkVerifier`를 그대로 쓸 수 있는지 먼저 봅니다. 결과: 그대로 실을 수 있는 클래스 목록, Forge 패치 멤버 때문에 실을 수 없는 클래스 목록.
  3. Forge 이벤트 중 묶음 모드가 듣는 것의 목록과 빈도, 각각에 해당하는 NeoForge 이벤트(같은 자리의 훅)를 표로 만듭니다. Forge가 그 이벤트를 쏘는 곳(`ForgeEventFactory`)과 NeoForge가 쏘는 곳(`EventHooks`)을 패치된 jar에서 javap로 짝짓습니다.
  4. Access transformer 파일 형식(Forge AT 8.2 대 NeoForge AT 11)과 night-config 3.7.4 → 3.9.0에서 `ForgeConfigSpec`이 링크되는지 확인합니다.
- 완료: 위 네 표가 이 문서에 있고, F-N1 이후의 범위(첫 PoC에서 다룰 API)가 정해짐.

### F-N1. 호스트 선택과 진단 (실행 없음)

- 목표: Forge 모드와 NeoForge 모드가 함께 있고 `-Dpoly.forgeOnNeoForge=on`이면 NeoForge가 호스트가 되고, Forge 모드는 각각 판정만 받습니다. 기본값(꺼짐)의 동작은 그대로(`221`).
- 바꿀 곳: `PolyRuntime`의 호스트 선택(`forgeJars`/`neoForgeJars`/`reportNeoForgeBesideForge`), `MinecraftForgeBridge`의 판정(호스트가 NeoForge일 때의 새 판정), 새 오류 코드.
- 새 test-mods: 없음(기존 `forge-hello`, `neoforge-hello` 사용).
- 검증: 새 `compatibilityMatrix` 시나리오 `forgeOnNeoForgeDiagnosed`(Forge·NeoForge 테스트 모드, 플래그 켬): NeoForge 모드가 실행되고 Forge 모드는 새 코드로 "아직 실행하지 않음"을 받음. 플래그 없는 기존 시나리오는 그대로 `221`.
- 완료: 시나리오 통과, `check` 통과.

### F-N2. Forge 모드 하나 생성 (가장 작은 PoC)

- 목표: `@net.minecraftforge.fml.common.Mod` 생성자가 NeoForge 게임에서 불리고, 모드 버스의 `FMLCommonSetupEvent`를 받습니다.
- 만들 것:
  - Forge 모드 jar를 NeoForge FML의 모드 파일로 읽는 `IModFileReader`(Forge의 `META-INF/mods.toml`을 NeoForge의 모드 정보로 옮김: modId, version, displayName, 의존성). FML의 모드 목록에 Forge 모드로 보이게 하고, `ModList`(Forge 쪽)에도 보이게 함(ADR-0025와 같은 장치).
  - Forge 모드를 생성하는 `IModLanguageLoader`(이름 예: `poly_forge_javafml`). Forge의 `FMLJavaModLoadingContext`와 모드 버스(이벤트 버스 7의 `BusGroup`)를 Forge 자신의 클래스로 만들고 생성자를 부름.
  - 이 둘과 F-N0에서 "그대로 실을 수 있음"으로 나온 Forge jar들을 NeoForge 게임 레이어에 FML 라이브러리로 싣는 설치 단계(`NeoForgeInstallation`에 Forge 쪽 파일 준비를 더하거나 새 클래스). Forge 파일은 `ForgeInstallation`처럼 SHA-1을 확인해 받음.
  - 모듈: `poly-bridge-forge` 안의 새 패키지로 시작하고, NeoForge 게임 레이어에서 실행되는 코드는 지금의 `polymodloader-neoforge-host.jar`처럼 별도 jar로 만듭니다. 새 Gradle 모듈이 필요하면 README 8항목을 둡니다(AGENTS.md 원칙 5).
- 새 test-mods: `forge-on-neoforge-hello`(Forge 26.2 API로 컴파일, `@Mod` 생성자와 `FMLCommonSetupEvent`에서 로그 한 줄씩, 다른 API 없음).
- 검증: 시나리오 `forgeOnNeoForgeHello`(게임 테스트 서버, `neoforge-gametest-harness` 포함): 두 로그 줄, `All 1 required tests passed`, 월드 저장 줄.
- 완료: 26.2에서 통과. 그다음 26.1 계열에서도 통과.
- 위험: NeoForge의 모드 파일 검색이 `IModFileReader`를 외부에서 끼워 넣는 것을 허용하는지(서비스 등록은 FML 라이브러리 jar의 `META-INF/services`로 함). 안 되면 `NeoForgeGameHost`가 이미 바꾸는 검사된 호출(`ModsFolderLocator` 추가, ADR-0026)에 같은 방식으로 더함.

### F-N3. 이벤트 잇기

- 목표: F-N0 표의 상위 이벤트(예: `TickEvent`, `PlayerEvent$PlayerLoggedInEvent`, `RegisterCommandsEvent`, `RegisterKeyMappingsEvent`)가 Forge 모드에 옵니다. 취소·결과를 돌려주는 이벤트는 결과가 NeoForge 쪽에 반영됩니다.
- 만들 것: NeoForge 이벤트를 듣고 같은 뜻의 Forge 이벤트를 Forge 버스(`MinecraftForge.EVENT_BUS`)에 쏘는 이음. 이음 표는 데이터(이벤트 쌍과 필드 대응)로 두고, 쌍마다 javap로 확인한 근거를 주석으로 둡니다. 이음이 없는 Forge 이벤트를 듣는 모드는 등록할 때 경고(새 코드).
- 새 test-mods: `forge-on-neoforge-events`(이음 대상 이벤트를 듣고 결과를 게임 테스트로 확인).
- 검증: 시나리오 `forgeOnNeoForgeEvents`. 단위 테스트: 이음 표의 이벤트 쌍이 두 jar에 실제로 있는지.
- 완료: F-N0 표 상위 N개(시작할 때 정함)가 통과.

### F-N4. 등록

- 목표: `DeferredRegister`·`RegistryObject`·`ForgeRegistries`로 등록한 블록·아이템·엔티티가 NeoForge의 등록 시점에 들어가고 게임에 나옵니다.
- 할 일: Forge의 등록 API가 Forge 자신의 클래스로 링크되는지(F-N0)에 따라, 그대로 쓰고 NeoForge의 `RegisterEvent`에서 Forge의 `RegisterEvent`를 쏘거나, 연결 층이 대신함.
- 새 test-mods: `forge-on-neoforge-content`(블록·아이템 하나, 게임 테스트로 설치·꺼내기).
- 검증: 시나리오 `forgeOnNeoForgeContent`.

### F-N5. 확장 메서드와 capability

- 목표: Forge 모드의 `Item`·`Block`·`Entity` 하위 클래스가 Forge 확장 메서드를 덮어쓰고 부르는 것이 NeoForge 게임에서 같은 뜻으로 동작합니다.
- 할 일: 장벽 6의 표에서 이름·설명자가 다른 메서드마다 대응(NeoForge 메서드로 다시 쓰기, 형 변환 어댑터, 대응 없음)을 정하고, Forge 모드 클래스를 FML의 `ClassProcessor` 단계(또는 Poly 모드 로더의 변환 규칙, ADR-0004·0013)에서 다시 씁니다. capability는 NeoForge의 capability·data attachment와 뜻이 달라 F-N0의 사용 빈도를 보고 범위를 정합니다. 대응이 없는 멤버를 쓰는 모드는 링크 검사로 막습니다.
- 검증: 단위 테스트(대응 표가 두 jar의 실제 설명자와 맞는지), 시나리오 `forgeOnNeoForgeExtensions`.

### F-N6. 설정·네트워크·클라이언트

- 목표: `ForgeConfigSpec` 설정 파일, Forge 네트워크 채널(`ChannelBuilder`, `PacketDistributor`, `CustomPayloadEvent`), 클라이언트 이벤트(키 매핑, GUI 레이어, 설정 화면 `ConfigScreenHandler`)가 동작합니다.
- 할 일: 네트워크는 Forge 채널을 NeoForge의 페이로드 등록에 얹습니다. 한 연결에 두 로더의 페이로드가 있는 문제는 ADR-0026 40·41번의 장치를 봅니다.
- 새 test-mods: `forge-on-neoforge-network`(채널 하나, 창 없는 접속으로 확인, `neoforge-network-probe`와 같은 방식).
- 검증: 시나리오 `forgeOnNeoForgeNetwork`, 클라이언트는 마지막에 창으로.

### F-N7. Mixin·access transformer·coremod

- 목표: Forge 모드의 mixin이 공유 Mixin으로 적용되고, Forge 패치에만 있는 대상을 겨눈 주입은 이유와 함께 알리고 뺍니다(Fabric on Forge의 `138`·`212`·`213`과 같은 판정). Access transformer가 적용됩니다. JavaScript coremod를 쓰는 모드는 막습니다.
- 새 test-mods: `forge-on-neoforge-mixin`(바닐라 대상 주입 하나, Forge 패치 대상 주입 하나).
- 검증: 시나리오 `forgeOnNeoForgeMixin`.

### F-N8. 실제 묶음과 성능

- 목표: 완료 기준 1~6.
- 할 일: 묶음 작업 `corpusForgeOnNeoForge`(Forge 묶음 + NeoForge 묶음, 게임 테스트 서버), `corpusForgeOnNeoForgeServer`, `corpusForgeOnNeoForgeClient`(창), 런처 프로필 `launcherProfileForgeOnNeoForgeTest`(창), 측정 `startupBenchmarkForgeOnNeoForge`·`tickBenchmarkForgeOnNeoForge`. 빠지는 Forge 모드의 이유를 표로.
- 완료: 지원 버전 모두에서 통과하면 `Verified`로 올리고 기본값을 켤지 정함(ADR-0030 고침).

## ADR 초안 (ADR-0030, 시작할 때 번호 다시 확인)

제목 예: "Forge 모드를 NeoForge 호스트에서 실행(Forge on NeoForge)". 결정할 것:
1. 함께 있을 때의 호스트는 NeoForge. Forge 모드만 있으면 지금처럼 실제 Forge(ADR-0021). 기본값은 꺼짐(`-Dpoly.forgeOnNeoForge=on`)에서 시작.
2. 방안 C2: Forge 자신의 클래스 중 링크되는 것은 그대로 싣고, 연결 층은 이벤트·등록·확장 메서드·네트워크의 "잇기"만 함. 실을 수 없는 Forge 클래스 목록과 그 대체는 F-N0 결과로.
3. 모드 파일 읽기와 모드 생성은 NeoForge FML의 SPI(`IModFileReader`, `IModLanguageLoader`)로. FML 내부 호출을 더 바꿔야 하면 ADR-0026처럼 형태를 검사하고 바꿈.
4. 옮길 수 없는 모드는 시작 전에 빼고 알림(새 오류 코드). 이벤트 이음이 없는 리스너는 경고.
5. 라이선스: Forge·FML은 LGPL 2.1입니다. Forge의 jar는 받아서 쓰기만 하고 코드를 복사하지 않습니다. 연결 층이 Forge API와 같은 클래스·메서드 이름을 새로 구현해야 하면, MPL 2.0 저장소에서 그렇게 해도 되는지(이름·서명만 같은 독자 구현) 먼저 확인하고 ADR에 근거를 적습니다(AGENTS.md "하지 말 것"의 라이선스 확인).
6. ADR-0026 34번의 "지금은 하지 않음"을 이 ADR이 고칩니다.

## 오류 코드 (제안)

2026-10-25 기준 `POLY-COMPAT-2xx`는 `200..223`까지 쓰였습니다(`grep -rhoE "POLY-COMPAT-2[0-9][0-9]"`). 시작할 때 다시 확인하고 다음 빈 번호부터 씁니다.

| 제안 | 뜻 |
|---|---|
| `224` | NeoForge 호스트에서 이 Forge 모드를 아직 실행하지 않음(F-N1의 진단, 이유와 함께). F-N2 이후에는 연결할 수 없는 Forge API·Forge 패치 멤버를 써서 빠짐 |
| `225` | Forge JavaScript coremod를 쓰는 Forge 모드(NeoForge 호스트에 coremod 없음) |
| `226` | 이음이 없는 Forge 이벤트를 들음(경고, 그 리스너는 불리지 않음) |
| `227` | Forge 모드의 mixin 주입 대상이 Forge 패치에만 있음(경고, 그 주입은 실행되지 않음) |

`221`(NeoForge 모드가 Forge 옆에서 빠짐)은 플래그가 꺼져 있을 때 그대로 씁니다. 의미를 바꾸지 않습니다.

## 위험

1. **이벤트 의미 차이.** 같은 자리의 Forge·NeoForge 이벤트라도 필드·취소·결과 의미가 다를 수 있습니다(ADR-0026 34번의 `LivingEntity` 예). 이음 표의 쌍마다 두 패치 jar의 호출 자리를 javap로 맞춰 보고, 다르면 이음하지 않고 `226`으로 알립니다.
2. **유지 비용.** Minecraft 버전과 Forge·NeoForge 릴리스마다 이음 표·확장 메서드 대응·링크 결과가 바뀝니다. Sinytra Connector의 Fabric API 층과 같은 종류의 일입니다. 표를 데이터로 두고 두 jar로 검사하는 단위 테스트를 버전마다 돌립니다.
3. **capability와 상위 클래스.** Forge의 capability는 `Entity`·`Level`·`BlockEntity`·`ItemStack`의 상위 클래스에 기대고, NeoForge는 그 자리에 `AttachmentHolder`를 둡니다(장벽 1). 이것을 많이 쓰는 모드(F-N0에서 셈)는 오래 막힐 수 있습니다.
4. 라이브러리 판 차이(night-config 3.7.4 → 3.9.0, jline, Mixin 배포판)로 Forge 클래스가 다르게 동작할 수 있습니다. F-N0의 링크 검사와 F-N2·F-N6의 실행으로 확인합니다.
5. 라이선스(ADR 초안 5번).

## 고르지 않은 것

- **두 패치를 합친 게임**(방안 A), **로더마다 게임 사본**(방안 B): ADR-0026 34번의 측정과 이유.
- **Forge API 전체를 새로 구현**(방안 C1): Forge의 의미를 추측해 다시 만드는 일이고 규모가 Forge `universal`(클래스 1,121개) 전체입니다.
- **Forge를 호스트로 하고 NeoForge 모드를 옮김**: NeoForge 쪽 API·패치 규모가 더 크고(패치한 바닐라 클래스 2,202개 대 587개, ADR-0026 34번), Forge FML에는 NeoForge FML의 `IModFileReader` 같은 공개 SPI가 확인되지 않았습니다(확인하지 않음, 필요하면 F-N0에서).

## 진행

(시작하면 단계마다 날짜·실행한 작업·결과를 적습니다.)
