# Minecraft 26.3 지원 (2026-10-24 작성, 2026-10-25 시작, 1.2.0에서 지원)

[`ROADMAP.md`](ROADMAP.md) 4단계(Minecraft 버전 확장, `1.0.0` 이후)의 첫 버전인 26.3을 시작할 때 쓸 자료입니다. **2026-10-25에 시작했습니다.** 모듈 `poly-version-adapter-v26_3`·`poly-game-v26_3`을 만들고 26.3을 `gradle/minecraft-versions.properties`의 `experimental`에 넣었습니다(ADR-0019의 실험 버전과 같은 취급: 빌드·테스트하고 `-PpolyMinecraft=26.3`으로 실행하지만 설치 프로그램·SDK·웹사이트에는 나오지 않습니다). `supported`로 옮기는 것은 아래 "지원으로 옮기는 조건"을 채운 뒤입니다. 작업은 `minecraft-26.3` 브랜치에서 합니다.

시작할 때 가장 먼저 `minecraftVersionDiff`를 다시 돌려 이 문서의 숫자를 고칩니다. 그때는 26.3 핫픽스(26.3.x)가 나왔을 수 있으니, 가장 새로운 26.3.x를 대상으로 삼습니다.

```powershell
.\gradlew.bat minecraftVersionDiff "-Pfrom=26.2" "-Pto=26.3"
# 보고서: build/reports/minecraft-version-diff/26.2-to-26.3.md
```

## Mojang 배포 (2026-10-24 확인, 버전 목록과 26.2·26.3 버전 JSON)

- 26.3 정식판은 2026-09-15에 나왔습니다. 핫픽스는 아직 없습니다. 26.4는 스냅숏 5(2026-10-20)까지 나왔습니다.
- Java는 그대로 25입니다(`java-runtime-epsilon`). `gradle/minecraft-versions.properties`에 `java.26.3=25`가 이미 있습니다.
- 메인 클래스는 그대로 `net.minecraft.client.main.Main`입니다. 에셋 색인은 32에서 34로 바뀌었습니다. 공식 매핑 파일은 없습니다(26.x는 난독화하지 않음).
- 라이브러리:
  - LWJGL은 3.4.1에서 3.4.3으로 올랐습니다.
  - **`lwjgl-glfw`와 `lwjgl-tinyfd`가 빠지고 `lwjgl-sdl`이 들어왔습니다.** 창과 입력이 GLFW에서 SDL로 바뀌었습니다.
  - LWJGL의 Windows x86(32비트) 네이티브가 빠졌습니다.
  - authlib는 9.0.75에서 10.0.77로, jtracy는 1.0.37에서 1.14.38로 올랐습니다. netty 4.2.16, brigadier 1.3.11, joml 1.10.9로도 바뀌었습니다.
- 클라이언트 JVM 인수:
  - 빠짐: `--sun-misc-unsafe-memory-access=allow`
  - 더해짐: `-XX:StackShadowPages=32`, `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`

## 로더의 26.3 빌드 (2026-10-24 확인)

| 로더 | 26.3 빌드 | 확인한 곳 |
|---|---|---|
| Fabric | Loader의 게임 목록에 26.3, Fabric API `0.165.0+26.3` 등 8개 | `meta.fabricmc.net`, Modrinth |
| MinecraftForge | `26.3-66.0.12` ~ `26.3-66.1.0` | Forge Maven 메타데이터 |
| NeoForge | `26.3.0.93` ~ `26.3.0.95` | NeoForged Maven 메타데이터 |
| Quilt | Quilt 메타데이터의 게임 목록에 26.3 | `meta.quiltmc.org` |

Modrinth에서 그 버전을 표시한 모드 수입니다. 두 버전을 모두 표시한 모드는 양쪽에 셉니다.

| 로더 분류 | 26.2 | 26.3 |
|---|---|---|
| Fabric | 13,282 | 8,326 |
| NeoForge | 6,886 | 4,703 |
| Quilt | 3,445 | 2,529 |
| Forge | 3,426 | 2,448 |

## 26.2 모듈과의 차이 (`minecraftVersionDiff`, 2026-10-25 다시 실행)

