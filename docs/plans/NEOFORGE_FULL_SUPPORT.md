# NeoForge 완전 지원 계획 (2026-10-23 작성, 2026-10-24 갱신)

[`ROADMAP.md`](ROADMAP.md)의 1단계입니다. 베타(`0.2.0-beta.1`) 뒤에 시작합니다. "완전히 지원"은 `compatibilityCorpus`와 [`COMPATIBILITY.md`](../COMPATIBILITY.md)의 결과로만 판단합니다.

원칙은 Fabric·Forge 때와 같습니다.
- **로더의 의미를 추측해 다시 만들지 않습니다.** NeoForge 자신의 코드를 Poly 모드 로더 위에서 실행합니다.
- **실패하는 검증을 먼저 만들고, 그것을 통과시킵니다.**

> 2026-10-24 갱신: **완료.** N0~N13을 마쳤고 NeoForge는 기본값으로 켜집니다. 창이 열리는 검증(N7 클라이언트, N9 런처 프로필)도 다섯 번째 실행에서 모두 통과해 상태는 `Verified`입니다. 판정은 "N6~N13 결과"와 [`COMPATIBILITY.md`](../COMPATIBILITY.md) "NeoForge 완전 지원 점검"에 있습니다.

## 완료 기준

"NeoForge 완전 지원"이라고 쓰려면 다음이 모두 참이어야 합니다. 지원하는 모든 Minecraft 버전(26.1 계열, 26.2)에서입니다. 여섯 기준은 [Forge 완전 지원](FORGE_FULL_SUPPORT.md)과 같습니다.

1. 실제 인기 NeoForge 모드 묶음이 **클라이언트**에서 저장된 월드에 들어가고, 틱이 돌고, 저장하고 나올 수 있다.
2. 같은 묶음이 게임 테스트 서버(월드 생성·틱·저장)를 통과하고, 전용 서버 시작은 NeoForge가 게임을 시작해 EULA 확인까지 간다. Poly 모드 로더는 EULA에 동의하지 않습니다.
3. 설치 프로그램으로 만든 런처 프로필과 서버에서 NeoForge 모드가 있는 채로 게임이 시작된다.
4. NeoForge 런타임에서 Poly 모드 로더가 빼거나 거부하는 기능이 없다. 지금 알려진 것은 FML의 `ClassProcessor` 네 가지(access transformer, enum 확장, Mixin, dist 정리)입니다. 나머지 목록은 N0에서 확인합니다.
5. 빠지는 NeoForge 모드는 NeoForge에서도 빠지거나 실행되지 않는 경우뿐이다. 예: FML 요구 조건이 맞지 않는 모드, 서버의 클라이언트 전용 모드.
6. NeoForge 모드를 넣은 상태의 서버 틱·시작 시간을 측정해 [`PERFORMANCE.md`](../PERFORMANCE.md)에 남긴다.

NeoForge 모드를 Fabric 모드나 Forge 모드와 한 게임에서 함께 실행하는 것은 이 기준 밖입니다("범위 밖" 참고, 함께 실행하지 않는 이유는 [ADR-0026](../adr/0026-neoforge-on-poly-mod-loader.md) 22번).

## 시작할 때 상태 (2026-10-23, 사실)

- **상태는 `Blocked`입니다.** `poly-bridge-forge`의 `NeoForgeBridge`는 `FmlModsTomlBridge`를 이어받아 `META-INF/neoforge.mods.toml`을 읽고 진단만 합니다(`POLY-COMPAT-200..205`). 실행 코드는 없습니다.
- **모드 묶음의 NeoForge 모드:** 26.2 44개, 26.1 44개, 26.1.2 45개(`poly-bootstrap/compat/mod-corpus-<버전>.json`). 지금은 하나도 실행되지 않습니다. `corpusNeoForge` 작업은 있습니다.
- **NeoForge 26.2.0.93의 구조**(`COMPATIBILITY.md` "실험 2"와 "조사 요약", 2026-10-19 확인):
  - 메타데이터는 `META-INF/neoforge.mods.toml`(`modLoader=javafml`)입니다. 진입점은 `@Mod` 생성자와 이벤트 버스입니다.
  - Access transformer 652줄, Mixin 설정 `neoforge.mixins.json` 1개가 있습니다.
  - FancyModLoader(FML)가 ModLauncher를 흡수해 없앴습니다. FML이 자기 `TransformingClassLoader`와 JPMS 게임 레이어를 만들고, 변환은 `ClassProcessor` SPI로 합니다.
  - Mixin은 Fabric과 같은 포크 `net.fabricmc:sponge-mixin`입니다. FML 0.17.4, Fabric Loader 0.17.5 기준이고, Forge는 원본 0.8.7을 씁니다.
  - 26.x는 난독화가 없어서 NeoForge 모드도 Mojang 이름을 씁니다.

## N0 조사 결과 (2026-10-23)

실제 jar와 NeoForge Maven(`maven.neoforged.net`)에서 확인한 것입니다. 받은 파일은 실행하지 않고 `javap`·`jar`로만 읽었습니다.

- **버전.** NeoForge는 Forge처럼 핫픽스마다 따로 빌드합니다. 정식 빌드는 26.2 `26.2.0.93`(35개 중 최신)과 26.1.2 `26.1.2.122`(50개)입니다. 26.1과 26.1.1에는 베타 빌드만 있습니다(마지막 `26.1.0.19-beta`, `26.1.1.15-beta`). 26.1 계열은 26.1.2를 기준으로 하고, 26.1·26.1.1을 어떻게 다룰지는 N1에서 정합니다.
- **설치 프로그램(26.2.0.93).** `version.json`의 `mainClass`는 `net.neoforged.fml.startup.Client`(서버는 `...startup.Server`)이고 `inheritsFrom`은 바닐라 `26.2`입니다. 게임 인자는 `--fml.neoForgeVersion 26.2.0.93 --fml.mcVersion 26.2 --fml.neoFormVersion 2`입니다. 서버는 모듈 경로 없이 `-classpath`로 시작합니다. 라이브러리에 ModLauncher·securemodules·bootstrap이 없습니다(FancyModLoader `loader`·`earlydisplay` 11.0.16, `bus` 8.0.5, `accesstransformers` 11.0.2, ASM 9.10.1, night-config 3.9.0, JarJarSelector·JarJarMetadata 0.5.1).
- **Mixin이 Poly 모드 로더의 Fabric 쪽과 같은 버전입니다.** NeoForge 26.2.0.93과 26.1.2.122 모두 `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`을 씁니다. Poly 모드 로더 설치 프로필의 Mixin과 같습니다. 위 "위험"의 버전 차이(0.17.4 대 0.17.5)는 지금 버전에서는 없습니다.
- **패치.** `install_profile.json`의 처리기는 `installertools 4.0.17`의 `PROCESS_MINECRAFT_JAR`(`--apply-patches {BINPATCH}`) 하나입니다. 클라이언트와 서버가 같은 `data/client.lzma`를 씁니다. 이 파일은 Forge의 binpatch zip이 아니라 LZMA로 압축한 `NFPATCHBUNDLE001` 형식입니다(풀면 7,231,187바이트). Forge용 `ForgePatchSet`을 그대로 쓸 수 없고 이 형식을 읽는 코드가 필요합니다(N2). NeoForge 본체 `neoforge-26.2.0.93-universal.jar`는 설치 프로그램이 라이브러리 폴더에 넣고, 안에 `META-INF/neoforge.mods.toml`, `accesstransformer.cfg`, `accesstransformergenerated.cfg`, `neoforge.mixins.json`이 있습니다.
- **FML이 게임 클래스로더를 직접 만듭니다.** `FMLLoader.create(StartupArgs)`(와 `Instrumentation`을 받는 판)는 공개 메서드이고, `StartupArgs`는 게임 폴더, dist, 프로그램 인자, 이미 가진 파일(`claimedFiles`), 남은 클래스 경로, 부모 클래스로더(`parentClassLoader`)를 받습니다. 하지만 `TransformingClassLoader`(FML의 `ModuleClassLoader`)는 private 메서드 `buildTransformingLoader`가 만듭니다. ModLauncher처럼 갈아 끼울 공개 서비스 계약이 없습니다. 게임 클래스를 Poly 모드 로더의 게임 클래스로더가 소유하게 할 방법이 N1의 가장 큰 결정입니다.
- **변환 목록.** `FMLLoader.createClassProcessorSet`은 access transformer, `RuntimeEnumExtender`(enum 확장), `SimpleProcessorsGroup`, `NeoForgeDevDistCleaner`(`cleanDist`일 때), `FMLMixinClassProcessor`를 더하고, `ServiceLoader`로 찾은 `ClassProcessor`를 붙입니다(SPI `net.neoforged.neoforgespi.transformation`).
- **패치 묶음 `NFPATCHBUNDLE001`**(`binarypatcher` 4.0.17 소스의 `PatchBundleReader`, `installertools` 4.0.17의 `ProcessMinecraftJar.applyPatches`).
  - 파일 전체가 LZMA입니다. 풀면 서명 16바이트(`NFPATCHBUNDLE001`), `int` 항목 수, 1바이트 대상 배포판 비트(`CLIENT` 1, `SERVER` 2, `JOINED` 4)가 오고 항목이 이어집니다.
  - 항목: 1바이트 플래그(`0x18` 비트가 종류: `0x00` 새로 만듦, `0x08` 수정, `0x10` 지움 / `0x07` 비트가 그 항목의 배포판), `unsigned short` 길이의 ASCII 경로(`0x20..0x7E`만), 수정이면 `int` 원본 검사합(Adler32), `int` 데이터 길이와 데이터. 수정의 데이터는 GDIFF 델타이고(`com.nothome.delta.GDiffPatcher`), 새로 만듦은 파일 내용 그대로, 지움은 데이터가 없습니다.
  - 적용: 클라이언트 jar에는 `CLIENT`, 서버 jar에는 `SERVER`, 합친 jar에는 `JOINED` 항목만. 수정 전에 원본의 Adler32를 확인하고, 다르면 실패합니다. 새로 만드는 항목의 시각은 `628041600000`(1989-11-25)입니다. Forge의 GDIFF 적용 코드(`ForgeBinaryPatch.gdiff`)를 그대로 쓸 수 있습니다.
  - 실제 수: 26.2.0.93은 10,285개(수정 10,272: 모든 배포판 7,013, `CLIENT|JOINED` 3,259 / 새 클래스 13 / 새 파일 `META-INF/neoforge.mods.toml` 1). 26.1.2.122는 13,587개(수정 13,576: 모든 배포판 6,921, `CLIENT`만 3,324, `JOINED`만 3,331 / 새 클래스 10 / 같은 새 파일 1). Forge 26.2(1,394개)보다 훨씬 많습니다. 26.1.2의 처리기는 `installertools` 4.0.12입니다.
