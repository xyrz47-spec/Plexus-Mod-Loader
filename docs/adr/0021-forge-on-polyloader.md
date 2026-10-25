# ADR-0021: Forge 모드를 Forge 자신의 코드로 실행 — 제안

- 상태: **제안** (2026-10-22, 같은 날 G3 조사로 고침). G3 PoC가 통과해야 채택합니다.
- 관련: ADR-0003(게임 클래스로더), ADR-0009(로더 브리지), ADR-0019(사용자 PC에서만 만드는 jar), ADR-0020(Fabric을 Fabric Loader처럼)
- 계획과 조사: [`docs/plans/FORGE_SUPPORT.md`](../plans/FORGE_SUPPORT.md)

## 배경

- MinecraftForge 26.2-65.1.5는 바닐라 클래스 **1394개**를 바이너리 패치로 바꿉니다. 패치는 설치 프로그램의 `data/client.lzma`·`server.lzma`에 있습니다.
- 모드 로딩은 패치된 게임 코드가 FML(`fmlcore`의 `ModLoader`)을 부르면서 진행됩니다. 그래서 FML만 흉내 내서는 Forge 모드가 기대하는 이벤트가 오지 않습니다.
- **Forge는 JPMS 모듈을 전제로 합니다**(G3 조사, 2026-10-22, Forge가 Maven에 올린 소스 jar로 확인). 처음 제안은 `fmlloader`의 상태 클래스만 바꾸면 된다고 보았지만, 모듈 레이어는 `fmlloader` 밖에서도 씁니다.
  - `fmlcore`: `Bindings`와 `ModStateManager`는 `ServiceLoader.load(FMLLoader.getGameLayer(), …)`로 서비스를 찾습니다. `ModLoader`는 게임 레이어를 언어 공급자에 넘깁니다.
  - `javafmllanguage`: `FMLModContainer`는 모드 클래스를 `gameLayer.findModule(<모드 ID>)`에서 `Class.forName(module, name)`으로 엽니다. 모드 jar의 `Add-Opens`/`Add-Exports`도 모듈 사이에 적용합니다.
  - Forge(`universal.jar`): `ForgeMod`는 모듈 사이의 open을 바꾸고, `CrashReportAnalyser`는 게임 레이어의 모듈 패키지를 읽습니다.
  - `fmlloader`: 실행 핸들러는 `layer.findModule("minecraft")`에서 `net.minecraft.server.Main`을 엽니다. 모드 검색(`ModDiscoverer`)과 언어 공급자(`LanguageLoadingProvider`)는 ModLauncher의 SERVICE·PLUGIN 레이어에서 `ServiceLoader`로 찾습니다.
- ModLauncher 10.2.6은 약 4천 줄입니다. 하는 일은 레이어(BOOT, SERVICE, PLUGIN, GAME)를 만들고, `ITransformationService`·`ILaunchPluginService`·`ITransformer`를 정해진 순서로 부르고, 변환하는 모듈 클래스로더로 실행 핸들러를 돌리는 것입니다.

## 결정 (제안)

1. Forge 모드가 하나라도 로드되면, 사용자 PC에서 바닐라 jar에 Forge 바이너리 패치를 적용한 jar를 만들어 캐시에 두고 게임 jar로 씁니다(G2, 완료).
   - 패치 데이터와 Forge 라이브러리는 Forge Maven에서 받고, Poly 모드 로더가 배포하지 않습니다.
   - 같은 입력에서 Forge 설치 프로그램과 같은 결과가 나와야 합니다(클래스 바이트 비교 테스트).