`poly-version-adapter-v26_2`와 `poly-game-v26_2`가 쓰는 게임 클래스 469개, 필드·메서드 1,007개를(1.1.0의 변경으로 2026-10-24보다 늘었음) 26.3 클라이언트 jar와 비교했습니다.

- 26.3에 없는 클래스: 24개
- 찾을 수 없는 필드·메서드: 64개
- 바이트가 바뀐 클래스: 206개
- 클라이언트 jar 전체(26.2 10,952개 → 26.3 11,383개): 980개 더해짐, 549개 빠짐, 3,188개 바뀜

판정은 **새 모듈이 필요함**입니다. 핫픽스로 26.2 모듈에서 실행할 수 없습니다.

이 비교가 보지 못하는 것이 있습니다.
- `javac`가 인라인한 상수(`static final`)
- 변환 규칙의 멤버 대상(문자열)

이 둘은 26.3 모듈을 만든 뒤 컴파일과 `BuiltInRulesTest`로 확인합니다.

## 영향받는 영역

| 영역 | 26.3에서 바뀐 것 | 26.2 게임 계층 파일 | 공개 API에 미치는 영향 |
|---|---|---|---|
| 렌더링(모드 셰이더 파이프라인, GUI 그리기, 컨테이너 화면) | `com.mojang.blaze3d`의 파이프라인·장치·버퍼(`RenderPipeline`, `BindGroupLayout`, `GpuDevice`, `GpuBuffer`, `RenderPass`, `VertexFormat`, `CompareOp` 등)와 OpenGL·Vulkan 백엔드가 빠지고 `com.mojang.renderpearl`(`api/pipeline`, `api/device`, `backend/opengl`, `backend/vulkan`)이 들어옴. `GuiGraphicsExtractor.blit`·`blitSprite`의 `RenderPipeline` 판, `RenderPipelines.GUI_TEXTURED`, `RenderSetup.builder` 없음 | `client/ModShaderPipelines`, `client/ClientHooks`, `client/PolyContainerScreen` | 공개 API에는 Minecraft 타입이 없으므로 게임 계층만 다시 씁니다. **가장 큰 일**입니다 |
| 키 바인딩 | 키 코드가 GLFW 값에서 SDL 스캔코드로 바뀜. `InputConstants.KEY_A` 65→4, `KEY_Z` 90→29, `KEY_F6` 295→63, `KEY_F10` 299→67, `KEY_ESCAPE` 256→41. 26.3 jar에는 GLFW를 언급하는 클래스가 없음 | `client/ClientHooks` | **결정이 필요합니다.** 아래 "키 코드" |
| 월드 생성 | `DensityFunction`이 `levelgen.densityfunction`(약 120개 클래스)으로 다시 짜임. `FeaturePlaceContext`, `NoneFeatureConfiguration`, `KeyDispatchDataCodec`, `BuiltInRegistries.FEATURE`, `Feature(Codec)` 생성자 없음. `FeatureTypes`, `FeaturePlacer`가 새로 생김 | `worldgen/CodeWorldgen` | 코드로 만드는 피처·밀도 함수 구현을 다시 씁니다 |
| 몹 스폰 | `Biome.getMobSettings`, `MobSpawnSettings.getMobs`, `SpawnerData(EntityType, int, int)`, `NaturalSpawner.spawnMobsForChunkGeneration` 없음 | `Views`, `worldgen/PolyChunkGenerator`, `WorldgenModifier` | 스폰 수정 API의 구현을 고칩니다 |
| 엔티티·플레이어 | `Entity.hurtMarked`, `Player.drop(ItemStack, boolean)` 없음 | `Views` | 구현을 고칩니다 |
| 리소스 다시 읽기 | `ResourceManager.listResources(String, Predicate)` 없음 | `ModReloadListener` | 구현을 고칩니다 |
| 블록 엔티티 렌더러 | `PoseStack.mulPose(Quaternionfc)` 없음 | `client/PolyBlockEntityRenderer` | 구현을 고칩니다 |
| 후처리 효과 | `GameRenderer.clearPostEffect`, `currentPostEffect` 없음 | `client/ClientHooks` | 구현을 고칩니다 |