- **FML 시작**(`loader` 11.0.16 소스).
  - `startup.Client`·`Server`·`GameTestServer`(게임 테스트 서버 진입점이 FML에 있음)가 `Entrypoint.startup`에서 `StartupArgs`(게임 폴더, `headless`, dist, `cleanDist`, 프로그램 인자, 이미 가진 파일, 클래스 경로 항목, 부모 클래스로더)를 만들고 `FMLLoader.create`를 부릅니다. 그 뒤 게임 클래스로더에서 `net.minecraft.server.Main`(게임 테스트는 `net.minecraft.gametest.Main`)의 `main`을 부르고 `Server thread`를 기다립니다.
  - `FMLLoader.create` 순서: 경로·설정 → 초기 서비스(`EarlyServiceDiscovery`, mods 폴더에서도 찾음) → 모드 검색 → 모듈 설명자 → `ClassLoadingGuardian`(`Instrumentation`이 있을 때만) → `MixinFacade` → 플러그인 → 언어 공급자 → `createClassProcessorSet` → **`buildTransformingLoader`** → `MixinFacade.finishInitialization(LoadingModList, TransformingClassLoader)`.
  - `buildTransformingLoader`(private)는 `Configuration.resolveAndBind`와 `ModuleLayer.defineModules(cf, [boot], f -> loader)`로 게임 레이어를 만들고, 그 레이어의 모든 모듈을 `TransformingClassLoader` 하나가 정의합니다. 그 로더를 `currentClassLoader`, `gameLayer`, 스레드 문맥 클래스로더로 둡니다. `ModLoader`는 `FMLLoader.getCurrent().getGameLayer()`로 모드를 열고, FML의 Mixin 클래스 공급자(`FMLClassProvider`)는 `getCurrentClassLoader()`를 씁니다.
  - `TransformingClassLoader`(`@ApiStatus.Internal`)는 `ModuleClassLoader`를 이어받고 `maybeTransformClassBytes`에서 `ClassTransformer(ClassProcessorSet, …)`를 부릅니다. 자기 모듈이 아닌 패키지는 부모 레이어나 대체 클래스로더(`currentClassLoader`)에 맡깁니다.
- **모드 검색.** 기본 위치는 `GameLocator`(클래스 경로에서 Minecraft·NeoForge를 찾음; 없으면 `libraryDirectory`와 명령줄 버전으로 운영 환경 위치), `InDevFolderLocator`, `InDevJarLocator`, `ModsFolderLocator`이고, `ServiceLoader`의 `IModFileCandidateLocator`가 더해집니다. `ModsFolderLocator`는 읽지 못한 jar를 `WARN_ALWAYS`로 넘기므로, 같은 폴더의 Fabric jar(`IncompatibleModReason.FABRIC`)나 Poly 모드는 **오류가 아니라 경고**입니다. 다만 FML은 Poly 모드 로더가 제외한 NeoForge 모드도 스스로 찾아 싣습니다.
- **라이선스.** NeoForge(`neoforge` POM: LGPL 2.1), FancyModLoader(GitHub 저장소 `LICENSE.txt`: LGPL 2.1, POM에는 표시 없음), `binarypatcher`·`installertools`(POM: LGPL-2.1-only). Forge와 같게 받아서 쓰기만 하고 코드를 복사하지 않습니다. 패치 묶음 읽기는 위 형식 설명으로 새로 씁니다.
- **jdeps는 하지 않았습니다.** 의존 라이브러리는 설치 프로그램 `version.json`의 목록(위 "설치 프로그램")으로 대신합니다. 겹치는 라이브러리(ASM 9.10.1, night-config)는 N3에서 실제 실행으로 확인합니다.

## N3 PoC 결과 (2026-10-23)

ADR-0026의 방식으로 NeoForge 26.2.0.93을 실행했습니다. `-Dpoly.neoforge=on`일 때만 켜집니다(기본값은 꺼짐, 기존 진단 그대로).

