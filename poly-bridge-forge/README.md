# poly-bridge-forge

## 이 모듈이 하는 일
- `META-INF/neoforge.mods.toml`과 `META-INF/mods.toml`을 분석해 필요한 FML 기능을 진단합니다. jar에 든 모든 `[[mods]]` ID를 보고해, 그 ID가 필요한 다른 로더의 모드가 "설치됐지만 차단됨" 이유를 받게 합니다. 형식이 틀린 값은 예외 대신 `POLY-COMPAT-200`으로 보고합니다.
- MinecraftForge를 Poly 모드 로더 게임 클래스로더 위에서 실행합니다(ADR-0021, 제안). Forge 모드가 있거나 `-Dpoly.forge=always`일 때 `MinecraftForgeBridge.gameHost`가 `ForgeGameHost`를 돌려줍니다.
  1. `ForgeInstallation`: Forge 설치 프로그램(SHA-1 고정, `forge-releases.properties`)과 그 `version.json`의 라이브러리를 SHA-1로 확인해 `.poly/forge/<버전>/`에 받고, 바닐라 jar에 Forge 바이너리 패치를 적용합니다(`ForgePatchSet`, G2).
  2. `ForgeBootModules`: Forge 라이브러리를 FML의 BOOT 레이어 모듈로 게임 클래스로더에 정의합니다. Poly 모드 로더 클래스 경로에 이미 있는 라이브러리는 나누어 쓰고, `cpw.mods.modlauncher`는 ModLauncher의 API와 Poly 모드 로더의 구현(`forge-host.jar`)으로 만듭니다.
  3. FML이 보는 Forge 모드는 Poly 모드 로더가 찾은 것뿐입니다. 그 jar를 게임 클래스로더의 리소스로 두어 FML의 `ClasspathLocator`가 찾게 하고, FML의 mods 폴더 검색(`ModsFolderLocator`)은 BOOT 레이어에서 뺍니다. 같은 폴더의 Fabric·Poly 모드 때문에 FML이 멈추지 않습니다.
  4. `PolyModLauncher`(`src/forgeHost`, `forge-host.jar`로 묶임): ModLauncher의 서비스 계약을 구현합니다. FML의 서비스를 ModLauncher 순서대로 부르고, SERVICE·PLUGIN·GAME 레이어를 게임 클래스로더에 정의하고, 실행 플러그인을 변환 단계로 설치하고, Forge의 실행 핸들러로 게임을 시작합니다. 게임 테스트 서버는 `GameTestLaunchHandler`(`polymodloader_server_gametest`)로 시작합니다.
  5. 서로의 모드 목록(ADR-0025): FML이 실행하는 모드를 `GameHost.mods()`로 다른 로더에 알립니다(Fabric Loader의 `isModLoaded`). 반대로 Poly Mod와 다른 로더의 모드(`GameHost.otherMods`, `ForeignModIndex`)는 FML의 `ModList.getModFileById`·`isLoaded`가 FML의 답이 없을 때 답합니다. `ForgeModListPatch`가 `ModList`의 두 메서드를 검사한 형태일 때만 바꾸고(아니면 `POLY-COMPAT-211`), `forge-host.jar`의 `ForeignModList`가 forgespi 보기(`IModFileInfo`·`IModInfo`·`IModFile`)를 줍니다. `fmlcore`는 변환하지 않는 PLUGIN 레이어에 있으므로 레이어 모듈을 읽을 때 패치합니다(`PatchedClassReference`, night-config 패치와 같음).
  6. NeoForge(ADR-0026, 기본값 켜짐, `-Dpoly.neoforge=off`로 끔): NeoForge 모드가 있으면 `NeoForgeBridge.gameHost`가 `NeoForgeGameHost`를 돌려줍니다. 릴리스는 `neoforge-releases.properties`(26.1 `26.1.0.19-beta`, 26.1.1 `26.1.1.15-beta`, 26.1.2 `26.1.2.122`, 26.2 `26.2.0.93`; 26.1·26.1.1은 NeoForge에 베타 빌드만 있음). `NeoForgeInstallation`이 NeoForge 설치 프로그램과 라이브러리를 받고 `NeoForgePatchBundle`로 패치된 게임 jar를 FML의 라이브러리 폴더 구조에 만듭니다. `NeoForgeGameHost`가 로더 라이브러리를 자동 모듈 레이어로 게임 클래스로더에 정의하고, `NeoForgeLoaderPatch`로 `FMLLoader`의 두 호출(게임 레이어 만들기, mods 폴더 검색)을 검사한 형태일 때만 `neoforge-host.jar`(`src/neoForgeHost`)의 `PolyFml`로 바꾼 뒤, FML 자신의 `FMLLoader.create`를 실행합니다. 게임 레이어는 게임 클래스로더가 정의하고, FML의 `ClassProcessor`는 브리지 변환 단계에서 돕니다(`PolyTransformingClassLoader`; 처리기가 만드는 클래스는 `generate`). FML의 플러그인 라이브러리 체인은 게임 클래스로더의 대체 로더가 됩니다(`addFallback`). FML이 거부할 모드는 `NeoForgeModRequirements`가 FML 시작 전에 빼고(`POLY-COMPAT-217`), FML이 실행하는 모드는 `GameHost.mods()`로 Poly 모드와 다른 로더에 보입니다. 반대로 Poly 모드와 다른 로더의 모드는 FML의 `ModList.getModFileById`·`isLoaded`가 답합니다: `ForgeModListPatch`의 NeoForge 모양(`Shape.NEOFORGE`, 인스턴스 메서드)으로 FML `loader` 모듈의 `ModList`를 검사한 형태일 때만 바꾸고(아니면 `POLY-COMPAT-218`), `neoforge-host.jar`의 `ForeignModList`가 neoforgespi 보기를 줍니다. 타이틀 화면의 로더별 모드 수는 `NeoForgeTitleBranding`이 NeoForge의 브랜딩 줄 위에 그립니다.
  7. NeoForge와 Fabric·Quilt 모드를 한 게임에서(ADR-0026 30~33): Fabric의 Mixin 하나가 NeoForge 모드도 맡습니다. `NeoForgeSharedMixinPatch`가 FML의 `MixinFacade`(자기 Mixin을 시작하지 않음, `finishInitialization`은 `PolyFml.finishSharedMixin`으로)와 `FMLLoader.createClassProcessorSet`의 Mixin 처리기(같은 이름의 빈 처리기)를 검사한 형태일 때만 바꾸고, 형태가 다르면 NeoForge 시작 전에 알아 `216`으로 뺍니다. `FmlSharedMixin`이 FML의 `addMixins`처럼 설정을 찾고(`requiredMods`는 다른 로더의 모드도 셈), MixinExtras 게임 라이브러리는 게임 레이어에서 뺍니다. FML의 Mixin 라이브러리는 클래스 경로의 같은 판을 씁니다. Fabric 모드는 `NeoForgeRegistryOpening`이 NeoForge의 레지스트리(`GameData.unfreezeData`/`freezeData`, 루트 레지스트리)를 연 채로 초기화합니다. `classBytesJars`로 패치된 게임과 universal jar의 클래스를 FML이 정의하기 전에도 읽게 해 Fabric 쪽 Mixin과 그 계획이 NeoForge의 클래스를 봅니다. `WidenedFieldGetters`는 넓힌 필드 때문에 NeoForge의 `ReplaceFieldWithGetterAccess`가 거부한 클래스를 다시 처리합니다.

