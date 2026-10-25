# Minecraft 1.16.5 지원 준비 (2026-10-24 작성)

[`ROADMAP.md`](ROADMAP.md) 4단계(Minecraft 버전 확장, `1.0.0` 이후)의 레거시 쪽, [ADR-0019](../adr/0019-obfuscated-minecraft-versions.md)(제안)의 **3단계(1.16.5, 1.16.4, 1.15.2, 1.14.4)** 를 시작할 때 쓸 자료입니다. 같은 방식의 1단계 자료는 [`MINECRAFT_1_21.md`](MINECRAFT_1_21.md)에 있습니다.

**지금은 준비만 합니다.** `supported`에 1.16.5를 넣지 않고 모듈도 만들지 않습니다. ADR-0019 7번은 "각 단계는 앞 단계가 검사를 통과한 뒤에만 시작한다"고 정합니다. 그래서 1.16.5는 1단계(1.21.x, 1.20.6)와 2단계(1.19.4, 1.18.x, 1.17.1)가 끝난 뒤입니다. 1단계에서 만드는 다시 매핑 단계와 자체 검사가 여기서도 필요합니다.

클라이언트 jar 다시 매핑과 자체 검사는 이미 1.16.5에서도 통과합니다(`remapMinecraft "-Pminecraft=1.16.5"`, 2026-10-24). 결과는 5,445개 클래스, 검사한 참조 63,645개, 찾지 못한 참조 0개입니다. 모든 클래스가 Java 25에서 로드·링크되고, 다시 매핑에는 4.2초가 걸렸습니다. 결과 표와 방법은 [`MINECRAFT_1_21.md`](MINECRAFT_1_21.md) "클라이언트 jar 다시 매핑"에 있습니다. 게임을 실행해 본 것은 아닙니다.

시작할 때 가장 먼저 다음 명령으로 이 문서의 숫자를 다시 확인합니다.

```powershell
.\gradlew.bat minecraftVersionDiff "-Pfrom=26.1" "-Pto=1.16.5"
# 보고서: build/reports/minecraft-version-diff/26.1-to-1.16.5.md
```

그때 1.17.1이나 1.18.2 모듈이 이미 있으면 `-Pfrom`을 그 버전으로 바꿉니다. 1.16.5에 더 가까워 차이가 훨씬 작게 나올 것입니다(`-Pfrom`은 지원 버전만 받습니다). 매핑은 SHA-1을 확인해 받고 Gradle 캐시에만 둡니다. 커밋하거나 배포하지 않습니다(ADR-0019 3번).

## 대상 버전과 사실 (2026-10-24 확인)

| | 1.16.5 | 참고: 1.21.11 | 참고: 26.1 |
|---|---|---|---|
| 출시 | 2021-01-14 | 2025-12-09 | 2026-03-24 |
| 버전 JSON의 Java | 8 (`jre-legacy`, class 52) | 21 | 25 |
| 클라이언트 jar 클래스 | 5,445 (매핑에 5,747개) | 10,291 | 10,682 |
| LWJGL | 3.2.1/3.2.2, `classifiers` natives | 3.3.3, 별도 artifact | 3.4.1 |
| 서버 jar | 라이브러리를 품은 일반 jar, `net.minecraft.server.Main` | bundler | bundler |
| log4j / slf4j / gson | 2.8.1 / 없음 / 2.8.0 | 2.25.2 / 2.0.17 / 2.13.2 | 2.25.2 / 2.0.17 / 2.13.2 |
| 게임 테스트 | `GameTestRegistry`, `GameTestServer` (`gametest.Main` 없음) | `gametest.Main` | `gametest.Main` |
| Modrinth Fabric 모드 | 3,038 | 18,035 | 10,996 |
| Modrinth Forge 모드 | **4,261** | 4,471 | 3,371 |
| Modrinth Quilt 모드 | 878 | 4,430 | 3,508 |