- **통과: `neoForgeServerBoot`**(전용 서버, `neoforge_hello` + Poly 모드). Poly 모드 로더가 NeoForge 파일을 받아 패치된 jar를 만들고(`NeoForgeInstallation`), NeoForge의 로더 라이브러리 레이어를 게임 클래스로더에 정의하고, `FMLLoader`의 두 호출을 바꾼 뒤(`NeoForgeLoaderPatch`, `NeoForgeLoaderPatchTest`: 실제 FML 11.0.16 클래스, JDK 검증기 통과) FML 자신의 `FMLLoader.create`를 실행합니다. FML은 `neoforge_hello`를 찾고, FML의 Mixin 서비스가 올라오고, 게임 레이어(`minecraft`, `mixinextras.neoforge`, `neoforge`, `neoforge_hello`, FML 생성 모듈)를 Poly 모드 로더의 게임 클래스로더가 정의합니다(실제 NeoForge의 게임 레이어와 같은 모듈). 게임이 시작해 EULA 확인에서 멈춥니다. Poly 모드 로더는 EULA에 동의하지 않습니다.
- **게임 테스트 서버(`neoForgeGameTest`)**: 모드가 FML에 의해 생성되고(`[neoforge_hello] Constructed by FML in module neoforge_hello`), `FMLCommonSetupEvent`가 돌고, 아이템 `neoforge_hello:hello_item`이 등록되고, `ModList.get().isLoaded("neoforge")`가 `true`입니다. 월드가 만들어지고 게임 테스트 서버가 시작합니다. 그러나 Mojang의 `always_pass` 테스트가 시작되지 않고(진행 표시 `[ ]`가 틱마다 출력), 끝나지 않습니다. **NeoForge 자신의 설치(설치 프로그램 `--install-server`, `net.neoforged.fml.startup.GameTestServer`, 같은 인자)에서도 똑같이 멈춥니다**(`-Dneoforge.enableGameTest=true`를 줘도 같음). 원인은 아래 "N4~N6 결과"에 있습니다(2026-10-24: NeoForge가 운영 환경에서 게임 테스트 틱을 끔).
- **ADR-0026에 더한 것.**
  - FML은 `ModuleClassLoader`의 정적 초기화에서 `MethodHandles.Lookup.IMPL_LOOKUP`을 읽습니다. NeoForge의 런처 프로필은 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`를 주지만, 이 옵션은 Poly 모드 로더가 만든 레이어의 이름 있는 모듈에는 듣지 않습니다. 그래서 Poly 모드 로더의 Java agent가 `java.lang.invoke`를 FML 라이브러리 레이어와 게임 레이어의 모듈에 엽니다(`JdkModuleAccess`, ADR-0024의 agent). agent 없이 시작하면 NeoForge 모드를 빼고 알립니다(`POLY-COMPAT-216`). 개발용 Gradle 작업도 agent로 실행합니다.
  - NeoForge는 로더 라이브러리를 클래스 경로에서 실행합니다. 그래서 라이브러리 레이어의 모듈은 모두 자동 모듈로 만듭니다(Mixin의 `module-info`가 ModLauncher 서비스를 `uses`로 요구해 그대로는 레이어를 만들 수 없음). Mixin은 Poly 모드 로더 클래스 경로에 같은 버전이 있어도 FML 옆의 모듈로 둡니다. FML의 Mixin 서비스는 Mixin 자신의 클래스로더에서 이름으로 찾기 때문입니다.
- **남은 것(N4~).** NeoForge 모드의 요구 조건 사전 점검(Forge의 `209`에 해당), 다른 로더 모드의 목록 표시(ADR-0025), 내장 규칙 재검사(`poly:payload_codecs`가 NeoForge 패치로 꺼짐, 그 기능을 쓰는 Poly 모드 진단), 클라이언트(FML 초기 창), 26.1 계열, 설치한 프로필.

## N4~N6 결과 (2026-10-24)

### 게임 테스트가 시작되지 않던 원인 (N6 차단 요인)

- **청크 문제가 아니라 NeoForge가 운영 환경에서 게임 테스트 틱을 끕니다.** NeoForge 26.2.0.93의 패치 `net/minecraft/server/MinecraftServer.java.patch`(`neoforge-26.2.0.93-userdev.jar`)가 `tickChildren`의 `GameTestTicker.SINGLETON.tick()`을 `GameTestHooks.isGametestEnabled() && this.tickRateManager.runsNormally()`일 때만 부르게 바꿉니다. `GameTestInfo`를 진행시키는 것은 이 틱뿐이라, `GameTestServer.tickServer`는 진행 표시 `[ ]`를 계속 찍지만 테스트는 시작되지 않습니다.
- `GameTestHooks.isGametestEnabled()`(NeoForge 소스)는 `!FMLEnvironment.isProduction() && (SharedConstants.IS_RUNNING_IN_IDE || ServerModLoader.isGameTestServer() || Boolean.getBoolean("neoforge.enableGameTest"))`입니다. 운영 환경이면 처음 조건에서 거짓이 되므로 `-Dneoforge.enableGameTest=true`도, FML의 `startup.GameTestServer`도 소용이 없습니다.
- FML 11.0.16의 `FMLLoader.detectProduction`은 시스템 클래스 경로의 `net/minecraft/SharedConstants.class`에 `net/neoforged/fml` 바이트가 없으면 운영 환경으로 정합니다(개발 환경 ModDevGradle·NeoDev만 해당). 설치 프로그램으로 설치한 서버는 Minecraft가 `libraries/`에 있어 항상 운영 환경입니다(로그 "DEDICATED_SERVER in PROD"). 이것을 바꾸는 시스템 속성·프로그램 인자·설정은 FML·NeoForge 소스에 없습니다.
- 다른 패치는 관계없습니다. `GameTestServer.java.patch`는 테스트용 게임 규칙과 생명주기 훅만, `GameTestMainUtil.java.patch`는 `ServerModLoader.load(true)`만 더합니다. 게임 테스트용 청크 티켓을 바꾸는 패치는 없습니다.
- **확인(NeoForge 자신의 설치, Poly 모드 로더 없음).** 설치 프로그램 `--install-server`로 만든 서버를 `net.neoforged.fml.startup.GameTestServer`로 실행: 하네스 없이 120초 동안 `[ ]`만 찍고 멈춤. 아래 하네스 모드를 넣으면 약 7초 만에 "All 1 required tests passed"와 세 차원 저장.
- **결정(검증용).** 지원되는 스위치가 없으므로 NeoForge의 동작을 바꾸지 않고, 게임 테스트 서버에서만 쓰는 테스트 하네스 모드 `test-mods/neoforge-gametest-harness`를 둡니다(Fabric의 `fabric-gametest-harness`와 같은 위치). 게임 테스트 서버(`ServerModLoader.isGameTestServer()`)이고 NeoForge가 직접 돌리지 않을 때(`!GameTestHooks.isGametestEnabled()`)만 `ServerTickEvent.Post`에서 `GameTestTicker.SINGLETON.tick()`을 부릅니다. Poly 모드 로더 런타임과 브리지는 NeoForge의 이 동작을 바꾸지 않습니다. 브리지가 같은 틱을 넣는 방법은 NeoForge의 의미를 바꾸므로 ADR 없이 하지 않습니다.

### 통과한 검증

- **`neoForgeGameTest`(26.2, `-Dpoly.neoforge=on`, agent):** `neoforge_hello` + 하네스 + `neoforge-unmet` + Poly 모드 2개. FML이 모드를 생성하고 아이템을 등록하고, 월드 생성·`always_pass` 통과·저장(`universe/gametestworld/level.dat`). `neoforge_unmet`은 `217`로 빠지고 나머지는 실행. Poly 모드 `native_lib`의 `ModContext.mods()`가 `neoforge_hello: neoforge 1.0.0`을 봄(`GameHost.mods()`).
- **`corpusNeoForgeRun`(26.2, 실험, `compatibilityCorpus` 밖):** 묶음의 서버용 NeoForge 모드 20개(AppleSkin, Architectury, Balm, Cloth Config, Collective, CreativeCore, FerriteCore, Forge Config API Port, GeckoLib, Jade, JEI, Kotlin for Forge, KotlinLangForge, Lithium, mezz_config, No Chat Reports, Puzzles Lib, Simple Voice Chat, Veinminer, YACL) + 하네스로 월드 생성·테스트·저장. FML의 모드 목록 24개(중첩 JarJar 모드, `minecraft`, `neoforge` 포함)가 같은 jar로 NeoForge 자신의 설치에서 실행한 목록과 같습니다(하네스 이름만 다름).
- 단위 테스트: `NeoForgeModRequirementsTest`(7), `NeoForgeJarsTest`(3), `GameClassLoaderFallbackTest`(1).

### N4: 모드 요구 조건과 모드 목록

- `NeoForgeModRequirements`가 FML 11.0.16 소스(`LanguageProviderLoader.findLanguage`, `ModSorter.verifyDependencyVersions`, `ModInfo`)대로 `neoforge.mods.toml`을 검사합니다: `javafml` 버전(설치한 NeoForge처럼 FML `loader` jar의 `Implementation-Version`, 11.0.16은 `11.0`), 실행 쪽의 `required`(설치·범위), `optional`(설치됐으면 범위), `incompatible`(설치됐고 범위 안이면 거부). `type`이 없으면 `required`이고 Forge식 `mandatory`는 읽지 않습니다. `discouraged`는 경고뿐이라 빼지 않습니다. 중첩 JarJar 모드는 바깥 jar와 함께 설치된 것으로 칩니다. FML은 하나라도 실패하면 `minecraft`·`neoforge`만 싣고 실행을 멈추므로, 그 모드와 그것이 필요한 모드를 FML 시작 전에 뺍니다(`POLY-COMPAT-217`, 새 코드).
- 서버의 클라이언트 전용 모드는 빼지 않습니다. NeoForge는 `@Mod(dist = Dist.CLIENT)`를 서버에서 스스로 생성하지 않습니다(Forge의 `209`와 다름).
- `NeoForgeGameHost.modIds()`·`mods()`: FML이 실행하는 모드(각 jar의 `[[mods]]`, 그다음 중첩 JarJar 모드, 로더 이름 `NeoForge`). Poly 모드와 다른 로더 런타임 API가 봅니다(ADR-0025와 같은 방향). 반대 방향(FML의 `ModList`에 Poly 모드 보이기)은 아직 없습니다.
- `fabric.mod.json`과 `neoforge.mods.toml`을 함께 가진 jar(Collective, Iris, LambDynamicLights, Zoomify)는 NeoForge가 실행될 때 NeoForge가 실행합니다(`PolyRuntime.neoForgeJars`). NeoForge 자신도 자기 메타데이터만 읽습니다. 그런 jar는 `216`의 Fabric 모드 수에 세지 않습니다.

### N5: 실제 모드로 찾은 Poly 쪽 문제 (고침)

- **FML 플러그인 라이브러리.** FML은 `LIBRARY` 형식 jar(Kotlin for Forge의 Kotlin 표준 라이브러리, YACL의 `org.quiltmc.parsers` 등)를 "FML Plugins" `URLClassLoader`에 두고, 자기 `TransformingClassLoader`가 모르는 클래스를 그 체인(`setFallbackClassLoader(currentClassLoader)`)에 묻습니다. Poly 모드 로더의 게임 클래스로더는 이 체인을 몰라 `kotlin.jvm.internal.Intrinsics` 등에서 `ClassNotFoundException`. 게임 클래스로더에 마지막으로 묻는 대체 클래스로더(`GameModuleLayers.addFallback`)를 더하고, `PolyFml`이 FML의 체인을 넘깁니다. 그 체인의 부모가 게임 클래스로더이므로, 대체 로더가 되묻는 조회는 대체 로더 없이 답합니다(무한 반복 없음).
- **FML 처리기가 만드는 클래스.** Mixin이 만드는 합성 클래스(Lithium의 `Brain$Anonymous$...`)는 어느 jar에도 없습니다. FML은 빈 바이트로 처리기를 돌려 만듭니다. `PolyFml`의 브리지 변환 단계가 게임 레이어 패키지에서 같은 일을 합니다(`BridgeClassTransformer.generate`).
- **실제 NeoForge에서도 서버에서 안 되는 것.** Sodium(과 그것을 쓰는 클라이언트 모드)을 넣으면 NeoForge 자신의 서버도 FML 초기 서비스에서 멈춥니다(Sodium의 `GraphicsBootstrapper`가 LWJGL을 찾음, `NoClassDefFoundError: org/lwjgl/Version`). 그래서 `corpusNeoForgeRun`은 서버용 모드 20개만 씁니다. 묶음의 나머지는 클라이언트(N7)에서 봅니다.

### 남은 것 (2026-10-24에 아래 "N6~N13 결과"에서 처리)

- ~~전용 서버에 실제 모드, 클라이언트(N7), 26.1 계열(N10), 묶음 전체~~: "N6~N13 결과".
- ~~로그 "Loaded mods per loader"가 NeoForge 모드를 `Forge`로 셈~~: 고침. 로그는 `N Poly, N Fabric, N Quilt, N Forge, N NeoForge`, 타이틀 화면은 "NeoForge mods"/"NeoForge 모드" 줄(두 게임 계층, `LoadedModCounterTest`, `ModCountLinesTest`, `TitleScreenModCountsTest`).
- ~~FML의 `ModList`에 Poly 모드·다른 로더 모드 보이기~~: 고침(ADR-0026 19번, `218`).
- 확인할 것(남음): 실행 중 FML이 보는 `javafml` 버전. FML의 `JarVersionLookupHandler`는 모듈 버전을 먼저 읽습니다. Poly 모드 로더는 FML `loader` jar를 자동 모듈로 정의하므로 파일 이름의 `11.0.16`을 읽을 수 있고, 설치한 NeoForge(클래스 경로)는 `Implementation-Version` `11.0`을 읽습니다(코드를 읽은 추정, 실행으로 확인하지 않음). `loaderVersion`이 그 사이를 가르는 모드에서만 차이가 납니다. 사전 점검(`217`)은 설치한 NeoForge 값을 씁니다.

## N6~N13 결과 (2026-10-24)

모두 이 날 이 저장소에서 실행한 결과입니다. NeoForge 자신의 설치(설치 프로그램 `--install-server`, Poly 모드 로더 없음)와 비교한 것은 그렇게 적었습니다.

### N6: 묶음 전체, 게임 테스트 서버 (`corpusNeoForge`)

`corpusNeoForge`가 이제 실험 판정 대신 NeoForge로 묶음을 실행합니다(`corpusNeoForgeRun`을 대신함, `compatibilityCorpus`에 포함). 하네스 모드와 함께 월드 생성·`always_pass`·저장까지 통과했습니다.

| | 26.2 (NeoForge 26.2.0.93) | 26.1.2 (26.1.2.122) | 26.1 (26.1.0.19-beta) |
|---|---|---|---|
| 묶음의 NeoForge jar | 42 | 43 | 41 |
| 서버 실행에서 뺀 것(N11) | 6 | 5 | 5 + 실제 NeoForge도 실패하는 빌드 3 |
| FML에 넘긴 jar(하네스 포함) | 37 | 39 | 34, 그중 4개는 `217` |
| FML이 실행한 모드 / 게임 레이어 모듈 | 49 / 56 | 51 / 58 | 39 / 45 |
| 같은 실행에 `neoforge-environment-probe`를 더한 뒤(2026-10-24, 세 번째 창 작업 실행 뒤) | 50 / 57 | 52 / 59 | 40 / 46 |
| NeoForge 자신의 설치에서 같은 jar로 만든 게임 레이어 모듈 | 56 | 58 | 45 |

### N8: 전용 서버 (`corpusNeoForgeServer`)

같은 묶음(게임 테스트 하네스 대신, 서버에서 모드 생성 때 멈추는 Zoomify 포함)으로 NeoForge가 게임을 시작해 EULA 확인까지 갑니다. 세 버전 모두 통과. NeoForge 자신의 서버도 EULA를 모드 생성보다 먼저 확인합니다(NeoForge 26.2.0.93 설치로 확인). 그래서 모드 생성은 게임 테스트 서버(N6)에서 봅니다.

### N7: 클라이언트

2026-10-24 창 작업 첫 실행(사용자가 하나씩 실행): `neoForgeClient`(26.2) 통과, `launcherProfileNeoForgeTest`(26.2) 통과. `corpusNeoForgeClient`(26.2·26.1.2·26.1)와 `launcherProfileNeoForgeTest`(26.1.2)는 실패했습니다. 원인은 둘입니다.

- Sodium의 NeoForge jar는 초기 서비스(`GraphicsBootstrapper`)가 든 FML `LIBRARY`이고 모드는 안의 JarJar에 있습니다. FML은 그런 jar를 초기 서비스로 싣고(mods 폴더와 클래스 경로에서 찾음) 검색에서 "이미 찾음"으로 건너뛴 뒤 안의 모드를 JarJar로 엽니다. Poly 모드 로더의 `PolyModsLocator`가 같은 jar를 다시 넘겨, FML이 그것을 라이브러리 원본으로 보고 JarJar의 Sodium 모드를 버렸습니다("Attempted to select a dependency jar for JarJar which was passed in as source: sodium"). 그래서 Iris·Sodium Extra·Reese's가 Sodium이 없다고 멈췄습니다. 고침: FML이 이미 찾은 jar는 넘기지 않고, mods 폴더 밖의 NeoForge 모드는 FML의 초기 서비스 검색에 미청구 클래스 경로 항목으로 줍니다. 회귀 시나리오 `neoForgeEarlyService`(새 테스트 모드 `neoforge-early-service`, Sodium과 같은 구조)는 고치기 전에 실패하고 고친 뒤 통과합니다.
- 26.1.2 런처 프로필에서는 Lambda Dynamic Lights가 `217`로 빠졌습니다. JarJar 목록(`metadata.json`)이 `META-INF/jars/`의 jar를 가리키는데 점검은 `META-INF/jarjar/`만 보았습니다. FML처럼 목록을 따르게 고쳤습니다(`NeoForgeModRequirementsTest`).

두 실패한 작업은 고친 뒤 다시 실행해야 합니다.

두 번째 창 작업 실행(2026-10-24): `neoForgeClient`, `launcherProfileNeoForgeTest`(26.2) 다시 통과. Sodium은 이제 로드됩니다. 그러나 `corpusNeoForgeClient` 26.2·26.1.2와 `launcherProfileNeoForgeTest` 26.1.2는 시작 약 30초 뒤 클라이언트가 -1로 끝났고, 26.1은 모드 생성 중 멈췄습니다. 앞의 실행 기록은 다음 버전 실행이 같은 폴더를 지워 남지 않았습니다.

- 이제 클라이언트·런처 프로필 작업은 버전마다 따로 폴더를 씁니다(`build/poly/<작업>-<버전>/`). 실패하면 메시지가 크래시 보고서와 `hs_err_pid` 파일을 가리킵니다.
- 새 헤드리스 작업 `corpusNeoForgeClientMixins`(`compatibilityCorpus`에 포함): 창 없이 클라이언트로 FML을 시작하고, 게임 대신 묶음 모드들의 Mixin이 겨냥하는 클래스를 모두 FML의 클래스로더로 불러 실패를 보고합니다(`-Dpoly.neoforge.mixinAudit=true`). FML이 시작 화면에 보일 문제도 적습니다.
- 26.1의 원인 두 가지를 이것으로 찾았습니다. 둘 다 실제 NeoForge에서도 같은 이유로 실패하므로 26.1 실행에서 뺍니다(`brokenUnderNeoForge`).
  - Iris 1.11.4+mc26.1.2의 Sodium용 Mixin이 묶음의 Sodium 0.8.9에 없는 멤버를 찾습니다(모드끼리의 Mixin).
  - Zoomify 2.17.0+mc26.1.2는 `@Local(name = "partialTicks", argsOnly = true)`를 씁니다. Mixin은 동작 버전 0.17.0부터 인자 이름을 지역 변수 표에서 읽는데, FML 11.0.5(NeoForge 26.1.0.19-beta)는 `behaviorVersion`이 없는 설정에 0.16.5를 줍니다(FML 11.0.15는 0.17.1). 근거는 FML 11.0.5와 sponge-mixin 0.17.0 소스입니다.
- 26.2·26.1.2에서는 이 점검이 Mixin 실패 0개, FML 문제 0개입니다. 그러니 -1 종료는 창이 열린 뒤(모드 생성, 클라이언트 초기화)에 일어난 것으로 보며, 헤드리스로는 재현하지 못했습니다. 버전별 폴더가 생겼으므로 다음 실행에서 원인 기록이 남습니다.

세 번째 창 작업 실행(2026-10-24): `corpusNeoForgeClient` 세 버전과 `launcherProfileNeoForgeTest` 26.1.2가 모두 같은 원인으로 실패했습니다(버전별 폴더에 크래시 보고서가 남음). `IllegalStateException: This distribution of LambDynamicLights is broken: cannot find the default configuration file inside of the mod's JAR`이고, `Minecraft.<init>`의 Mixin에서 Yumi가 진입점을 실행할 때 일어납니다.

