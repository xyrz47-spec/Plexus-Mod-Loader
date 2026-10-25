# Forge 지원 계획 (2026-10-22 작성)

MinecraftForge 모드(`META-INF/mods.toml`, `modLoader="javafml"`)를 Poly 모드 로더에서 실행하기 위한 계획입니다. NeoForge는 같은 FML 계열이지만 별개 스택이므로(ModLauncher를 없앤 FancyModLoader) 이 계획 뒤에 따로 다룹니다. 원칙은 Fabric 때와 같습니다. **로더의 의미를 추측해 다시 만들지 않고, 그 로더 자신의 코드를 Poly 모드 로더 위에서 돌립니다.** 순서도 같습니다. 실패하는 검증을 먼저 만들고, 그것을 통과시킵니다.

## 지금 상태 (사실)

- Poly 모드 로더는 Forge 모드를 진단만 하고 로드하지 않습니다(`POLY-COMPAT-200..205`).
- 실제 모드 묶음(26.2): 직접 설치 Forge 모드 35개 중 30개는 `201`(FML 런타임 없음)만으로 막힙니다. 5개는 Mixin(`202`)도 씁니다.
- Forge 26.2-65.1.5의 구조. 설치 프로그램의 `version.json`, `install_profile.json`, `data/*.lzma`에서 확인했습니다.
  - 시작: `net.minecraftforge.bootstrap.ForgeBootstrap`, ModLauncher 10.2.6, securemodules 2.2.24(JPMS 모듈 레이어)
  - FML: `fmlcore`, `fmlloader`, `javafmllanguage`, `lowcodelanguage`, `mclanguage`, `forgespi` 8.0.0, `eventbus` 7.0.5, `accesstransformers` 8.2.17, `coremods-api` 5.3.1, `forge-transformers`
  - Mixin: 원본 `org.spongepowered:mixin` 0.8.7(Fabric 포크 아님), `mixinextras-forge` 0.5.4
  - 게임 패치: 설치할 때 `binarypatcher`가 바닐라 jar에 **1394개 클래스**의 바이너리 패치를 적용해 `forge:<버전>:client` jar를 만듭니다(서버도 1394개).
  - Forge 자신도 모드(`forge`)이며, `universal.jar`에 `META-INF/accesstransformer.cfg`(약 100 KB)가 있습니다.
- 모드 로딩은 패치된 Minecraft 코드가 FML(`ModLoader`)을 부르면서 진행됩니다. 그래서 FML만 따로 흉내 내서는 Forge 모드가 기대하는 이벤트가 오지 않습니다.

## G0 조사 결과: FML이 ModLauncher에 기대는 정도 (`jdeps`, 26.2-65.1.5)

| jar | 하는 일 | ModLauncher·securemodules에 기대는 것 |
|---|---|---|
| `fmlcore` | 모드 로딩 단계(`ModLoader`), 이벤트 분배, 설정 | `ServiceLoaderUtils`, `INameMappingService.Domain` 두 개뿐. `fmlloader`의 `FMLLoader`·`LoadingModList`·`FMLEnvironment`·`FMLPaths`·`moddiscovery`를 많이 씀 |
| `javafmllanguage` | `@Mod` 클래스 생성, 모드 이벤트 버스 | `LamdbaExceptionUtils`, `SecureJar.ModuleDataProvider` |
| `fmlloader` | 검색, 실행 플러그인, JPMS 레이어 | `Launcher`, `IEnvironment`, `ILaunchPluginService`, `IModuleLayerManager`, `SecureJar` 등 대부분 |

G0의 결론은 "`fmlloader`의 상태 클래스만 대체"였습니다. **G3 조사에서 이 결론을 고쳤습니다**(아래 "G3 조사 결과").

### G3 조사 결과: Forge는 JPMS 모듈 레이어를 전제로 함 (2026-10-22, Forge Maven의 소스 jar)

- `fmlcore`의 `Bindings`·`ModStateManager`는 `ServiceLoader.load(FMLLoader.getGameLayer(), …)`로 서비스를 찾습니다.
- `javafmllanguage`의 `FMLModContainer`는 모드 클래스를 `gameLayer.findModule(<모드 ID>)`에서 엽니다. 모드 jar의 `Add-Opens`도 모듈 사이에 적용합니다.
- Forge의 `ForgeMod`는 모듈 open을 바꾸고, 실행 핸들러는 `layer.findModule("minecraft")`에서 메인 클래스를 엽니다.
- `ModDiscoverer`·`LanguageLoadingProvider`는 ModLauncher의 SERVICE·PLUGIN 레이어에서 서비스를 찾습니다.
- ModLauncher 10.2.6은 약 4천 줄입니다. 레이어를 만들고 서비스를 순서대로 부르는 일을 합니다.
- 서버 설치 프로그램이 하는 일(`install_profile.json`의 processors): 번들 서버 jar 풀기, 라이브러리 풀기, `binarypatcher --data --unpatched --store`(G2와 같은 결과). 클라이언트는 여기에 `--marker .forge_patched_minecraft`를 더합니다.

그래서 접근을 고쳤습니다([ADR-0021](../adr/0021-forge-on-polyloader.md)): 게임 클래스로더가 JPMS 모듈도 정의하고, Poly 모드 로더가 ModLauncher의 서비스 계약을 구현합니다. FML과 Forge의 코드는 그대로 실행합니다.

### Forge jar의 자리 (26.2-65.1.5 서버 설치 결과의 매니페스트)

Forge의 `ForgeBootstrap`은 클래스 경로의 jar를 BOOT 레이어 모듈로 만들되, 모드 파일(`META-INF/mods.toml`이 있거나, Minecraft이거나, 매니페스트에 `FMLModType`이 있는 jar)은 빼고 FML이 나중에 찾게 둡니다.

| 자리 | jar |
|---|---|
| BOOT(실행기와 FML 로더) | `bootstrap`·`bootstrap-api`(module-info), `modlauncher`·`securemodules`(module-info), `fmlloader`, `forgespi`, `eventbus`·`coremods-api`(module-info), `accesstransformers`, `forge-transformers`, `JarJar*`, `srgutils`, `unsafe`, `roimfs`, `mergetool-api`, `hash-utils`, `fmlearlydisplay`, 그 밖의 일반 라이브러리 |
| PLUGIN(언어 공급자, `FMLModType: LANGPROVIDER`) | `javafmllanguage`, `lowcodelanguage`, `mclanguage` |
| GAME(라이브러리, `FMLModType: LIBRARY`) | `fmlcore` |
| GAME(모드) | Forge `universal.jar`(`mods.toml`, 모듈 이름 `net.minecraftforge.forge`), 패치된 Minecraft(`forge-…-server.jar`, 모듈 이름 `minecraft`) |

