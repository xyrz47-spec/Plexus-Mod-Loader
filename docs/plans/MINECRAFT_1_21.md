# Minecraft 1.21 지원 준비 (2026-10-24 작성)

[`ROADMAP.md`](ROADMAP.md) 4단계(Minecraft 버전 확장, `1.0.0` 이후)의 레거시 쪽, [ADR-0019](../adr/0019-obfuscated-minecraft-versions.md)(제안)의 **1단계(1.21.11, 1.21.10, 1.20.6)** 를 시작할 때 쓸 자료입니다. 최신 쪽은 [`MINECRAFT_26_3.md`](MINECRAFT_26_3.md)에, 3단계의 1.16.5는 [`MINECRAFT_1_16_5.md`](MINECRAFT_1_16_5.md)에 있습니다.

**1.21.11은 실험 버전입니다(2026-10-24).** `gradle/minecraft-versions.properties`의 `experimental`에 있어서 모듈(`poly-version-adapter-v1_21_11`, `poly-game-v1_21_11`)을 빌드·테스트하고 `-PpolyMinecraft=1.21.11`로 실행할 수 있습니다. **지원 버전이 아닙니다.** 설치 프로그램·SDK·웹사이트에 나오지 않고, 지원한다고 쓰지 않습니다(ADR-0019 8번). 26.1 이전 버전은 난독화되어 있어서, 게임 jar를 Mojang 이름으로 다시 매핑한 jar로 컴파일·실행합니다(아래 "다시 매핑", "1.21.11 서버 PoC").

시작할 때 가장 먼저 다음 명령으로 이 문서의 숫자를 다시 확인합니다.

```powershell
.\gradlew.bat minecraftVersionDiff "-Pfrom=26.1" "-Pto=1.21.11"
# 보고서: build/reports/minecraft-version-diff/26.1-to-1.21.11.md
```

난독화된 버전은 공식 매핑(`downloads.client_mappings`)으로 이름을 번역해 비교하므로, 보고서의 이름은 모두 Mojang 이름입니다. 매핑은 SHA-1을 확인해 받고 Gradle 캐시에만 둡니다. `MinecraftFiles/<버전>/client.txt`가 있으면 그 파일을 같은 확인 뒤에 씁니다. 커밋하거나 배포하지 않습니다(ADR-0019 3번).

## 대상 버전과 사실 (2026-10-24 확인)

| | 1.21.11 | 1.21.10 | 1.20.6 | 참고: 26.1 |
|---|---|---|---|---|
| 출시 | 2025-12-09 | 2025-10-07 | 2024-04-29 | 2026-03-24 |
| 버전 JSON의 Java | 21 | 21 | 21 | 25 |
| 클라이언트 jar 클래스 | 10,291 (모두 매핑에 있음) | | | 10,682 |
| Modrinth Fabric 모드 | 18,035 | | 7,989 | 10,987 |
| Modrinth NeoForge 모드 | 8,206 | | 3,787 | 6,293 |
| Modrinth Forge 모드 | 4,471 | | 4,597 | 3,367 |
| Modrinth Quilt 모드 | 4,430 | | 2,868 | 3,505 |

- Modrinth 수는 그 버전을 표시한 모드 수입니다. 여러 버전을 표시한 모드는 각 버전에 셉니다. **1.21.11은 Poly 모드 로더가 지금 지원하는 어느 버전보다 모드가 많습니다.**
- Poly 모드 로더는 모든 버전을 Java 25로 실행합니다(ADR-0019 5번). 그래서 `newMinecraftVersion`에는 `-Pjava=25`를 씁니다. Java 21용 클래스 파일이 Java 25에서 *돌아가는지*는 실행해 봐야 압니다.
- 1.21.11의 라이브러리는 26.1과 비교하면 LWJGL 3.3.3(26.1은 3.4.1), authlib 7.0.61, lz4-java 1.8.1입니다. 창·입력은 26.1과 같은 GLFW입니다. 그래서 공개 API `KeyCodes`(GLFW 값)를 그대로 쓸 수 있습니다. 26.3의 SDL과 다릅니다.
- 1.21.11 버전 JSON에는 `--sun-misc-unsafe-memory-access=allow`, `--enable-native-access=ALL-UNNAMED`가 없습니다. ADR-0019 5번대로, 이런 인수는 기록된 실패가 있을 때만 버전별로 더합니다.

## 로더의 빌드 (2026-10-24 확인)