- 원인: Lambda Dynamic Lights는 기본 설정 `lambdynlights.toml`(jar 루트에 있음)을 Yumi의 `ModContainer.findPath`로 찾습니다. Yumi(`yumi_mc_core`)는 `Class.forName("net.fabricmc.loader.api.FabricLoader")`이 되면 Fabric 런타임을 첫 번째로 고릅니다(`YumiModsImpl`, `EnvironmentUtils.FABRIC`). Poly 모드 로더는 Fabric 브리지를 위해 Fabric Loader API를 클래스 경로에 두고 게임 클래스로더가 그것을 찾아 주었으므로, Yumi는 NeoForge 클라이언트를 Fabric으로 보고 `FabricModContainer`로 찾았습니다. 호스트(NeoForge)가 실행하는 모드는 Fabric 쪽 목록에 루트 경로가 없어(ADR-0025) 빈 결과가 나왔습니다. NeoForge의 `JarContents`는 Poly 모드 로더에서도 맞게 동작합니다(`neoforge_hello`가 자기 jar 내용을 읽음).
- 헤드리스 재현: 새 테스트 모드 `neoforge-environment-probe`가 게임 테스트 서버의 공통 설정에서 같은 Yumi 조회를 합니다. 고치기 전 `corpusNeoForge`(26.2): `Fabric Loader API visible: true`, `Yumi's FabricModContainer for lambdynlights finds lambdynlights.toml: false`.
- 고침(ADR-0026 27번): 모드를 실행하지 않는 브리지의 로더 API를 게임·모드 코드에서 숨깁니다(`GameClassLoader.hideFromParent`, `GameClassLoaderHiddenParentTest`). 고친 뒤: `Fabric Loader API visible: false, Quilt Loader API visible: false`, `Yumi's NeoModContainer for lambdynlights finds lambdynlights.toml: true`. `corpusNeoForge`가 두 줄을 기대하고, `neoForgeGameTest`는 `neoforge_hello`가 Fabric Loader를 보지 않는지 확인합니다.
- 클라이언트 쪽 Mixin 점검 `corpusNeoForgeClientMixins`는 세 버전에서 다시 실패 0개입니다. 창 작업은 다시 실행해야 합니다.

