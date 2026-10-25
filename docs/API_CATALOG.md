# API 카탈로그 (0.1.0-alpha.10)

설계서 §5.3 분류에 따라, **현재 존재하는** 모든 공개·내부 타입의 안정성 등급과 지원 동작을 정리합니다(§21-14). 등급은 코드의 `@ApiStability` 표시와 같아야 합니다. 이 문서와 코드가 다르면 코드를 사실로 보고, 이 문서를 고칩니다.

- **등급:** `STABLE` / `PREVIEW` / `VERSION_BOUND` / `INTERNAL` / `UNSAFE` (§5.4)
- **동작:** `READ` / `WRITE` / `REGISTER` / `EXTEND` / `REPLACE` / `INTERCEPT` / `TRANSFORM` / `MIGRATE` / `INSPECT`

0.1.0-alpha.10에는 `STABLE` API가 없습니다. 첫 공개 API는 사용 경험을 확인하기 전까지 `PREVIEW`입니다.

## A. Runtime Foundation — `poly-runtime-api`

| 타입 | 등급 | 동작 | 설명 |
|---|---|---|---|
| `dev.polymodloader.api.stability.ApiStability` | PREVIEW | INSPECT | 등급 표시 애너테이션. JavaDoc과 런타임에 노출 |
| `dev.polymodloader.api.stability.StabilityLevel` | PREVIEW | — | 등급 열거형 |
| `dev.polymodloader.api.runtime.GameSide` | PREVIEW | READ | `CLIENT` / `DEDICATED_SERVER` |

## B. System Composition — `poly-system-api`

| 타입 | 등급 | 동작 | 설명 |
|---|---|---|---|
| `dev.polymodloader.api.system.SystemId` | PREVIEW | — | `namespace:path` |
| `dev.polymodloader.api.system.PolySystem` | PREVIEW | — | 시스템 계약 표시 |
| `dev.polymodloader.api.system.SystemProvider<T>` | PREVIEW | REPLACE | 교체 구현 생성. 메인 스레드, 엔트리포인트 전 1회. 실패 시 `POLY-SYSTEM-003` |
| `dev.polymodloader.api.system.SystemContext` | PREVIEW | READ | 시스템 ID, 측, 제공 모드 |
| `dev.polymodloader.api.system.SystemLookup` | PREVIEW | READ | 활성 구현 조회 |
| `dev.polymodloader.api.system.brand.GameBrandSystem` | PREVIEW | READ, REPLACE | `poly:game_brand`. 아무 스레드, 상태 없음, 결과 1–64 출력 가능 ASCII |

## C-1. Events — `poly-event-api`

