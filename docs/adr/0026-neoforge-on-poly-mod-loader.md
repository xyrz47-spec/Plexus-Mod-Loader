# ADR-0026: NeoForge 모드를 NeoForge 자신의 코드로 실행 — 채택

- 상태: **채택** (2026-10-24). 같은 날 Fabric·Quilt 모드와 함께 실행(30~33번)과 MinecraftForge 모드와 함께 실행의 가능성 조사(34번)를 더함. 채택 조건이던 N2(패치된 jar)와 N3(NeoForge와 테스트 모드로 전용 서버·게임 테스트 서버) PoC가 통과했고, 지원하는 네 버전에서 실제 모드 묶음까지 확인했습니다("N6~N13에서 더한 것"). 2026-10-23 제안.
- 관련: ADR-0003(게임 클래스로더), ADR-0009(로더 브리지), ADR-0019(사용자 PC에서만 만드는 jar), ADR-0021(Forge를 Forge 자신의 코드로), ADR-0025(FML 자신의 클래스를 검사한 형태일 때만 바꿈)
- 계획과 조사: [`docs/plans/NEOFORGE_FULL_SUPPORT.md`](../plans/NEOFORGE_FULL_SUPPORT.md) "N0 조사 결과"

## 배경

- NeoForge 26.2.0.93은 바닐라 클래스 **10,272개**를 바꾸고 13개를 더합니다(26.1.2.122는 13,576개와 10개). 패치는 설치 프로그램의 `data/client.lzma` 하나에 있고, 형식은 Forge의 binpatch zip이 아니라 `NFPATCHBUNDLE001`입니다. 델타는 Forge와 같은 GDIFF입니다.
- FancyModLoader(FML) 11은 ModLauncher를 흡수했습니다. ADR-0021처럼 다시 구현할 별도의 실행기 계약이 없습니다. `FMLLoader.create(StartupArgs)`가 모드 검색, 정렬, 언어 공급자, 변환 목록(`ClassProcessorSet`), 게임 레이어, Mixin 시작을 한 메서드에서 합니다.
- 게임 레이어는 private 메서드 `buildTransformingLoader`가 `ModuleLayer.defineModules(cf, [boot], f -> TransformingClassLoader)`로 만듭니다. 그대로 두면 FML의 `TransformingClassLoader`가 Minecraft 클래스를 소유합니다. ADR-0003·0021과 같은 이유로 받아들일 수 없습니다. Poly 모드와 게임 계층이 같은 게임 클래스를 볼 수 없고, Poly 모드 로더의 변환 규칙을 정해진 순서로 적용할 수 없습니다.
- FML이 그 로더를 쓰는 곳: `currentClassLoader`·`gameLayer` 필드(`getCurrentClassLoader()`, `getGameLayer()`; `ModLoader`가 모드를 열고, FML의 Mixin 클래스 공급자가 클래스를 찾음), `ClassLoadingGuardian.setAllowedClassLoader(ClassLoader)`, `MixinFacade.finishInitialization(LoadingModList, TransformingClassLoader)`(스레드 문맥 클래스로더가 그 로더여야 함).
- `TransformingClassLoader`는 `ModuleClassLoader`를 이어받습니다. 자기 모듈의 패키지가 아니면 부모 레이어 또는 대체 클래스로더(`setFallbackClassLoader`)에 맡깁니다. 변환은 공개 클래스 `ClassTransformer(ClassProcessorSet, ClassProcessorAuditLog).transform(…)`가 합니다.
- FML의 Mixin(`MixinFacade`)은 `mixin.service` 시스템 속성을 `FMLMixinService`로 정하고 `MixinBootstrap.init()`을 부릅니다. Mixin 라이브러리는 Poly 모드 로더의 Fabric 쪽과 같은 `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`입니다. 한 Mixin에 서비스는 하나뿐입니다.
- `ModsFolderLocator`는 읽지 못한 jar를 경고로만 넘기지만, Poly 모드 로더가 제외한 NeoForge 모드도 스스로 찾습니다.

## 결정 (제안)

1. **패치된 게임 jar는 사용자 PC에서 만듭니다**(ADR-0021 1번과 같음). NeoForge 모드가 하나라도 로드되면 바닐라 jar에 `NFPATCHBUNDLE001`을 적용한 jar를 캐시에 두고 게임 jar로 씁니다. 클라이언트 jar에는 `CLIENT`, 서버 jar에는 `SERVER` 항목만 적용하고, 수정 전에 원본의 Adler32를 확인합니다. 패치 데이터와 NeoForge 라이브러리는 NeoForge Maven에서 받고 배포하지 않습니다. 같은 입력에서 NeoForge 설치 프로그램과 클래스 바이트까지 같은 결과가 나와야 합니다(테스트). 읽는 코드는 형식 설명으로 새로 쓰고, GDIFF 적용은 Forge 브리지의 것을 씁니다.
2. **게임 클래스는 계속 Poly 모드 로더의 게임 클래스로더가 소유합니다**(ADR-0003, ADR-0021 2번 유지). NeoForge가 있을 때 그 로더가 FML이 계산한 게임 레이어(패치된 Minecraft, NeoForge `universal.jar`, FML의 게임 라이브러리, NeoForge 모드)를 JPMS 모듈로 정의합니다.
3. **FML 자신의 시작은 그대로 실행하고, 게임 레이어를 만드는 한 곳만 바꿉니다.** Poly 모드 로더가 `StartupArgs`를 만들어 `FMLLoader.create`를 부릅니다. FML의 `FMLLoader` 클래스는 Poly 모드 로더가 정의하고, `create`의 `buildTransformingLoader` 호출만 Poly 모드 로더 호스트 호출로 바꿉니다. ADR-0025처럼 **검사한 바이트코드 형태일 때만** 바꾸고, 다르면 새 `POLY-COMPAT-` 번호로 시작을 멈춥니다.
   - 호스트는 FML이 넘긴 `JarContentsModule` 목록으로 2번의 레이어를 만들고, 그 레이어를 `gameLayer`로 둡니다.
   - FML이 기대하는 `TransformingClassLoader` 값은 **빈 구성의 `TransformingClassLoader`** 로 채웁니다. 자기 모듈이 없으므로 모든 클래스와 리소스를 대체 클래스로더인 Poly 모드 로더의 게임 클래스로더에 맡깁니다. 이 객체를 `currentClassLoader`와 스레드 문맥 클래스로더로 두어 FML의 나머지 코드(`MixinFacade` 포함)는 바뀌지 않습니다.
   - FML의 `ClassProcessorSet`(access transformer, enum 확장, `SimpleProcessorsGroup`, Mixin, `ServiceLoader`로 찾은 처리기)은 Poly 모드 로더의 `ModLoaderBridge.classTransformer` 단계에서 `ClassTransformer`로 부릅니다. 처리기가 다른 클래스의 중간 바이트를 묻는 연결(`ClassProcessorSet.link`)은 게임 클래스로더가 답합니다. Poly 모드 로더 내장 규칙과의 순서는 ADR-0020·0021과 같게 정합니다.
4. **FML이 보는 NeoForge 모드는 Poly 모드 로더가 찾은 것뿐입니다**(N4). Poly 모드 로더의 `IModFileCandidateLocator`가 그 jar를 넘기고, FML의 `ModsFolderLocator`는 쓰지 않습니다(3번과 같은 검사한 바꿈). 경고로 넘어가는 Fabric·Poly 모드 jar 때문에 클라이언트에 FML 경고 화면이 뜨지 않게 합니다.
5. **처음에는 NeoForge 모드와 Fabric 모드를 함께 넣으면 NeoForge 쪽을 막고 이유를 알립니다**(30번에서 바꿈). Mixin 서비스가 하나뿐이기 때문입니다. NeoForge만 있으면 FML의 `FMLMixinService`가 Mixin을 맡습니다. 함께 실행은 완전 지원 계획의 범위 밖입니다.
6. 패치된 jar 위의 Poly 모드 로더 내장 규칙은 ADR-0021 5·6번과 같이 다시 검사하고, 맞지 않는 규칙은 그 jar에서만 끄고 진단합니다.
7. NeoForge 클래스 이름은 `poly-bridge-forge`(NeoForge 쪽 패키지)에만 둡니다. 공통으로 쓸 수 있는 것(GDIFF, 게임 레이어 정의, `GameHost`)은 Forge 쪽과 나눠 씁니다.

## N3 PoC에서 더한 것 (2026-10-23)