네 번째 창 작업 실행(2026-10-24): `neoForgeClient`, `launcherProfileNeoForgeTest`(26.2·26.1.2) 통과. Lambda Dynamic Lights 수정이 동작했습니다. `corpusNeoForgeClient`는 세 버전 모두 실패했습니다.

- 26.2·26.1.2: 클라이언트가 월드에 들어가 200틱, 저장, 종료 0까지 갔고 타이틀 화면의 모드 수 줄만 없었습니다. FancyMenu 3.9.14가 타이틀 화면의 `BrandingControl.forEachLine` 호출을 끄고 NeoForge 줄을 직접 그리기 때문입니다(실제 NeoForge에서도 같음). 그 호출을 바꾸던 규칙을 NeoForge의 브랜딩 목록(`getBrandings`)을 바꾸는 규칙으로 바꿨습니다(ADR-0026 28번, `NeoForgeTitleBrandingTest`, `POLY-COMPAT-219` 폐기). 이제 FancyMenu가 그리는 브랜딩에도 모드 수가 들어갑니다. `corpusNeoForgeClient`는 그 줄을 계속 요구합니다.
- 26.1: Lambda Dynamic Lights 4.11.2+26.1.2가 클라이언트 초기화에서 `NoClassDefFoundError: TagsUpdatedEvent$ClientPacketReceived`로 멈췄습니다. 그 클래스는 NeoForge 26.1.2.122에 있고 26.1.0.19-beta에는 없습니다(universal jar로 확인). 모드는 NeoForge 버전을 적지 않아 FML이 로드하므로 실제 NeoForge도 같게 멈춥니다. 26.1 클라이언트 실행에서만 뺍니다(`brokenOnNeoForgeClient`, 서버에서는 실행). 링크 검사로 미리 막는 방법을 생각했지만 막는 검사는 하지 않습니다(ADR-0026 29번).
- 헤드리스 재실행: `NeoForgeTitleBrandingTest`(세 릴리스의 universal jar와 패치된 클라이언트), `corpusNeoForgeClientMixins`·`corpusNeoForge`(세 버전).