### G3 실행으로 알게 된 것 (2026-10-22)

- **Forge의 파일 시스템은 JVM 클래스 경로에 있어야 합니다.** Java는 파일 시스템(`FileSystemProvider`)과 URL 처리기를 시스템 클래스로더에서만 찾습니다. Forge의 shim도 그래서 `securemodules`, `unsafe`, `JarJarFileSystems`, `roimfs`를 클래스 경로(`Class-Path`)에 둡니다. Poly 모드 로더도 이 넷이 클래스 경로에 있을 때만 Forge를 시작합니다(없으면 `POLY-COMPAT-206`). 개발 시나리오는 `forgeClassPath`로 넣고, 런처 프로필에는 G7에서 런처가 받는 라이브러리로 넣습니다.
- **Forge는 운영(production) 환경에서 GameTest를 돌리지 않습니다.** `ForgeGameTestHooks.isGametestEnabled()`가 `FMLLoader.isProduction()`이면 거짓이라, 패치된 서버가 테스트를 진행하지 않습니다(테스트가 끝없이 대기). Forge의 `server_gametest` 실행 핸들러는 개발용뿐이라, Poly 모드 로더가 같은 실행 유형(`net.minecraft.gametest.Main`, 운영 아님)에 운영 서버처럼 Minecraft를 찾는 `polymodloader_server_gametest` 핸들러를 둡니다.
- **패키지 버전.** FML은 자기 패키지의 `Implementation-Version`을 읽습니다. 게임 클래스로더가 모듈 패키지를 매니페스트 속성으로 정의합니다(securemodules와 같음).
- **내장 규칙.** 패치된 jar에서 Poly 모드 로더 규칙 71개 중 `poly:server_brand`만 맞지 않아 거부됩니다(Forge가 `getServerModName`을 바꿈). 나머지는 사전조건이 맞아 적용됩니다. 진단용으로 `-Dpoly.transform.disable=<규칙 ID|*>`를 두었습니다.

### G4 실행으로 알게 된 것 (2026-10-22)

- **FML의 mods 폴더 검색은 뺍니다.** FML의 `ModsFolderLocator`는 `mods/`의 모든 jar를 Forge 모드로 읽으려 하고, `mods.toml`이 없는 jar(Fabric·Poly 모드)는 "Forge 모드가 아님" 오류로 만듭니다. Poly 모드 로더는 자신이 찾은 Forge 모드 jar를 게임 클래스로더의 리소스로 두고(FML의 `ClasspathLocator`가 찾음), BOOT 레이어의 `fmlloader` 모듈에서 `ModsFolderLocator` 제공을 뺍니다(`ForgeBootModules`).
- **Forge 모드도 `pack.mcmeta`가 있어야 합니다.** 없으면 Minecraft가 그 모드의 데이터 팩을 "Missing metadata"로 건너뜁니다. 테스트 모드는 Forge `universal.jar`와 같은 형식(`min_format` 107.1)을 둡니다.
- **Poly 모드와 Forge 모드는 함께 실행됩니다.** 같은 게임 테스트 서버에서 두 모드 모두 동작했습니다. Fabric 모드와 함께는 아직 아닙니다(G8, `207`).

G3b는 이 표대로 레이어를 나눕니다. BOOT·SERVICE·PLUGIN 레이어는 실행기 쪽 코드이므로 JDK 클래스로더로 정의하고, GAME 레이어만 게임 클래스로더가 정의합니다(G3a).

### 바이너리 패치 형식 (`net.minecraftforge:binarypatcher:1.3.4`, `javap`로 확인)

- `data/<side>.lzma`는 LZMA로 압축한 zip이고, 클래스마다 `<클래스>.binpatch`가 하나씩 있습니다.
- `.binpatch`는 `DataInputStream`으로 읽습니다.
  - 형식 번호 바이트(1이 아니면 "Unsupported patch format")
  - UTF 문자열 두 개(원래 이름, 패치 후 이름)
  - `boolean` 원본 존재 여부
  - 원본이 있으면 `int` 체크섬
  - `int` 길이와 그만큼의 패치 바이트
- 패치 바이트는 원본 클래스에 대한 GDIFF 델타입니다(`com.nothome.delta.GDiffPatcher`).
- 따라서 G2는 LZMA 해제(Java에는 없음, xz-java 등 필요), zip 읽기, GDIFF 적용으로 구현할 수 있습니다.
- **형식 확인(2026-10-22).** Forge 설치 프로그램을 `--installServer`로 임시 폴더에 실행해 `forge-26.2-65.1.5-server.jar`를 얻었습니다. 같은 `server.lzma`를 바닐라 `server-26.2-unpacked.jar`에 위 해석대로 적용했습니다(Python, 저장소 밖). 결과는 1394개 모두 Forge 결과와 바이트까지 같았습니다. 그중 1029개는 바뀐 클래스, 365개는 Forge가 새로 더하는 클래스입니다.

## 접근 (제안, ADR-0021로 정할 것)

**A. Forge 자신의 코드를 Poly 모드 로더 게임 클래스로더에서 실행 (권장, G3 조사로 2번을 고침)**

1. 사용자 PC에서 Forge 설치 프로그램이 하는 패치를 똑같이 합니다. 바닐라 jar에 Forge 바이너리 패치를 적용해 패치된 게임 jar를 만들고, 배포하지 않고 캐시에만 둡니다(ADR-0019의 "사용자 PC에서만 만듦"과 같은 원칙). Forge 모드가 있을 때만 이 jar를 게임 jar로 씁니다.
2. FML 라이브러리, Forge `universal.jar`, 패치된 Minecraft, Forge 모드를 게임 클래스로더가 정의하는 JPMS 게임 레이어에 둡니다. ModLauncher·securemodules는 쓰지 않습니다. 대신 FML이 ModLauncher에 기대하는 서비스(`IEnvironment`, `IModuleLayerManager`, 서비스 호출 순서)를 Poly 모드 로더가 제공합니다. Fabric의 Knot을 쓰지 않고 `PolyMixinService`를 만든 것과 같은 방식입니다.
   - 변환 단계: access transformer, coremods, Mixin
   - 모드 목록: `LoadingModList`
   - 환경 정보: `FMLEnvironment.dist` 등
3. 모드 검색과 의존성 판정은 지금처럼 Poly 모드 로더가 합니다. 그 결과로 FML의 `LoadingModList`를 만듭니다. 생성자 호출과 이벤트 버스, 로딩 단계는 FML 자신(`javafmllanguage`, `ModLoader`)이 합니다.
4. Forge Mixin은 Fabric Mixin과 같은 JVM에 둘 수 없을 수 있습니다(Mixin 상태는 전역, 원본과 Fabric 포크). 처음에는 **Forge 모드와 Fabric 모드를 함께 넣으면 Forge 쪽을 막고 이유를 알립니다.** 함께 실행하는 것은 다음 단계입니다.