8. **JDK 패키지 열기.** FML의 `ModuleClassLoader`는 정적 초기화에서 `MethodHandles.Lookup.IMPL_LOOKUP`을 읽습니다. NeoForge 런처 프로필의 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`는 Poly 모드 로더가 만든 레이어의 이름 있는 모듈에는 듣지 않으므로, Poly 모드 로더의 Java agent(ADR-0024)가 `Instrumentation.redefineModule`로 `java.lang.invoke`를 라이브러리 레이어와 게임 레이어의 모듈에 엽니다. 내부 SPI `JdkModuleAccess`가 `GameHost.Request`로 이것을 넘깁니다. agent가 없으면 NeoForge 모드를 빼고 알립니다(`POLY-COMPAT-216`).
9. **라이브러리는 자동 모듈.** NeoForge는 로더 라이브러리를 클래스 경로에서 실행하므로, 라이브러리 레이어에서는 `module-info`가 있는 jar도 자동 모듈로 정의합니다(제공하는 서비스는 `module-info`와 `META-INF/services`에서). Mixin은 Poly 모드 로더 클래스 경로에 같은 버전이 있어도 FML 옆의 모듈로 둡니다. FML의 Mixin 서비스를 Mixin이 자기 클래스로더에서 이름으로 찾기 때문입니다.
10. **실험 스위치.** 검증이 끝날 때까지 `-Dpoly.neoforge=on`일 때만 NeoForge가 실행됩니다. 기본값에서는 NeoForge 모드를 지금처럼 진단하고 뺍니다(`200..205`).
11. 확인: `NeoForgeLoaderPatchTest`, `NeoForgePatchBundleTest`, `neoForgeServerBoot`. 게임 테스트 서버의 `always_pass`는 NeoForge 자신의 설치에서도 시작되지 않아 아직 검증에 쓰지 않습니다(`NEOFORGE_FULL_SUPPORT.md` "N3 PoC 결과").

## N4~N6에서 더한 것 (2026-10-24)

12. **FML의 대체 클래스로더 체인.** FML의 `TransformingClassLoader`는 자기 모듈에 없는 클래스를 FML의 클래스로더 체인(초기 서비스, `LIBRARY` 형식 jar의 "FML Plugins")에 묻습니다. 게임 레이어를 정의하는 Poly 모드 로더의 게임 클래스로더도 같은 체인을 마지막에 묻습니다(`GameModuleLayers.addFallback`, 내부 SPI). 그 체인의 클래스는 FML에서처럼 변환하지 않습니다. 체인이 게임 클래스로더로 되묻는 조회는 대체 로더 없이 답해 반복하지 않습니다. 근거: Kotlin for Forge의 Kotlin 표준 라이브러리, YACL의 중첩 라이브러리.
13. **처리기가 만드는 클래스.** FML은 어느 jar에도 없는 게임 레이어 패키지의 클래스를 빈 바이트로 처리기에 넘겨 만들게 합니다(Mixin의 합성 클래스). 브리지 변환 단계가 `BridgeClassTransformer.generate`로 같은 일을 합니다.
14. **요구 조건 사전 점검.** FML 11이 `neoforge.mods.toml`에서 거부할 모드는 FML 시작 전에 빼고 알립니다(`POLY-COMPAT-217`, Forge의 `209`와 같은 이유). 클라이언트 전용 모드는 빼지 않습니다(NeoForge가 서버에서 스스로 생성하지 않음).
15. **여러 로더용 jar.** NeoForge가 실행될 때 `neoforge.mods.toml`이 있는 jar는 `fabric.mod.json`이 함께 있어도 NeoForge가 실행합니다(ADR-0021의 Forge와 같음).
16. **게임 테스트 검증.** NeoForge는 운영 환경에서 Mojang의 게임 테스트 틱을 끕니다(`GameTestHooks.isGametestEnabled`, NeoForge 자신의 설치에서도). Poly 모드 로더는 이 동작을 바꾸지 않고, 검증에서만 테스트 하네스 모드(`test-mods/neoforge-gametest-harness`)로 틱을 돌립니다. 근거와 확인: `NEOFORGE_FULL_SUPPORT.md` "N4~N6 결과".
17. 확인: `neoForgeGameTest`, `corpusNeoForgeRun`(서버용 실제 모드 20개, NeoForge 자신의 설치와 같은 모드 목록), `NeoForgeModRequirementsTest`, `NeoForgeJarsTest`, `GameClassLoaderFallbackTest`. 상태는 그대로 **제안**입니다(클라이언트, 26.1, 묶음 전체가 남음).

## N6~N13에서 더한 것 (2026-10-24)

18. **26.1 계열의 NeoForge.** NeoForge는 26.1과 26.1.1에 베타 빌드만 냈습니다(정식 빌드는 26.1.2부터). Poly 모드 로더는 각 버전의 마지막 빌드를 씁니다: 26.1 `26.1.0.19-beta`, 26.1.1 `26.1.1.15-beta`(둘 다 FML 11.0.5, `sponge-mixin` 0.17.0), 26.1.2 `26.1.2.122`(FML 11.0.15), 26.2 `26.2.0.93`(FML 11.0.16). NeoForge 설치 프로그램이 그 버전에 주는 것이 이것뿐이기 때문입니다. 세 버전의 패치 묶음은 설치 프로그램 결과와 클라이언트·서버 모두 바이트까지 같고(`NeoForgePatchBundleTest`), `FMLLoader`의 두 호출도 같은 형태라 바뀝니다(`neoForgeWorldTest`). 모드가 26.1.x의 더 뒤 NeoForge를 요구하면 `217`로 빠집니다.
19. **FML의 모드 목록이 다른 로더의 모드에 답함**(ADR-0025와 같은 방향). NeoForge의 `ModList`는 Forge와 달리 한 인스턴스의 메서드이고 `fileById`·`indexedMods` 인스턴스 필드를 읽습니다(FML 11.0.16 `javap`). `getModFileById`·`isLoaded`가 FML의 답이 없을 때 `neoforge-host.jar`의 `ForeignModList`에 묻도록, 검사한 형태일 때만 바꿉니다(`ForgeModListPatch`의 `Shape.NEOFORGE`). FML 쪽에서 바꾸는 세 번째 곳이며, 다른 로더의 모드가 있을 때만 바꿉니다. 형태가 다르면 그대로 두고 실행을 계속합니다(`POLY-COMPAT-218`). `getModContainerById`·`getMods`·`getModFiles`는 바꾸지 않습니다(ADR-0025와 같은 이유).
20. **타이틀 화면.** NeoForge의 패치는 바닐라 버전 글자 대신 `BrandingControl.forEachLine(boolean, boolean, BiConsumer<Integer, String>)`로 브랜딩을 그립니다. 선택 규칙 `poly:client_title_mod_counts_neoforge`(`NeoForgeTitleBranding`)가 NeoForge 줄 바로 위에 로더별 모드 수를 그립니다(Forge의 `ForgeTitleBranding`과 같음). 2026-10-24 바꿈(28번): 처음에는 그 호출을 바꿨고 닿지 못하면 `POLY-COMPAT-219`였으나, 이제 그 호출이 읽는 목록 `getBrandings`를 바꾸고 `219`는 폐기합니다. 로그와 타이틀 화면은 NeoForge 모드를 Forge 모드와 따로 셉니다.
21. **서버의 클라이언트 전용 모드는 빼지 않습니다.** NeoForge 메타데이터에는 모드가 클라이언트 전용이라는 표시가 없고(`@Mod(dist)`는 생성 여부만 정함), NeoForge 자신도 Sodium·ImmediatelyFast 1.16.5·Zoomify 같은 모드를 서버에 넣으면 멈춥니다(NeoForge 자신의 설치로 확인, `NEOFORGE_FULL_SUPPORT.md` "N11"). Poly 모드 로더가 추측해서 빼면 NeoForge와 다르게 동작하므로 NeoForge와 같이 둡니다. 검증 묶음에서는 이유와 함께 서버 실행에서만 뺍니다.
22. **Fabric·Quilt 모드와 함께 실행하지 않습니다(`216` 유지).** (2026-10-24 바꿈: 30~33번에서 세 이유를 모두 풀고 함께 실행합니다. 아래는 그때의 판단입니다.) Forge 때(ADR-0021 G8)처럼 하나의 Mixin이 두 로더를 맡게 하려면 다음이 필요하고, 이번 완전 지원 범위에서 하지 않습니다.
    - FML 11의 Mixin은 `FMLLoader.create` 안의 `MixinFacade`가 시작하고 `ClassProcessorSet`의 `FMLMixinClassProcessor`가 적용합니다. 끄는 설정이 없어서, Fabric의 Mixin이 NeoForge 모드도 맡게 하려면 FML 안을 지금의 검사한 세 곳보다 더 바꿔야 합니다(3번 원칙). 반대로 FML의 Mixin이 Fabric 모드를 맡으면 Fabric 브리지의 Mixin 서비스·class tweaker·MixinExtras 순서(ADR-0020)를 FML 쪽에서 다시 만들어야 합니다.
    - Fabric 모드는 `onInitialize`에서 레지스트리에 등록하는데, NeoForge는 레지스트리를 `RegisterEvent` 동안만 엽니다. Forge에서는 Fabric 초기화 동안 Forge 레지스트리를 여는 코드(`runForeignModInitialization`)가 있었고, NeoForge에는 아직 없습니다.
    - NeoForge 패치는 바닐라 클래스 10,272개를 바꿉니다(Forge 26.2는 1,394개). NeoForge가 바꾼 메서드를 겨냥한 Fabric Mixin을 맞추는 일(Forge의 `ForgeInjectionPoints`, `138`·`212`·`213`)을 NeoForge 패치에 대해 다시 해야 합니다. Sinytra Connector가 겪는 실패 원인과 같습니다.
    그래서 NeoForge 모드와 Fabric·Quilt 모드를 함께 넣으면 NeoForge 모드를 빼고 이유를 알립니다. Poly 모드와는 함께 실행합니다.
23. **기본값 켜짐.** 검사한 형태의 FML에서만 바꾸고, 다르면 시작을 멈추거나(`215`) 그 기능만 끄므로(`218`, 선택 규칙), 검증을 마친 네 릴리스에서 기본값으로 켭니다. `-Dpoly.neoforge=off`이면 예전처럼 진단만 합니다(`200..205`).
24. **꺼지는 내장 규칙**(6번). NeoForge 패치로 맞지 않는 Poly 모드 로더 내장 규칙은 네 릴리스 모두 같습니다(`BuiltInRulesTest`의 NeoForge 테스트): 서버 `poly:server_brand`·`poly:payload_codecs`, 클라이언트는 그 둘과 `poly:client_brand`, `poly:client_title_mod_counts`, 크리에이티브 탭 쪽 넘김 네 개. 브랜드·타이틀 화면·탭 쪽은 NeoForge가 직접 하고(타이틀 화면 수는 20번), `poly:payload_codecs`가 맡던 Poly 모드 네트워크 채널의 코덱은 NeoForge와 함께일 때 확인하지 않았습니다.
25. **초기 서비스 jar와 JarJar 목록**(4번 보충). FML은 초기 서비스가 든 jar(Sodium: `FMLModType: LIBRARY`, 모드는 안의 JarJar)를 mods 폴더와 미청구 클래스 경로 항목에서 찾아 먼저 싣고, 검색에서는 "이미 찾음"으로 건너뛴 뒤 안의 모드를 JarJar로 엽니다. `PolyModsLocator`는 FML이 이미 찾은 jar를 넘기지 않고, mods 폴더 밖의 NeoForge 모드는 `StartupArgs`의 미청구 클래스 경로 항목으로 줘 FML의 초기 서비스 검색이 보게 합니다(FML을 더 바꾸지 않음). `217` 점검의 중첩 jar는 FML처럼 `META-INF/jarjar/metadata.json`이 가리키는 jar입니다. 근거: 첫 `corpusNeoForgeClient` 실패, 회귀 시나리오 `neoForgeEarlyService`. 같은 날 12번(대체 클래스로더 체인)을 고침: 게임 클래스로더는 부모와 대체 로더에 자기 잠금을 놓고 묻습니다. 대체 로더는 게임 클래스로더를 부모로 두고 자기 잠금을 쥔 채 되묻기 때문에, 잠금을 쥐고 물으면 병렬 모드 생성에서 교착합니다(`GameClassLoaderFallbackTest`).
26. 확인(2026-10-24, 헤드리스): `neoForgeGameTest`, `neoForgeServerBoot`, `neoForgeWorldTest`(26.1·26.1.1·26.1.2·26.2), `corpusNeoForge`·`corpusNeoForgeServer`(26.1·26.1.2·26.2), `serverInstallTest`(NeoForge 모드로 게임 테스트 서버·전용 서버), `NeoForgePatchBundleTest`, `NeoForgeTitleBrandingTest`, `ForgeModListPatchTest`. 창이 열리는 `neoForgeClient`, `corpusNeoForgeClient`(26.1·26.1.2·26.2), `launcherProfileNeoForgeTest`(26.2·26.1.2)는 다섯 번째 실행(2026-10-24)에서 모두 통과했고, NeoForge 상태는 `Verified`입니다(`NEOFORGE_FULL_SUPPORT.md` "N13").
27. **모드가 없는 로더의 API는 게임·모드 코드에 보이지 않습니다**(2026-10-24, 세 번째 창 작업 실행). Poly 모드 로더의 클래스 경로에는 Fabric·Quilt 브리지를 위해 Fabric Loader와 Quilt Loader의 API가 있고, 게임 클래스로더가 부모에서 그것을 찾아 주었습니다. 실제 NeoForge에는 그 클래스가 없습니다. 여러 로더용 라이브러리는 그 클래스로 로더를 알아냅니다. Lambda Dynamic Lights 안의 Yumi(`yumi_mc_core`)는 `net.fabricmc.loader.api.FabricLoader`가 있으면 Fabric 런타임을 먼저 고르고, Fabric Loader 쪽 `ModContainer`로 모드 파일을 찾습니다. 호스트가 실행하는 모드는 Fabric 쪽 목록에 루트 경로가 없으므로(ADR-0025) 기본 설정 파일을 찾지 못했고, 클라이언트는 `Minecraft.<init>`에서 멈췄습니다(26.2·26.1.2·26.1 모두). 이제 게임 클래스로더는 모드를 하나도 실행하지 않는 브리지의 `runtimePackages()`를 부모에서 찾지 않습니다(`GameClassLoader.hideFromParent`). 모드를 실행하는 브리지도 가진 접두사는 그대로 둡니다(Quilt 모드는 Fabric Loader API를 씀). NeoForge·Forge만 실행하거나 Poly 모드만 있을 때 Fabric·Quilt API가 보이지 않습니다. Poly 모드 로더 자신의 코드는 부모 로더에서 실행되므로 영향이 없습니다. 근거: `GameClassLoaderHiddenParentTest`, `neoForgeGameTest`의 `Fabric Loader API visible: false`, 새 테스트 모드 `neoforge-environment-probe`가 게임 테스트 서버에서 Lambda Dynamic Lights와 같은 Yumi 조회를 합니다. 숨기기를 끄면 `Yumi's FabricModContainer for lambdynlights finds lambdynlights.toml: false`, 켜면 `NeoModContainer`와 `true`(`corpusNeoForge`).
28. **타이틀 화면의 모드 수는 NeoForge의 브랜딩 목록에 더합니다**(20번을 바꿈, 2026-10-24, 네 번째 창 작업 실행). FancyMenu 3.9.14는 타이틀 화면의 `BrandingControl.forEachLine` 호출을 `@WrapWithCondition`(항상 `false`)으로 끄고, NeoForge의 브랜딩 줄을 `forEachLine`으로 직접 읽어 자기 배치로 그립니다(`MixinNeoForgeTitleScreen`, `NeoForgeCompatibilityLayer.getTitleScreenBrandingLines`, javap로 확인). 실제 NeoForge에서도 NeoForge 줄은 FancyMenu가 그리고 타이틀 화면의 호출은 돌지 않습니다. 그 호출을 바꾸던 규칙은 FancyMenu가 있으면 아무것도 그리지 못했습니다(`corpusNeoForgeClient` 26.2·26.1.2, 클라이언트는 정상 종료). 이제 규칙은 `forEachLine`이 읽는 목록 `BrandingControl.getBrandings(boolean, boolean)`의 반환값을 `NeoForgeTitleBranding.withModCounts`로 바꿉니다(`InvokeHookBeforeReturn`). 그래서 타이틀 화면과 FancyMenu처럼 브랜딩을 직접 그리는 모드 모두에 NeoForge 줄 위로 모드 수가 나옵니다. 대상이 게임 jar가 아니라 NeoForge의 universal jar에 있으므로, `GameHost.transformTargetJars()`(새 기본 메서드, 비어 있음)로 호스트가 그 jar를 넘기고 런타임이 게임 jar 다음에 그 jar로 규칙을 계획합니다. 규칙은 선택 규칙이라, 다른 형태의 릴리스에서는 꺼지고 NeoForge 줄만 보입니다. Poly 모드 로더가 `BrandingControl`을 직접 부르지 않으므로 `POLY-COMPAT-219`는 폐기합니다(쓰인 릴리스 없음, 번호는 다시 쓰지 않음). 근거: `NeoForgeTitleBrandingTest`(26.1.0.19-beta·26.1.2.122·26.2.0.93의 universal jar와 패치된 클라이언트 jar), `corpusNeoForgeClientMixins`는 규칙이 꺼졌다는 줄이 없어야 통과합니다(universal jar를 넘기지 않으면 실패함을 확인). 타이틀 화면을 대신 그리는 모드가 있으면 모드 수 줄을 요구하지 않는 방법은 고르지 않았습니다. 그 모드도 NeoForge의 브랜딩은 보여 주므로, 모드 수도 같이 보이는 편이 실제 NeoForge에 가깝습니다.
29. **NeoForge API가 그 릴리스에 없는 모드는 링크 검사로 막지 않습니다.** Lambda Dynamic Lights 4.11.2+26.1.2는 Minecraft `[26.1,26.1.2]`만 요구하고 NeoForge 버전은 적지 않아 FML이 26.1에서 로드합니다. 클라이언트 초기화(`NeoForgePlatform$1.register`)가 NeoForge 26.1.2.122에만 있는 `TagsUpdatedEvent$ClientPacketReceived`를 써서 26.1.0.19-beta에서는 `NoClassDefFoundError`로 멈춥니다(universal jar 두 개와 모드 jar를 javap·목록으로 확인). 서버는 그 코드에 닿지 않아 실행됩니다(`corpusNeoForge`). 실제 NeoForge도 같은 jar를 같은 FML로 로드하므로 같은 곳에서 멈춥니다. 26.1 클라이언트 묶음에서만 뺍니다(`brokenOnNeoForgeClient`). Fabric 모드의 `111`처럼 상수 풀에서 NeoForge 클래스 참조를 universal jar와 맞춰 보는 검사를 생각했지만, 막는 검사로는 하지 않습니다. 실행 경로를 보지 않으므로, 쓰지 않는 경로의 참조나 버전을 확인한 뒤의 참조로도 모드를 막게 됩니다. 그러면 FML이 로드해 실행하는 모드를 Poly 모드 로더만 빼게 되어 21번과 어긋납니다. 막지 않는 경고(`113`처럼)라면 맞는 판정이지만 아직 만들지 않았습니다(`NEOFORGE_FULL_SUPPORT.md` 남은 일).