- Modrinth 수는 그 버전을 표시한 모드 수입니다. NeoForge는 1.20.1부터 있으므로 1.16.5에는 없습니다. Modrinth에서 1.16.5·NeoForge로 표시된 513개는 표시 오류로 보고 세지 않습니다.
- **1.16.5의 가치는 Forge 모드에 있습니다.** Forge 모드 수가 26.1보다 많고, 오래된 모드팩 대부분이 Forge입니다(ADR-0019 위험 "Fabric ecosystem").
- 버전 JSON의 `logging`은 `client-1.12.xml`(Mojang이 Log4Shell 뒤에 고친 설정)을 가리킵니다. log4j 2.8.1은 Log4Shell 버전이므로 모든 실행이 이 설정이나 `%msg{nolookups}`를 써야 합니다. 개발용 `log4j2-dev.xml`은 이미 `%msg{nolookups}`입니다.
- 버전 JSON의 JVM 인수에는 Windows 10에서 `-Dos.name=Windows 10 -Dos.version=10.0`이 붙습니다. 26.1의 `--sun-misc-unsafe-memory-access=allow`, `--enable-native-access=ALL-UNNAMED`, natives 경로 인수는 없습니다.

## 로더의 빌드 (2026-10-24 확인)

| 로더 | 1.16.5 빌드 | 비고 |
|---|---|---|
| Fabric | Fabric API `0.42.0+1.16`까지 (Modrinth), Fabric 메타데이터에 1.16.5 있음 | 모드는 intermediary 이름으로 컴파일됨 |
| MinecraftForge | `1.16.5-36.2.42`까지, 권장 `36.2.34` (Forge Maven·promotions) | 모드는 SRG 이름으로 컴파일됨. ModLauncher 8 세대 |
| NeoForge | 없음 | NeoForge는 1.20.1부터 |
| Quilt | Quilt 메타데이터의 게임 목록에 1.16.5 있음. Quilt Standard Libraries(`qsl`)의 1.16.5 빌드는 Modrinth에 없음 | |

## 26.1 모듈과의 차이 (`minecraftVersionDiff`, 2026-10-24)

비교 대상은 `poly-version-adapter-v26_1`·`poly-game-v26_1`입니다. 이 두 모듈이 쓰는 게임 클래스는 460개, 필드·메서드는 993개입니다.

| 대상 | 없는 클래스 | 찾을 수 없는 멤버 | 라이브러리 변화 |
|---|---|---|---|
| **1.16.5** | **200** | **539** | 64 |
| 참고: 1.20.6 | 86 | 271 | |
| 참고: 1.21.11 | 12 | 42 | 10 |

- 판정은 "다시 매핑한 jar와 자체 모듈이 필요함"입니다.
- 쓰는 클래스의 43%가 없습니다. `poly-game-v26_1`의 Java 파일 62개 중 53개가 없는 클래스를 import합니다(`import` 줄로 센 값). **26.1 게임 계층을 복사해 고치는 것이 아니라, 대부분 다시 쓰는 일입니다.** 버전 어댑터 3개 파일은 없는 클래스를 import하지 않지만, 변환 규칙의 대상 멤버는 이 비교에 들어가지 않습니다.
- 난독화된 jar는 다시 매핑하면 바이트가 달라지므로 "바뀐 클래스"는 비교하지 않습니다.

### 1.16.5에서 없는 것 (영역별)