**B. Forge API를 다시 구현** — 고르지 않습니다. 패치 1394개와 이벤트 수백 개의 의미를 추측해야 해서, AGENTS.md의 "추측으로 구현하지 않습니다"에 어긋납니다.

### G5 실행으로 알게 된 것 (2026-10-23)

- **access transformer는 Forge가 이미 적용합니다.** ModLauncher 서비스의 `accesstransformer` 실행 플러그인이 모드의 `META-INF/accesstransformer.cfg`를 읽어 적용합니다. Poly 모드 로더가 따로 할 일이 없었습니다.
- **Forge의 Mixin은 BOOT 모듈입니다.** Poly 모드 로더의 클래스 경로에는 Fabric의 Mixin 포크가 같은 패키지로 있어서, 처음에는 Forge의 원본 Mixin 0.8.7을 "이미 있음"으로 뺐습니다. 이제 모듈로 둡니다. 게임 클래스로더는 모듈 패키지를 부모보다 먼저 찾으므로 FML과 Forge 모드는 Forge의 Mixin을 씁니다. Mixin 자신의 ModLauncher 서비스(`MixinTransformationServiceLegacy`, `MixinLaunchPluginLegacy`)가 BOOT 레이어에서 그대로 발견되고, 모드 jar의 `MixinConfigs`는 `addResources`로 전달됩니다.
- **실행 플러그인이 처리 중일 때는 빈 클래스를 만들지 않습니다.** Mixin은 변환기를 만들면서 없는 클래스를 찾아 봅니다. ModLauncher에서는 그 요청이 BOOT 레이어 클래스로더로 가서 그냥 없다고 끝나지만, Poly 모드 로더는 BOOT 레이어도 게임 클래스로더가 정의하므로 실행 플러그인에 빈 클래스 생성을 물었고, Mixin이 변환기를 다시 만들려다 멈췄습니다(`Terminating MixinTransformer instance`). 플러그인이 처리 중인 스레드에서는 생성을 묻지 않습니다(`ForgeClassTransformer`).
- **BOOT 모듈은 열린 모듈입니다.** Forge의 securemodules는 jar의 `module-info`를 열린 모듈로 다시 만듭니다(`ModuleJarMetadata`). MixinExtras(GAME 레이어)가 Mixin 내부를 리플렉션으로 읽기 때문에 Poly 모드 로더도 같게 했습니다. 클래스 경로가 대신하는 모듈에만 내보내는 패키지(`exports … to`)는 클래스 경로에 내보냅니다.
- **묶음의 Forge 모드는 아직 실행되지 않습니다.** `corpusForge`의 `forge` 폴더에는 Fabric용 `fabric.mod.json`도 든 여러 로더용 jar 5개가 있어서 `207`(Forge와 Fabric 함께)로 Forge 모드 33개가 모두 빠집니다. G6에서 정합니다.

### G6 실행으로 알게 된 것 (2026-10-23)

- **여러 로더용 jar.** 실제 묶음의 `forge` 폴더에는 `fabric.mod.json`·`neoforge.mods.toml`·`mods.toml`을 함께 가진 jar가 있습니다(collective, FallingTree, Cubes Without Borders, Fast IP Ping). Poly 모드 로더는 jar 하나에 종류를 하나만 붙였고(Fabric이 먼저), 그래서 Forge 모드 전체가 `207`로 빠졌습니다. 이제 jar가 가진 로더 메타데이터를 모두 기억합니다. Forge만 실행할 수 있는 jar가 있고 Fabric만 실행할 수 있는 jar가 없으면, Forge 메타데이터도 가진 jar는 FML이 실행합니다. Forge와 Fabric은 각자 자기 메타데이터만 읽으므로 실제 설치와 같습니다. ModLauncher 변환 서비스(`META-INF/services/cpw.mods.modlauncher.api.ITransformationService`)를 가진 jar도 Forge의 것으로 봅니다(CustomSkinLoader).
- **FML이 거부할 모드는 먼저 뺍니다(`209`).** FML은 한 모드라도 요구 조건이 맞지 않으면 실행 전체를 멈춥니다. Poly 모드 로더가 FML 시작 전에 같은 조건을 확인합니다. 언어 로더 버전(`loaderVersion`), 필수 의존 모드, 설치된 의존 모드의 버전 범위(`minecraft`, `forge` 포함)를 보고, 버전 범위는 Forge 라이브러리의 Maven `VersionRange`로 FML과 같게 판단합니다. jar 안에 든 모드(`META-INF/jarjar/`)도 설치된 것으로 칩니다. 빠진 모드가 필요한 모드도 함께 뺍니다. 묶음에서는 Minecraft 26.3용 `libIPN`과 그것을 쓰는 InventoryProfilesNext가 빠졌습니다.
- **서버의 클라이언트 전용 Forge 모드.** `@Mod` 클래스가 `@OnlyIn(Dist.CLIENT)`이거나(Cubes Without Borders) `minecraft` 의존을 클라이언트 쪽에서만 선언한 모드(BHMenu, BetterAdvancements, Euphoria Patcher, Fast IP Ping)는 서버에서 FML이 그대로 만들다가 클라이언트 클래스에 닿아 멈춥니다. 서버에서는 `209`로 뺍니다. Fabric의 `108`과 같은 판단입니다.
- **게임 테스트 실행도 운영(production) 동작으로.** Forge의 `RuntimeDistCleaner`는 운영이 아닐 때 모드 클래스까지 검사해서, 메서드에 붙은 `@OnlyIn`을 "더 이상 지원하지 않음"으로 거부합니다(voicechat, FallingTree). 플레이어의 서버에서는 일어나지 않는 일입니다. Poly 모드 로더의 게임 테스트 실행은 Forge가 게임 테스트를 돌리도록 운영이 아니지만(`FMLLoader.isProduction()`), Forge 자신의 `production` 속성을 켜서 나머지는 운영처럼 동작하게 했습니다(`FMLEnvironment.production`).
- **새 빈 설정 파일이 열린 채로 남습니다(night-config 3.7.4).** 월드를 시작할 때 Forge Config API Port가 Open Parties and Claims의 서버 설정을 `REPLACE_ATOMIC`으로 저장하다 Windows에서 `AccessDeniedException`으로 멈췄습니다. 원인은 Forge가 쓰는 night-config의 `ConfigFormat.initEmptyFile(WriterSupplier)`가 연 writer를 닫지 않는 것입니다(`javap`로 확인). Forge는 새 설정 파일마다 이것을 부르고(`ConfigFileTypeHandler.setupConfigFile`), Windows는 열린 파일을 교체하지 못합니다. night-config만으로 같은 순서를 실행해도 똑같이 실패하므로, Windows의 실제 Forge에서도 일어나는 일입니다. Poly 모드 로더는 BOOT 레이어에서 그 메서드를 `try (Writer w = supplier.get()) { initEmptyFile(w); }`로 바꿉니다(`NightConfigEmptyFilePatch`). 메서드 본문이 정확히 그 누수 형태일 때만 바꾸고, 그 밖의 night-config는 그대로 둡니다.
- **변환 서비스 jar는 Poly 모드 로더가 찾은 목록에서.** Forge는 `mods/`에서 `ITransformationService`·`IModLocator`·`IDependencyLocator`를 제공하는 jar를 SERVICE 레이어에 넣고(`ModDirTransformerDiscoverer`), `ClasspathLocator`는 그런 jar를 건너뜁니다. Poly 모드 로더는 Forge 모드를 클래스 경로 자원으로 넘기므로, `mods/` 검색에 맡기면 게임 테스트처럼 다른 폴더의 모드는 빠지고, `209`로 뺀 jar나 Fabric에 준 jar는 들어갑니다. 이제 Poly 모드 로더가 FML에 넘기는 Forge 모드 중 그런 jar만 SERVICE 레이어에 넣습니다(판정은 FML의 `isServiceProvider`). Crash Assistant가 이렇게 로드되고, 자기 의존 위치 지정자로 안쪽 모드를 FML에 줍니다.
- **ModLauncher의 실행 인자.** Crash Assistant와 CustomSkinLoader는 `Launcher.argumentHandler`의 `args`를 리플렉션으로 읽습니다. Poly 모드 로더의 `Launcher`에 같은 이름의 필드와 `ArgumentHandler`를 두었습니다(`--launchTarget <대상>`과 게임 인자).
- **서버의 클라이언트 전용 서비스 jar.** CustomSkinLoader는 `mods.toml` 없이 변환 서비스로만 Forge에 들어오고, `fabric.mod.json`에 `"environment": "client"`라고 적습니다. 서버에서도 `ResourceManager`에 클라이언트 인터페이스를 붙여 서버가 멈춥니다. `mods.toml`이 없어 FML이 쪽을 알 수 없는 jar를 다른 로더의 메타데이터가 클라이언트 전용이라고 하면, 서버에서는 `209`로 뺍니다.
- **게임이 오류로 끝나면 프로세스도 끝냅니다(`POLY-BOOTSTRAP-007`).** 위 실패에서 main 스레드가 끝난 뒤에도 모드가 만든 데몬이 아닌 스레드 때문에 프로세스가 10분 동안 남았습니다.
- **전용 서버로는 모드 로딩을 볼 수 없습니다.** 전용 서버는 Forge가 모드를 로드하기 전에 EULA를 확인하고, Poly 모드 로더는 EULA에 동의하지 않습니다. 실제 모드 확인은 게임 테스트 서버와 클라이언트(G7)에서 합니다.

