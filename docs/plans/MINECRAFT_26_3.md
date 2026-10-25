# Minecraft 26.3 지원 준비 (2026-10-24 작성)

[`ROADMAP.md`](ROADMAP.md) 4단계(Minecraft 버전 확장, `1.0.0` 이후)의 첫 버전인 26.3을 시작할 때 쓸 자료입니다. **지금은 준비만 합니다.** `gradle/minecraft-versions.properties`의 `supported`에 26.3을 넣지 않고, `poly-*-v26_3` 모듈도 만들지 않습니다. 넣으면 그때부터 모든 작업과 검증이 26.3에서도 돌아야 하기 때문입니다(ROADMAP "왜 이 순서인가").

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

## 26.2 모듈과의 차이 (`minecraftVersionDiff`, 2026-10-24)

`poly-version-adapter-v26_2`와 `poly-game-v26_2`가 쓰는 게임 클래스 464개, 필드·메서드 999개를 26.3 클라이언트 jar와 비교했습니다.

- 26.3에 없는 클래스: 24개
- 찾을 수 없는 필드·메서드: 64개
- 바이트가 바뀐 클래스: 204개
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

이 표는 클래스·멤버가 없어진 곳만 담았습니다. 바뀐 클래스 204개와 변환 규칙의 대상은 모듈을 만든 뒤 확인합니다.

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

## 지금 한 것

- `minecraftVersionDiff` 작업을 만들었습니다(build-logic, `MinecraftVersionDiff`·`MinecraftVersionDiffTask`, 단위 테스트 `MinecraftVersionDiffTest`). 26.4나 핫픽스 판단에도 씁니다.
- 위의 사실을 2026-10-24에 확인했습니다. 26.3에서 게임을 실행하지는 않았습니다.