| 타입 | 등급 | 동작 | 호출 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.event.EventKey<L>` | PREVIEW | — | 이벤트 식별 |
| `dev.polymodloader.api.event.EventRegistry` | PREVIEW | REGISTER | 아무 때나, 스레드 안전. `register(key, priority, listener)`로 우선순위 지정, 기존 `register(key, listener)`는 `NORMAL` |
| `dev.polymodloader.api.event.EventPriority` | PREVIEW | — | `HIGHEST`→`LOWEST` 순으로 실행, 같은 우선순위는 등록 순서 (Unreleased) |
| `dev.polymodloader.api.event.EventResult` | PREVIEW | INTERCEPT | 결정 이벤트의 결과 `PASS`/`ALLOW`/`DENY`. 처음 결정한 리스너에서 끝남, `null`은 `POLY-EVENT-003` (Unreleased) |
| `dev.polymodloader.api.event.lifecycle.LifecycleEvents.MODS_INITIALIZED`, `ModsInitializedListener` | PREVIEW | OBSERVE | 메인 스레드, Minecraft 부트스트랩 뒤 다른 로더의 모드까지 모두 초기화된 다음 1회. 다른 로더의 모드가 없어도 같은 자리(ADR-0022) (Unreleased) |
| `dev.polymodloader.api.event.server.ServerEvents.STARTED` | PREVIEW | OBSERVE | 서버 스레드, 서버마다 첫 틱 직전 1회 |
| `dev.polymodloader.api.event.server.ServerEvents.TICK` | PREVIEW | OBSERVE | 서버 스레드, 매 틱 시작 |
| `dev.polymodloader.api.event.server.ServerEvents.STOPPING` | PREVIEW | OBSERVE | 서버 스레드, 정지 시작 시(월드 저장 전) |
| `dev.polymodloader.api.event.server.ServerEvents.STARTING`, `ServerStartingListener` | PREVIEW | OBSERVE | 서버 스레드, 레지스트리·데이터 팩을 불러온 뒤 월드를 만들기 전 1회 (Unreleased) |
| `dev.polymodloader.api.event.server.ServerEvents.STOPPED`, `ServerStoppedListener` | PREVIEW | OBSERVE | 서버 스레드, 플레이어·월드를 저장하고 월드를 닫은 뒤 1회 (Unreleased) |
| `dev.polymodloader.api.event.server.ServerView` | PREVIEW | READ | 리스너 실행 중에만 유효 |

## C. Lifecycle — `poly-lifecycle-api`

| 타입 | 등급 | 동작 | 호출 시점·스레드 | 실패 |
|---|---|---|---|---|
| `dev.polymodloader.api.lifecycle.CommonModInitializer` | PREVIEW | REGISTER | 의존성 순서, 메인 스레드, 게임 `main` 전 (레지스트리 아직 없음) | 예외 시 `POLY-ENTRYPOINT-003`으로 실행 중단 |
| `dev.polymodloader.api.lifecycle.ClientModInitializer` | PREVIEW | REGISTER | 모든 common 이후, 클라이언트만 | 위와 같음 |
| `dev.polymodloader.api.lifecycle.DedicatedServerModInitializer` | PREVIEW | REGISTER | 모든 common 이후, 전용 서버만 | 위와 같음 |
| `dev.polymodloader.api.lifecycle.ModContext` | PREVIEW | READ, REGISTER | 엔트리포인트 인수. `onShutdown(description, task)`는 JVM 종료 시 등록 역순으로 Poly 모드 로더 종료 스레드에서 실행 | 종료 작업 예외는 `POLY-SHUTDOWN-001`로 기록하고 계속, 종료 시작 후 등록은 `POLY-SHUTDOWN-002` |

## C-2. Lifecycle 확장 — `ModContext`

| 메서드 | 등급 | 동작 | 설명 |
|---|---|---|---|
| `content()` | PREVIEW | REGISTER | 블록·아이템 선언(`ContentRegistrar`) |
| `commands()` | PREVIEW | REGISTER | 명령어 등록(`CommandRegistrar`) |
| `network()` | PREVIEW | REGISTER | 페이로드 채널(`NetworkRegistrar`) |
| `gameTests()` | PREVIEW | REGISTER | 게임 내 테스트(`GameTestRegistrar`) |
| `client()` | PREVIEW | REGISTER | 키 바인딩·HUD(`ClientRegistrar`), 서버에서는 `POLY-CLIENT-001` |

## D. Content and Data — `poly-game-api` (사용법: [NATIVE_API.md](NATIVE_API.md))

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.resource.ResourceId` | PREVIEW | — | 생성 시 이름 검증 |
| `dev.polymodloader.api.content.ContentRegistrar` | PREVIEW | REGISTER | common 엔트리포인트, 레지스트리 동결 전 |
| `…content.ContentHandle`, `block.BlockHandle`, `item.ItemHandle` | PREVIEW | READ | 아무 스레드 |
| `…content.block.BlockDefinition`, `BlockSound` | PREVIEW | — | 불변, 생성 시 검증 |
| `…content.block.BlockBehavior`, `BlockUse` | PREVIEW | EXTEND | 월드 스레드, 양쪽 |
| `…content.item.ItemDefinition`, `FoodDefinition`, `ItemRarity` | PREVIEW | — | 불변, 생성 시 검증 |
| `…content.item.ItemBehavior`, `ItemUse` | PREVIEW | EXTEND | 월드 스레드, 양쪽 |
| `…content.CreativeTab` | PREVIEW | EXTEND | 탭 끝에 추가 |
| `…content.CreativeTabDefinition`, `ContentRegistrar.creativeTab` | PREVIEW | REGISTER | common 엔트리포인트. 모드 탭은 창작 화면의 추가 페이지(페이지당 10개)에 놓임. 제목은 `itemGroup.<모드 ID>.<경로>`. 등록되지 않은 항목·아이콘은 `POLY-CONTENT-008` (Unreleased) |
| 데이터 팩·리소스 팩(`data/`, `assets/`) | PREVIEW | REGISTER | 모드 jar 자동 추가 |