## 무엇을 소유하는가
- `dev.polymodloader.compat.forge`(`NeoForgeBridge`, `NeoForgeGameHost`, `NeoForgeModRequirements`, `NeoForgeInstallation`, `NeoForgePatchBundle`, `NeoForgeLoaderPatch`, `NeoForgeLaunch`, `NeoForgeTitleBranding`, `MinecraftForgeBridge`, `ForgeGameHost`, `ForgeInstallation`, `ForgeBootModules`, `ForgePatchSet`, `ForgeBinaryPatch`, `ForgeTitleBranding`, `ForeignModIndex`, `ForgeModListPatch`, `PatchedClassReference`)
- `forge-host.jar`의 `ForeignModList`, `ForeignModFileInfo`, `ForeignModInfo`, `ForeignModFile`(다른 로더의 모드를 FML 모드 목록에 보이는 forgespi 보기)
- `neoforge-host.jar`(`src/neoForgeHost`)의 `PolyFml`, `PolyModsLocator`, `PolyTransformingClassLoader`와, 다른 로더의 모드를 NeoForge FML 모드 목록에 보이는 neoforgespi 보기 `ForeignModList`, `ForeignModFileInfo`, `ForeignModInfo`, `ForeignModFile`
- `NeoForgeForeignPayloads`: Fabric·Quilt 모드와 함께일 때 NeoForge의 채널 검사(`checkPacket`, `handleModdedPayload`)가 다른 로더의 페이로드를 그 로더에 맡기게 하는 규칙, 묶음 패킷 걸러내기(`filterGameBundlePackets`)가 다른 로더의 페이로드를 빼지 않게 하는 규칙(`poly:neoforge_bundle_filter`, ADR-0026 41), NeoForge 자기 페이로드를 NeoForge 코덱으로 쓰게 하는 `ForeignPayloads` 호스트(ADR-0026 40).
- `MixinTypeAudit`(`neoforge-host.jar`): Mixin 검사(`-Dpoly.neoforge.mixinAudit=true`)에서, 설정에 적힌 mixin(빠진 것 포함)이 대상에 줄 인터페이스·접근자가 로드된 대상에 없는데 변환된 게임 클래스가 그 형을 `instanceof` 확인 없이 쓰면 실패로 적습니다(ADR-0026 39). 진단 속성 `-Dpoly.neoforge.headless=true`는 클라이언트에서도 FML을 초기 창 없이 시작합니다(ADR-0026 38).
- `NeoForgeTitleBranding`: NeoForge가 패치한 타이틀 화면은 `BrandingControl.forEachLine(boolean, boolean, BiConsumer<Integer, String>)`으로 자기 브랜딩 줄을 그립니다. `forEachLine`은 `BrandingControl.getBrandings(boolean, boolean)`의 목록을 넘깁니다. 규칙 `poly:client_title_mod_counts_neoforge`가 그 목록이 `NeoForgeTitleBranding.withModCounts`를 거쳐 돌아가게 해, NeoForge의 줄 바로 위에 로더별 모드 수 줄이 옵니다. 타이틀 화면의 호출이 아니라 목록을 바꾸므로 브랜딩을 직접 그리는 모드(FancyMenu)에서도 보입니다. 대상이 NeoForge의 universal jar에 있어 `NeoForgeGameHost.transformTargetJars`가 그 jar를 계획에 넘깁니다. NeoForge 26.1.0.19-beta·26.1.2.122·26.2.0.93의 universal jar와 설치 프로그램이 패치한 클라이언트 jar에서 `NeoForgeTitleBrandingTest`(`-PneoForgeUniversal`, `-PneoForgePatchedClient`)로 확인했습니다. 목록 구성이 다른 릴리스에서는 규칙이 꺼지고 NeoForge 줄만 보입니다(`POLY-COMPAT-219`는 폐기).
- `ForgeTitleBranding`: Forge가 패치한 타이틀 화면은 바닐라 버전 글자 대신 `BrandingControl.forEachLine(boolean, boolean, ObjIntConsumer)`로 자기 브랜딩 줄을 아래에서부터 그립니다. 규칙 `poly:client_title_mod_counts_forge`(CLIENT, DISABLE_RULE, `RedirectCall`)가 그 호출을 `ForgeTitleBranding.forEachLine`으로 바꾸고, 훅은 Forge의 줄을 그린 뒤 같은 소비자에 다음 번호로 로더별 모드 수 줄을 넘겨 Forge의 줄 바로 위에 같은 모양으로 그립니다. 대상은 `forge-releases.properties`의 네 릴리스(26.1-62.0.9, 26.1.1-63.0.2, 26.1.2-64.1.5, 26.2-65.1.5)에서 `javap`와 `ForgeTitleBrandingTest`(`-PforgePatchedClient` 또는 `-PforgeClientInstaller`+`-PforgeVanillaClient`, `-PforgeUniversal`)로 확인했습니다.
- `dev.polymodloader.compat.forge.modlauncher`와 ModLauncher의 `Launcher`·`Environment` 대체본(`src/forgeHost`). ModLauncher(LGPL-3.0)의 코드는 복사하지 않았고 공개 API의 계약을 따라 새로 썼습니다.