이 표는 클래스·멤버가 없어진 곳만 담았습니다. 바뀐 클래스와 변환 규칙의 대상은 모듈을 만든 뒤 확인합니다.

## 키 코드

공개 API `dev.polymodloader.api.client.KeyCodes`(`PREVIEW`)와 `NativeDeclarations`의 기본 키는 **GLFW 키 코드**입니다. 26.1·26.2의 게임이 쓰는 값이라 게임 계층이 그대로 넘겨 왔습니다.

`KeyCodes`의 필드는 `static final int` 상수입니다. 그래서 모드를 컴파일할 때 값이 모드 안에 박히고, API의 값을 바꿔도 이미 빌드된 모드에는 반영되지 않습니다.

방향은 두 가지입니다. 26.3을 시작할 때 ADR로 정합니다.

1. **GLFW 값을 Poly 모드 로더의 키 코드로 고정합니다(권장).** 게임 계층이 그 버전의 게임 키 코드로 바꿉니다. 26.1·26.2는 그대로 넘기고, 26.3은 GLFW→SDL 스캔코드 표를 둡니다. 이미 빌드된 모드가 다시 빌드하지 않고 26.3에서도 같은 키를 받습니다. 표에 없는 값은 "키 없음"으로 두고 진단합니다.
2. **새 API를 만듭니다.** 키 이름(`"key.keyboard.a"`)이나 열거형으로 받습니다. 기존 상수는 폐기로 표시합니다. 이미 빌드된 모드는 1번처럼 바꿔 주지 않으면 26.3에서 엉뚱한 키를 받습니다.

어느 쪽이든 `KeyCodes`의 JavaDoc에 "게임 버전의 키 코드가 아니라 Poly 모드 로더의 키 코드"라고 적고, 다른 키 코드를 받는 API(키 입력 이벤트 등)가 있으면 같은 규칙을 씁니다.

## 실행·설치

- 클라이언트 실행 인수는 버전 JSON에서 읽으므로(`MinecraftDistributionInstaller`, `LauncherProfileInstaller`) 26.3의 새 인수를 그대로 따릅니다.
- 전용 서버의 JVM 인수는 고정값입니다. `--sun-misc-unsafe-memory-access=allow`와 `--enable-native-access=ALL-UNNAMED`를 `MinecraftDistributionInstaller`(개발 실행)와 `poly-installer`의 `ServerInstaller`가 씁니다. 26.3 서버에 필요한 인수(`--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` 등)를 실제 실행으로 확인해 버전별로 둡니다.
- 설치 프로그램의 버전 목록에는 `supported`와 핫픽스가 자동으로 들어갑니다. 26.3을 `supported`에 넣기 전에는 설치 프로그램이 26.3을 보여 주지 않습니다.

## 로더 브리지

- 각 로더의 26.3 빌드는 이미 있습니다(위 표). 브리지가 버전마다 고정한 값을 26.3용으로 더합니다. 예: Forge·NeoForge의 버전과 파일 해시, Forge 패치가 옮긴 주입 지점(`ForgeInjectionPoints`), 버전별 테스트 모드의 `mods.toml` 범위.
- 묶음 `poly-bootstrap/compat/mod-corpus-26.3.json`을 `modCorpus`로 만듭니다.
- 네 로더의 완전 지원 기준(클라이언트, 서버, 설치 프로필, 런타임 기능, 빠지는 모드 점검, 성능)을 26.3에서도 다시 확인합니다. `1.0.0` 이후이므로 26.3을 `supported`에 넣는 릴리스부터는 네 로더 모두 26.3에서 같은 상태여야 합니다.

## 시작할 때 순서

1. `minecraftVersionDiff`를 가장 새로운 26.3.x로 다시 돌리고 이 문서를 고칩니다.
2. 키 코드 ADR을 씁니다.
3. `newMinecraftVersion -Pfrom=26.2 -Pto=<26.3.x>`로 모듈을 만듭니다. Java가 25 그대로면 `-Pjava`는 필요 없습니다.
4. 영역별로 게임 계층을 고칩니다. 렌더링이 가장 크므로 따로 단계를 나눕니다. 변환 규칙은 `javap`로 대상을 확인하고 `BuiltInRulesTest`로 검사합니다.
5. 헤드리스 검증을 합니다: `check`, `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests`, `compatibilityMatrix`(`-PpolyMinecraft=26.3`).
6. 로더 브리지와 26.3 묶음, 네 로더의 기준을 확인합니다.
7. 창이 열리는 검증(`clientWorldReloadTest`, 묶음 클라이언트, 런처 프로필)은 마지막에 하나씩 합니다(`AGENTS.md`).
8. `default`를 26.3으로 바꿀지 정하고, 문서 사이트 버전을 추가합니다([`new-minecraft-version.md`](../contributing/new-minecraft-version.md) 4·5단계).