다섯 번째 창 작업 실행(2026-10-24): 여섯 작업 모두 통과. `corpusNeoForgeClient` 26.2(NeoForge 모드 54개), 26.1.2(55개), 26.1(36개)는 저장된 월드에 들어가 200틱 뒤 저장하고 나왔고, 타이틀 화면에 모드 수 줄이 그려졌습니다. 세 로그 모두 `Transformed net/neoforged/neoforge/internal/BrandingControl`이 한 번 있습니다(FancyMenu와 함께). `neoForgeClient`(26.2), `launcherProfileNeoForgeTest` 26.2(`neoforge_hello`)와 26.1.2(묶음의 NeoForge 모드 54개)도 통과했습니다.

구현한 것:

- `neoForgeClient`(26.2): `neoforge_hello` + Poly 모드 2개 + `neoforge-client-session`(새 테스트 모드, NeoForge 이벤트만으로 200틱 후 저장·종료). 로더별 수, `ModList`가 Poly 모드에 답함, 타이틀 화면 줄을 확인합니다.
- `corpusNeoForgeClient`(모든 버전): 묶음의 NeoForge 모드 전부(서버에서 빠지는 클라이언트 전용 모드 포함).
- 타이틀 화면: NeoForge 패치는 `BrandingControl.forEachLine`으로 브랜딩을 그립니다. `NeoForgeTitleBranding`이 그 위에 로더별 모드 수를 그립니다. 규칙은 네 릴리스의 설치 프로그램이 만든 클라이언트 jar에서 적용되고(`NeoForgeTitleBrandingTest`), 바닐라 규칙 `poly:client_title_mod_counts`는 꺼집니다.
- 창이 열리므로 실행하지 않았습니다. FML의 초기 창(`earlydisplay`)과 Minecraft 창의 이어받기는 이 작업들로만 확인됩니다.

### N9: 설치한 서버와 런처 프로필

- `serverInstallTest`(헤드리스): 설치 jar로 만든 서버의 `mods/`에 NeoForge 모드를 넣고 `java -jar polymodloader-server.jar`로 게임 테스트 서버(GameTest 통과)와 전용 서버(EULA 확인까지). 26.2(`neoforge_hello` + 하네스), 26.1·26.1.1·26.1.2(하네스) 모두 통과. 실행 jar의 Java agent(`Launcher-Agent-Class`)가 `java.lang.invoke`를 엽니다.
- `launcherProfileNeoForgeTest`(창이 열림, 실행 전): 26.2는 `neoforge_hello`, 다른 버전은 그 버전 묶음의 NeoForge 모드.

### N10: 26.1 계열

- NeoForge는 26.1·26.1.1에 베타 빌드만 냈습니다. 마지막 빌드 `26.1.0.19-beta`, `26.1.1.15-beta`(FML 11.0.5, Mixin 0.17.0)와 `26.1.2.122`(FML 11.0.15)를 고정했습니다(`neoforge-releases.properties`, ADR-0026 18번).
- 패치 묶음: 세 릴리스 모두 클라이언트·서버의 결과가 설치 프로그램이 만든 jar와 바이트까지 같습니다(`NeoForgePatchBundleTest`, 여섯 경우). 26.2.0.93은 2026-10-23에 확인.
- `neoForgeWorldTest`(NeoForge + 하네스, 월드·GameTest·저장): 26.1, 26.1.1, 26.1.2, 26.2 통과.
- 묶음: 위 N6·N8 표. 26.1.1은 묶음을 따로 두지 않았습니다(Forge와 같음).
- 실행하며 고친 것: NeoForge 자신의 서버는 `version.json` 밖의 `install_profile.json` 라이브러리도 클래스 경로에 둡니다(26.1.x는 LWJGL 코어와 tinyfd, 26.2는 tinyfd; 설치 프로그램의 `data/win_args.txt`). Poly 모드 로더 서버에 없어서 ImmediatelyFast 26.1 빌드가 `org/lwjgl/system/MathUtil`에서 멈췄습니다(NeoForge 자신의 서버에서는 실행됨). 서버에서는 그 라이브러리도 받아 씁니다(`NeoForgeInstallation.serverClassPathLibraries`, `NeoForgeInstallationTest`).

### N11: 빠지는 모드 점검

모든 항목을 NeoForge 자신의 설치 서버(게임 테스트 서버, 같은 jar와 하네스)로 확인했습니다. Poly 모드 로더에서도 같은 곳에서 멈춥니다.

| 모드 | 버전 | NeoForge 자신의 서버에서 | 묶음에서 |
|---|---|---|---|
| Sodium | 세 버전 | FML 초기 서비스(`SodiumWorkarounds`)가 모드 로딩 전에 멈춤: 26.2는 LWJGL 버전 확인(`NoClassDefFoundError: org/lwjgl/Version`), 26.1·26.1.2는 Windows 그래픽 장치 조사(`Gdi32`, LWJGL) | 서버 실행에서 뺌, 클라이언트에서 실행 |
| Sodium Extra, Reese's Sodium Options, Iris | 세 버전 | Sodium 없이 넣으면 Mixin 플러그인이 Sodium 클래스를 찾다가 게임 시작 전에 멈춤(`.../sodium/client/services/PlatformRuntimeInformation`). Sodium을 필수로 선언하지 않아 `217`로 빠지지 않음 | 서버 실행에서 뺌 |
| ImmediatelyFast 1.16.5 | 26.2 | Mixin 플러그인이 설정을 읽으며 `org/lwjgl/system/MathUtil`에서 멈춤(26.2 서버 클래스 경로에 LWJGL 코어 없음) | 26.2 서버 실행에서 뺌. 26.1 빌드 1.15.3은 서버에서 실행 |
| Zoomify | 세 버전 | 모드 생성에서 클라이언트 클래스 `KeyMapping$Category`가 없어 멈춤 | 게임 테스트 서버에서 뺌, 전용 서버(EULA 확인까지)에는 넣음 |
| Forge Config API Port, JEI(안의 mezz_config 포함), Puzzles Lib | 26.1 | FML이 "Missing or unsupported mandatory dependencies"로 모드 로딩 전체를 멈춤(26.1.2.58 또는 26.1.2.99 이상 요구) | `217`로 그 모드만 빠짐 |
| Veinminer Client | 26.1 | Veinminer가 빠지면 필수 의존이 없어 FML이 멈춤 | `217` |
| Architectury 20.0.4, Veinminer 2.10.4 | 26.1 | `neoforge.mods.toml`은 26.1.0.19-beta를 허용하지만 그 릴리스에 없는 `BreakBlockEvent`를 써서 모드 생성에서 멈춤 | 묶음 실행에서 뺌(`brokenUnderNeoForge`) |
| Jade 26.1.12 | 26.1 | Mixin이 Minecraft 26.1에 없는 `ReloadableServerResources.registryAccess`를 찾다 멈춤 | 묶음 실행에서 뺌 |

Poly 모드 로더는 서버의 클라이언트 전용 NeoForge 모드를 스스로 빼지 않습니다(ADR-0026 21번). NeoForge 메타데이터에 그런 표시가 없고, NeoForge 자신도 위처럼 멈추기 때문입니다.

### N4·N5 마무리