## 무엇을 하지 않는가
- NeoForge 모드와 Forge 모드를 함께 실행하지 않습니다. 함께 넣으면 Forge가 실행되고 NeoForge 모드는 이유와 함께 빠진 뒤 진단만 합니다(`POLY-COMPAT-221`, 이유는 ADR-0026 34번: 두 로더가 같은 바닐라 메서드 736개를 서로 다르게 패치하고, 6개 클래스의 상위 클래스를 서로 다르게 바꿈). Fabric·Quilt 모드와는 함께 실행합니다(위 7). `-Dpoly.neoforge=off`이면 NeoForge 모드는 진단만 합니다. Forge 언어 공급자 jar는 아직 진단만 합니다.
- 서버에서 클라이언트 전용 NeoForge 모드를 빼지 않습니다. NeoForge 메타데이터에는 클라이언트 전용이라는 표시가 없고, NeoForge 자신도 그런 모드(Sodium 등)를 넣으면 서버가 멈춥니다(`docs/plans/NEOFORGE_FULL_SUPPORT.md` "N11").
- Forge 모드와 Fabric 모드를 함께 넣으면 Forge 쪽을 로드하지 않습니다(`POLY-COMPAT-207`).
- Forge 파일과 FML 라이브러리를 배포하지 않습니다. 사용자 PC에서 Forge Maven으로부터 받습니다.