### G8 실행으로 알게 된 것 (2026-10-23)

- **Mixin은 하나만.** 클래스로더 하나에는 Mixin이 하나만 있을 수 있습니다(Fabric 모드의 설정 플러그인이 Forge의 `IMixinConfigPlugin`으로 바뀌어 `ClassCastException`). Fabric 모드가 함께 있으면 Forge의 Mixin을 BOOT 레이어에서 빼고, Poly 모드 로더의 클래스 경로에 있는 Fabric의 Mixin이 Forge 모드의 `MixinConfigs`도 적용합니다(`SharedMixinConfig`). 설정은 FML이 GAME 레이어를 만든 뒤, Fabric의 `preLaunch` 전에 더합니다.
- **MixinExtras도 하나만.** FML은 `mixinextras-forge`의 jar-in-jar를 반드시 찾으므로 그대로 두되, 안에 든 MixinExtras jar를 빈 jar로 바꾼 사본을 줍니다(`MixinExtrasStub`). MixinExtras가 만드는 클래스(`LocalRef` 구현)가 클래스 경로 쪽 패키지에 있어야 하기 때문입니다.
- **Fabric 모드의 mixin이 Forge 패치와 맞지 않는 곳.** 정적 비교로 Fabric API를 포함한 묶음에서 주입 지점 약 33곳이 Forge 패치에서 사라졌습니다. Mixin을 시작하기 전에 바닐라 클래스와 Forge가 패치한 클래스를 비교해 (1) Forge가 같은 지점을 옮긴 곳이 알려졌으면 거기로 옮기고(`Bootstrap.wrapStreams` → `GameData.vanillaSnapshot`), (2) 아니면 그 주입이 실행되지 않는다고 알리고(`138`), (3) mixin이 쓰는 멤버를 패치가 없앴으면 그 mixin을 설정에서 뺍니다. 한 mixin이 실패하면 Mixin이 같은 클래스의 다른 mixin을 모두 건너뛰므로(다른 모드의 mixin까지 사라짐) 모든 Fabric 주입을 `require = 0`, 지역 변수 캡처를 `CAPTURE_FAILSOFT`로 바꿉니다. 그래도 남는 오류는 Mixin 오류 처리기가 `138`로 알리고 건너뜁니다.
- **매개변수가 바뀐 대상 메서드(2026-10-23, 플레이어 보고).** Forge는 `LevelExtractor.extractVisibleBlockEntities`에 `Frustum` 매개변수를 더합니다. Sodium의 `@Inject` 처리기는 바닐라 매개변수로 쓰여 Mixin이 "Invalid descriptor"로 거부했고, 그 실패로 같은 클래스의 다른 mixin(Fabric API의 `LevelRendererExtensions` 인터페이스)까지 빠져 첫 프레임에서 `ClassCastException`이 났습니다. 이제 Mixin을 시작하기 전에, 패치가 매개변수를 더하기만 했으면 처리기는 일반 메서드로 두고 패치된 매개변수를 받는 다리 처리기가 `@Inject`를 맡아 원래 매개변수만 넘깁니다. 매개변수가 바뀌었으면 그 주입만 빼고 `138`로 알립니다(`HostPatchAdjustments`, `forgeSodiumClient`).
- **레지스트리.** Fabric 모드는 `onInitialize`에서 게임 레지스트리에 바로 등록하는데, Forge는 바닐라 내용 뒤에 레지스트리를 잠급니다. Fabric 모드 초기화 동안 Forge가 `RegisterEvent`에서 하는 것처럼 레지스트리를 풀었다가 다시 잠급니다(`ForgeRegistryOpening`).
- **Forge의 필드-메서드 변환.** Fabric API의 class tweaker가 연 `Biome.climateSettings`를 Forge의 `FieldToMethodTransformer`가 거부합니다. 그 변환기가 도는 동안만 필드를 private로 보이게 합니다.
- **같은 모드의 두 빌드.** Forge Config API Port, geckolib, voicechat처럼 Fabric용과 Forge용이 함께 있으면 같은 패키지를 써서 한 게임에 둘 수 없습니다. Forge 빌드를 실행하고 Fabric 빌드는 뺍니다(`139`).
- **서로의 모드 목록(2026-10-23, 플레이어 보고, [ADR-0025](../adr/0025-foreign-mods-in-host-mod-list.md)).** Physics Mod(Forge)는 `ModList.getModFileById("sodium") != null`로 Sodium을 찾는데, FML의 모드 목록에는 Fabric 모드가 없어서 바닐라 렌더링 경로를 탔습니다(Sodium과 함께 파편·래그돌이 잘못 그려짐). 반대로 Fabric 모드의 `isModLoaded`도 Forge 모드를 보지 못했습니다. 이제 FML의 `ModList.getModFileById`·`isLoaded`는 FML의 답이 없을 때 Poly Mod와 다른 로더의 모드를 forgespi 보기로 답합니다(`fmlcore`는 PLUGIN 레이어라서 클래스 변환 대신 모듈 읽기에서 패치하고, 검사한 형태가 아니면 `210`). Fabric Loader는 Forge 모드를 루트 경로 없는 컨테이너로 봅니다. `getModContainerById`와 `LoadingModList`는 그대로입니다(`forgeFabricGameTest`, `forgeGameTest`).
- **Forge 패치가 더하거나 바꾼 멤버(2026-10-23).** Fabric API의 mixin 4개가 통째로 빠졌습니다: Forge가 `ServerCommonPacketListenerImpl.getConnection()`을 더해 네트워킹 접근자와 겹치고, `TagFile`에 `remove` 레코드 구성 요소를 더해 태그 API의 `remove()`와 겹치고, `AttributeSupplier$Builder.builder`를 `ImmutableMap.Builder`에서 `Map`으로 바꾸고, `Advancement$Builder.save(Consumer, Identifier)`를 더합니다. 이제 Mixin 전에 바닐라와 패치된 클래스를 비교해, 같은 코드면 Forge의 메서드에 맡기고(`212`), 다른 코드면 mixin의 메서드 이름을 바꾸고 같은 모드의 mixin 호출을 따라 바꾸며(`213`), 타입이 바뀐 필드의 접근자는 나머지 mixin만 적용하고 부르면 `214`로 알립니다. 태그 제거는 Forge가 `TagLoader.tryBuildTag`의 지역 변수 `values`를 `builder`(`LinkedHashSet`)로 바꾼 탓에 `@Local`로도 조용히 실행되지 않아, 바뀐 이름·타입으로 잡게 했습니다(`HostAddedMembers`, `HostRenamedLocals`, `corpusFabricForge`의 `fabric-host-patch-probe`).
- **아직 남은 것.** 정적 비교가 못 보는 주입(식·slice)은 조용히 건너뜁니다. Fabric API의 기능 일부(유체 수영, 수면 이벤트, 아이템 사용 wrap, 인챈트 테이블 검사 등)는 Forge에서 실행되지 않습니다(`138`로 목록이 남음). 클라이언트(G7)는 아직입니다.
- **블록 부수기 이벤트·리소스 조건·조합(2026-10-23).** Forge가 `destroyBlock`의 호출을 `removeBlock`으로 옮긴 자리는 호스트가 놓인 방식과 함께 선언하고(`SAME_PLACE`, `AFTER_TRUE_RESULT`), 브리지가 패치된 클래스에서 모양을 확인한 뒤 옮깁니다. 매개변수 순서가 바뀐 람다는 다리로, Fabric·Forge 인터페이스의 같은 default 충돌(조합 때마다 `IncompatibleClassChangeError`)은 Forge 쪽 default로 풉니다(ADR-0021 7번, `forgeFabricApiGameTest`).