| 로더 | 1.21.11 빌드 |
|---|---|
| Fabric | Fabric API `0.141.6+1.21.11` 등 11개 (Modrinth) |
| MinecraftForge | `1.21.11-61.2.4`까지 (Forge Maven) |
| NeoForge | `21.11.48`까지 (NeoForged Maven) |
| Quilt | Quilt 메타데이터의 게임 목록에 1.21.11, 1.21.10, 1.20.6 |

## 26.1 모듈과의 차이 (`minecraftVersionDiff`, 2026-10-24)

비교 대상은 `poly-version-adapter-v26_1`·`poly-game-v26_1`입니다. 지원 버전 가운데 가장 오래되어 1.21.x에 가장 가깝습니다. 이 두 모듈이 쓰는 게임 클래스는 460개, 필드·메서드는 993개입니다.

| 대상 | 없는 클래스 | 찾을 수 없는 멤버 | jar 전체(26.1 기준 Mojang 이름) |
|---|---|---|---|
| **1.21.11** | 12 | 42 | 315개 더 있음, 706개 없음 |
| 1.21.10 | 33 | 119 | |
| 1.20.6 | 86 | 271 | |

- 셋 다 판정은 "다시 매핑한 jar와 자체 모듈이 필요함"입니다.
- 1.21.11이 가장 가까우므로 ADR-0019의 순서대로 1.21.11부터 합니다. 1.21.10은 1.21.11 모듈에서 복사하는 편이 가깝습니다. 1.21.10용 모듈을 따로 둘지, 같은 모듈을 핫픽스처럼 쓸지는 1.21.11 모듈을 만든 뒤 `-Pfrom=1.21.11`로 다시 비교해 정합니다.
- 난독화된 jar는 다시 매핑하면 바이트가 달라지므로 "바뀐 클래스"는 비교하지 않습니다. 컴파일과 `BuiltInRulesTest`로 확인합니다.

### 1.21.11에서 없는 것 (영역별)

| 영역 | 26.1에 있고 1.21.11에 없는 것 | 26.1 게임 계층에서 쓰는 곳 |
|---|---|---|
| GUI 그리기 | `GuiGraphicsExtractor`(26.1의 GUI 그리기 클래스. 1.21.11은 `GuiGraphics`)와 그 `blit`·`blitSprite`·`fill`·`text`·`guiWidth`/`guiHeight`. `Screen.extractRenderState`, `AbstractContainerScreen.extractBackground`와 5인자 생성자 | 클라이언트 화면·컨테이너 화면·타이틀 화면 |
| 렌더 파이프라인 | `ColorTargetState`, `DepthStencilState`, `CompareOp`, `RenderPipeline.Builder.withColorTargetState`·`withDepthStencilState` | 모드 셰이더 파이프라인 |
| 블록·엔티티 렌더 상태 | `BlockModelRenderState`, `BlockModelResolver`, `BlockDisplayContext`, `CameraRenderState`, `LevelRenderState`, `BlockEntityRendererProvider.Context.blockModelResolver`, `MobRenderer.submit`의 26.1 판, `SingleQuadParticle.getLightCoords`, `LightCoordsUtil` | 블록 엔티티 렌더러, 엔티티 렌더러, 파티클 |
| 저장 데이터 | `SavedDataStorage`, `ServerLevel.getDataStorage`의 반환 타입, `SavedDataType`의 4인자 생성자 | 월드 저장 데이터 API |
| 아이템·블록 | `ItemStackTemplate`, `ItemParticleOption(ParticleType, ItemStackTemplate)`, `ItemStack.is(Object)`, `BlockState.is(Object)` | 아이템·블록 조회, 파티클 |
| 플레이어·채팅 | `Player.sendSystemMessage`, `LocalPlayer.sendOverlayMessage`, `ServerPlayer.interactOn(Entity, InteractionHand, Vec3)` | 메시지 API, 상호작용 이벤트 |
| 좌표 | `ChunkPos.x()`, `ChunkPos.z()`(26.1에서 메서드, 1.21.11에서는 다른 형태) | 청크 좌표를 쓰는 곳 |

정확한 목록은 보고서에 있습니다. 위 "쓰는 곳"은 멤버 이름으로 짐작한 영역입니다. 시작할 때 `grep`으로 파일을 확인합니다.

## 다시 매핑 (`remapMinecraft`, 2026-10-24)