## E·F. World and Entity — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.world.WorldView` | PREVIEW | READ, WRITE | 월드 스레드, 콜백 동안만 유효 |
| `…world.BlockPos`, `InteractionResult` | PREVIEW | — | 값 |
| `dev.polymodloader.api.entity.EntityView`, `PlayerView` | PREVIEW | READ, WRITE | 월드 스레드, 콜백 동안만 유효 |
| `dev.polymodloader.api.event.player.PlayerEvents.JOINED/LEAVING` | PREVIEW | OBSERVE | 서버 스레드 |
| `dev.polymodloader.api.event.block.BlockEvents.BROKEN` | PREVIEW | OBSERVE | 서버 스레드, 제거 후 |
| `…event.block.BlockEvents.BREAK`, `BlockBreakListener` | PREVIEW | INTERCEPT | 서버 스레드, 제거 전. `EventResult.DENY`면 블록 유지 (Unreleased) |
| `…event.block.BlockEvents.PLACE`/`PLACED`, `BlockPlaceListener`, `BlockPlacedListener` | PREVIEW | INTERCEPT, OBSERVE | 서버 스레드, 플레이어가 블록 아이템으로 놓을 때. `DENY`면 놓지 않음 (Unreleased) |
| `…event.interaction.InteractionEvents.USE_BLOCK`/`USE_ITEM`/`USE_ENTITY`, `UseBlockListener`, `UseItemListener`, `UseEntityListener` | PREVIEW | INTERCEPT | 서버 스레드, 게임 처리 전. `PASS`가 아닌 `InteractionResult`가 결과를 정함 (Unreleased) |
| `…event.interaction.InteractionEvents.ATTACK_ENTITY`, `AttackEntityListener` | PREVIEW | INTERCEPT | 서버 스레드, 피해 전. `DENY`면 공격 취소 (Unreleased) |
| `…entity.Hand` | PREVIEW | — | `MAIN_HAND`, `OFF_HAND` (Unreleased) |
| `…event.world.WorldEvents.LOADED`/`SAVING`/`UNLOADED`, `WorldListener` | PREVIEW | OBSERVE | 서버 스레드. 모든 차원을 만든 뒤 / 저장 직전 / 정지 중 저장한 뒤 닫기 직전 (Unreleased) |
| `…event.player.PlayerEvents.COPY`, `PlayerCopyListener` | PREVIEW | OBSERVE, WRITE | 서버 스레드, 리스폰으로 새 플레이어 객체를 만들 때(`EntityDataKey` 데이터 복사 뒤) (Unreleased) |
| `…event.player.PlayerEvents.RESPAWNED`, `PlayerRespawnedListener` | PREVIEW | OBSERVE | 서버 스레드, 새 플레이어가 월드에 들어간 뒤 (Unreleased) |
| `…event.entity.EntityEvents.JOINED_WORLD`, `EntityJoinedWorldListener` | PREVIEW | OBSERVE | 서버 스레드, 모든 종류의 엔티티가 월드에 추적되기 시작할 때(생성·청크 로드·차원 이동) (Unreleased) |
| `GameTestContext.createConnectedMockPlayer`, `respawn`, `disconnect` | PREVIEW | WRITE | 게임 테스트. 메모리 연결로 접속한 플레이어, 클라이언트처럼 리스폰 요청 (Unreleased) |
| `dev.polymodloader.api.tag.TagKey`, `TagRegistry` | PREVIEW | — | 값. `TagKey.block/item/entityType(id)` (Unreleased) |
| `…tag.TagView`, `WorldView.tags()`, `WorldView.isBlockIn`, `EntityView.isIn`, `ItemStackRef.isIn` | PREVIEW | READ | 월드 스레드. 서버는 데이터 팩의 태그, 클라이언트는 받은 사본. 다른 종류의 태그는 `IllegalArgumentException` (Unreleased) |
| `ContentRegistrar.addToTag` | PREVIEW | REGISTER | common 엔트리포인트. 생성 데이터 팩에 `replace: false` 태그 파일로 기록. 등록되지 않은 항목은 `POLY-CONTENT-007`로 알리고 뺌 (Unreleased) |