2. **게임 클래스로더가 JPMS 모듈도 정의합니다.** Forge가 있을 때 Poly 모드 로더는 Minecraft(패치된 jar), Forge, FML 라이브러리, Forge 모드를 모듈로 하는 게임 레이어를 `ModuleLayer.defineModules(…, module -> 게임 클래스로더)`로 만듭니다. 그 패키지의 클래스는 같은 게임 클래스로더가 변환해 정의하므로 이름 있는 모듈에 들어갑니다. 다른 패키지(Poly 모드, Fabric 모드, Poly 모드 로더 게임 계층)는 지금처럼 이름 없는 모듈에 남습니다. 게임 클래스의 소유자는 계속 하나입니다(ADR-0003 유지).
3. **Poly 모드 로더가 ModLauncher의 서비스 계약을 구현합니다.** Fabric에서 Knot 대신 `PolyMixinService`를 둔 것과 같습니다. ModLauncher의 코드는 복사하지 않고(LGPL-3.0), 공개 API(`cpw.mods.modlauncher.api`, `serviceapi`)의 계약을 따라 새로 씁니다.
   - FML 자신(`FMLServiceProvider`, 모드 검색, 언어 공급자, `ModLoader`, `javafmllanguage`)과 Forge `universal.jar`는 그대로 실행합니다.
   - FML이 보는 Forge 모드는 Poly 모드 로더가 찾은 것뿐입니다(G4). 그 jar를 게임 클래스로더의 리소스로 두면 FML의 `ClasspathLocator`가 찾습니다. FML의 mods 폴더 검색(`ModsFolderLocator`)은 BOOT 레이어에서 뺍니다. 그대로 두면 같은 폴더의 Fabric·Poly 모드마다 "Forge 모드가 아님" 오류로 멈춥니다.
   - `IEnvironment`, `IModuleLayerManager`, `Launcher.INSTANCE`처럼 FML이 부르는 부분만 Poly 모드 로더 구현으로 둡니다.
   - 실행 플러그인(access transformer, `RuntimeDistCleaner`)과 `ITransformer`(coremods)는 `ModLoaderBridge.classTransformer` 단계에서 부릅니다.
4. 처음에는 Forge 모드와 Fabric 모드를 함께 넣으면 Forge 쪽을 막고 이유를 알립니다. Forge의 Mixin(원본 0.8.7)과 Fabric 포크를 한 JVM에 둘 수 있는지 조사한 뒤 따로 정합니다.
5. 패치된 jar 위에서 Poly 모드 로더 내장 규칙은 다시 검사합니다. `BuiltInRulesTest`를 패치된 jar로도 실행합니다. 맞지 않는 규칙은 그 jar에서만 끄고, 그 규칙에 기대는 Poly 모드 기능을 진단으로 알립니다.
   - 추가(2026-10-23): 꺼진 규칙이 선택 규칙(`DISABLE_RULE`)이면 Forge의 코드가 그 자리를 대신하므로 경고가 아니라 INFO로 남깁니다("patched the target of Poly Mod Loader's optional rule …; that rule is off"). 예: `poly:server_brand`, `poly:client_title_mod_counts`.
6. 추가(2026-10-23): **꺼진 규칙의 일을 Forge가 다른 코드로 하면, 호스트가 그 코드에 맞춘 Poly 모드 로더 규칙을 냅니다**(`GameHost.transformRules()`). 런타임은 이 규칙을 어댑터 규칙과 함께 패치된 jar로 계획하므로 같은 선언·사전조건·실패 정책을 따릅니다. Forge 클래스 이름은 `poly-bridge-forge`에만 둡니다.
   - 첫 사례: 타이틀 화면의 로더별 모드 수. 바닐라는 버전 글자 위에 그리고(`poly:client_title_mod_counts`), Forge는 버전 글자 대신 `BrandingControl.forEachLine`으로 자기 줄을 그리므로 그 호출을 바꿔 Forge의 줄 위에 더합니다(`poly:client_title_mod_counts_forge`, `ForgeTitleBranding`).
   - 줄의 글자는 게임 계층이 만들고(게임 언어), 런타임이 `GameLayer.brandingLines()`를 `GameHost.addBrandingLines`로 넘깁니다. 브리지는 게임 계층을, 게임 계층은 Forge를 모릅니다.