```powershell
.\gradlew.bat remapMinecraft "-Pminecraft=1.21.11"                    # 클라이언트 jar
.\gradlew.bat remapMinecraft "-Pminecraft=1.21.11" "-Pside=server"    # 서버 jar (1.18 이후 bundler)
# 다시 매핑한 jar: ~/.gradle/caches/poly-minecraft/versions/1.21.11/<side>-1.21.11-mojang.jar (캐시에만 둠)
# 보고서: build/reports/minecraft-remap/1.21.11-<side>.md
```

설치기(`MinecraftDistributionInstaller`)는 공식 매핑이 있는 버전이면 같은 캐시 jar를 컴파일과 실행의 게임 jar로 씁니다(`RemappedGameJar`).

- 다시 매핑 코드는 build-logic의 `MojangJarRemapper`입니다. ASM `ClassRemapper`와 게임 jar의 계층을 아는 `Remapper`를 씁니다(ADR-0019 4번). 하위 클래스를 거쳐 부르는 상속 멤버와 람다(`LambdaMetafactory`)의 인터페이스 메서드 이름도 바꿉니다.
- 결과는 늘 같은 바이트입니다(항목 정렬, 고정 시각). 작업은 두 번 만들어 SHA-1이 같은지 확인합니다. 원본 jar SHA-1, 매핑 SHA-1, 다시 매핑 코드의 판(`REVISION`)이 같으면 캐시를 다시 씁니다.
- 서명 파일(`MOJANGCS.SF`·`.RSA`)은 지우고, 매니페스트의 주 속성, `version.json`, 리소스는 남깁니다.
- 자체 검사(`RemappedJarCheck`, `RemappedJarLoadCheck`)는 세 가지를 봅니다. 어느 하나라도 걸리면 작업이 실패합니다.
  - 자기 이름으로 매핑되지 않은 기본 패키지 클래스나 참조가 남았는지, 같은 이름·descriptor의 멤버가 둘인지 봅니다.
  - 게임 클래스로 가는 필드·메서드 참조마다 계층(jar, 라이브러리, JDK)에서 선언한 곳을 찾습니다.
  - 별도 JVM(Java 25)에서 모든 클래스를 초기화 없이 로드하고 링크합니다. `getDeclaredMethods()`가 링크를 일으키므로 검증기가 모든 클래스를 한 번씩 봅니다.
- 테스트(`MojangJarRemapperTest`)는 javac로 만든 작은 난독화 jar로 위 내용을 확인합니다. 일부러 깨뜨린 jar(매핑에서 뺀 클래스, 아무도 선언하지 않는 메서드 참조)를 검사가 잡는지도 확인합니다.

ADR-0019 범위의 모든 버전 결과입니다(다시 매핑 코드 판 2, 프레임 다시 계산). 모두 검사를 통과했고, 프레임을 계산할 때 어디에도 없는 타입은 한 번도 나오지 않았습니다. 다시 매핑 시간은 Gradle 데몬 안에서 한 번 만든 시간이고, Windows 개발 PC에서 측정했습니다(1.21.11은 데몬을 새로 띄운 뒤라 더 깁니다).

| 버전 | 클래스 | 검사한 참조 | 남은 기본 패키지·중복·찾지 못한 참조 | 로드·링크 | 다시 매핑 시간 |
|---|---|---|---|---|---|
| 1.21.11 | 10,291 | 113,826 | 0 · 0 · 0 | 10,290 (+1) | 14.2초 |
| 1.21.10 | 9,969 | 111,319 | 0 · 0 · 0 | 9,968 (+1) | 8.0초 |
| 1.20.6 | 8,206 | 93,845 | 0 · 0 · 0 | 8,205 (+1) | 6.2초 |
| 1.19.4 | 7,331 | 82,542 | 0 · 0 · 0 | 7,330 (+1) | 5.5초 |
| 1.18.2 | 6,399 | 72,483 | 0 · 0 · 0 | 6,398 (+1) | 4.8초 |
| 1.18.1 | 6,288 | 71,413 | 0 · 0 · 0 | 6,287 (+1) | 4.7초 |
| 1.17.1 | 6,113 | 69,470 | 0 · 0 · 0 | 6,112 (+1) | 4.6초 |
| 1.16.5 | 5,445 | 63,645 | 0 · 0 · 0 | 5,445 | 4.2초 |
| 1.16.4 | 5,445 | 63,644 | 0 · 0 · 0 | 5,445 | 4.2초 |
| 1.15.2 | 5,136 | 56,707 | 0 · 0 · 0 | 5,136 | 3.8초 |
| 1.14.4 | 4,976 | 54,342 | 0 · 0 · 0 | 4,976 | 4.1초 |