- FML의 `ModList.getModFileById`·`isLoaded`가 Poly 모드에 답합니다(`neoForgeGameTest`: `Poly Mod native_lib: isLoaded = true; getModFileById = Native Library 2.1.0 (mod native_lib); getModContainerById present = false`). FML 11.0.16의 실제 `ModList`가 바뀌고 검증기를 통과합니다(`ForgeModListPatchTest`). 형태가 다르면 `POLY-COMPAT-218`.
- 로그와 타이틀 화면이 NeoForge 모드를 따로 셉니다.
- 내장 규칙 재검사: NeoForge 패치로 꺼지는 Poly 모드 로더 내장 규칙을 `BuiltInRulesTest`의 NeoForge 테스트(`-PneoForgePatchedClient`, `-PneoForgePatchedServer`)로 고정했습니다. 네 릴리스 모두 같습니다. 서버: `poly:server_brand`, `poly:payload_codecs`. 클라이언트: 그 둘과 `poly:client_brand`, `poly:client_title_mod_counts`, 크리에이티브 화면의 탭 쪽 넘김 네 개(`poly:client_creative_tabs_clicked`·`_released`·`_drawn`·`_hovered`). 브랜드·타이틀 화면·크리에이티브 탭 쪽은 NeoForge가 직접 합니다(Forge와 같음). `poly:payload_codecs`가 꺼지므로 **NeoForge가 실행될 때 Poly 모드의 네트워크 채널(Native API)은 Poly 모드 로더의 코덱 훅을 거치지 않습니다.** 이 기능을 NeoForge와 함께 게임 안에서 확인하지 않았습니다(남은 일).

### NeoForge 모드와 Fabric·Quilt 모드

2026-10-25 갱신: 이제 함께 실행합니다(ADR-0026 30~43 채택, 세 버전에서 클라이언트 창 실행까지 확인, `docs/COMPATIBILITY.md`). 아래는 처음 계획 당시의 기록입니다. 당시에는 함께 넣으면 NeoForge 모드를 빼고 알렸습니다(`216`). Forge처럼 하나의 Mixin이 두 로더를 맡게 하려면 FML 안을 지금보다 더 바꾸고, NeoForge 레지스트리를 Fabric 초기화 동안 열고, NeoForge 패치(바닐라 클래스 10,272개)에 맞춘 Fabric Mixin 조정을 새로 해야 합니다. 이유는 [ADR-0026](../adr/0026-neoforge-on-poly-mod-loader.md) 22번에 있습니다. Poly 모드와는 함께 실행합니다(`neoForgeGameTest`).

### N12: 성능

[`PERFORMANCE.md`](../PERFORMANCE.md) 측정 8(26.2, 묶음 36개 + 하네스). 시작 시간 7.58초 → 21.29초, GC 직후 힙 141 → 225 MiB, 틱 p50 9.67 → 4.80ms(묶음의 Lithium·FerriteCore 효과로 보며 우위로 주장하지 않음). NeoForge 자체 실행기와는 비교하지 않았습니다.

첫 틱 측정 시도에서 Poly 모드 로더 쪽 실행 하나가 모드 생성 중 멈췄습니다. 같은 멈춤이 뒤의 `corpusNeoForge`(26.1.2)에서 다시 나타나 `jstack`으로 원인을 찾았습니다. **교착(deadlock)이었고 고쳤습니다.** 두 모드 로딩 작업이 병렬로 Kotlin 모드(Kotlin for Forge의 `kotlin.jvm.internal.Reflection`, Veinminer의 Kotlin 클래스)를 생성할 때, 한쪽은 FML 플러그인 클래스로더의 잠금을 쥐고 부모인 게임 클래스로더에 같은 클래스를 묻고, 다른 쪽은 게임 클래스로더의 잠금을 쥐고 대체 로더(그 플러그인 클래스로더)에 같은 클래스를 물었습니다. 이제 게임 클래스로더는 자기 클래스만 자기 잠금 안에서 정의하고, 부모와 대체 로더에는 잠금을 놓고 묻습니다. 회귀 테스트 `GameClassLoaderFallbackTest.theFallbackIsAskedWithoutThisLoadersLock`(고치기 전 교착으로 실패).

### 기본값

검증을 마친 네 릴리스에서 NeoForge를 기본값으로 켰습니다. `-Dpoly.neoforge=off`이면 예전처럼 진단만 합니다. 개발용 Gradle 실행은 Poly 모드 로더 Java agent가 있어야 NeoForge가 실행됩니다(`runsNeoForge()`). `compatibilityMatrix`의 NeoForge 시나리오(`matrixNeoForgeOnly` 등)는 agent 없이 또는 Fabric 모드와 함께 실행하므로 예전 판정(`216` 뒤 `201`·`202`)을 그대로 검사합니다.

## Forge에서 그대로 쓸 수 있는 것과 다른 것

| 항목 | Forge에서 한 것 | NeoForge |
|---|---|---|
| 로더 자신의 코드를 실행 | ADR-0021: 패치된 게임 jar, 게임 클래스로더가 JPMS 모듈 정의, ModLauncher 서비스 계약을 새로 구현 | 방향은 같습니다. ModLauncher가 없으므로 FML 자신의 시작·클래스로더 계약을 대신할 지점을 N0에서 찾습니다 |
| 게임 클래스 소유권 | ModLauncher의 `TransformingClassLoader`를 그대로 쓰지 않음(설계서 §15.4) | FML의 `TransformingClassLoader`도 같은 문제입니다. 게임 클래스는 Poly 모드 로더의 게임 클래스로더가 소유해야 합니다 |
| 변환 | access transformer·`RuntimeDistCleaner`를 `ModLoaderBridge.classTransformer` 단계에서 부름 | `ClassProcessor`(AT, enum 확장, Mixin, dist 정리)를 같은 단계에서 부르는 방식을 검토합니다 |
| Mixin | 원본 Mixin 0.8.7을 따로 둠. Fabric과 함께면 공유 Mixin 문제(G8) | Fabric과 같은 `sponge-mixin` 포크입니다. 버전이 맞으면 Fabric 브리지의 Mixin 서비스를 다시 쓸 수 있는지 N0에서 봅니다 |
| 패치된 게임 jar | 설치 프로그램 데이터의 바이너리 패치(26.2 1394개)를 사용자 PC에서 적용(G2) | 패치 형식과 수, 설치 프로그램이 하는 일은 N0에서 확인합니다 |
| 혼합 판정·차단 연쇄 | ADR-0014·0025, `GameHost.mods()` | 그대로 씁니다 |
| 묶음 검증 | `corpusForge`, `corpusForgeClient`, `corpusForgeServer`, `launcherProfileForgeTest`, `startupBenchmarkForge`·`tickBenchmarkForge` | 같은 모양의 NeoForge 작업을 만듭니다(N 단계마다 먼저 만듦) |
| 외부 파일 | Forge 파일은 Forge Maven에서 받고 배포하지 않음 | NeoForge Maven에서 받고 배포하지 않습니다. 라이선스는 N0에서 확인합니다 |

## 단계