## G. Client — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.client.ClientRegistrar` | PREVIEW | REGISTER | client 엔트리포인트 |
| `…client.KeyBinding`, `KeyCodes` | PREVIEW | READ | 클라이언트 스레드 |
| `…client.HudLayer`, `HudCanvas` | PREVIEW | EXTEND | 렌더 스레드, 매 프레임 |
| `…client.ClientEvents.TICK`, `ClientView` | PREVIEW | OBSERVE | 클라이언트 스레드 |
| `…client.ClientEvents.ITEM_TOOLTIP`, `ItemTooltipListener`, `TooltipLines` | PREVIEW | EXTEND | 클라이언트 스레드, 툴팁을 만들 때마다(게임 줄 뒤). 숨긴 툴팁에는 불리지 않음 (Unreleased) |

## I. Network — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.network.NetworkRegistrar` | PREVIEW | REGISTER | common 엔트리포인트 |
| `…network.PayloadChannel` | PREVIEW | WRITE | 송신: 서버/클라이언트 스레드, 수신 처리: 해당 게임 스레드 |
| `…network.PayloadCodec`, `PayloadWriter`, `PayloadReader` | PREVIEW | — | 네트워크 스레드, 게임 상태 접근 금지 |

## J. Commands — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.command.CommandRegistrar` | PREVIEW | REGISTER | common 엔트리포인트, 서버 시작·`/reload`마다 설치 |
| `…command.CommandNode`, `LiteralCommand`, `ArgumentCommand`, `CommandArgumentType` | PREVIEW | — | 등록 후 변경 금지 |
| `…command.CommandAction`, `CommandContext`, `CommandSource`, `CommandFailure` | PREVIEW | EXTEND | 서버 스레드 |

## D·E·F·G 확장 (2단계) — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…content.block.state.BlockProperty`, `BlockStateView`, `…world.Direction` | PREVIEW | READ, WRITE | 월드 스레드 |
| `…content.block.entity.BlockEntityDefinition`, `BlockEntityView`, `BlockEntityTicker` | PREVIEW | REGISTER, READ, WRITE | 틱: 서버 스레드 |
| `…inventory.InventoryView`, `ItemStackView` | PREVIEW | READ, WRITE | 월드 스레드 |
| `…data.DataTree`, `…world.StoredData` | PREVIEW | READ, WRITE | 월드 스레드, 월드와 함께 저장 |
| `…event.world.WorldEvents.READY` | PREVIEW | OBSERVE | 서버 스레드 |
| `…content.entity.EntityDefinition`, `EntityBehavior`, `MobCategory`, `EntityHandle` | PREVIEW | REGISTER, EXTEND | 서버 스레드(동작) |
| `…entity.LivingEntityView`, `DamageInfo` | PREVIEW | READ, WRITE | 월드 스레드 |
| `…event.entity.EntityEvents.ALLOW_DAMAGE`, `DIED` | PREVIEW | INTERCEPT, OBSERVE | 서버 스레드 |
| `…content.effect.EffectDefinition`, `EffectBehavior`, `EffectCategory` | PREVIEW | REGISTER | 서버 스레드(동작) |
| `…content.rule.GameRuleKey`, `GameRuleCategory` | PREVIEW | REGISTER, READ, WRITE | 서버 스레드 |
| `…content.SoundHandle` | PREVIEW | REGISTER | — |
| `…worldgen.WorldgenRegistrar`, `BiomeSelector`, `GenerationStepName` | PREVIEW | EXTEND | common 엔트리포인트, 월드 생성 시 적용 |
| `…client.ModScreen`, `ScreenBuilder`, `TextInput` | PREVIEW | EXTEND | 클라이언트 스레드 |
| `ModContext.configDirectory()` | PREVIEW | READ | 아무 스레드 |