- "(+1)"은 `com.mojang.blaze3d.platform.MacosUtil`입니다. macOS 전용 라이브러리(`ca.weblite.objc`)를 쓰는데, Windows에서는 받지 않으므로 로드하지 못합니다. jar에 없고 Mojang 이름도 아닌 라이브러리 클래스라서 다시 매핑 때문에 생길 수 없습니다. 그래서 따로 셉니다.
- 1.16.5 이하는 매핑에만 있고 jar에는 없는 클래스가 278~302개 있습니다(`package-info`, 난독화 때 지워진 클래스). 그래서 "매핑의 모든 클래스가 jar에 있다"는 검사 대신 "jar의 모든 클래스가 매핑에 있다"를 봅니다. 11개 버전 모두 jar의 모든 클래스가 매핑에 있습니다.
- **스택 맵 프레임을 다시 계산합니다(`REVISION` 2).** 난독화 도구는 프레임에 살아 있는 지역 변수만 남깁니다. 그래서 메서드 마지막 `return`의 프레임에 지역 변수가 하나도 없을 수 있고, 변환 규칙이 그 자리에 넣은 훅 호출이 `this`나 매개변수를 읽지 못해 `VerifyError`가 납니다(1.21.11 `Entity.load`에서 발견). 다시 계산한 프레임은 javac처럼 그때까지 대입된 지역 변수를 모두 둡니다. 두 타입을 합칠 때는 jar의 클래스, 라이브러리, JDK 순서로 상위 클래스를 찾습니다.
- 서버 jar는 `"-Pside=server"`로 같은 검사를 합니다. 1.21.11 서버: 클래스 7,163개, 참조 81,393개, 모두 로드·링크됩니다.
- 이 검사들로 확인되는 것은 이름 바꾸기와 로드·링크까지입니다. 실제로 실행되는지는 아래 서버 PoC가 확인합니다.

## 1.21.11 서버 PoC (ADR-0019 "First step", 2026-10-24)

`newMinecraftVersion -Pfrom=26.1 -Pto=1.21.11 -Pjava=25 -Pexperimental=true`로 모듈을 만들고 고쳤습니다. ADR-0019의 합격 기준별 결과입니다.

| 기준 | 결과 |
|---|---|
| 1. `server_mappings`를 받아 SHA-1 확인, 커밋 안 함 | 됨. Gradle 캐시에만 있습니다 |
| 2. bundler에서 꺼낸 서버 jar 다시 매핑: 두 번 같은 해시, 기본 패키지 클래스 없음, 서명 파일 제거, `version.json` 유지, 모든 클래스 `initialize=false`로 로드 | 됨(`remapMinecraft "-Pside=server"`) |
| 3. 두 모듈이 다시 매핑한 jar로 컴파일 | 됨. 컴파일 오류 57개를 고쳤습니다(아래). 기능을 빼거나 `unsupported`로 바꾼 것은 없습니다 |
| 4. `poly:server_brand` 등 규칙이 다시 매핑한 jar에서 계획을 통과 | 됨. `BuiltInRulesTest`가 클라이언트·서버 규칙 모두 통과하고, 서버 실행에서 "50 rule(s) accepted, 0 rejected"입니다 |
| 5. `serverBootSmokeTest -PpolyMinecraft=1.21.11`이 Java 25에서 통과, Poly 모드의 서버 이벤트 처리기가 로그를 남김 | 됨. Poly 모드 로딩, 게임 계층 시작, `[minimal_native_mod] Hello …`, Minecraft EULA 확인, 종료 작업까지. **더한 JVM 인수는 없습니다** |
| 6. `check`, `-p build-logic check` 통과, 26.2 검사는 그대로 | 됨. 26.2의 `serverBootSmokeTest`·`worldCreateSaveTest`도 통과합니다 |

고친 것(26.1과 다른 점, 모두 다시 매핑한 jar에서 `javap`로 확인):