| 영역 | 26.1에 있고 1.16.5에 없는 것 | 1.16.5에 있는 것 (매핑에서 확인) |
|---|---|---|
| 레지스트리·식별자 | `BuiltInRegistries`, `Registries`, `Holder`·`HolderSet`·`HolderLookup`, `TagKey`, `Identifier`, `RegistryAccess$Frozen` | `Registry`의 정적 필드(`Registry.BLOCK`·`ITEM`·`ENTITY_TYPE`은 `DefaultedRegistry`, `BLOCK_ENTITY_TYPE`), `BuiltinRegistries`, `ResourceLocation`, `RegistryAccess` |
| 아이템 데이터 | `DataComponentType`, `DataComponents`, `ItemStackTemplate`, `TooltipDisplay`, `Item$TooltipContext`, `ItemDisplayContext` | NBT: `ItemStack.getTag()`·`getOrCreateTag()`가 `CompoundTag`를 돌려줌 |
| 크리에이티브 탭 | `CreativeModeTab$Builder`, `CreativeModeTabs` | `CreativeModeTab`(하위 클래스로 만드는 방식) |
| 렌더링 | `blaze3d.pipeline`·`buffers`·`systems.GpuDevice`·`RenderPass`·`CommandEncoder` 등 16개, `RenderPipelines`, `ShaderManager`, 렌더 상태(`*RenderState`), `SubmitNodeCollector`, `rendertype.RenderType` | `RenderSystem`, `PoseStack`, `client.renderer.RenderType`. 코어 셰이더(`ShaderInstance`, 1.17부터)는 없음 |
| 엔티티 모델·애니메이션 | `ModelLayerLocation`, `LayerDefinition`·`MeshDefinition`·`PartDefinition`·`CubeListBuilder`·`PartPose`, `client.animation.*` 10개, `AnimationState` | `ModelPart`를 코드에서 만드는 방식 |
| GUI | `GuiGraphicsExtractor`, `Button$Builder`, `Tooltip`, `MouseButtonEvent`, `KeyMapping$Category`, `DeltaTracker` | `GuiComponent` |
| 네트워크 | `StreamCodec`·`ByteBufCodecs`, `CustomPacketPayload` 계열, `protocol.common.*` 패킷, 설정 단계(`ClientConfigurationPacketListenerImpl`, `ServerConfigurationPacketListenerImpl`, `ConfigurationTask`, `CommonListenerCookie`) | `protocol.game.ClientboundCustomPayloadPacket`(`ResourceLocation` + `FriendlyByteBuf`) |
| 월드 생성 | `DensityFunction`, `RandomState`, `PlacedFeature`, `FeaturePlaceContext`, `FeatureSorter`, `Structure` 계열·`StructurePieceType`, `WorldDimensions`, `Blender`, `LevelHeightAccessor` | `Feature`, `ConfiguredFeature`, `WorldGenSettings` |
| 블록 엔티티 | `BlockEntityTicker` | `TickableBlockEntity` |
| 저장 데이터 | `SavedDataType`, `SavedDataStorage`, `ValueInput`·`ValueOutput`, `DataVersion` | `SavedData`, `DimensionDataStorage` |
| 팩 | `BuiltInPackSource`, `KnownPack`, `PackLocationInfo`, `PackSelectionConfig`, `PathPackResources`, `Pack$Metadata`, `ReloadableServerResources` | `RepositorySource` |
| 게임 규칙·권한·명령 | `gamerules.*` 6개, `permissions.*` 3개, `NameAndId`, `CommandBuildContext` | `world.level.GameRules` |
| 그 밖 | `InteractionResult`의 하위 타입(`Success`·`Pass`·`Fail`), `DamageSources`·`DamageType`, `RandomSource`, `FeatureFlagSet`, `EntitySpawnReason`, `TeleportTransition`, `gizmos.*`, `gametest.Main` | `InteractionResult`는 enum(`SUCCESS`·`CONSUME`·`PASS`·`FAIL`) |

정확한 목록은 보고서에 있습니다. "1.16.5에 있는 것"은 대응할 만한 클래스를 매핑에서 이름으로 찾은 것입니다. 실제로 같은 일을 하는지는 시작할 때 `javap`로 다시 매핑한 jar에서 확인합니다(`AGENTS.md` 원칙 6).

## 1단계보다 더 필요한 것

1.21.x용으로 만드는 다시 매핑 단계의 빌드 연결·서버 jar 다시 매핑·플레이어 쪽 다시 매핑([`MINECRAFT_1_21.md`](MINECRAFT_1_21.md) "아직 없는 것")은 그대로 씁니다. 그 위에 1.16.5에서 더 필요한 것은 다음과 같습니다. ADR-0019의 2·3단계 항목에서 1.16.5에 해당하는 것을 모았습니다.