### G7 실행으로 알게 된 것 (2026-10-23)

- **Poly 모드 로더 자신의 규칙.** Forge의 클라이언트는 크리에이티브 탭을 스스로 페이지로 나눠서, 그 화면을 바꾸는 Poly 모드 로더의 규칙(`poly:client_creative_tabs_*`)이 대상을 찾지 못합니다. 호스트가 패치한 대상이면 Poly 모드 로더 규칙은 경고와 함께 꺼지고 실행은 계속됩니다.
- **여러 로더용 jar는 Forge가.** Forge가 실행되면 Forge 메타데이터를 가진 jar는 Fabric 모드가 함께 있어도 Forge가 실행합니다. JourneyMap의 Forge jar 안 Fabric 쪽은 Fabric Loader를 일부러 멈추는 안내 스텁입니다.
- **Mixin이 만드는 이름.** Mixin은 모드 ID로 멤버 이름을 만들므로, 중첩 jar의 Mixin 설정에는 그 jar를 담은 모드의 ID를 씁니다.
- **Crash Assistant.** 클라이언트에서 별도 프로그램을 띄우고 그 프로그램이 게임보다 오래 남아 실행 폴더를 잡습니다. 실제 동작이라 `corpusForgeClient`에서만 뺍니다.

### Minecraft 26.1 (2026-10-23)

- Forge는 26.1 핫픽스마다 따로 빌드합니다(26.1 → 62.0.9, 26.1.1 → 63.0.2, 26.1.2 → 64.1.5 권장). 세 릴리스를 `forge-releases.properties`에 넣었습니다.
- Forge 26.1의 패치 일부는 패치 후 이름이 원래 이름과 다릅니다(`Transformation$com/mojang/math/Transformation`). Forge의 binarypatcher처럼 원래 이름으로 클래스를 찾아 그 자리에 패치합니다.
- `forgeWorldTest`(Forge만, 게임 테스트 서버에서 월드 생성·GameTest·저장)가 26.1과 26.2에서 통과합니다. 26.1의 실제 묶음(`corpusForge -PpolyMinecraft=26.1`)은 40개 중 25개가 요구 조건을 만족하지만, 중첩 jar의 Mixin 설정(`kuma_api.api.mixins.json`)을 Forge의 Mixin이 읽지 못해 아직 멈춥니다. (그 뒤 B1에서 Balm을 `209`로 빼서 통과. 실제 Forge도 같은 곳에서 멈춤: 아래 B5)

### B5 Mixin 설정으로 빠지는 모드: 실제 Forge와 비교 (2026-10-23)

`209`의 이유 중 Mixin 설정에 관한 두 가지("Mixin would stop the launch")를 Forge의 Mixin 0.8.7 바이트코드(`javap -c`)와 실제 Forge 서버로 확인했습니다. **둘 다 실제 Forge도 Minecraft가 시작하기 전에 멈춥니다.** Poly 모드 로더가 이 모드를 빼는 것은 Forge보다 엄격하지 않습니다.