## Fabric·Quilt 모드와 함께 실행 (2026-10-24, 22번을 바꿈; 2026-10-25 채택)

30~43번은 채택되었습니다(2026-10-25). 근거: `docs/COMPATIBILITY.md` "NeoForge와 Fabric·Quilt 모드를 한 게임에서"의 세 버전 헤드리스 작업과 `corpusFabricNeoForgeClient` 창 실행(여덟 번째, 세 버전 통과). MinecraftForge 모드와 함께는 34번대로 채택하지 않았습니다(`221`).

30. **하나의 Mixin(Fabric의 것)이 NeoForge 모드도 맡습니다**(ADR-0021 G8과 같은 설계). Fabric 또는 Quilt 모드가 함께 있으면 `216`으로 빼지 않고 NeoForge를 실행합니다(22번의 첫째 이유를 해결).
    - FML 11에는 자기 Mixin을 끄는 설정이 없습니다. `FMLLoader.create`가 언제나 `MixinFacade`를 만들고, 생성자가 `mixin.service`를 `FMLMixinService`로 정하고 `MixinBootstrap.init()`을 부르며, `finishInitialization`이 모드의 설정을 더하고 Mixin을 기본 단계로 옮깁니다(`loader` 11.0.5·11.0.15·11.0.16 `javap`). 그래서 Fabric·Quilt 모드가 있을 때만 FML 안을 두 곳 더 바꿉니다(3번처럼 검사한 형태일 때만, `NeoForgeSharedMixinPatch`): `MixinFacade`의 생성자는 두 필드만 `null`로 두고(Mixin은 이미 실행 중), `finishInitialization`은 `PolyFml.finishSharedMixin`에 모드 목록을 넘기고, `close`는 아무것도 하지 않습니다. `FMLLoader.createClassProcessorSet`의 `getClassProcessor()` 한 호출은 FML의 Mixin 이름(`ClassProcessorIds.MIXIN`)을 가진, 아무 클래스도 맡지 않는 처리기로 바뀌어 다른 처리기의 순서가 그대로입니다. 형태가 다르면 NeoForge가 시작하기 전에 알고 NeoForge 모드를 빼며(`216`, 이유를 적음), 두 Mixin이 함께 시작하는 일은 없습니다.
    - FML의 Mixin 라이브러리(`sponge-mixin`)는 Poly 모드 로더 클래스 경로의 같은 판을 씁니다(26.2는 같은 0.17.4, 26.1 계열의 FML 11.0.5는 0.17.0을 받지만 0.17.4로 실행). FML이 쓰는 Mixin은 원래 FabricMC 포크이고 FML도 설정에 `fabric-modId`·`fabric-compat` 장식을 붙입니다.
    - 설정은 FML의 `addMixins`처럼 찾습니다(`FmlSharedMixin`): 모드 파일 순서, `[[mixins]]`마다 FML 자신의 행동 버전 검사(`validateMixinBehavior`, `calculateBehaviorVersion`), 같은 설정 이름의 중복·없는 설정 파일은 FML의 로딩 문제(`fml.modloadingissue.mixin.*`)로. `requiredMods`는 FML의 모드와 다른 로더의 모드를 함께 봅니다(ADR-0025와 같은 방향). 플러그인·게임 라이브러리 jar의 매니페스트 `MixinConfigs`도 더합니다. 행동 버전은 `SharedMixinConfig.compatibility`로 Fabric 쪽 Mixin이 `fabric-compat`로 붙입니다.
    - 설정은 FML이 시작한 뒤에야 알 수 있으므로(`GameHost.sharesMixin()`), 런타임은 호스트를 준비한 뒤 그 설정을 담은 새 `BridgeLaunchContext`를 `beforeGame`에 넘깁니다. Mixin 0.8.7은 늦게 더한 설정을 아직 아무 클래스에도 mixin을 적용하지 않았을 때만 고르므로(`MixinProcessor.checkSelect`), Fabric 쪽 묶음은 Mixin을 시작할 때 고르고, 호스트 묶음은 더한 직후 Mixin 자신의 `select`로 고릅니다. 이 선택은 Mixin의 재진입 잠금 밖에서 합니다. NeoForge용 Iris의 설정 플러그인은 선택 도중 `BlockGetter`를 로드하는데(플랫폼 서비스, 26.2는 정적 초기화, 26.1.2는 `shouldApplyMixin`), 잠금 안이면 Mixin이 그 클래스에 Fabric 모드의 mixin을 적용하지 못하고 멈춥니다(`ReEntrantTransformerError`). 그 클래스는 그때까지 고른 mixin을 받고, 그 뒤에 준비되는 호스트 모드의 mixin은 적용하지 못합니다. 이것을 `POLY-COMPAT-220`(새 코드)으로 알리고 건너뜁니다. NeoForge만 있을 때 FML의 서비스는 그런 클래스를 알아채지 못해 그 mixin을 조용히 적용하지 않습니다(NeoForge만 넣은 `corpusNeoForgeClientMixins`를 Mixin 자세한 로그로 실행: Lithium의 `world.raycast.BlockGetterMixin`이 적용되었다는 줄이 없음).
    - FML이 고른 MixinExtras 게임 라이브러리(NeoForge의 `mixinextras-neoforge`, 또는 모드 JarJar의 더 새 판)는 게임 레이어에서 뺍니다. MixinExtras가 만드는 클래스가 클래스 경로 쪽 패키지에 있어야 하기 때문이며, Forge의 `MixinExtrasStub`과 같은 이유입니다. 그 jar의 `mixinextras.init.mixins.json`도 더하지 않습니다(Fabric 쪽이 이미 MixinExtras를 시작함).
    - 순서 차이: Mixin은 Poly 모드 로더의 브리지 단계에서 FML의 처리기(access transformer, enum 확장, NeoForge coremod)보다 먼저 적용됩니다. FML만 있을 때는 access transformer 뒤, coremod 앞입니다. Forge와 Fabric을 함께 실행할 때와 같은 순서이며, 이 차이로 실패한 실제 모드는 아직 없습니다.