7. 추가(2026-10-23, G8): **Forge 패치가 옮긴 주입 지점은 호스트가 놓인 방식과 함께 선언하고, Fabric 브리지가 패치된 클래스에서 그 모양을 확인한 뒤에만 옮깁니다**(`InjectionPointReplacement.Placement`, `ForgeInjectionPoints`). 모드 이름으로 분기하지 않습니다.
   - `SAME_PLACE`: 같은 자리의 다른 호출. `@Inject`는 자리만 쓰므로 옮기고, 호출에 묶인 주입기(`@Redirect`, `@WrapOperation` 등)는 소유자와 descriptor가 같을 때만 옮깁니다. 예: `ServerPlayerGameMode.destroyBlock`의 `Block.playerWillDestroy` → 같은 권한 검사 뒤 첫 호출 `ServerPlayer.preventsBlockDrops()`.
   - `AFTER_TRUE_RESULT`: 패치가 호출을 같은 클래스의 `boolean` 도우미로 옮겼고, 도우미는 그 호출을 했을 때만 true를 돌려줌. 취소할 수 없는 `@Inject`(지역 변수 캡처·slice·shift·ordinal 없음, 처리기의 나머지 매개변수는 MixinExtras `@Local` 같은 sugar만)는 도우미 호출마다 MixinExtras `@WrapOperation` 다리가 맡아, 도우미가 true를 돌려준 뒤 처리기를 부릅니다. 예: `Block.destroy` → Forge의 `removeBlock(BlockPos, boolean)`(생존·창작 두 곳). 바닐라에서는 `Block.destroy` 바로 앞, Forge에서는 그 호출 뒤(도우미 안에서 `Block.destroy`가 먼저)라는 순서 차이가 남습니다.
   - 패치가 람다 매개변수 순서를 바꾼 `@Inject`(각 타입이 한 번씩)는 매개변수를 더하기만 한 경우처럼 다리로 원래 매개변수를 넘깁니다(`SimpleJsonResourceReloadListener.lambda$scanDirectory$0`).
   - Fabric mixin이 더하는 인터페이스의 default 메서드를 Forge 패치의 인터페이스도 같은 클래스에 주면 JVM이 그 메서드 호출을 모두 거부합니다("Conflicting default methods"). 그 클래스는 호스트의 default를 씁니다(mixin에 호스트 인터페이스의 default를 부르는 메서드를 더함). 하위 클래스의 재정의는 두 로더 모두 그대로 이깁니다. 예: `ItemStack.getCraftingRemainder()`(`IForgeItemStack`·`FabricItemStack`), `Item.getCraftingRemainder(ItemStack)`(`IForgeItem`·`FabricItem`), 두 default는 같은 바닐라 동작입니다.
   - 확인: `HostPatchAdjustmentsTest`, `ForgeInjectionPointsTest`(26.2-65.1.5와 26.1-62.0.9의 패치된 클라이언트 jar), `forgeFabricApiGameTest`.

## 고르지 않은 것

- **Forge API를 다시 구현:** 패치 1394개와 이벤트의 의미를 추측해야 합니다(AGENTS.md 원칙 6).
- **ModLauncher·securemodules를 그대로 실행:** ModLauncher의 `TransformingClassLoader`가 게임 클래스를 소유합니다. Fabric 모드·Poly 모드와 같은 클래스를 나누고 Poly 모드 로더 변환 규칙을 같은 순서로 적용할 수 없습니다(설계서 §15.4의 "게임 클래스 소유권" 위험). 바꿀 수 있는 지점도 없습니다(`Launcher.run`이 구체 클래스로 클래스로더를 만듦).
- **`fmlloader` 상태 클래스만 대체(처음 제안):** 위 배경처럼 `fmlcore`·`javafmllanguage`·Forge가 모듈 레이어를 직접 씁니다. 이것까지 바꾸면 Forge 자신의 코드를 고치는 셈이 됩니다.

## 결과와 위험

- Forge 모드가 Forge에서와 같은 패치·이벤트·로딩 단계로 실행될 수 있습니다.
- 게임 클래스로더에 모듈 정의가 생깁니다. Forge가 없을 때의 동작은 바뀌지 않아야 합니다(기존 시나리오로 확인).
- ModLauncher 계약을 Poly 모드 로더가 다시 구현하므로, FML이 기대하는 순서와 다르면 Forge가 다르게 동작할 수 있습니다. 순서는 ModLauncher 소스를 읽어 정하고, Forge만 넣은 시작(G3)과 실제 모드(G6)로 확인합니다.
- 패치된 jar에서는 Poly 모드 기능 일부가 꺼질 수 있습니다(5번).
- Forge 라이브러리 버전이 Poly 모드 로더 라이브러리와 겹칩니다(ASM 9.10.1 대 9.11.1, night-config 대 tomlj). 공유 라이브러리 정책(ADR-0005)을 다시 봐야 합니다.
- 라이선스: Forge(LGPL-2.1), FML·ModLauncher(LGPL-3.0)는 받아서 쓰기만 하고 코드를 복사하지 않습니다. 재배포 조건은 채택 전에 확인합니다.