## D 심화 (3단계) — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…content.block.state.BlockProperty.ChoiceProperty` | PREVIEW | REGISTER, READ, WRITE | 월드 스레드 |
| `BlockDefinition.Builder.lightLevel(ToIntFunction)`, `WorldView.emittedLight` | PREVIEW | REGISTER, READ | 등록 시 한 번 계산 |
| `…content.block.entity.MenuLayout`, `PlayerView.openMenu` | PREVIEW | REGISTER, WRITE | 서버 스레드에서 열기 |
| `…content.item.ItemComponentKey`, `ComponentType`, `…inventory.ItemStackRef` | PREVIEW | REGISTER, READ, WRITE | 월드 스레드 |
| `…content.entity.ProjectileDefinition`, `ProjectileBehavior`, `ProjectileHit`, `LivingEntityView.launchProjectile` | PREVIEW | REGISTER, EXTEND | 서버 스레드 |
| `…content.entity.NaturalSpawn`, `WorldView.naturalSpawns` | PREVIEW | REGISTER, READ | 월드 생성 시 적용 |

## F 게임플레이 (3단계) — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…content.entity.AiGoal`, `GoalControl`, `EntityDefinition.GoalEntry`, `…entity.MobView` | PREVIEW | EXTEND, READ, WRITE | 서버 스레드(목표 스케줄러) |
| `…content.block.BlockShape`, `BlockDefinition.Builder.shape`/`collisionShape` | PREVIEW | REGISTER | 상태마다 시작할 때 한 번 계산(`POLY-CONTENT-006`) |
| `…network.ConfigurationExchange`, `NetworkRegistrar.configurationExchange` | PREVIEW | REGISTER, EXTEND | 서버 쪽은 서버 스레드, 답은 클라이언트 스레드 |
| `…client.WorldPainter`, `WorldCanvas`, `ClientRegistrar.worldPainter` | PREVIEW | EXTEND, REGISTER | 렌더 스레드, 매 프레임 |
| `…client.ShaderPipeline`, `ShaderPipelineDefinition`, `VertexSink`, `ClientRegistrar.shaderPipeline` | PREVIEW | REGISTER, WRITE | 렌더 스레드에서 처음 쓸 때 만듦(`POLY-CLIENT-006`). 텍스처는 `Sampler0`~`3` |
| `…client.ShaderUniform` (`ShaderPipelineDefinition.Builder.uniform`) | PREVIEW | WRITE | 값 공급자는 렌더 스레드, 매 프레임 1회. 잘못된 값은 `POLY-CLIENT-007` |
| `BlockEntityCanvas.block`, `defaultState`, `quads` | PREVIEW | WRITE | 렌더 스레드 |
| `…content.block.state.BlockStateView.with` | PREVIEW | READ | 바꾼 사본을 돌려줌, 월드는 그대로. 잘못된 값은 `IllegalArgumentException` |
| `…worldgen.CodeFeature`, `FeatureContext`, `FeaturePlacement`, `WorldgenRegistrar.feature` | PREVIEW | EXTEND, REGISTER | 월드 생성 스레드, 여러 청크 동시 |
| `…worldgen.NoiseFunction`, `WorldgenRegistrar.densityFunction` | PREVIEW | EXTEND, REGISTER | 월드 생성 스레드, 매우 자주; 상태 없이 |
| `…worldgen.StructureDefinition`, `StructureBuilder`, `StructurePieceContext`, `WorldgenRegistrar.structure` | PREVIEW | EXTEND, REGISTER | 월드 생성 스레드, 청크마다 다시 호출 |
| `EntityDefinition.Movement`, `Builder.flying`/`swimming` | PREVIEW | REGISTER | 길찾기·이동 제어·배회 목표가 함께 바뀜 |
| `…content.entity.model.KeyframeAnimation`, `EntityModelDefinition.Builder.animation`, `MobView.playAnimation`/`stopAnimation` | PREVIEW | REGISTER, WRITE | 재생은 서버 스레드, 그리기는 클라이언트 |
| `EntityView` 이동·탑승(`velocity`, `setVelocity`, `startRiding` 등), `…world.Vector3`, `EntityDefinition.Riding` | PREVIEW | READ, WRITE | 월드 스레드, 변경은 서버 |
| `…event.entity.EntityEvents.MODIFY_DAMAGE`, `DamageModifier` | PREVIEW | INTERCEPT | 서버 스레드 |
| `…content.AdvancementTrigger`, `PlayerView.hasAdvancement`/`grantAdvancement`/`revokeAdvancement` | PREVIEW | REGISTER, READ, WRITE | 서버 스레드 |
| `GameTestContext.createMockServerPlayer` | PREVIEW | READ | 게임 테스트 |
| `GameTestContext.interactBlock`, `interactItem`, `interactEntity`, `attackEntity`, `breakBlock` | PREVIEW | WRITE | 게임 테스트. 서버 플레이어가 클라이언트 입력처럼 행동(이벤트 포함) (Unreleased) |