31. **Fabric 모드는 NeoForge의 레지스트리를 연 채로 초기화합니다**(22번의 둘째 이유를 해결, `NeoForgeRegistryOpening`). NeoForge는 `CommonModLoader`에서 `GameData.unfreezeData()`, `RegisterEvent`, `GameData.freezeData()`(빈 태그 묶기, 동결, 동결 스냅숏) 순서로 레지스트리를 엽니다(26.2.0.93·26.1.0.19-beta `javap`). Fabric 모드의 `onInitialize` 앞뒤로 같은 두 호출을 하고, Fabric 레지스트리 빌더가 새 레지스트리를 넣는 루트 레지스트리도 함께 엽니다. 레지스트리가 이미 열려 있으면 아무것도 하지 않습니다. Fabric 모드는 Fabric Loader에서처럼 Minecraft의 모드 초기화 지점에서 초기화됩니다(ADR-0020·0022). 그 지점은 서버에서는 NeoForge의 모드 로딩(`ServerModLoader.load`)보다 앞이고, 클라이언트에서는 뒤입니다(NeoForge는 클라이언트 `Main`의 `ClientModLoader.begin`에서 모드를 로드하고, Fabric의 지점은 `Minecraft.<init>` 안). 그래서 클라이언트에서는 NeoForge가 등록 뒤에 하는 일(크리에이티브 탭 정렬, 속성·스폰 위치 이벤트)이 Fabric 모드의 항목을 보지 못할 수 있고, 확인하지 않았습니다. 확인: `neoForgeFabricGameTest`의 `fabric_registry`(`onInitialize`에서 아이템을 등록하고 NeoForge의 등록·동결 뒤 첫 서버 틱에 레지스트리에 있음).
32. **NeoForge 패치와 Fabric mixin 맞추기**(22번의 셋째 이유, ADR-0021 7번의 장치를 NeoForge에 그대로 씀). `HostPatchAdjustments`는 호스트와 상관없이 바닐라 클래스와 패치된 클래스를 비교하므로 `138`·`212`·`213`·`214`가 NeoForge에서도 나옵니다. 실제 묶음에서 찾아 고친 것:
    - **패치된 게임을 일찍 읽기**(`GameHost.classBytesJars`, 새 기본 메서드). NeoForge는 패치된 게임과 universal jar를 FML이 시작할 때에야 게임 레이어로 정의하므로, 그 전에 시작하는 Fabric의 Mixin과 그 계획은 Poly 모드 로더 클래스 경로의 바닐라 클래스를 읽었습니다. 계획은 아무 차이도 찾지 못했고, Mixin의 `ClassInfo`는 바닐라 상위 클래스를 기억해 `MappedRegistry`의 생성자 주입이 "Delegate constructor lookup failed"로 멈췄습니다. 이제 게임 클래스로더는 호스트가 그 클래스를 정의하기 전까지 이 jar에서 클래스 바이트를 읽습니다(리소스로 보이지도, 거기서 정의하지도 않음).
    - **필드 초기화 위치**: NeoForge의 `Pack`은 생성자 안 반복문에서 자식 팩을 만듭니다(`new Pack(...)`). Mixin의 기본 방식은 mixin 필드 초기화를 "대상 클래스 생성자 호출 중 마지막 것" 뒤에 두므로 Fabric API의 `parentsPredicate` 초기화가 그 반복문 안에 들어가 실행되지 않았고, 데이터 팩을 고를 때 `NullPointerException`으로 멈췄습니다. 호스트가 있는 실행에서는 `mixin.initialiserInjectionMode=safe`(위임 생성자 호출 바로 뒤)로 둡니다. 그런 호출이 하나뿐인 생성자에서는 두 방식이 같은 자리입니다(sponge-mixin 0.17.4 `Constructor.findInitialiserInjectionPoint`).
    - **넓힌 필드와 NeoForge coremod**: NeoForge의 `ReplaceFieldWithGetterAccess`는 `Biome.climateSettings` 같은 private 필드 읽기를 getter로 바꾸고, private가 아닌 필드는 거부합니다. Fabric API의 class tweaker가 그 필드를 넓혀서 `Biome`을 정의하지 못했습니다. FML 처리기가 이 이유로 거부하면, 넓힌 인스턴스 필드를 private로 되돌려 다시 처리하고 그 필드에 넓힌 접근과 FML 처리기 결과 중 넓은 쪽을 줍니다(`WidenedFieldGetters`, Forge의 `FieldToMethodTransformer` 처리와 같음).
    - **함께 동작하는 주입**(`CoupledInjections`): 한 mixin의 주입이 패치 때문에 실행되지 않으면, 그 주입이 설정하는 mixin 상태(필드, `ScopedValue`·`ThreadLocal`)를 읽는 같은 mixin의 다른 주입도 실행하지 않고 `138`로 알립니다. Fabric API의 `RegistryDataLoaderMixin`은 한 주입에서 `ScopedValue`를 묶고 다른 주입에서 읽는데, NeoForge가 앞의 대상 메서드를 바꿔 뒤의 주입이 월드를 불러올 때마다 `ScopedValue not bound`로 멈췄습니다. 상수(클래스 초기화에서만 쓰는 필드)는 묶지 않습니다.
33. **호스트가 직접 계산하는 인자**(`HostArguments`). NeoForge 26.1.0.19-beta의 `WorldLoader.lambda$load$0`은 `RegistryDataLoader.load`에 바닐라의 `RegistryDataLoader.WORLDGEN_REGISTRIES` 대신 자기 `DataPackRegistriesHooks.getDataPackRegistries()`를 넘깁니다(`javap`). Fabric API의 `@ModifyArg modifyLoadedEntries`는 이 인자를 바닐라 목록으로 만든 Fabric 목록으로 바꿔, NeoForge의 데이터 팩 레지스트리(`neoforge:biome_modifier`)가 사라지고 서버가 시작에서 멈췄습니다(26.2에서는 호출 형태가 바뀌어 이미 `138`). 이제 `@ModifyArg`·`@ModifyArgs`가 바꾸는 인자를, 바닐라에서는 게임의 원천(정적 필드 읽기나 인자 없는 정적 호출)이 만들고 패치된 메서드에서는 호스트 자신의(바닐라에 없는 클래스의) 원천이 만들면, 그 주입은 패치를 되돌리는 것이므로 실행하지 않고 `138`로 알립니다. 호스트가 바닐라 값을 받아 바꿔 돌려주는 호출(Forge의 `ForgeEventFactory.onResourceReload(listeners)`)은 원천이 아니므로 그대로 둡니다. 처음 구현은 이것도 막아 Fabric API의 리소스 로더가 Forge 옆에서 멈췄습니다(`forgeFabricApiGameTest`, `HostArgumentsTest`). 인자를 만든 명령은 호출 앞의 직선 코드를 거꾸로 따라가 찾고(적재, 상수, 필드 읽기, 형 변환, 호출), 분기가 들어오는 자리나 모르는 명령을 만나면 판단하지 않습니다.
    - 확인(2026-10-24, 헤드리스, 아래 "확인"): `neoForgeFabricGameTest`(26.2), `corpusFabricNeoForge`, `corpusFabricOnNeoForge`, `corpusFabricNeoForgeServer`, `corpusQuiltNeoForge`, `corpusFabricNeoForgeClientMixins`(26.2·26.1.2·26.1), `NeoForgeSharedMixinPatchTest`, `CoupledInjectionsTest`. Forge와 Fabric 쪽 회귀: `forgeFabricGameTest`, `forgeFabricApiGameTest`, `corpusFabricForge`, `corpusQuiltForge`. 창이 열리는 `corpusFabricNeoForgeClient`는 아직 실행하지 않았습니다.
    - `216`은 그대로 씁니다. 뜻(이번 실행에서 NeoForge를 실행할 수 없어 NeoForge 모드를 뺌)은 같고, Fabric·Quilt 모드와 함께라는 이유만 "FML의 Mixin 시작이 검사한 형태가 아님"으로 바뀌었습니다.

35. **FML이 거부할 여러 로더용 jar는 다른 로더가 실행합니다**(`GameHost.declinedJars`, 2026-10-24). NeoForge가 실행될 때 `neoforge.mods.toml`이 있는 jar는 NeoForge가 맡습니다(15번). 그 jar가 FML 요구 조건을 못 맞추면(`217`) 지금까지는 그대로 빠졌습니다. Fabric·Quilt 모드와 함께라 Fabric의 Mixin이 이미 공유될 때는, 그 jar를 Fabric 쪽 메타데이터로 Fabric 브리지가 실행합니다. NeoForge 없이 Fabric Loader가 하는 것과 같습니다. 예: Zoomify 2.17.0의 하나의 jar는 NeoForge 쪽에서 NeoForge용 YACL과 언어 공급자 `klf`를 요구하는데, Fabric 모드만 넣은 NeoForge에서는 둘 다 없어 Sinytra Connector는 NeoForge가 시작에서 멈춥니다(측정 9). Poly 모드 로더에서는 `217`로 NeoForge 쪽을 빼고 Fabric 쪽을 Fabric YACL과 함께 실행할 수 있습니다(클라이언트 모드라 서버 묶음에서는 확인하지 않음). Mixin이 공유되지 않으면(Fabric 모드 없음) 두 번째 Mixin이 생기므로 하지 않습니다. 확인: `neoForgeFabricGameTest`의 테스트 jar `multi-loader-fallback`(NeoForge 쪽이 `neoforge [99,)`를 요구).

36. **호스트 모드의 파일은 Fabric 쪽에서도 찾을 수 있습니다**(27번 보충, ADR-0025 1번을 바꾸지 않음). Fabric·Quilt 모드가 함께면 Fabric Loader API가 보이고(27번은 그 API가 없을 때만 숨김), Lambda Dynamic Lights 안의 Yumi처럼 그 API가 있으면 Fabric 쪽을 고르는 라이브러리가 NeoForge가 실행하는 모드의 파일을 Fabric 쪽 `ModContainer.findPath`로 찾습니다. ADR-0025의 호스트 모드 컨테이너는 루트 경로가 비어 있어(리소스가 두 번 실리지 않게) 찾지 못했습니다(`neoforge-environment-probe`: `lambdynlights.toml: false`). 이제 호스트 모드의 Fabric 컨테이너는 `getRootPaths()`는 그대로 비우고 `findPath`만 그 jar에서 찾습니다(중첩 JarJar 모드는 제외). Fabric API의 리소스 로더는 루트 경로를 쓰므로 바뀌지 않습니다. 확인: `corpusFabricNeoForge`(26.2·26.1.2·26.1)에 `for lambdynlights finds lambdynlights.toml: true`, `forgeFabricGameTest`의 `0 root path(s)`는 그대로.