## 진행 상황 (2026-10-25)

한 것:

- 26.3 핫픽스가 아직 없음을 Mojang 버전 목록에서 확인했습니다(최신 정식판 26.3, 스냅숏 26.4-snapshot-5). 대상은 26.3입니다.
- 키 코드: [ADR-0029](../adr/0029-poly-key-codes.md)로 1번(GLFW 값 고정, 게임 계층이 변환)을 채택했습니다. 26.3 게임 계층의 `GameKeyCodes`가 두 버전의 `InputConstants$Type` 키 이름표를 짝지어 만든 GLFW→SDL 스캔코드 표(119개)를 씁니다. 키패드 소수점은 물리 키인 `keypad.period`(99)로, F25는 26.3에 없어 "키 없음"(`POLY-CLIENT-008` 경고)입니다.
- `newMinecraftVersion -Pexperimental=true`로 모듈을 만들고 컴파일 오류 79개를 고쳤습니다.
  - 엔티티: `hurtMarked` → `syncVelocity`, `Player.drop(…, Prediction.SERVER_ONLY)`(서버가 준 아이템이므로 클라이언트 예측 아님).
  - 몹 스폰: 26.3은 바이옴의 스폰 목록을 환경 속성 `EnvironmentAttributes.NATURAL_MOB_SPAWNS`(모디파이어 `overlay`, 인수 `MobSpawnSettings`)로 둡니다. `WorldgenModifier.applySpawns`는 그 항목에 모드 스폰을 더한 새 속성 맵을 바이옴에 넣고, `WorldView.naturalSpawns`는 게임이 스폰할 때와 같이 `level.environmentAttributes()`를 읽습니다. 코드로 만든 바이옴의 JSON(`GeneratedDataPack`)도 같은 형식이고, 게임의 `the_void.json`처럼 모든 스폰 그룹을 적어 선언한 스폰만 갖습니다.
  - 월드 생성: `ChunkGenerator.fillFromNoise`·`buildSurface`·`applyCarvers`가 `buildTerrain` 하나로 합쳐졌습니다. `Feature`는 설정을 품은 인터페이스가 되어(레지스트리 `FEATURE_TYPE`, 데이터 폴더 `worldgen/feature`, `config` 없이 평평한 JSON) 코드 피처는 게임의 `NoOpFeature`처럼 `MapCodec.unit(this)`입니다. 밀도 함수는 `levelgen.densityfunction`의 컴파일되는 샘플러 구조가 되어, 코드 밀도 함수는 자식 없는 세 축 잎 노드(`sampleVolumeNaive`)입니다.
  - 렌더링: 대부분은 `com.mojang.blaze3d.*` → `com.mojang.renderpearl.api.*` 패키지 이동입니다. `BindGroupLayouts.MATRICES_PROJECTION` → `DYNAMIC_TRANSFORMS`+`PROJECTION`, 샘플러는 `UniformType.COMBINED_IMAGE_SAMPLER` 유니폼, `precompilePipeline(..).isValid()` → `RenderSystem.getCompiledPipeline`(실패하면 예외). `RenderPass`가 인터페이스가 되어 `poly:client_shader_uniforms`는 유일한 구현 `FrontendRenderPass.setPipeline(CompiledRenderPipeline)`을 고치고, 유니폼 버퍼는 파이프라인 위치 문자열(`FrontendRenderPipeline.name()`)로 찾습니다.
  - 후처리: 26.2의 한 칸짜리 후처리 효과는 26.3의 `spectatedEntityPostEffect`입니다(`setSpectatedEntityPostEffect`/`clearSpectatedEntityPostEffect`).
  - `PoseStack.mulPose(Quaternionfc)` → `rotate(Quaternionfc)`(바이트코드가 같음). 반사로 찾는 멤버 14개는 26.3 jar에 모두 있습니다.