## E 월드 (3단계) — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…worldgen.TerrainGenerator`, `ColumnBuilder`, `WorldgenRegistrar.chunkGenerator` | PREVIEW | REGISTER, EXTEND | 월드 생성 스레드(동시 호출) |
| `…worldgen.BiomeDefinition`, `BiomeSpawn`, `WorldgenRegistrar.biome` | PREVIEW | REGISTER | common 엔트리포인트, 생성 팩으로 기록 |
| `WorldView.setChunkForced`, `isChunkLoaded`, `biomeAt` | PREVIEW | READ, WRITE | 서버 스레드 |
| `…event.world.WorldEvents.CHUNK_LOADED`, `CHUNK_UNLOADED`, `ChunkListener` | PREVIEW | OBSERVE | 서버 스레드 |

## G 클라이언트 그리기 (3단계) — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…content.entity.model.EntityModelDefinition`, `ModelPartDefinition`, `ModelBox`, `PartAnimation` | PREVIEW | REGISTER | 클라이언트가 렌더러를 만들 때 사용 |
| `…content.particle.ParticleDefinition`, `ContentRegistrar.particle` | PREVIEW | REGISTER | common 엔트리포인트 |
| `…client.BlockEntityPainter`, `BlockEntityCanvas`, `ClientRegistrar.blockEntityRenderer` | PREVIEW | EXTEND | 렌더 스레드, 매 프레임 |
| `…client.DynamicTexture`, `HudCanvas.drawTexture` | PREVIEW | REGISTER, WRITE | 클라이언트 스레드 |
| `…client.ClientEvents.FIELD_OF_VIEW`, `FovModifier`, `CameraPerspective` | PREVIEW | INTERCEPT, READ, WRITE | 렌더 스레드 |
| `ClientView.setPostEffect`, `clearPostEffect`, `postEffect` | PREVIEW | WRITE, READ | 클라이언트 스레드 |

## H·I·J 리소스·네트워크·운영 (3단계) — `poly-game-api`, `poly-lifecycle-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `…resource.ResourceRegistrar`, `ResourceReloadListener`, `ModContext.resources()` | PREVIEW | OBSERVE | 리로드 적용 스레드 |
| `…content.EntityDataKey`, `EntityView.data` | PREVIEW | REGISTER, READ, WRITE | 월드 스레드, 클라이언트는 읽기 전용 |
| `…world.StorageOptions`, `…data.DataMigration`, `WorldView.storage(id, options)`, `BlockEntityDefinition.dataVersion` | PREVIEW | READ, WRITE | 서버 스레드, 클라이언트는 읽기 전용 사본 |
| `…permission.PermissionNode`, `PermissionProvider`, `PermissionResult`, `PermissionRegistrar`, `ModContext.permissions()` | PREVIEW | REGISTER, INTERCEPT, READ | 서버 스레드 |
| `CommandNode.requiresPermission(PermissionNode)`, `CommandSource.hasPermission(PermissionNode)`, `PlayerView.hasPermission` | PREVIEW | READ | 서버 스레드 |
| `…client.ClientEvents.JOINED_WORLD`, `LEFT_WORLD`, `ClientConnectionListener`, `ClientView.leaveWorld` | PREVIEW | OBSERVE, WRITE | 클라이언트 스레드 |
| `…devtools.gametest.TestArea` | PREVIEW | REGISTER | common 엔트리포인트 |

## L. Developer tools — `poly-game-api`

| 타입 | 등급 | 동작 | 시점·스레드 |
|---|---|---|---|
| `dev.polymodloader.api.devtools.gametest.GameTestRegistrar`, `GameTest`, `GameTestContext` | PREVIEW | REGISTER, INSPECT | 게임 테스트 서버의 서버 스레드 |

## 모드 메타데이터 — `poly.mod.json` 스키마 1