- **경로.** ModLauncher가 `LaunchServiceHandler.launch`에서 `announceLaunch`를 부르고(예외 처리 없음), `MixinLaunchPluginLegacy.initializeLaunch` → `MixinBootstrap.inject` → `MixinPlatformManager.scanForContainers` → `addNestedContainers`(처리기 밖) → `MixinPlatformAgentDefault.prepare`가 각 모드 jar(FML이 JarJar로 꺼낸 중첩 jar 포함)의 `MixinConfigs`를 `,`로 나눠 `addConfig`로 넘깁니다. `addConfig`는 `.json`으로 끝나는 이름만 `Mixins.addConfiguration`에 넘기고 나머지는 무시합니다.
- **설정 파일이 없을 때.** `MixinConfig.create`가 `service.getResourceAsStream`이 `null`이면 `IllegalArgumentException("The specified resource '%s' was invalid or could not be read")`을 던지고, `Config.create`가 `catch Exception`으로 받아 `MixinInitialisationError("Error initialising mixin config …")`로 바꿉니다. 이것은 `Error`(`MixinError extends Error`)라 `Mixins.createConfiguration`의 `catch Exception`(로그만 남기고 건너뛰는 곳)을 지나 실행이 멈춥니다.
- **모르는 호환 수준.** `MixinConfig.onLoad` → `postInit` → `initCompatibilityLevel`이 `compatibility` 문자열을 `trim().toUpperCase(Locale.ROOT)`해 `CompatibilityLevel.valueOf`로 읽고, `IllegalArgumentException`이면 `MixinInitialisationError("Mixin config %s specifies compatibility level %s which is not recognised")`를 던집니다. Mixin 0.8.7은 `JAVA_6`~`JAVA_21`만 압니다. 같은 이유로 멈춥니다.
- **실제 Forge.** Forge 26.1-62.0.9 설치 프로그램 `--installServer`로 만든 서버(EULA 동의 없음)에 한 모드씩 넣어 실행: 모드 없음 → EULA 확인까지. `balm-forge-26.1-26.1.0.6.jar` → `MixinInitialisationError: Error initialising mixin config kuma_api.api.mixins.json`(원인 `The specified resource 'kuma_api.api.mixins.json' was invalid or could not be read`). `boatiview-forge-0.0.9-26.1.jar`, `NoChatReports-FORGE-26.1-v2.19.0.jar` → `Mixin config … specifies compatibility level JAVA_25 which is not recognised`. 셋 다 `announceLaunch` 안에서, EULA 확인 전에 멈춤.
- **고친 것(Forge보다 엄격하거나 느슨했던 곳).** 매니페스트 이름 중 `.json`으로 끝나는 것만 확인하고 적용합니다(없어도 Mixin이 무시하는 이름으로 모드를 빼지 않고, 공유 Mixin도 읽으려 하지 않음). 호환 수준은 Mixin처럼 `trim`·대문자로 비교합니다. 중첩 jar 설정의 호환 수준도 봅니다(전에는 바깥 jar만). 묶음 결과는 같습니다: 26.1 38개 중 20개, 26.2 38개 중 30개.
- **남은 차이.** Mixin은 설정을 jar가 아니라 서비스의 리소스 조회로 찾으므로, 다른 jar에 같은 이름의 설정이 있으면 멈추지 않습니다. Poly 모드 로더는 그 jar 안에서만 찾습니다(묶음에는 이런 경우가 없음). FML이 같은 중첩 jar의 다른 버전을 고르면 고르지 않은 jar의 설정은 읽히지 않지만, 확인은 모든 중첩 jar를 봅니다. `.json@` 이름(Mixin이 `MixinError`로 멈춤)은 확인하지 않습니다. 공유 Mixin(Fabric 모드와 함께)에서는 확인이 Fabric의 Mixin(`sponge-mixin` 0.17.4, `JAVA_25`까지 앎, 같은 `trim`·대문자 읽기)의 수준을 쓰므로 Boatiview·No Chat Reports는 이 이유로 빠지지 않습니다(코드로 확인, 26.1 `corpusFabricForge`는 실행하지 않음).

### B5 나머지 이유: Forge 버전 범위와 클라이언트 전용 (2026-10-23)

`209`로 빠지는 모드를 26.1(Forge 62.0.9), 26.1.2(64.1.5), 26.2(65.1.5)의 게임 테스트 서버(`corpusForge`)에서 모두 모아 이유별로 확인했습니다.

| 이유 | 26.1 | 26.1.2 | 26.2 | 실제 Forge |
|---|---|---|---|---|
| Forge·Minecraft·언어 로더 버전 범위 | 9개: Advancement Plaques, Carry On, Durability Tooltip, Highlighter, Iceberg, Inventory Profiles Next, libIPN, Prism, SuperMartijn642's Config Lib. 모두 26.1.2용 빌드로 Forge 64 이상을 요구 | 0 | 0 (아래 libIPN) | 모드 로딩 전체가 멈춤 |
| Mixin 설정(없는 파일, `JAVA_25`) | 3개: Balm, Boatiview, No Chat Reports | 1개: No Chat Reports | 0 | 멈춤(위 B5, 세 릴리스 모두 Mixin 0.8.7) |
| 서버의 클라이언트 전용 모드 | 6개 | 6개 | 6개 | 완료 기준 5의 허용 범위. 클라이언트에서는 실행(`corpusForgeClient`) |