- 서버: `ChunkPos.x`/`z`는 메서드가 아니라 필드, `Player.interactOn(Entity, InteractionHand)`(클릭 위치 없음), 메시지는 `displayClientMessage`, `SavedDataType`의 이름은 식별자가 아니라 파일 이름 하나(`poly_<namespace>__<path>.dat`, 두 저장소가 같은 파일이 되면 거부), `ItemParticleOption(ParticleType, ItemStack)`.
- 클라이언트: `GuiGraphicsExtractor` → `GuiGraphics`(`text` → `drawString`, `extractRenderState` → `render`, 컨테이너 화면 `renderBg`), 렌더 상태 패키지 `renderer.state`, 파이프라인 `withDepthTestFunction`·`withDepthWrite`·`withBlend`, 파티클 `getLightColor`, 블록 그리기 `submitBlock(PoseStack, BlockState, …)`.
- 규칙 대상: `Gui.render`, `CreativeModeInventoryScreen.render`·`renderBg`, `TitleScreen.render`의 `drawString`, `GlRenderPass.setPipeline`(`RenderPass`는 1.21.11에서 인터페이스), `GameRenderer.getFov`(1.21.11에는 `Camera.calculateFov`가 없음), `LevelRenderer.submitEntities`의 descriptor.

더 확인한 것:

- `worldCreateSaveTest -PpolyMinecraft=1.21.11`은 **아직 실패합니다.** 예제 모드 `native_showcase`의 `data/native_showcase/dimension_type/pocket.json`이 26.x 형식이라 1.21.11에 없는 환경 속성 `minecraft:visual/ambient_light_color`를 씁니다. 데이터 팩 형식은 버전마다 다르므로 Poly 모드 로더의 버그는 아닙니다. 그 한 줄을 잠시 빼고 돌리면 월드 생성, 예제 모드의 변환 규칙, 게임 테스트, 저장까지 통과했습니다(파일은 되돌렸습니다). 1.21.11 월드는 26.x와 저장 구조가 달라(`level.dat`, 최상위 `region/`) 이 작업의 기대 파일을 버전에 따라 고쳤습니다.
- 클라이언트(창이 뜨는 실행)는 돌리지 않았습니다.
- 실험 버전을 돌릴 때만, Native API만 쓰는 예제 모드 셋의 `"26.*"`에 그 버전을 더해 빌드합니다(루트 `build.gradle.kts`). 원본 파일과 공개 예제는 `"26.*"` 그대로입니다.

## 아직 없는 것

순서대로 적었습니다.

1. **모드의 버전별 데이터.** 위 `pocket.json`처럼 데이터 팩 형식이 다른 버전을 한 모드가 함께 지원하는 방법(Minecraft의 팩 overlay 같은 것)이 Native API에 없습니다. 정하기 전까지 `worldCreateSaveTest`는 1.21.11에서 실패합니다.
2. **Fabric 모드 막기.** 1.21.11의 Fabric 모드는 intermediary 이름으로 컴파일되어 있는데, 지금은 Fabric 브리지가 그대로 읽습니다(테스트 모드 `fabric_hello`는 게임 클래스를 쓰지 않아 실행됨). ADR-0019대로 intermediary를 다루는 ADR 전까지 새 `POLY-COMPAT-` 코드로 막아야 합니다. Forge·NeoForge·Quilt 브리지도 1.21.11용 값이 없습니다.
3. **클라이언트 실행 확인**(`clientWorldReloadTest -PpolyMinecraft=1.21.11`, 창이 뜸).
4. **플레이어 쪽**(ADR-0017):
   - 설치하거나 처음 시작할 때 사용자 PC에서 매핑을 받아 다시 매핑합니다. 다시 매핑 코드는 지금 build-logic 안에만 있으므로 실행 쪽에서도 쓸 수 있는 모듈로 옮길지 정합니다.
   - 런처 프로필이 Java 25를 쓰게 합니다. 자식 버전 JSON의 `javaVersion`이 공식 런처에서 부모 것을 덮어쓰는지는 아직 확인하지 않았습니다.
5. **로더 브리지와 모드 묶음:** 네 로더의 1.21.11 빌드(위 표)로 각 브리지의 버전별 값을 더하고, `mod-corpus-1.21.11.json`을 만듭니다.
6. 1.21.10, 1.20.6으로 넓힙니다.

**결정이 필요한 것:** ADR-0019 3번에 "사용자 PC에서 다시 매핑할 때 매핑 라이선스가 어떻게 적용되는지는 프로젝트 소유자가 판단하고 확인해야 한다"고 적혀 있습니다. 매핑 헤더가 허용하는 것은 개발 목적의 사용입니다. 그래서 지금은 개발자 PC의 캐시에서만 다시 매핑합니다. 플레이어 쪽(4번)을 시작하기 전에 정합니다. PoC 기준은 모두 맞췄지만, ADR-0019를 채택할지는 이 결정과 함께 프로젝트 소유자가 정합니다.