| 필드 | 상태 |
|---|---|
| `schemaVersion`, `id`, `version`, `name`, `environment`, `minecraft`, `entrypoints`, `depends`, `conflicts` | 지원 (PREVIEW) |
| `capabilities`, `replacesSystems` | 지원 (PREVIEW). `replacesSystems`는 `system.provider.replace` 권한 필요 |
| `transforms` | 지원 (PREVIEW, `UNSAFE` 성격). `internal.bytecode.transform` 권한 필요. 형식: `{id: "<modid>:<path>", minecraft, sides: [client|dedicated_server], target: {owner, method, descriptor}, action: {type: replace_returned_string_constant, expected, replacement}, onFailure: abort_startup|disable_rule}` |
| `provides` | 예약됨. 비어 있지 않으면 `POLY-METADATA-010`으로 거부 |

## K. Deep Internal and Transformation — `poly-transform-api` (INTERNAL)

모드는 아직 이 API를 쓸 수 없습니다. 버전 어댑터와 Poly 모드 로더 내부만 사용합니다.

| 타입 | 등급 | 동작 |
|---|---|---|
| `dev.polymodloader.internal.transform.TransformRuleDefinition` | INTERNAL | TRANSFORM |
| `dev.polymodloader.internal.transform.MethodTarget` | INTERNAL | — |
| `dev.polymodloader.internal.transform.MethodTransformAction` (sealed) | INTERNAL | TRANSFORM |
| `dev.polymodloader.internal.transform.ReplaceReturnedStringConstant` | INTERNAL | TRANSFORM |
| `dev.polymodloader.internal.transform.ReplaceConstantReturnWithStaticCall` | INTERNAL | TRANSFORM, INTERCEPT |
| `dev.polymodloader.internal.transform.InjectStaticCallAtHead` | INTERNAL | INTERCEPT |
| `dev.polymodloader.internal.transform.InvokeHookAtHead`, `HeadHookResult` | INTERNAL | INTERCEPT |
| `dev.polymodloader.internal.transform.InvokeHookBeforeReturn` | INTERNAL | INTERCEPT, TRANSFORM |
| `dev.polymodloader.internal.transform.TransformFailurePolicy` | INTERNAL | — |
| `dev.polymodloader.internal.transform.TransformProblem`, `TransformProblemCode`, `TransformException` | INTERNAL | INSPECT |

## 버전 어댑터 — `poly-version-adapter-api` (INTERNAL)

| 타입 | 등급 | 설명 |
|---|---|---|
| `dev.polymodloader.game.version.GameVersionAdapter` | INTERNAL | 버전별 진입점·내장 변환 규칙. `ServiceLoader`로 선택 |
| `dev.polymodloader.game.version.GameLaunchTarget` | INTERNAL | `CLIENT`, `DEDICATED_SERVER`, `GAME_TEST_SERVER` |
| `dev.polymodloader.game.version.v26_2.Minecraft26_2Adapter` | VERSION_BOUND | Minecraft 26.2 전용 |
| `dev.polymodloader.game.version.layer.GameLayer`, `GameLayerContext`, `NativeDeclarations` 등 | INTERNAL | 게임 계층 SPI ([ADR-0010](adr/0010-native-api-game-layer.md)) |
| `dev.polymodloader.game.layer.v26_2.*` (`poly-game-v26_2`) | VERSION_BOUND | 26.2 게임 계층. 게임 클래스로더가 정의. 지원 버전마다 `v<id>` 패키지 하나(ADR-0011) |

## 구현 모듈 (모드가 의존하면 안 됨)

`poly-mod-metadata`, `poly-transform-engine`, `poly-runtime-impl`, `poly-bootstrap`은 Poly 모드 로더 내부 구현입니다(INTERNAL). `GameClassLoader`는 이 패키지들을 모드 쪽 로더에서 정의하지 않고 항상 부모 로더에서 가져옵니다.

## 아직 없는 영역

D~J의 나머지(블록 상태 속성, 블록 엔티티, 엔티티 타입, 월드 생성, AI·물리, 화면·렌더링, 사운드·리로드, 상태 동기화, 저장·마이그레이션)는 **미구현**입니다. 목록은 [NATIVE_API.md](NATIVE_API.md)의 "아직 없는 것"에 있습니다.