- **버전 범위.** FML(62.0.9, 65.1.5 `javap -c`)은 `ModValidator.validateLanguages`에서 언어 로더 범위가 맞지 않는 파일의 오류를 모으고, `ModSorter.sort`에서 지금 쪽(`DependencySide.isCorrectSide`)의 필수 의존 범위를 확인해 오류가 하나라도 있으면 `EarlyLoadingException("failure to validate mod list")`를 `LoadingModList`에 남깁니다. `ModLoader`는 이 오류가 있으면 "Error during pre-loading phase"를 남기고 `ModList.clearLoadedMods()` 뒤 `LoadingFailedException`을 던집니다. 실제 Forge에서는 그 모드뿐 아니라 **어떤 모드도 실행되지 않습니다.** Poly 모드 로더는 그 모드(와 그것에 의존하는 모드)만 빼므로 Forge보다 엄격하지 않습니다.
- **26.1 대 26.1.2.** 26.1 묶음의 Forge 모드 대부분은 26.1.2용 빌드입니다. Forge는 핫픽스마다 따로 빌드하므로, 26.1에서는 Forge 62.0.9가 실행되고 이 모드들은 FML이 거부합니다. 같은 모드를 26.1.2(Forge 64.1.5)에서 실행하려고 26.1.2 묶음(`compat/mod-corpus-26.1.2.json`, Modrinth에서 26.1.2용으로 가장 많이 받은 모드)을 만들었고, 34개 중 28개가 월드 생성·GameTest·저장까지 실행됩니다(Better Advancements·Rhino를 뺀 뒤).
- **libIPN(26.2).** Modrinth의 libIPN 6.9.0에는 26.2용으로 표시된 Forge 파일이 둘 있습니다. `forge-26.3-6.9.0`(`DaLgbo0u`)은 `mods.toml`이 Minecraft `[26.3,26.4)`·Forge `[66,)`이라 FML이 거부하는데, 같은 시각에 올린 `forge-26.2-6.9.0`(`sWHNYAmg`)보다 몇 ms 늦어서 묶음이 그것을 골랐습니다. 잠금 파일을 26.2용 빌드로 고정했습니다(`note` 항목). `modCorpus`는 이제 잠금 파일에 없는 jar를 받은 폴더에서 지웁니다(전에는 바뀐 jar가 남아 함께 실행됐음).
- **Better Advancements 0.6.0.76(26.1.2).** 26.1.2 런처 프로필(B6)에서 설정을 읽을 때 `IllegalAccessError`로 클라이언트가 멈췄습니다. 이 모드는 자기 패키지에서 패키지 전용 enum `AdvancementTabType`을 부르고, 그 클래스를 Fabric 형식 access widener(`betteradvancements.accesswidener`)로만 엽니다. Forge 64.1.5의 라이브러리에는 access widener를 읽는 코드가 없고, Forge의 access transformer에도 바이너리 패치 결과에도(`javap`) 이 클래스는 `public`이 아닙니다. 실제 Forge에서도 같은 곳에서 멈추므로 묶음 실행에서 뺍니다(`brokenUnderForge`). 바닐라 26.2에서는 이 클래스가 `public`이라 26.2 빌드는 실행됩니다.
- **Rhino 2101.2.7(26.1·26.1.2).** 같은 프로필에서 월드에 들어가자마자 Xaero's Minimap·World Map이 서버 데이터가 없다며(`serverData` `null`) 접속을 끊었습니다. 원인은 Rhino입니다. Rhino 2101.2.7은 Minecraft 1.21용 빌드인데 Modrinth에 26.1.x용으로도 올라 있고, `pack.mcmeta`가 없습니다. Forge의 `ResourcePackLoader.findPacks`는 모든 모드 파일의 루트를 팩으로 읽고, 바닐라가 메타데이터를 찾지 못하면("Missing metadata in pack mod:rhino") `fml.modloading.brokenresources` 경고를 더합니다. `ClientModLoader.completeModLoading`은 경고가 하나라도 있으면 `LoadingErrorScreen`을 띄우기 전에 `BusGroup.DEFAULT.shutdown()`을 부르고, 그 화면의 버튼은 버스를 다시 켜지 않습니다(`javap`, Forge 64.1.5·65.1.5). 그 뒤로는 `ServerStartingEvent`를 포함한 게임 이벤트가 어떤 모드에게도 가지 않습니다. 확인은 실행 중에 Forge 버스에 리스너를 더해 서버 시작 이벤트 세 가지가 한 번도 오지 않는 것과 `ModLoader.getWarnings()`에 이 경고 하나만 있는 것을 본 것입니다(임시 진단, 저장소에 남기지 않음). Forge 자신의 동작이므로 실제 Forge도 같습니다. 두 빌드를 묶음 실행에서 뺍니다(`brokenUnderForge`). B2의 26.1 클라이언트도 Rhino build 81과 함께 이 상태로 실행됐습니다(그때는 서버 이벤트에 기대어 접속을 끊는 모드가 없어 드러나지 않음).
- **26.1 클라이언트의 실험 설정 확인.** Rhino를 뺀 뒤 26.1 `corpusForgeClient`가 바닐라의 "Worlds using Experimental Settings are not supported" 확인 화면에서 멈췄습니다(버스가 꺼져 있을 때는 모드 데이터 팩이 들어가지 않아 나오지 않았음). 바닐라는 월드의 lifecycle을 차원의 안정성과 모든 레지스트리의 lifecycle을 합쳐 정하고(`LevelStorageSource.getLevelDataAndDimensions`: `dimensions.lifecycle().add(allRegistriesLifecycle())`, 26.1·26.2 같음), 알려진 팩(`KnownPack`)이 아닌 데이터 팩의 항목은 `Experimental`로 등록합니다. Forge(62.0.9·64.1.5·65.1.5)는 모드 팩을 알려진 팩으로 등록하지 않습니다. 실행 중 진단(임시)으로 26.1에서는 Dungeons and Taverns·Trek의 항목 때문에 월드가 `Experimental`이 되고, 26.1.2에서는 같은 종류의 `Experimental` 항목이 있어도 월드가 `Stable`인 것을 확인했습니다(26.1.2·26.2 묶음에는 TerraBlender·Biomes O' Plenty가 있음, 26.1의 TerraBlender 26.1.0.2는 위의 이유로 뺌). 화면은 실제 Forge에서도 나오는 바닐라 동작이므로, `fabric-client-session`이 플레이어처럼 "I know what I'm doing"(백업 없이 계속)으로 답하고 로그에 남기게 했습니다. 그 뒤 26.1 클라이언트가 Forge 모드 26개로 월드에 들어가 200틱 후 저장·종료합니다.
- **클라이언트 전용.** `minecraft` 의존이 `side="CLIENT"`뿐인 모드, `@Mod` 클래스가 `@OnlyIn(Dist.CLIENT)`인 모드(Forge가 서버에서 그 클래스를 지워 멈춤), Fabric 메타데이터만 `"environment": "client"`인 ModLauncher 서비스(CustomSkinLoader)입니다. 서버의 Forge는 앞의 경우를 그대로 만들기 때문에 모드에 따라 동작이 다릅니다. 완료 기준 5는 이것을 허용 범위로 둡니다. 26.1 클라이언트(`corpusForgeClient`)에서는 이 모드들이 빠지지 않고 실행됐습니다.

### G9 실행으로 알게 된 것 (2026-10-23)

- **Java는 파일 시스템 목록을 한 번만 만듭니다.** `FileSystemProvider.installedProviders()`는 처음 불릴 때 목록을 고정합니다. 그 뒤 시스템 클래스로더에 jar를 더하면 클래스는 보이지만 파일 시스템은 늘지 않습니다(`SystemClassPathAgentTest.javaListsItsFileSystemsOnlyOnce`). 이전 검사는 Forge 파일을 준비하기 전에 바로 이 메서드를 불렀습니다. 이제는 리소스 조회로 판단하고, jar를 더한 뒤에만 목록을 확인합니다(`ForgeFileSystems`).
- **Java agent는 `Instrumentation`만 기억합니다.** 런처 프로필은 `-javaagent:${library_directory}/<poly-bootstrap>`로, 서버는 `polymodloader-server.jar`의 `Launcher-Agent-Class`로 시작합니다. Forge 모드가 있을 때만 `appendToSystemClassLoaderSearch`로 Forge의 네 jar를 더합니다. 그 뒤 `ForgeBootModules`는 개발 실행과 같이 이 jar를 BOOT 모듈로 만들지 않고 나누어 씁니다.
- **설치한 서버에서 확인.** 설치 jar로 설치한 26.2 서버에 `forge_hello`를 넣자 게임 테스트 서버에서 FML이 모드를 만들고 GameTest가 통과했고, 전용 서버는 Forge의 실행 핸들러로 EULA 확인까지 갔습니다. 모드가 없을 때는 `.poly/forge`가 생기지 않고 로그에 Forge가 나오지 않습니다.

## 단계

| # | 일 | 검증(먼저 만듦) | 상태 |
|---|---|---|---|
| G0 | 사실 조사 | 이 문서의 "지금 상태"와 "G0 조사 결과" | **완료** (2026-10-22) |
| G1 | ADR-0021 (접근 A) | 리뷰 | 제안으로 작성, G3 조사로 고침 (2026-10-22) |
| G2 | 패치된 게임 jar 만들기 | `ForgePatchSet`·`ForgeBinaryPatch`(`poly-bridge-forge`). `ForgePatchSetTest`: GDIFF·형식 단위 테스트, 그리고 실제 Forge 설치 프로그램 결과와 바이트 비교(`-PforgeInstaller` 등을 줄 때) | 라이브러리 완료. 런타임 연결은 G3 |
| G3a | 게임 클래스로더의 모듈 정의 | 단위 테스트(`GameClassLoaderModulesTest`): 모듈 jar 둘을 레이어로 정의하고, 변환을 거친 클래스가 이름 있는 모듈에 들어가며 `Class.forName(module, name)`, `ServiceLoader.load(layer, …)`, 모듈 리소스가 동작함. Forge가 없을 때 기존 시나리오가 그대로 통과 | **완료** (2026-10-22) |
| G3b | ModLauncher 서비스 계약 구현(`poly-bridge-forge`의 `forgeHost` 소스 세트, `PolyModLauncher`) | G3d 시나리오. FML의 서비스가 ModLauncher 순서대로 불리고 FML이 Minecraft·Forge·언어 공급자를 찾음 | **완료** (2026-10-22) |
| G3c | Forge 파일 준비(`ForgeInstallation`) | 설치 프로그램(SHA-1 고정)과 `version.json`의 라이브러리를 SHA-1로 확인해 받고, 패치된 서버 jar를 G2로 만듦(1394개 패치) | **완료** (2026-10-22) |
| G3d | Forge 자신(`forge` 모드)만으로 서버 시작 | `forgeServerBoot`: 전용 서버가 EULA 확인까지. `forgeGameTest`: 게임 테스트 서버에서 Forge의 모드 로딩("Forge mod loading, version 65.1.5"), 월드 생성, GameTest 통과, 저장 | **완료** (2026-10-22, 26.2) |
| G4 | 테스트 모드 `forge-hello` (`@Mod` + 모드 이벤트 버스 + `FMLCommonSetupEvent` + `DeferredRegister`로 아이템 하나) | `forgeGameTest`: FML이 `forge_hello` 모듈로 모드를 만들고, 공통 설정 이벤트에서 `forge_hello:hello_item`이 등록돼 있음. 같은 실행에서 Poly 모드(`minimal_native_mod`)도 동작 | **완료** (2026-10-22, 26.2) |
| G5 | access transformer(`203`), Forge Mixin(`202`) | `forgeGameTest`에 `forge-at`(private 필드·메서드를 AT로 열어 생성자에서 씀), `forge-mixin`(매니페스트 `MixinConfigs`, Forge가 패치하고 Poly 모드 로더가 변환한 `MinecraftServer`에 `@Inject` 둘) | **완료** (2026-10-23, 26.2) |
| G6 | 실제 모드 | `corpusForge`: 묶음의 Forge 모드를 FML이 만들고, 월드 생성·GameTest·저장까지 | **완료** (2026-10-23, 26.2 게임 테스트 서버: 38개 중 30개 실행, 8개는 `209`로 이유와 함께 뺌) |
| G7 | 클라이언트 | `forgeClient`(Forge 테스트 모드 + Fabric 모드 `fabric-client-session`), `corpusForgeClient`(묶음의 Forge 모드). 둘 다 창이 열림 | **완료** (2026-10-23, 26.2: 저장된 월드에 들어가 200틱 후 저장하고 종료) |
| G8 | Fabric 모드와 함께 | `forgeFabricGameTest`(테스트 모드), `corpusFabricForge`(실제 묶음의 Fabric·Forge 모드를 한 게임에서) | **완료** (2026-10-23, 26.2 게임 테스트 서버: Fabric 모드 90개와 Forge 모드가 함께 월드 생성·GameTest·저장. 여러 로더용 jar가 Forge로 간 뒤의 수) |
| G9 | 설치한 Poly 모드 로더(런처 프로필, `install-server`의 전용 서버)에서 Forge 모드 (ADR-0024) | `ForgeFileSystemsTest`, `SystemClassPathAgentTest`(자식 JVM: `-javaagent`, `Launcher-Agent-Class`), `serverInstallTest`(설치한 서버: 모드 없이 Forge 흔적 없음, `forge_hello`로 게임 테스트 서버·전용 서버), `launcherProfileForgeTest`(창이 뜸) | **완료** (2026-10-23, 26.2: 설치한 서버에서 게임 테스트·전용 서버 시작, 런처를 재현한 프로필 실행에서 Forge 테스트 모드 3개와 Fabric 클라이언트 모드로 저장된 월드에 들어가 200틱 후 저장·종료. 실제 Minecraft Launcher 실행 기록은 없음) |

## 위험

- 패치된 게임 jar 위에서는 Poly 모드 로더 내장 규칙의 바이트코드 사전조건이 달라질 수 있습니다. 규칙마다 패치된 jar에서 `javap`로 다시 확인해야 합니다(`BuiltInRulesTest`를 패치된 jar로도 실행).
- Forge 라이브러리와 Poly 모드 로더 라이브러리의 버전이 겹칩니다(ASM 9.10.1 대 9.11.1, Mixin 원본 대 Fabric 포크, night-config 대 tomlj).
- Forge의 라이선스(LGPL-2.1, 일부 MIT)와 패치 데이터 배포 조건을 확인해야 합니다. 패치 데이터와 FML 라이브러리는 Poly 모드 로더가 배포하지 않고, 설치 시 Forge Maven에서 받는 것이 기본안입니다.