37. **로더마다 ID를 다르게 쓴 같은 모드는 한 빌드만 실행합니다**(`HostBuildMatcher`, 2026-10-24, `139`의 뜻은 그대로). 호스트가 실행하는 모드와 ID가 같은 Fabric·Quilt 모드는 원래부터 `139`로 뺐습니다. 하지만 Cloth Config는 Fabric에서 `cloth-config`, NeoForge에서 `cloth_config`입니다(FML 모드 ID에는 `-`가 들어갈 수 없음). 그래서 두 빌드가 다 실행됐습니다. 두 빌드는 패키지를 같이 쓰므로 게임에서는 NeoForge 빌드의 클래스만 보입니다. 그래서 Fabric 빌드의 `client` 엔트리포인트 `ExampleInits::exampleCommonInit`이 NeoForge 빌드의 코드를 한 번 더 실행했습니다. NeoForge 빌드의 모드 생성자(`ClothConfigForge.<init>`, `javap`)가 같은 예제 설정을 이미 등록했으므로, 클라이언트가 `Config ... already registered`(`110`)로 멈췄습니다(`corpusFabricNeoForgeClient`, 26.2·26.1.2·26.1, 창 실행). 개발 환경 판정이 원인은 아닙니다. Fabric 쪽 `isDevelopmentEnvironment()`는 `fabric.development` 속성이고 설정되지 않았습니다. FML도 로그에 `Assuming we're launching production`을 적습니다. 예제 등록은 두 빌드 모두 배포판에서 합니다.
    - 이제 다음 중 하나면 같은 모드로 봅니다. 위에서부터 확인합니다.
      1. 같은 ID.
      2. FML 표기로 바꾼 같은 ID(소문자, `-`와 `.`을 `_`로).
      3. 모드가 제공하는 ID(`provides`) 중 하나. 두 표기 모두 봅니다.
      4. 모드 클래스의 절반 이상이 호스트 빌드 하나의 jar에 있음. 중첩 JarJar 모드는 그 안의 jar를 읽습니다.
    - 26.2 묶음에서 NeoForge jar와 클래스를 공유하는 Fabric jar는 모두 그 모드의 다른 빌드였습니다. 그중 ID가 다른 셋(Cloth Config, Reese's Sodium Options, Sodium Extra)은 클래스의 97~100%를 공유합니다. 공유가 적은 쌍(Sodium 9%, Forge Config API Port 23%, AppleSkin 27%)은 모두 ID가 같습니다. 그래서 4번 기준으로 잘못 잡은 쌍은 없었습니다.
    - 호스트 빌드가 실행되고 다른 빌드는 `139`를 받습니다. 플레이어가 한 빌드만 설치하는 것과 같습니다.
    - 두 빌드가 대부분 같은 코드이면(빠진 빌드 클래스의 절반 이상이 호스트 빌드 jar에 있음, `Match.sameCode`), 그 빌드가 필요한 모드는 이제 호스트 빌드가 대신 충족합니다. 빠진 빌드의 ID와 제공 ID를 호스트 빌드의 버전으로 의존성 판정에 넘깁니다. 그 ID들은 Fabric Loader의 모드 목록에서 호스트 모드의 제공 ID로 보입니다. 링크 검사는 호스트 빌드 jar를 봅니다. 그 클래스는 원래부터 호스트 빌드의 것이었으므로, 의존하는 모드가 실제로 쓰는 코드는 바뀌지 않습니다. 예: Quilt 묶음의 Better Shulker Boxes(`cloth-config` 필요)는 NeoForge의 `cloth_config`로 계속 실행됩니다. Forge 옆에서는 FusionFix(Quilt, Fusion 필요)가 이제 Forge의 Fusion으로 실행됩니다(`corpusQuiltForge`, 전에는 의존 검사로 빠짐). 코드가 적게 겹치면 전처럼 의존 검사로 뺍니다. 처음 구현은 이 조건이 없었습니다. 그래서 Fabric용 Puzzles Lib가 Forge의 Forge Config API Port로 로드됐는데, 두 빌드는 클래스 96개 중 22개만 같고 Puzzles Lib가 부르는 Fabric API가 없습니다(링크 검사의 `113` 26개). `corpusFabricForge`에서 이를 보고 조건을 더했습니다.
    - Sinytra Connector는 이 ID 대응을 손으로 적은 별칭 목록으로 합니다. 여기서는 표기 규칙과 클래스 비교로 찾고, 찾은 근거를 `139` 줄에 적습니다(`... as cloth_config (the same ID as NeoForge spells it)`, `... (7 of its 7 classes are in that jar)`).
    - 같은 이름 규칙이 Forge 호스트에도 적용됩니다.
    - 확인:
      - 테스트 jar `fabric-twin`(`twin-mod`), `neoforge-twin`(`twin_mod`), `fabric-twin-user`. 두 빌드가 같은 설정 클래스를 공유하고 두 번 등록하면 멈춥니다. 고치기 전 코드로 `neoForgeFabricGameTest`를 돌리면 `Config 'example' already registered`로 실패했고, 고친 뒤 통과했습니다.
      - `HostBuildMatcherTest`(같은 ID지만 코드가 적게 겹치는 경우 포함), `CrossLoaderModResolverTest.theHostsBuildOfALeftOutModMeetsTheDependenciesOnIt`.
      - `corpusFabricForge`·`corpusQuiltForge`(26.2).
      - `corpusFabricNeoForge`·`corpusFabricNeoForgeServer`·`corpusFabricNeoForgeClientMixins`(26.2·26.1.2·26.1), `corpusQuiltNeoForge`(26.2).

38. **클라이언트에서 내장 레지스트리를 두 번 얼립니다**(`poly:builtin_registries_refreeze`, 31번의 클라이언트 쪽, 2026-10-24).
    - 현상: 37번을 고친 뒤 `corpusFabricNeoForgeClient`(창 실행, 세 버전)가 모드 초기화 직후 `Registry is already frozen`으로 멈췄습니다. 스택은 `Minecraft.<init>` → Fabric API(registry-sync-v0)의 `MinecraftMixin.afterModInit` → `BuiltInRegistries.bootStrap()` → `freeze()` → `bindBootstrappedTagsToEmpty` → `MappedRegistry.bindAllTagsToEmpty` → `validateWrite`입니다.
    - Fabric API가 기대하는 순서(`javap`):
      - `BootstrapMixin.delayRegistryFreeze`가 `Bootstrap.bootStrap`의 `BuiltInRegistries.bootStrap()` 호출을 `createContents()`로 바꿉니다. 그래서 내장 레지스트리는 얼지 않은 채로 남습니다.
      - `BuiltInRegistriesMixin`은 `createContents`를 한 번만 돌게 합니다.
      - 모드 초기화 뒤 `afterModInit`이 `BuiltInRegistries.bootStrap()`으로 얼립니다(클라이언트는 `Minecraft.<init>`의 `Thread.currentThread()` 앞, 서버는 `server.Main`의 `Util.startTimerHackThread()` 앞). 이어서 `BlockInitTracker.postFreeze()`와 `CreativeModeTabs.validate()`를 부릅니다.
    - NeoForge가 하는 일(26.2.0.93 `javap`):
      - 클라이언트 `Main`은 `new Minecraft` 앞에서 `ClientModLoader.begin`으로 모드를 로드합니다. 그 등록은 `GameData.unfreezeData()`, `RegisterEvent`, `GameData.freezeData()` 순서입니다.
      - `freezeData`는 루트 레지스트리의 `MappedRegistry`마다 `bindAllTagsToEmpty()`와 `freeze()`를 부르고, 얼린 스냅숏을 뜹니다.
      - 31번의 `NeoForgeRegistryOpening`은 Fabric 모드 초기화 앞뒤로 같은 두 호출을 합니다.
    - 그래서 `afterModInit`의 `freeze()`가 레지스트리마다 하는 일(태그를 빈 것으로 묶기, 얼리기)은 이미 끝나 있습니다. `freeze()`는 언 레지스트리에서 바로 돌아오지만, 태그 묶기는 실패합니다.
    - 고친 방법: 버전 어댑터 규칙이 `freeze()`의 유일한 `bindBootstrappedTagsToEmpty(Registry)` 호출을 게임 계층 훅으로 바꿉니다(26.1·26.2 어댑터, `DISABLE_RULE`, `poly:content_registration` 뒤).
      - 얼지 않은 레지스트리에는 원래 메서드처럼 `((MappedRegistry) registry).bindAllTagsToEmpty()`를 부릅니다.
      - 이미 언 레지스트리는 건너뜁니다. 같은 일이 이미 끝났다는 근거: `MappedRegistry.freeze()`는 묶이지 않은 태그가 있으면 실패하므로, 언 레지스트리의 태그는 묶여 있습니다.
      - 루트 레지스트리의 `freeze()`, `validate`, Fabric의 `postFreeze`·`CreativeModeTabs.validate`는 Fabric Loader에서처럼 그대로 실행됩니다. NeoForge의 얼린 스냅숏은 Fabric 모드의 등록 뒤에 떠집니다(31번).
      - 레지스트리를 한 번만 얼리는 실행(바닐라, Fabric만, NeoForge만)에서는 바뀌는 것이 없습니다.
    - 고르지 않은 것:
      - Fabric의 `afterModInit`이 얼릴 때까지 레지스트리를 열어 두기: NeoForge의 얼린 스냅숏이 Fabric의 얼리기 뒤에 떠져야 하는데, 그 자리가 없습니다.
      - `afterModInit`을 통째로 빼기: Fabric의 `postFreeze`와 탭 검사도 빠집니다.
    - 재현(창 없음): `corpusFabricNeoForgeClientBootstrap`.
      - FML을 초기 창 없이 시작하는 진단 속성 `poly.neoforge.headless`를 씁니다(`mixinAudit`처럼).
      - 테스트 하네스 `fabric-client-bootstrap-probe`는 `Minecraft.<init>`이 게임 창(`new Window`)을 만들기 바로 앞에서 클라이언트를 끝냅니다. NeoForge가 치명적 오류를 알리면 FML의 오류 대화 상자 대신 종료합니다.
      - 고치기 전에는 창 실행과 같은 스택으로 실패했고, 고친 뒤 26.2·26.1.2·26.1에서 통과했습니다(루트·아이템·블록 레지스트리가 얼어 있음).
    - 단위 테스트: `BuiltInRegistryFreezeTest`(26.1·26.2 게임 계층).
    - 확인하지 않은 것: 전용 서버에서도 Fabric의 `MainMixin.afterModInit`이 같은 자리에서 얼립니다. 하지만 서버의 모드 초기화 지점은 NeoForge의 모드 로딩보다 앞이라(31번) 그때는 아직 얼지 않았습니다. EULA를 넘긴 실제 서버 시작은 Poly 모드 로더가 EULA에 동의하지 않으므로 실행하지 않았습니다. 또 NeoForge·Fabric API와 함께 쓸 때, 콘텐츠를 선언한 Poly 모드가 `freeze()` 머리의 `poly:content_registration`에서 등록되는지는 확인하지 않았습니다(묶음에 Poly 모드 없음).

39. **빠진 mixin에 기대는 주입은 실행하지 않습니다**(`CoupledInjections.usingLeftOutTypes`, `HostPatchAdjustments.changedConstructor`, `MixinTypeAudit`, 2026-10-25, 32번 보충).
    - 현상: 38번을 고친 뒤 `corpusFabricNeoForgeClient`(창 실행, 세 버전)가 `GameRenderer.<init>`에서 `ClassCastException: GuiRenderer cannot be cast to GuiRendererExtensions`로 멈췄습니다.
    - 원인(`javap`):
      - Fabric API(rendering-v1)의 `GuiRendererMixin`은 `GuiRenderer`에 `GuiRendererExtensions`를 더하고 `@Shadow pictureInPictureRenderers`를 씁니다. NeoForge의 `GuiRenderer`에는 그 필드가 없어 32번이 이 mixin을 통째로 뺐습니다(`138`).
      - 그런데 같은 모드의 `GameRendererMixin.guiRendererReady`는 게임의 `GuiRenderer`를 그 인터페이스로 바꿔 `fabric_onReady()`를 부릅니다. 이 주입은 남아 있었습니다.
      - Mixin 검사(`corpusFabricNeoForgeClientMixins`)는 클래스를 로드만 하고 그 코드를 실행하지 않으므로 실패 0이었습니다.
    - 고친 방법 1 (`HostPatchAdjustments`):
      - mixin이 빠지면, 그 mixin이 대상에 줄 형을 모읍니다. 그 mixin의 인터페이스와, 접근자·invoker mixin이면 그 mixin 자신입니다.
      - 남은 mixin의 주입 중 그 형으로 바꾸거나 그 형의 메서드를 부르는 것은 실행하지 않습니다(`138`, `... uses <형>, which the mixin <M> adds to <대상>, and that mixin is left out; so this injection does not run`). 주입이 부르는 그 mixin 자신의 메서드와 람다 안까지 봅니다.
      - 같은 메서드에서 `instanceof`로 먼저 확인한 사용은 던지지 않으므로 그대로 둡니다.
      - 그렇게 멈춘 주입이 설정하는 상태를 읽는 주입도 32번처럼 멈춥니다.
      - 모드의 mixin이 아닌 일반 클래스가 그 형을 쓰는 경우는 다루지 않습니다. Fabric API rendering-v1에서는 두 mixin만 그 인터페이스를 씁니다.
    - 고친 방법 2 (Mixin 검사가 새로 찾은 것):
      - Fabric API의 `RenderPipelineBuilderMixin`은 `buildSnippet`의 `new RenderPipeline.Snippet(...)`을 `@WrapOperation`(`NEW`, 생성자 descriptor 없음)으로 감쌉니다. 그런데 NeoForge의 패치는 그 생성자에 매개변수를 하나 더 넘깁니다.
      - 그래서 Mixin이 핸들러 시그니처를 거부했고(`InvalidInjectionException`), 같은 클래스의 다른 mixin도 모두 버렸습니다. 그중 Iris(NeoForge)의 `PipelineBuilderMixin`이 있었는데, Iris의 `RenderPipeline` mixin이 그 인터페이스로 바꾸므로 다음 창 실행에서 같은 종류로 멈췄을 것입니다.
      - 이제 `NEW` 지점에서 바닐라 메서드가 부르는 그 클래스의 생성자를 패치된 메서드가 부르지 않으면, 호출 매개변수에 기대는 주입(`@WrapOperation`, `@Redirect`, `@ModifyArg`, `@ModifyArgs`)은 실행하지 않습니다(`138`).
    - 창 없는 재현 (`MixinTypeAudit`, Mixin 검사의 일부):
      - 모든 설정의 mixin을 jar에 있는 그대로 읽습니다. Poly 모드 로더가 바꾸기 전의 설정 리소스이므로 빠진 mixin도 포함됩니다.
      - 각 대상을 로드하고, mixin이 줄 형이 로드된 대상에 없으면 빠진 형으로 셉니다. 인터페이스 대상에 대한 mixin은 제외합니다.
      - 검사 중 Mixin과 FML 처리기를 거친 게임 클래스의 바이트를 훑습니다. 빠진 형을 `instanceof` 확인 없이 쓰면 실패입니다. 다음은 제외합니다:
        - 그 형 자신과 그 형을 가진 클래스
        - 클래스 안에서 아무도 부르지 않는 private 메서드(주입을 떼어 낸 핸들러가 이렇게 합쳐짐)
      - 빠졌지만 아무도 쓰지 않는 형(대부분 Lithium처럼 모드 자신의 설정이 끈 mixin)은 개수와 목록만 적습니다.
      - 고치기 전 코드로는 실패 2개(`guiRendererReady`, Iris `copyPipeline`), 고친 뒤 0개였습니다(26.2).
    - 단위 테스트: `HostPatchAdjustmentsTest.anInjectionUsingTheInterfaceOfALeftOutMixinDoesNotRun`(접근자 mixin, `instanceof` 확인 포함), `aWrapOfAConstructorCallThePatchGaveOtherParametersDoesNotRun`.
    - 이 판정이 빼는 것: NeoForge와 함께일 때 Fabric API의 GUI 화면 속 그림 렌더러 등록(`PictureInPictureRendererRegistry`)과 파이프라인 스니펫의 GUI 그리기 모드 복사는 동작하지 않습니다. NeoForge는 같은 일을 자기 API로 합니다.

40. **Fabric API의 네트워킹과 NeoForge의 네트워킹을 한 연결에서**(2026-10-25).
    - 현상: 39번을 고친 뒤 `corpusFabricNeoForgeClient`(창 실행, 세 버전)에서 클라이언트가 통합 서버에 들어가지 못했습니다. 서버가 `Failed to encode packet 'clientbound/minecraft:custom_payload' (minecraft:register)`로 연결을 끊었고, 원인은 `ClassCastException: RegistrationPayload cannot be cast to MinecraftRegisterPayload`였습니다.
    - 원인 1(`javap`, 세 버전 같음):
      - NeoForge의 패치는 `ClientboundCustomPayloadPacket`·`ServerboundCustomPayloadPacket`의 정적 초기화에서 바닐라의 `CustomPacketPayload.codec(fallback, types)` 대신 `codec(fallback, types, protocol, flow)`를 부릅니다. 서버 쪽은 놀이·설정 단계용으로 두 번 부릅니다.
      - Fabric API는 바닐라 호출을 `@WrapOperation`으로 감싸 페이로드 코덱에 자기 페이로드 형 목록을 붙입니다. 이 감싸기가 대상을 찾지 못해 `138`로 빠졌습니다.
      - 그래서 Fabric 페이로드는 NeoForge의 조회(`NetworkRegistry.getCodec`)로만 갔습니다. `minecraft:register`는 NeoForge의 내장 코덱이 맡아 Fabric 객체를 캐스트하지 못했습니다.
    - 원인 2: NeoForge는 자기가 협상하거나 `minecraft:register`로 받은 채널만 안다고 봅니다.
      - `NetworkRegistry.checkPacket`은 모르는 채널로 보내면 `Payload ... may not be sent to the client!`를 던집니다.
      - `handleModdedPayload`는 모르는 페이로드를 받으면 연결을 끊습니다.
    - 원인 3: 두 로더가 같은 식별자(`minecraft:register`·`minecraft:unregister`·`c:version`·`c:register`)를 각자 자기 Java 형으로 말합니다. 바이트 형식은 같습니다(플러그인 채널, 공통 네트워킹).
    - 결정: 각 로더가 자기가 등록한 식별자를 맡습니다.
      - (가) 위 감싸기를 되살립니다. 그러면 Fabric API의 코덱 조회가 Fabric Loader에서처럼 먼저 옵니다. 일반 장치로 했습니다: 호스트가 같은 호출에 인자를 뒤에 더 넘기면, `@Inject`는 그 호출로 옮기고 `@WrapOperation`은 다리(`poly$hostArguments$…`)로 실행합니다. 다리는 핸들러에 `AppendedArguments`를 주고, 이것이 더한 인자를 원래 호출에 붙여 줍니다. 같은 장치로 Fabric API의 `bindChannelInfo`(NeoForge가 `RegistryFriendlyByteBuf.decorator`에 `ConnectionType`을 더함)도 실행됩니다.
      - (나) `minecraft:register` 같은 공유 식별자의 선은 Fabric API가 맡습니다. NeoForge가 자기 객체(`MinecraftRegisterPayload` 등)를 보낼 때는 내장 규칙 `poly:custom_payload_encoding`이 NeoForge의 코덱을 고릅니다(`CustomPacketPayload$1.writeCap`의 유일한 `StreamCodec.encode` 호출, `ForeignPayloads`). 바이트 형식이 같으므로 받는 쪽은 그대로 읽습니다.
      - (다) NeoForge 호스트는 다른 로더의 모드가 함께 있을 때 `checkPacket`(양쪽)과 `handleModdedPayload`(양쪽) 앞에 `HostPayloadHooks`를 둡니다. NeoForge의 페이로드가 아니면 검사를 건너뛰고, 받는 쪽이 없는 다른 로더의 페이로드는 그 로더처럼 버립니다.
    - 왜 이 방향인가(`javap`):
      - NeoForge가 `minecraft:register`를 읽으면, 그 채널을 근거로 자기 공통 네트워킹 작업(`ConfigurationInitialization.configureModdedClient`가 `c:version`·`c:register` 채널이 있으면 `CommonVersionTask`·`CommonRegisterTask`를 추가)을 Fabric API의 같은 작업과 나란히 돌립니다. 그러면 같은 식별자로 두 로더의 객체가 오갑니다.
      - Fabric API의 설정 흐름(`ServerConfigurationNetworkAddon`의 등록 상태)은 클라이언트의 등록을 자기 `RegistrationPayload`로 기다립니다.
      - NeoForge끼리의 협상은 `minecraft:register`가 아니라 `neoforge:register`(`ModdedNetworkQueryPayload`)로 합니다.
    - Sinytra Connector와 비교(공식 출처: `Sinytra/ForgifiedFabricAPI` 저장소의 `26.1.2` 브랜치 원본):
      - Forgified Fabric API는 Fabric API를 NeoForge용으로 고친 포트입니다. `AbstractChanneledNetworkAddon`이 NeoForge의 `MinecraftRegisterPayload`·`MinecraftUnregisterPayload`를 보내고 받습니다(NeoForge가 등록 식별자를 맡음).
      - `NetworkRegistryMixin`이 `getCodec`에서 Fabric 코덱을 돌려주고, `checkPacket`·`handleModdedPayload`·묶음 걸러내기에서 Fabric 채널을 통과시킵니다.
      - `CommonRegisterTaskMixin`이 NeoForge의 `c:register`에 Fabric의 채널을 더합니다.
      - Poly 모드 로더는 Fabric API를 고치지 않고 그대로 실행하므로 반대 방향(Fabric API가 공유 식별자의 선을 맡음)을 택했습니다. NeoForge 쪽 검사를 다른 로더의 페이로드에 적용하지 않는 점은 같습니다.
    - 창 없는 재현 `corpusFabricNeoForgeJoin`(새 작업, `compatibilityCorpus`에 포함):
      - 테스트 하네스 `fabric-network-probe`가 게임 테스트 서버의 메모리 채널로 클라이언트처럼 접속해 로그인과 설정 단계를 지납니다.
      - 같은 모드를 가진 클라이언트처럼 답합니다: 자기 채널 등록, `c:version`·`c:register`, NeoForge 질의(`ModdedNetworkQueryPayload.fromRegistry`), Fabric API 레지스트리 동기화 완료.
      - 고치기 전에는 창 실행과 같은 `Failed to encode packet ... minecraft:register`로 끊겼습니다. (가)만 넣으면 `Payload fabric:registry/sync may not be sent to the client!`, (나)를 끄면 NeoForge의 `minecraft:unregister`에서 반대 방향 `ClassCastException`이 났습니다.
      - 고친 뒤 세 버전 모두 NeoForge의 등록·해제·협상(`neoforge:network`), 상표, `c:version`·`c:register`, Fabric API의 재료 동기화까지 연결이 유지되었습니다.
    - 함께 고친 것: 호스트가 매개변수 순서를 바꾼 메서드로의 `@Inject` 다리(32번)가 핸들러의 매개변수 주석을 잃었습니다. Fabric API `ReloadableServerRegistriesMixin.modifyLootTable`의 MixinExtras `@Local`이 빠져 Mixin이 `Invalid descriptor`로 그 클래스의 mixin을 모두 버렸습니다. 이제 다리가 각 매개변수의 주석을 옮깁니다(`aBridgeKeepsTheHandlersParameterAnnotations`).
    - 단위 테스트: `HostPatchAdjustmentsTest.aWrapOfACallThePatchGaveTrailingArgumentsRunsThroughABridge`, `appendedArgumentsHandTheWrappedCallItsTrailingArguments`, `NeoForgeForeignPayloadsTest`(NeoForge 코덱 고르기, 검사 건너뛰기, 규칙 네 개).
    - 확인하지 않은 것:
      - 놀이 단계의 페이로드. 탐침은 설정 단계에서 멈춥니다(41번에서 확인).
      - NeoForge의 묶음 패킷 걸러내기(`filterGameBundlePackets`). 묶음 안의 Fabric 페이로드는 NeoForge가 협상한 채널이 아니어서 빠질 수 있습니다(41번에서 확인하고 고침).
      - NeoForge 모드가 Fabric·NeoForge가 아닌 상대와 임시 채널로 말하는 경우. NeoForge는 상대의 `minecraft:register`를 읽지 않으므로 그 채널 목록이 비어 있습니다.
      - 실제 클라이언트로 저장된 월드에 들어가는 것은 창 실행으로만 확인합니다.
    - 저장된 게임 규칙 경고(`native_showcase:golems_greet_players`는 등록되지 않음)는 무해합니다. `worldCreateSaveTest`가 Poly 쇼케이스 모드와 함께 만든 월드를 그 모드 없이 열어서 생기며, 통과하는 `corpusNeoForgeClient`·`corpusFabricClient`에서도 같은 줄이 나옵니다. 게임은 모르는 규칙만 빼고 나머지를 읽습니다.
41. **놀이 단계의 페이로드: 호스트가 `super`를 부르게 바꾼 재정의, 묶음 패킷**(2026-10-25, 40번 보충).
    - 증상: 창 실행 `corpusFabricNeoForgeClient`(세 버전)가 "PolyDev joined the game" 뒤 약 2초 만에 끊겼습니다. 서버 쪽 `ServerCommonPacketListenerImpl`의 Fabric API 핸들러 `handleCustomPayloadReceivedAsync`가 `IllegalStateException: Unknown addon`을 던졌습니다.
    - 원인(`javap`, 26.2 바닐라 jar와 NeoForge 26.2.0.93이 패치한 jar, Fabric API `fabric-networking-api-v1`):
      - 놀이 단계 애드온(`ServerPlayNetworkAddon`)은 빠지지 않았습니다. `ServerGamePacketListenerImplMixin.initAddon`(`<init>`의 `RETURN`)이 그대로 붙이고, 같은 mixin의 `handleCustomPayloadReceivedAsync`(`ServerGamePacketListenerImpl.handleCustomPayload`의 `HEAD`)가 Fabric 채널의 페이로드를 받아 취소합니다.
      - 바닐라에서 `ServerGamePacketListenerImpl.handleCustomPayload`는 빈 메서드(`return`)이고 `ServerCommonPacketListenerImpl.handleCustomPayload`도 빈 메서드입니다. 공통 클래스의 메서드에는 재정의하지 않는 설정 단계 리스너만 닿습니다. 그래서 Fabric API의 `ServerCommonPacketListenerImplMixin.handleCustomPayloadReceivedAsync`(같은 메서드의 `HEAD`)는 애드온이 `ServerConfigurationNetworkAddon`이 아니면 `Unknown addon`을 던지게 쓰여 있습니다.
      - NeoForge의 패치는 놀이 리스너의 재정의가 `super.handleCustomPayload`를 부르게 바꿉니다(NeoForge는 공통 클래스에서 `minecraft:register`·`unregister`를 처리하고 `NetworkRegistry.handleModdedPayload`로 넘깁니다). Fabric API가 받지 않은 놀이 단계 페이로드(NeoForge 모드의 페이로드 모두)가 놀이 리스너를 데리고 공통 핸들러에 닿아 예외가 났습니다.
      - 138·결합 규칙(32·39번)이 잡지 못한 이유: 빠진 mixin도, 바뀐 주입 지점도 없습니다. 주입 대상 메서드는 그대로이고, 그 메서드에 닿는 수신 객체의 종류만 늘었습니다.
    - 결정(`HostPatchAdjustments.Receivers`, 새 진단 `POLY-COMPAT-222`, 정보):
      - mixin 대상 클래스의 메서드로의 `@Inject`에 대해, 다른 mixin이 대상으로 삼는 하위 클래스 중 바닐라의 재정의는 `super`를 부르지 않는데 패치된 재정의는 부르는 클래스를 찾습니다(사이의 클래스가 그 메서드를 선언하지 않을 때만).
      - 그런 주입은 가드 메서드(`poly$receivers$<핸들러>`)가 `@Inject`를 넘겨받아, 수신 객체가 그 하위 클래스면 아무것도 하지 않고 돌아가고 아니면 원래 핸들러를 부릅니다. 주입이 바닐라에서와 같은 수신 객체에서만 실행되고, 그 하위 클래스에서는 호스트의 코드(NeoForge의 처리)가 그대로 실행됩니다.
      - 범위: 핸들러가 `void`이고 대상 메서드 하나·대상 클래스 하나인 비정적 `@Inject`만. 다른 주입기(`@Redirect`·`@WrapOperation` 등)는 원래 동작을 대신 실행해야 해서 아직 다루지 않습니다. 하위 클래스는 mixin 대상인 클래스만 봅니다. 게임의 모든 클래스를 읽으면 호스트 실행마다 시작이 약 0.3~0.6초 늘어 지금은 하지 않습니다(바닐라 26.2 클라이언트 jar 10,952개 클래스의 상위 클래스를 읽는 데 걸린 시간, 개발 PC 측정).
      - 실제 모드 묶음에서 이 규칙에 걸리는 주입은 하나(Fabric API의 이것)입니다: `corpusFabricNeoForgeJoin` 세 버전, 26.1의 `compatibilityCorpus`에 든 NeoForge와 함께인 작업 모두(서버·클라이언트 Mixin 검사·Quilt 포함), 26.2 `corpusFabricNeoForgeClientMixins`. Forge와 함께인 작업에는 없습니다(Forge의 패치는 그 재정의를 바꾸지 않음).
    - 묶음 패킷: NeoForge의 `NetworkRegistry.filterGameBundlePackets`는 묶음 안의 페이로드 중 NeoForge가 협상한 채널이 아닌 것(모든 Fabric API 페이로드)을 말없이 뺍니다. 하나만 남으면 묶음 없이 보내고, 아무것도 남지 않으면 Netty가 `PacketBundleUnpacker must produce at least one message`로 연결을 끊습니다(창 없는 재현에서 확인).
      - 규칙 `poly:neoforge_bundle_filter`(`InvokeHookBeforeReturn`, 실패 정책 `DISABLE_RULE`)가 그 메서드가 돌려주기 직전에 다른 로더의 페이로드를 제자리에 되돌립니다(`HostPayloadHooks.keepOtherLoadersBundledPayloads`). 걸러낸 목록이 원래 순서의 부분열이 아니면 그대로 둡니다.
      - 대상 시그니처 `(ChannelHandlerContext, Iterable)List`와 매개변수 슬롯에 쓰지 않음을 NeoForge 26.1.0.19-beta·26.1.2.122·26.2.0.93의 jar에서 확인했습니다.
    - 창 없는 재현 확장(`corpusFabricNeoForgeJoin`):
      - `fabric-network-probe`가 설정 단계의 작업에 같은 모드를 가진 클라이언트처럼 답합니다: 알려진 팩, Fabric API의 재료·데이터 첨부 동기화, NeoForge의 데이터 맵·확장 열거형·기능 플래그·동결 레지스트리. 그 뒤 서버의 레지스트리로 놀이 단계에 들어갑니다. CreativeCore의 클라이언트 mixin이 놀이 단계 버퍼를 자기 것(`CreativeByteBuf`)으로 바꾸므로 탐침도 같은 버퍼를 씁니다.
      - 놀이 단계에서 자기 Fabric API 채널(`fabric_network_probe:echo`)로 보내고, 새 테스트 모드 `neoforge-network-probe`(NeoForge의 `playBidirectional`)가 접속한 플레이어에게 보낸 페이로드를 되돌려 보냅니다. 두 서버 쪽 수신기가 답하고, 서버 쪽 탐침이 Fabric 페이로드 두 개를 묶음으로 보냅니다. 3초 동안 연결을 유지하는지 봅니다.
      - 고치기 전(가드를 끔): 창 실행과 같은 `Unknown addon`으로 끊겼습니다. 묶음 규칙을 끄면(`-PpolyTransformDisable=poly:neoforge_bundle_filter`) 묶음이 비어 위의 Netty 오류로 끊겼습니다.
      - 고친 뒤 26.2·26.1.2·26.1에서 두 로더의 페이로드가 오가고 묶음이 도착하며 연결이 유지되었습니다.
    - 단위 테스트: `HostPatchAdjustmentsTest.anInjectionKeepsToTheReceiversItHasInVanillaWhenAPatchedOverrideCallsSuper`, `HostPayloadHooksTest`(26.2·26.1 게임 계층), `NeoForgeForeignPayloadsTest`(규칙 다섯 개).
    - 확인하지 않은 것: NeoForge 모드가 Fabric·NeoForge가 아닌 상대와 임시 채널로 말하는 경우(40번과 같음), 다른 주입기의 같은 경우, mixin 대상이 아닌 하위 클래스. 실제 클라이언트의 놀이 단계는 창 실행으로 확인합니다.
42. **호스트의 default와 모드의 default가 한 인터페이스에서 겹칠 때**(2026-10-25, 41번 다음).
    - 증상(여섯 번째 창 실행 `corpusFabricNeoForgeClient`): 세 버전 모두 월드에 들어간 뒤 그리는 중에 `IncompatibleClassChangeError: Conflicting default methods`로 멈췄습니다.
      - 26.2·26.1.2: `BlockStateModelExtension.particleMaterial`(NeoForge)과 `FabricBlockStateModel.particleMaterial`(Fabric API), `SingleVariant.particleMaterial`에서. Xaero's World Map이 부름.
      - 26.1: `IBlockEntityRendererExtension.getRenderBoundingBox`(NeoForge)와 EntityCulling의 `BlockEntityRenderFabricExtension.getRenderBoundingBox`, `TestInstanceRenderer`에서.
    - 원인(`javap`): NeoForge의 패치가 게임 인터페이스(`BlockStateModel`, `BlockEntityRenderer`, `TooltipFlag`)에 자기 확장 인터페이스를 주고, Fabric 쪽이 같은 인터페이스에 자기 인터페이스를 줍니다. Fabric API는 class tweaker(`transitive-inject-interface`)로, EntityCulling은 mixin으로 줍니다. 두 인터페이스가 같은 이름·descriptor의 default를 가지면, 그 메서드를 재정의하지 않는 클래스는 부를 때마다 JVM이 거부합니다.
      - Forge와 함께일 때의 처리(`HostPatchAdjustments.HostDefault`)는 mixin이 **클래스**에 인터페이스를 줄 때만 봤습니다. 대상이 인터페이스이거나 class tweaker로 준 경우는 보지 않았습니다.
    - 결정(`HostInterfaceDefaults`, 새 진단 `POLY-COMPAT-223`):
      - 호스트가 있을 때 Fabric 단계(인터페이스 주입, Mixin) 뒤에 확인합니다. 인터페이스가 그 단계에서 새 직접 상위 인터페이스(모드의 것)를 얻었고, 그 인터페이스의 default와 원래 있던 상위 인터페이스(호스트 패치나 바닐라)의 default가 서로 관계없는 인터페이스에서 오면, 그 인터페이스에 자기 default를 더합니다. 이 default는 원래 쪽(`Original.super.m(...)`)을 부릅니다.
      - Forge 때와 같은 정책입니다: 호스트의 default를 쓰고, 재정의는 그대로 이깁니다. 모드의 인터페이스로 부르면 호스트의 default가 실행됩니다.
      - 두 default의 명령이 같으면 정보, 다르면 `[limited]`로 알립니다.
      - 클래스(인터페이스가 아닌)는 지금처럼 `HostPatchAdjustments`가 mixin 쪽을 맡고, 나머지는 감사가 잡습니다.
    - 경우마다 확인한 것(`javap`, 26.2·26.1 jar):
      - `particleMaterial(BlockAndTintGetter, BlockPos, BlockState)`: NeoForge는 `self().particleMaterial()`, Fabric API는 `((BlockStateModel) this).particleMaterial()`을 부릅니다. 명령은 달라서 `[limited]`로 알리지만 뜻은 같습니다(둘 다 같은 객체의 인자 없는 `particleMaterial()`).
      - `createGeometryKey(BlockAndTintGetter, BlockPos, BlockState, RandomSource)`: 둘 다 `return null`.
      - `getRenderBoundingBox(BlockEntity)`(26.1): 둘 다 `new AABB(blockEntity.getBlockPos())`.
      - `TooltipFlag.shouldDisplayAllInformation()`(26.2·26.1.2): 명령이 같습니다.
    - 감사: NeoForge Mixin 감사(`corpusFabricNeoForgeClientMixins`)가 감사 중 로드된 게임 클래스와 인터페이스마다, 가장 구체적인 default가 둘 이상인데 자기(또는 상위 클래스)가 선언하지 않은 메서드를 실패로 셉니다(`MixinTypeAudit.conflictingDefaults`, 리플렉션).
      - 고치기 전(해결을 끔): 26.2 실패 6개(`BlockStateModel`·`SingleVariant`·NeoForge의 `DynamicBlockStateModel`·`DelegateBlockStateModel`), 26.1 실패 8개(위에 더해 `BlockEntityRenderer`·`AbstractEndPortalRenderer`). 창 실행의 두 충돌이 모두 나왔습니다.
      - 고친 뒤 26.2·26.1·26.1.2 모두 실패 0.
    - 단위 테스트: `HostInterfaceDefaultsTest`. 고치기 전에는 구현 클래스의 호출이 `IncompatibleClassChangeError`, 고친 뒤 호스트의 default 값.
    - 확인하지 않은 것: 클래스 tweaker로 **클래스**에 준 인터페이스의 충돌(감사가 잡지만 고치지 않음), 감사 중 로드되지 않은 클래스. 실제 그리기는 창 실행으로 확인합니다.
43. **호스트 설정을 고르기 전에 로드된 대상 하나 때문에 설정 전체가 빠지던 문제**(2026-10-25, 30번의 `220` 보충).
    - 증상(일곱 번째 창 실행, 26.2만): 접속 약 4초 뒤 통합 서버가 엔티티를 틱하다가 `ClassCastException: ServerLevel cannot be cast to BlockEntityGetter`로 멈췄습니다(`HopperHelper.updateHopperOnUpdateSuppression`, Lithium의 NeoForge 빌드 0.25.3). 26.1.2·26.1은 창 실행이 통과했습니다.
    - 원인(로그와 `javap`):
      - `BlockEntityGetter`는 `lithium.mixins.json`의 `util.block_entity_retrieval.LevelMixin`이 `Level`에 줍니다. 그것을 쓰는 `Level.markAndNotifyBlock` 주입은 다른 설정 `lithium-neoforge.mixins.json`(`block.hopper.LevelMixin`)에 있어 적용되었습니다.
      - Iris의 NeoForge 빌드 플러그인이 Mixin이 NeoForge 설정을 고르는 동안 `BlockGetter`를 로드합니다. 그 뒤 Lithium의 `world.raycast.BlockGetterMixin`을 준비하다가 Mixin이 `MixinTargetAlreadyLoadedException`을 냅니다(`220`).
      - 30번에서는 이 경우 "그 mixin만 빠진다"고 적었지만, 실제로는 Mixin의 처리기가 준비에 실패한 설정을 통째로 버립니다. `-PmixinVerbose`로 보면 `lithium.mixins.json`의 mixin은 하나도 적용되지 않았습니다.
      - 26.1.2·26.1의 이전 감사에는 `BlockEntityGetter`가 빠졌다는 메모가 없었습니다. 고친 뒤 26.1.2에서도 `BlockGetter`가 `220`으로 알려지지만(26.1은 없음), 26.1.2에서 이전에 설정이 통째로 빠지지 않은 이유는 확인하지 않았습니다.
      - NeoForge만 넣은 경우(`corpusNeoForgeClientMixins`, 26.2, 같은 Lithium 빌드)에도 그 메모가 없습니다. NeoForge 자신의 Mixin에서는 이 구멍이 없습니다.
    - 결정:
      - Mixin이 호스트 설정을 고르는 동안(`FabricMixinBootstrap.addShared`) 이미 로드된 대상을 Mixin 서비스가 "로드되지 않음"으로 답하고 기록합니다(`PolyMixinService.isClassLoaded`). 그러면 그 클래스로의 mixin만 적용되지 않고 설정의 나머지는 적용됩니다. 호스트 자신의 Mixin에서처럼 그 클래스를 알아채지 못하는 것과 같습니다.
      - 고른 뒤, Mixin이 준비 중에 물어본 대상 가운데 그 전이나 그 동안 로드된 클래스를 `220`으로 알립니다. 이번에는 `BlockGetter` 하나였습니다.
    - 감사(`MixinTypeAudit`):
      - 지금까지는 변환된 게임 클래스의 코드만 봤습니다. 이번 캐스트는 Lithium의 일반 클래스 `HopperHelper`에 있어서 놓쳤습니다.
      - 이제 변환된 게임 클래스가 부르는 모드 클래스의 메서드(두 단계까지)도 봅니다.
      - `T.class.isAssignableFrom(...)`·`isInstance(...)`로 확인한 형은 확인된 것으로 봅니다. Lithium의 `BlockInfoInitializer`가 이렇게 확인합니다.
      - 고치기 전(26.2): 실패 3개. `Level`의 주입 → `HopperHelper`가 `BlockEntityGetter`를 씀(창 실행의 크래시)과, `BlockInfoInitializer`의 두 형입니다. 그 둘은 설정이 통째로 빠져서 생긴 것이고, 고친 뒤 하나는 적용되고 하나는 위의 확인으로 지켜집니다.
      - 고친 뒤 26.2·26.1.2·26.1 모두 실패 0. 빠진 형은 26.2 기준 92개에서 14개가 되었습니다.
    - 확인하지 않은 것: 남은 `BlockGetterMixin`(광선 추적 최적화)은 적용되지 않습니다(`220`). 고르는 동안 로드되었지만 Mixin이 묻지 않은 클래스는 알리지 않습니다. 실제 호퍼 갱신 억제는 창 실행으로 확인합니다.

## MinecraftForge 모드와 함께: 가능성 조사 (2026-10-24)

34. **결론: 지금은 하지 않습니다.** Forge 모드와 NeoForge 모드를 함께 넣으면 지금처럼 Forge가 실행되고, NeoForge 모드는 이제 이유와 함께 한 번 알린 뒤(`POLY-COMPAT-221`, 새 코드) 각각 진단됩니다(`200..205`, 그래서 그 모드가 필요한 모드는 "설치됐지만 로드되지 않음"을 받음). 근거는 실제 jar에서 잰 아래 수치입니다.
    - **패치 겹침(결정 요인).** Minecraft 26.2 서버의 바닐라 클래스 7,445개를 Forge 26.2-65.1.5가 패치한 서버 jar, NeoForge 26.2.0.93이 패치한 서버 jar와 클래스·메서드마다 비교했습니다(명령어 열 비교, 줄 번호·레이블 제외, 람다 번호와 지역 변수 번호는 재컴파일 차이로 보고 맞춤). Forge는 바닐라 클래스 587개를 바꾸고 365개를 더하며, NeoForge는 2,202개를 바꾸고 8개를 더합니다. **두 쪽이 다 바꾸는 클래스는 549개**이고, 그 안에서 **두 쪽이 다 바꾸는 바닐라 메서드는 2,342개**입니다. 그중 1,570개는 두 쪽 바이트코드가 같고(같은 소스를 다시 컴파일한 차이로 보임), 35개는 서로 다른 바닐라 명령을 바꿔 3-way 병합이 가능하며, **736개(288개 클래스)는 같은 바닐라 명령을 서로 다르게 바꾸거나 한쪽이 메서드를 없앱니다.** 예: `LivingEntity`의 `heal`, `die`, `knockback`, `actuallyHurt`, `dropAllDeathLoot`처럼 두 로더가 같은 자리에 서로 다른 이벤트(Forge의 `ForgeEventFactory`, NeoForge의 `EventHooks`)를 넣고 값과 취소 여부를 각자 돌려받습니다(42개 메서드). `Blocks`는 130개입니다.
    - **한 클래스에 상위 클래스는 하나.** 두 쪽이 바닐라 상위 클래스를 서로 다르게 바꾸는 클래스가 6개입니다: `Entity`, `Level`, `BlockEntity`는 Forge에서 `CapabilityProvider$…`, NeoForge에서 `AttachmentHolder`를, `EnderDragonPart`는 각자의 `PartEntity`를, `MappedRegistry`는 NeoForge만 `BaseMappedRegistry`를, `ItemStack`은 Forge만 `CapabilityProvider$ItemStacks`를 상위 클래스로 둡니다. 두 로더의 모드는 이 상위 클래스의 메서드(`getCapability`, `getData`)를 그 게임 클래스로 부르므로, 병합된 하나의 `Entity`가 두 쪽을 다 만족할 수 없습니다. 이 밖에 두 쪽이 서로 다른 인터페이스를 더하는 클래스 33개, 필드를 서로 다르게 바꾸는 클래스 49개, 같은 이름·설명자의 메서드를 서로 다른 코드로 더하는 경우 74개가 있습니다.
    - **고른 것과 비교한 방안.**
      1. *Poly 모드 로더가 게임 하나를 갖고 두 FML을 동시에 호스트*(Forge는 ADR-0021, NeoForge는 이 ADR처럼 각 FML의 게임 레이어와 검사한 호출을 가로챔). 클래스 처리(하나의 Mixin, 두 쪽 access transformer·coremod), 레지스트리 일정, 모드 목록(ADR-0025), 이벤트 버스(패키지가 달라 클래스가 겹치지 않음)는 지금 있는 장치로 맞출 수 있는 문제입니다. 막는 것은 하나의 게임 클래스입니다. 위의 736개 메서드는 자동 3-way 병합이 되지 않고, 6개 클래스는 상위 클래스가 둘이 될 수 없습니다. Fabric mixin을 Forge 패치에 맞추는 방식(`138`·`212`·`213`)은 패치가 없앤 주입을 알리고 빼는 것이라, 한 쪽 패치 전체를 다른 쪽 위에 다시 얹는 데는 쓸 수 없습니다. 두 패치 묶음을 손으로 합친 세 번째 패치 묶음을 버전마다 만들어야 하고, 그것은 사실상 한 로더를 다른 로더로 옮기는 일입니다.
      2. *로더마다 게임 사본 하나*(Poly 모드 로더가 주 프로그램, Forge와 NeoForge가 각자 클래스로더 계층과 패치된 Minecraft를 가짐). 각 사본은 자기 레지스트리·월드·서버를 가지므로 Forge 모드의 블록이나 아이템은 NeoForge 사본의 월드에 있을 수 없습니다(블록은 그 사본의 `Block` 클래스 객체이고 그 사본의 레지스트리에만 등록됨). 창과 입력은 한 사본만 가질 수 있습니다(GLFW는 프로세스에 하나이고 이벤트는 그것을 초기화한 스레드에서 처리). 비용은 대략 두 게임의 합입니다. 측정 6·8(`PERFORMANCE.md`)의 GC 직후 힙이 Forge 묶음 267 MiB, NeoForge 묶음 225 MiB(바닐라 139~141 MiB)이고, 두 서버가 각자 틱을 돕니다. 나눌 수 있는 상태는 프로세스 사이처럼 명시적으로 옮긴 것(예: 한 사본이 다른 사본에 네트워크 클라이언트로 붙음)뿐이라, "한 게임에 두 로더의 모드"가 되지 않습니다. 고르지 않습니다.
      3. *한 로더가 게임을 갖고 다른 로더의 API를 옮겨 주는 층*(Poly 모드 로더가 Forge 위에서 Fabric을, Sinytra Connector가 NeoForge 위에서 Fabric을 실행하는 방식). 실현 가능한 유일한 방법입니다. 예를 들어 NeoForge가 게임을 갖고 Forge 모드가 부르는 Forge API(`net.minecraftforge.*`: 이벤트, 레지스트리, capability, 네트워크, 설정, `ForgeHooks`)를 NeoForge 위에 다시 구현하고, Forge 모드 바이트코드의 상위 클래스 메서드 호출(`getCapability`)을 옮겨야 합니다. Connector가 Fabric API를 위해 하는 일(Forgified Fabric API)과 같은 규모이고 버전마다 유지해야 합니다. 2026-10-24 검색에서 1.20.1 이후 판에서 Forge 모드를 NeoForge에서 실행하는 유지되는 층은 찾지 못했습니다(찾은 것은 NeoForge 위의 Fabric 층인 Sinytra Connector와 Insanity Wrapper). 이번 범위에서 하지 않습니다.
    - 근거 재현: 비교 프로그램은 저장소에 넣지 않았습니다(세 jar는 사용자 PC에서만 만들어짐). 같은 비교는 `ClassFile` API로 세 jar의 클래스마다 메서드 명령어 열을 비교하고 LCS로 바닐라 대비 편집 위치를 구해 겹침을 셉니다. 클라이언트 jar는 비교하지 않았습니다(NeoForge 26.2의 패치된 클라이언트 jar가 이 PC에 없음). 클라이언트에는 서버보다 많은 패치가 있으므로 겹침이 더 줄지 않습니다.

## 고르지 않은 것

- **FML의 `TransformingClassLoader`가 게임을 소유하고, Poly 모드 로더는 `ClassProcessor`로 끼어들기.** FML의 공개 SPI만 쓰므로 바꾸는 코드는 가장 적습니다. 그러나 게임 계층과 Poly 모드가 그 로더 안으로 들어가야 하고, Poly 모드 로더 시작 순서(모드 판정 → 변환 계획 → 게임 클래스로더)가 FML 아래로 뒤집힙니다. 게임 클래스 소유자가 로더마다 달라집니다(ADR-0003).
- **`FMLLoader.create`를 Poly 모드 로더 코드로 다시 쓰기.** 모드 검색·정렬·언어 공급자·Mixin 시작의 순서와 의미를 추측해야 합니다(AGENTS.md 원칙 6).
- **설치 프로그램이 만든 패치된 jar를 받아 쓰기.** NeoForge Maven에 `minecraft-client-patched`는 없고 설치 프로그램이 사용자 PC에서 만듭니다. Poly 모드 로더가 패치된 클래스를 배포할 수도 없습니다.

## 결과와 위험

- NeoForge 모드가 NeoForge에서와 같은 패치·이벤트·로딩 단계로 실행될 수 있습니다. FML 쪽에서 바꾸는 곳은 두 호출(`buildTransformingLoader`, `ModsFolderLocator` 추가)과, 다른 로더의 모드가 있을 때 `ModList`의 두 메서드(19번)뿐이고, 모두 형태를 검사합니다.
- 빈 `TransformingClassLoader`가 실제로 모든 일을 게임 클래스로더에 맡기는지는 N3에서 확인합니다. `ModuleClassLoader`의 리소스 조회, `ClassLoadingGuardian`(Poly 모드 로더는 `Instrumentation`을 넘기지 않음), FML Mixin의 클래스 바이트 공급자(`FMLClassBytecodeProvider`)가 확인 대상입니다. 맞지 않으면 이 ADR을 고칩니다.
- 패치 항목이 Forge의 약 7배입니다. 패치된 jar를 만드는 시간과 캐시 크기를 N2에서 잽니다.
- 라이브러리 충돌(ASM 9.10.1, night-config)은 ADR-0021과 같은 문제이고 같은 방식(ADR-0005 재검토)으로 다룹니다.
- 라이선스: NeoForge·FML·`binarypatcher`·`installertools`는 LGPL 2.1입니다. 받아서 쓰기만 하고 코드를 복사하지 않습니다.