1. **LWJGL `classifiers` natives.** 설치 프로그램은 지금 `artifact`만 받으므로 1.16.5의 네이티브 라이브러리를 받지 않습니다. 현재 OS의 `natives` 항목을 받아 풀어야 합니다(`extract.exclude` 포함). 2단계(1.17.1, 1.18.x)에서도 필요합니다.
2. **일반 서버 jar.** `installServer`는 bundler 서버만 받습니다. 1.16.5 서버 jar는 gson·netty·log4j 등을 품고 있으므로, 게임 클래스와 라이브러리를 나눠 ADR-0005의 공유 라이브러리와 두 번 정의되지 않게 해야 합니다. 2단계의 1.17.1에서 먼저 만듭니다.
3. **slf4j가 없음.** Poly 모드 로더가 slf4j-api 2.0과 log4j 2.8.1에 맞는 바인딩을 가져와야 합니다(ADR-0019 6번). log4j 2.8.1용 slf4j 2.0 바인딩이 있는지는 확인하지 않았습니다.
4. **gson 2.8.0.** `poly-mod-metadata`가 쓰는 `JsonParser.parseString`은 2.8.6부터 있습니다. Poly 모드 로더의 gson을 relocate할지, 새 API를 쓰지 않을지 3단계를 시작할 때 정합니다(ADR-0019 6번).
5. **오래된 라이브러리를 Java 25에서.** Java 8용으로 나온 게임을 Java 25에서 실행합니다(ADR-0019 5번). Guava 21, Netty 4.1.25, LWJGL 3.2.x, log4j 2.8.1, `oshi-core` 1.1, `jinput`이 Java 25에서 동작하는지는 실행해 봐야 압니다. 필요한 `--add-opens` 같은 JVM 인수는 기록된 실패가 있을 때만 더합니다.
6. **게임 테스트.** `gametest.Main`이 없으므로 `worldCreateSaveTest`·`tickBenchmark`(`BenchmarkLaunch`)를 그대로 쓸 수 없습니다. `GameTestServer`를 다른 방법으로 시작하고, 테스트는 코드로 등록합니다(`GameTestRegistry`). 1.20.6(1단계)에서 먼저 만듭니다.
7. **게임 계층 다시 쓰기.** 위 영역 표의 거의 모든 곳입니다. ADR-0011이 정한 "세 버전 이상이면 버전 사이 코드 공유를 검토"를 1.16.5 전에 정해야 합니다. 1.14.4~1.16.5를 한 게임 계층으로 묶을 수 있는지도 그때 비교로 확인합니다.
8. **로더 브리지.**
   - Forge 1.16.5 모드는 SRG 이름으로 컴파일되어 있고, Forge 36은 ModLauncher 8 세대입니다. 지금의 Forge 지원(ADR-0021, 26.x의 Forge를 그대로 실행)과 구조가 다릅니다. 게임 jar를 Mojang 이름으로 다시 매핑하면 Forge 모드를 SRG에서 Mojang 이름으로 바꿔야 하고, 그러려면 MCPConfig의 SRG 매핑이 필요합니다. 이 매핑의 라이선스와 방식은 따로 ADR로 정합니다. ADR-0019는 아직 Forge 쪽을 다루지 않습니다.
   - Fabric 모드는 intermediary 이름입니다. ADR-0019의 Fabric 절대로, intermediary를 다루는 ADR 전까지는 새 `POLY-COMPAT-` 코드로 막습니다.
   - Forge 36이 Java 25에서 동작하는지는 확인하지 않았습니다.

## 결정이 필요한 것

- **Mojang 매핑의 라이선스**(1단계와 같음): 사용자 PC에서 다시 매핑할 때 어떻게 적용되는지. ADR-0019 3번.
- **Forge 1.16.5 모드를 어떻게 실행할지**: SRG 매핑을 쓸지, Forge 36 자체를 실행할지. 1.16.5의 쓸모 대부분이 Forge 모드이므로, 이 결정 없이 1.16.5를 지원하면 Poly 모드만 실행됩니다.
- **gson과 slf4j**: 위 3·4번.

## 시작할 때 순서

1. 1단계·2단계가 검사를 통과했는지 확인합니다(ADR-0019 7번).
2. `minecraftVersionDiff`를 가장 가까운 지원 버전에서 다시 돌려 이 문서를 고칩니다.
3. 위 "결정이 필요한 것"을 정하고 ADR을 씁니다.
4. 1.16.5 전용 서버 PoC: 다시 매핑한 일반 서버 jar를 Poly 모드 로더로 부팅하고 Poly 모드 하나가 서버 이벤트를 받습니다(`serverBootSmokeTest -PpolyMinecraft=1.16.5`). 이때 Java 25에서 필요한 JVM 인수를 기록합니다.
5. 게임 계층을 영역별로 만듭니다. 헤드리스 검증 → 로더 브리지 → 창이 열리는 검증 순서로 진행합니다(`AGENTS.md`).
6. 1.16.4는 1.16.5 모듈과 비교해 핫픽스로 둘 수 있는지 봅니다(ADR-0011 4번).