- 검증(1차): `check` 통과(774개, 실패 0, 건너뜀 15), 26.3 게임 계층 테스트 28개 통과(`BuiltInRulesTest`가 클라이언트·서버의 모든 내장 규칙을 실제 26.3 클래스에 적용, NeoForge 패치 jar 2개는 건너뜀), `serverBootSmokeTest -PpolyMinecraft=26.3` 통과(EULA 확인까지).

정한 것(2026-10-25, 사용자 결정):

1. **여러 버전을 돕는 모드의 데이터·리소스는 바닐라 `pack.mcmeta` 오버레이로.** 모든 버전의 게임 계층 `ModPacks`가 모드 jar에 `pack.mcmeta`가 있으면 게임의 `Pack.readPackMetadata`로 그 버전의 팩 형식에 맞는 오버레이를 고릅니다(없으면 예전처럼, 읽지 못하면 `POLY-RESOURCE-010` 경고). 한 `pack.mcmeta`가 리소스·데이터 팩에 함께 쓰이고 범위는 팩 종류별 형식 번호로 견주므로(26.3: 리소스 97, 데이터 121) `assets/`용과 `data/`용 오버레이를 따로 둡니다. `ModPacksTest`가 게임의 코덱으로 서버는 데이터 오버레이, 클라이언트는 에셋 오버레이를 고르는지 확인합니다. 문서: `NATIVE_API.md` 2절.
2. **모드 셰이더도 오버레이로.** 게임 계층이 GLSL을 고쳐 쓰지 않습니다. 26.2 형식 셰이더만 든 모드는 26.3에서 `POLY-CLIENT-006`으로 그 사각형을 건너뜁니다. `ModShaderSourcesTest`가 26.3의 `GlslCompiler`(shaderc)와 클라이언트 jar의 include로 예제 셰이더를 창 없이 컴파일합니다: 26.3 형식 5개는 컴파일되고, 26.2 형식은 실패합니다.

예제 `native-showcase-mod`:

- `pack.mcmeta`에 오버레이 `poly_26_3`(데이터 121: `worldgen/feature/glow_ore.json`, 26.3 형식 `noise_settings/waves.json`)와 `poly_26_3_assets`(리소스 97: `#include`·`layout(location)` 형식의 `glow`·`pulse`·`sepia` 셰이더)를 둡니다.
- 26.3의 게임 테스트 서버 월드는 사막입니다(프리셋 `flat_all_dimensions`, 26.2까지는 평지 `flat`). 골렘이 평원과 사막에 자연 스폰하고, `natural_spawns_in_plains`는 그 자리 바이옴의 게임 스폰(평원 소, 사막 낙타)이 남는지 봅니다.

검사 줄: 26.3은 `MinecraftServer.saveAllChunks`에서 "ThreadedAnvilChunkStorage: All dimensions are saved"를 더 기록하지 않습니다. 모든 버전의 게임 계층이 `stopServer` 끝(`poly:server_stopped`, 월드를 저장하고 닫은 뒤)에 "[Poly Mod Loader] Server stopped after saving and closing its worlds"를 기록하고, `poly-bootstrap`의 검사는 26.2까지는 게임의 줄을, 26.3부터는 이 줄을 봅니다(`worldsSavedLine`).

검증(2차):

- 26.3: `worldCreateSaveTest` 통과, `nativeApiGameTests` 62개 통과, `serverBootSmokeTest` 통과. `compatibilityMatrix`(`--continue`)는 17개 시나리오 중 NeoForge 시나리오 `neoForgeEarlyService` 하나만 "Poly Mod Loader has no NeoForge release for Minecraft 26.3"로 실패합니다(`poly-bridge-forge`의 `neoforge-releases.properties`에 26.3이 없음, 아래 다음 일). Quilt 시나리오 3개는 처음에 테스트 모드의 `quilt.mod.json`이 Minecraft를 `26.1.x`·`26.2.x`로 적어 막혔고, 루트 빌드가 26.2 계열이 아닌 버전으로 빌드할 때 `"26.2.x"`를 `"=<버전>"`으로 바꾸게 해 통과합니다(26.1에서도 다시 통과).
- 회귀: 26.2·26.1에서 `worldCreateSaveTest`, `nativeApiGameTests`(62개), `serverBootSmokeTest` 통과. 마지막 `check` 통과(783개, 실패 0, 건너뜀 15).