## 의존성
`poly-bridge-api`, tomlj, XZ for Java(LZMA), gson. `forgeHost`는 ModLauncher·securemodules·fmlloader·forgespi의 API에 대해서만, `neoForgeHost`는 NeoForge의 FML 11(`loader`, `mergetool` API)에 대해서만 컴파일합니다(배포하지 않음).

실행할 때 JVM 클래스 경로에 Forge의 `securemodules`, `unsafe`, `JarJarFileSystems`, `roimfs`가 있어야 합니다. Java가 파일 시스템을 시스템 클래스로더에서만 찾기 때문입니다(Forge의 shim도 같음). 개발 실행은 처음부터 클래스 경로에 넣고(`forgeClassPath`), 설치한 Poly 모드 로더는 Forge 모드가 있을 때만 받은 jar를 Poly 모드 로더의 Java agent로 더합니다(`ForgeFileSystems`, ADR-0024). 둘 다 안 되면 `POLY-COMPAT-206`으로 Forge 모드를 로드하지 않습니다.

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bridge-forge:check
.\gradlew.bat :poly-bootstrap:forgeGameTest     # Forge + 테스트 모드 forge_hello + Poly 모드 (네트워크, 26.2)
.\gradlew.bat :poly-bootstrap:forgeServerBoot   # Forge만 넣고 전용 서버 (네트워크, 26.2)
.\gradlew.bat :poly-bootstrap:neoForgeGameTest  # NeoForge + neoforge_hello + Poly 모드, 게임 테스트 서버 (네트워크, 26.2, Java agent)
.\gradlew.bat :poly-bootstrap:neoForgeWorldTest # NeoForge + 게임 테스트 하네스 (네트워크, 모든 지원 버전)
```

## 대표 흐름
검색에서 Forge 모드를 찾으면 런타임이 `gameHost`를 묻습니다. 호스트가 Forge 파일을 준비하고, 런타임은 패치된 jar로 변환 계획을 세운 뒤 게임 클래스로더를 만듭니다. 호스트가 FML을 시작해 레이어를 정의하고, 런타임은 Poly 모드와 게임 계층을 준비한 다음 Forge의 실행 핸들러로 게임을 시작합니다.

## 알려진 제한
- 검증 범위는 `docs/plans/FORGE_SUPPORT.md`의 단계 표(G3~G9)에 있습니다.
- 이 버전 이전의 설치 프로그램으로 만든 런처 프로필·서버에는 Java agent가 없어서, Forge 모드를 넣으면 `206`이 다시 설치하라고 알립니다.
- 설치한 런처 프로필의 Forge는 런처를 재현한 `launcherProfileForgeTest`로만 확인합니다. 실제 Minecraft Launcher로 실행한 기록은 없습니다(ADR-0017과 같음).
- 패치된 jar에서 Poly 모드 로더 내장 규칙 `poly:server_brand`와 `poly:client_title_mod_counts`는 맞지 않아 꺼집니다(Forge와 NeoForge가 서버 브랜드와 타이틀 화면 브랜딩을 직접 그림). 경고가 아니라 INFO로 남습니다. NeoForge 패치에서는 `poly:payload_codecs`도 꺼집니다(경고).
- NeoForge는 Poly 모드 로더의 Java agent가 있어야 실행됩니다(FML이 `java.lang.invoke`를 반사로 읽음). 설치한 런처 프로필과 서버 실행 jar는 agent로 시작합니다. agent 없이 시작한 개발 실행에서는 NeoForge 모드가 `216`으로 빠집니다.
- Forge 모드에게 다른 로더의 모드는 `ModList.getModFileById`·`isLoaded`로만 보입니다. `getModContainerById`(FML의 생명주기 객체), `getModFiles`·`getMods`(FML이 모드 로딩·리소스 팩에 씀), `LoadingModList.getModFileById`(구체 클래스)에는 없습니다(ADR-0025).
- ADR-0009도 보세요.