| # | 일 | 검증(먼저 만듦) | 상태 |
|---|---|---|---|
| N0 | 조사 | `FORGE_SUPPORT.md`의 G0·G3처럼 사실로 기록. 26.2·26.1용 NeoForge 버전, 설치 프로그램(`version.json`, `install_profile.json`, 패치 데이터), FML이 시작에서 하는 일(클래스로더, 게임 레이어, `ClassProcessor` 목록, 모드 검색), `jdeps`, 라이선스 | 완료 (2026-10-23, "N0 조사 결과"). jdeps 대신 `version.json`의 라이브러리 목록 |
| N1 | 결정 | 새 ADR(번호는 [`docs/adr/README.md`](../adr/README.md)의 다음 번호): NeoForge를 Poly 모드 로더 위에서 실행하는 방식. ADR-0021과 공유할 부분과 다른 부분 | 채택 (2026-10-24): [ADR-0026](../adr/0026-neoforge-on-poly-mod-loader.md) |
| N2 | 패치된 게임 jar | 사용자 PC에서 만든 jar가 NeoForge 설치 프로그램 결과와 클래스 바이트까지 같음(테스트) | 패치 적용 완료 (2026-10-23): `NeoForgePatchBundle`, `NeoForgePatchBundleTest`. 26.2.0.93·26.1.2.122의 클라이언트·서버 네 경우 모두 설치 프로그램이 만든 jar와 디렉터리가 아닌 모든 항목이 바이트까지 같음(`-PneoForgeInstaller`·`-PneoForgeVanillaJar`·`-PneoForgePatchedJar`·`-PneoForgeSide`). 한 번 적용에 약 5~9초(테스트 시간, 비교 포함). 받기·캐시는 N3에서 시작 경로와 함께. 2026-10-24: 26.1.0.19-beta·26.1.1.15-beta도 클라이언트·서버 같음 |
| N3 | NeoForge만 넣고 시작 | `neoForgeGameTest`(가칭): 게임 테스트 서버에서 NeoForge와 테스트 모드(`neoforge_hello`, 새로 만듦)·Poly 모드를 함께 로딩하고 월드 저장 | 통과 (2026-10-24): `neoForgeServerBoot`, `neoForgeGameTest`(테스트 하네스 모드로 게임 테스트 틱, "N4~N6 결과") |
| N4 | 모드 검색 | FML이 보는 NeoForge 모드는 Poly 모드 로더가 찾은 것뿐. 같은 폴더의 Fabric·Poly 모드 때문에 FML이 멈추지 않음 | 완료 (2026-10-24): 요구 조건 사전 점검(`217`), `GameHost.mods()`, 여러 로더용 jar는 NeoForge가 실행, FML의 `ModList`가 Poly 모드에 답함(`218`) |
| N5 | 런타임 기능 | access transformer, enum 확장, Mixin, dist 정리가 실제 모드에서 적용됨. 내장 규칙을 패치된 jar로도 검사(`BuiltInRulesTest`) | 완료 (2026-10-24, 서버): 실제 묶음에서 Mixin·AT·JarJar·Kotlin 언어 공급자가 동작(`corpusNeoForge`, 세 버전). FML 플러그인 라이브러리 대체 로더, 처리기가 만드는 클래스, 서버 클래스 경로 라이브러리를 고침. 꺼지는 내장 규칙 목록을 `BuiltInRulesTest`로 고정. 클라이언트 쪽은 N7 |
| N6 | 실제 모드 묶음: 게임 테스트 서버 | `corpusNeoForge`가 26.2 묶음으로 월드 생성·GameTest·저장 | 완료 (2026-10-24): `corpusNeoForge` 26.2·26.1.2·26.1 통과("N6~N13 결과") |
| N7 | 클라이언트 | `corpusNeoForgeClient`(가칭, 창이 열림) | 완료 (2026-10-24): `neoForgeClient`, `corpusNeoForgeClient` 세 버전 통과(NeoForge 모드 26.2 54개·26.1.2 55개·26.1 36개). 앞의 네 번 실패에서 고친 것: Sodium(초기 서비스 jar)·JarJar 목록, 교착, Lambda Dynamic Lights(Yumi가 Fabric Loader API를 봄), FancyMenu와 타이틀 화면의 모드 수. 실제 NeoForge도 실패하는 26.1 빌드 셋을 뺌(위 "N7") |
| N8 | 전용 서버 | `corpusNeoForgeServer`(가칭): NeoForge가 게임을 시작해 EULA 확인까지 | 완료 (2026-10-24): 세 버전 통과 |
| N9 | 설치한 프로필·서버 | `launcherProfileNeoForgeTest`(가칭), `serverInstallTest`의 NeoForge 판 | 완료 (2026-10-24): `serverInstallTest` 단계 6·7, 네 버전. 런처 프로필 26.2(`neoforge_hello`)와 26.1.2(묶음의 NeoForge 모드 54개) 통과 |
| N10 | 26.1 계열 | N6~N9를 `-PpolyMinecraft=26.1`(필요하면 26.1.2 묶음)로 | 헤드리스 완료 (2026-10-24): 26.1·26.1.1 베타와 26.1.2.122 고정, `neoForgeWorldTest`·`serverInstallTest` 세 버전, 묶음 26.1·26.1.2. 클라이언트(26.1·26.1.2)와 런처 프로필(26.1.2)도 통과(N7·N9) |
| N11 | 빠지는 모드 점검 | 빠지는 모드마다 실제 NeoForge에서도 빠지거나 멈추는지 확인해 `COMPATIBILITY.md`에 적음 | 완료 (2026-10-24): 서버에서 빠지는 모드 모두 NeoForge 자신의 서버에서도 멈춤("N11") |
| N12 | 성능 | `startupBenchmarkNeoForge`·`tickBenchmarkNeoForge`(가칭), `PERFORMANCE.md` | 완료 (2026-10-24, 26.2): `PERFORMANCE.md` 측정 8. 측정 중 찾은 클래스로더 교착을 고침(위 "N12") |
| N13 | 판정 | `COMPATIBILITY.md`의 "NeoForge 완전 지원 점검", 상태 `Verified`, 웹사이트·README | 완료 (2026-10-24): 상태 `Verified`(26.1·26.1.2·26.2), 기본값 켜짐, README 갱신. 알려진 제한: Fabric·Quilt 모드와 한 게임에서 실행하지 않음(`216`). 웹사이트는 릴리스 때 갱신(이 작업에서는 오류 코드 문서만) |

창이 열리는 작업(N7, N9)은 헤드리스 검증을 마친 뒤 하나씩 실행합니다(`AGENTS.md`).

## 범위 밖

- **NeoForge 모드와 Fabric 모드를 함께 실행.** (2026-10-25 완료: ADR-0026 30~43.) Mixin 포크가 같아서 Forge보다 쉬울 수 있지만, NeoForge 패치가 바꾼 메서드를 겨냥한 Fabric Mixin 같은 문제가 남습니다. Sinytra Connector가 겪는 실패 원인과 같습니다. 완전 지원 뒤에 따로 다룹니다.
- **NeoForge와 MinecraftForge를 한 게임에서 함께 실행.** 둘 다 같은 바닐라 클래스를 서로 다른 패치로 바꿉니다. 가능한지부터 따로 조사합니다. 지금처럼 함께 넣으면 한쪽을 막고 이유를 알리는 방식을 유지하는 것을 기본으로 봅니다.
- **NeoForge API를 Native API로 다시 구현.** 설계서의 원칙(AGENTS.md 원칙 6)과 ADR-0021의 "고르지 않은 것"과 같은 이유입니다.
- **그 NeoForge 릴리스에 없는 NeoForge 클래스를 참조하는 모드의 경고**(남은 일, ADR-0026 29번). 막지 않는 경고(`113`처럼)로는 맞는 판정입니다. 시작 로그에서 26.1 Lambda Dynamic Lights 같은 경우를 미리 알려 줄 수 있습니다. 막는 검사는 FML과 다르게 동작하므로 하지 않습니다.

## 위험

- **FML의 클래스로더 계약을 대신할 지점이 없을 수 있습니다.** ModLauncher는 공개 서비스 API가 있었지만, FML이 클래스로더를 직접 만들면 바꿀 수 있는 곳이 적을 수 있습니다. N0의 가장 중요한 질문입니다. 없으면 N1에서 다른 방식을 정합니다.
- **Mixin 버전 차이.** NeoForge와 Fabric이 같은 포크여도 버전이 다르면 하나의 Mixin 서비스로 합칠 수 없습니다(조사 당시 0.17.4 대 0.17.5).
- **라이브러리 충돌.** Forge 때처럼 NeoForge 라이브러리 버전이 Poly 모드 로더 라이브러리(ASM, TOML 읽기 등)와 겹칠 수 있습니다. 공유 라이브러리 정책(ADR-0005)을 다시 봅니다.
- **꺼지는 Poly 모드 기능.** 패치된 jar에서 맞지 않는 내장 규칙은 그 jar에서만 끄고 진단합니다(ADR-0021 5·6번과 같음).
- **오류 코드.** 새 진단에는 새 `POLY-COMPAT-` 번호를 씁니다. `200..205`의 의미는 바꾸지 않습니다.
- **검증 시간.** 묶음 작업이 로더·버전마다 늘어나 `compatibilityCorpus`가 길어집니다. 작업을 나눠 돌릴 수 있게 둡니다.