로더 브리지와 지원 전환(2026-10-25, 1.2.0):

- 릴리스 표에 Forge `26.3-66.1.0`(recommended)과 NeoForge `26.3.0.98`(최신)을 더했습니다. 설치 파일 SHA-1은 Maven의 `.sha1`과 받은 파일로 다시 계산한 값이 같습니다. 테스트 모드의 범위는 `[26.1,)`처럼 열려 있어 고칠 것이 없었습니다.
- NeoForge for 26.3은 FML 12입니다. FML 12의 `ImmediateWindowHandler.load`는 헤드리스일 때 그래픽 부트스트랩 플러그인을 실행하기 전에 돌아갑니다(FML 11은 실행한 뒤 돌아감). Poly 모드 로더는 NeoForge의 코드를 그대로 실행하므로, `neoForgeEarlyService`와 `serverInstallTest`는 26.3에서 "Found 1 early service jars"와 "Not loading early display in headless mode."를 봅니다.
- 26.3 게임 테스트 서버의 월드는 네더·엔드까지 평지이고, 게임은 평지 네더·엔드를 실험적 설정으로 보아 월드를 열기 전에 백업 확인 화면(`BackupConfirmScreen`)을 띄웁니다(클라이언트 힙에서 확인). 창을 여는 검사(`clientWorldReloadTest`, `launcherProfileTest`, `frameBenchmark`)는 복사한 월드의 네더·엔드를 게임 자신의 노이즈 생성기로 바꿉니다(`StableVanillaDimensions`, `GzipNbt`, 단위 테스트 `StableVanillaDimensionsTest`).
- `serverInstallTest`는 빌드가 실행하는 Minecraft 버전(테스트 모드를 만든 버전)을 설치합니다. 전에는 가장 새로운 지원 버전을 썼는데, 그것이 기본 버전과 같다고 가정했습니다.
- 검증: 26.1·26.2·26.3에서 `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests`(62개), `compatibilityMatrix`(17개 시나리오) 통과, `check` 통과. 26.3에서 `neoForgeWorldTest`, `forgeWorldTest`, `serverInstallTest`(26.2도), 창 `clientWorldReloadTest`(모드 셰이더 2개 컴파일·연결, `ModUniforms` 업로드, 키 바인딩 경고 없음)와 `launcherProfileTest` 통과.

남은 일:

- 26.3 실제 모드 묶음(`mod-corpus-26.3.json`, `modCorpus`)과 네 로더의 완전 지원 점검. 그 전까지 `docs/COMPATIBILITY.md`에서 26.3의 다른 로더 모드는 실제 모드로는 Untested입니다.
- Forge·NeoForge 클라이언트(`forgeClient`, `neoForgeClient`)와 후처리 효과를 26.3 창에서 확인.
- `default`를 26.3으로 바꿀지(묶음 검증 뒤), 문서 사이트의 현재 문서 버전.

## 지원으로 옮기는 조건

- 헤드리스: `check`, `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests`, `compatibilityMatrix`(`-PpolyMinecraft=26.3`)
- 로더 브리지와 26.3 묶음(`mod-corpus-26.3.json`), 네 로더의 완전 지원 기준
- 창: `clientWorldReloadTest`, 모드 셰이더·키 바인딩·후처리 효과 확인, 묶음 클라이언트, 런처 프로필

## 처음 준비할 때 한 것 (2026-10-24)

- `minecraftVersionDiff` 작업을 만들었습니다(build-logic, `MinecraftVersionDiff`·`MinecraftVersionDiffTask`, 단위 테스트 `MinecraftVersionDiffTest`). 26.4나 핫픽스 판단에도 씁니다.
- 위의 사실을 2026-10-24에 확인했습니다. 26.3에서 게임을 실행하지는 않았습니다.
