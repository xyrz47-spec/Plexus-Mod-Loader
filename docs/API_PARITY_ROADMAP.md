# Native API parity roadmap (Fabric API / NeoForge → Poly Mod Loader)

Status: 2026-10-22, Poly Mod Loader 0.1.0-alpha.10. Targets: Minecraft 26.1 (with 26.1.1, 26.1.2) and 26.2.

**Goal.** Everything a Fabric, Quilt, NeoForge or Forge mod commonly does should be expressible with the Native API
(`dev.polymodloader.api.*`, no Minecraft types), so that the Fabric and Forge bridges can later map those
loaders' APIs onto it instead of re-implementing game hooks themselves (design doc §13). This page is
the gap list. It is a plan, not a compatibility claim: compatibility is only claimed through
`compatibilityMatrix`, `compatibilityCorpus` and [`COMPATIBILITY.md`](COMPATIBILITY.md).

Legend:

- **Status:** `exists` (usable today, game-tested), `partial` (covers the common case, gaps listed),
  `missing`.
- **Priority:** `P0` blocks most content mods, `P1` common, `P2` niche or large.
- **This change** marks what the Unreleased change set implements (see [`CHANGELOG.md`](../CHANGELOG.md)).

The Korean user guide for everything that exists is [`NATIVE_API.md`](NATIVE_API.md); the type
catalogue with stability levels is [`API_CATALOG.md`](API_CATALOG.md).

## 1. Event infrastructure

| Capability (Fabric / NeoForge) | Poly Mod Loader status | Planned Native API shape | Priority |
|---|---|---|---|
| Typed events, per-mod listener isolation (`Event<T>` / `IEventBus`) | exists: `EventKey`, `EventRegistry`, `POLY-EVENT-001` disables a throwing listener | — | — |
| Listener order: Fabric phases (`addPhaseOrdering`), NeoForge `EventPriority` | **exists (this change):** `EventPriority` (`HIGHEST`…`LOWEST`), `EventRegistry.register(key, priority, listener)`; same priority keeps registration order | Fabric phases are mapped by the Fabric bridge onto priorities (default phase = `NORMAL`; phases ordered before it = `HIGH`, after = `LOW`); arbitrary phase graphs stay inside the bridge | P0 |
| Cancellation / result: Fabric `ActionResult`/`TriState` returns, NeoForge `ICancellableEvent`, `Event.Result` | **exists (this change):** `EventResult` (`PASS`/`ALLOW`/`DENY`) for decision events, `InteractionResult` for interactions; first non-`PASS` result wins and later listeners are skipped | NeoForge `receiveCanceled` listeners (seeing and un-cancelling a cancelled event) are not expressible; the Forge bridge must run such listeners inside one Poly listener | P0 |
| Generic/parameterised events, event inheritance | missing (by design: one key per event) | keys per concrete case; bridges fan out | P2 |

## 2. Events by area

| Capability | Fabric / NeoForge reference | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|---|
| Server lifecycle | `ServerLifecycleEvents.SERVER_STARTING/STARTED/STOPPING/STOPPED`; `ServerAboutToStart/Starting/Started/Stopping/StoppedEvent` | **exists:** `ServerEvents.STARTED`, `TICK`, `STOPPING`; **this change:** `STARTING`, `STOPPED` | `END_DATA_PACK_RELOAD` → `ServerEvents.DATA_RELOADED` | P0 |
| Server tick start/end | `ServerTickEvents.START/END_SERVER_TICK`; `ServerTickEvent.Pre/Post` | partial: `ServerEvents.TICK` (start only) | `TICK_END` | P1 |
| World load/unload/save | `ServerWorldEvents.LOAD/UNLOAD`; `LevelEvent.Load/Unload/Save` | **exists:** `WorldEvents.READY` (first tick); **this change:** `LOADED`, `UNLOADED`, `SAVING` | world tick start/end | P0 |
| Chunk load/unload | `ServerChunkEvents`; `ChunkEvent` | exists: `WorldEvents.CHUNK_LOADED/UNLOADED` | chunk data read/write hooks | P1 |
| Use block / use item / use entity / attack entity | `UseBlockCallback`, `UseItemCallback`, `UseEntityCallback`, `AttackEntityCallback`; `PlayerInteractEvent.*`, `AttackEntityEvent` | **this change (server side):** `InteractionEvents.USE_BLOCK`, `USE_ITEM`, `USE_ENTITY`, `ATTACK_ENTITY` with results | client-side prediction events (same keys fired on the client thread) | P0 |
| Attack (left-click) block | `AttackBlockCallback`; `PlayerInteractEvent.LeftClickBlock` | missing | `InteractionEvents.ATTACK_BLOCK` (`ServerPlayerGameMode.handleBlockBreakAction`) | P1 |
| Block break before/after/cancelled | `PlayerBlockBreakEvents.BEFORE/AFTER/CANCELED`; `BlockEvent.BreakEvent` | **exists:** `BlockEvents.BROKEN` (after); **this change:** `BlockEvents.BREAK` (before, cancellable) | `BREAK_CANCELLED` | P0 |
| Block place | — (Fabric uses mixins); `BlockEvent.EntityPlaceEvent` | **this change:** `BlockEvents.PLACE` (before, cancellable), `BlockEvents.PLACED` (after) for players placing block items | non-player placement (dispensers, endermen) | P1 |
| Entity join/leave world | `ServerEntityEvents.ENTITY_LOAD/UNLOAD`; `EntityJoinLevelEvent`, `EntityLeaveLevelEvent` | **this change:** `EntityEvents.JOINED_WORLD` | `LEFT_WORLD`; cancellable spawn (`EntityJoinLevelEvent` is cancellable) | P1 |
| Living damage / death | `ServerLivingEntityEvents.ALLOW_DAMAGE/AFTER_DEATH`; `LivingIncomingDamageEvent`, `LivingDeathEvent` | exists: `EntityEvents.ALLOW_DAMAGE`, `MODIFY_DAMAGE`, `DIED` | `ALLOW_DEATH` (totem-like), `AFTER_DAMAGE`, drops (`LivingDropsEvent`) | P1 |
| Player join/leave/login check | `ServerPlayConnectionEvents`; `PlayerLoggedIn/OutEvent` | exists: `PlayerEvents.JOINED`, `LEAVING`, `LOGIN_CHECK` | — | — |
| Player respawn / copy data on respawn | `ServerPlayerEvents.AFTER_RESPAWN`, `COPY_FROM`; `PlayerRespawnEvent`, `PlayerEvent.Clone` | partial: `EntityDataKey` data is copied automatically; **this change:** `PlayerEvents.RESPAWNED`, `PlayerEvents.COPY` | — | P0 |
| Player tick | `PlayerTickEvent.Pre/Post` | missing | `PlayerEvents.TICK` at the head of `ServerPlayer.tick()` (testable with `GameTestContext.createConnectedMockPlayer`), client player tick | P1 |
| Item tooltip | `ItemTooltipCallback`; `ItemTooltipEvent` | **exists (this change):** `ClientEvents.ITEM_TOOLTIP`, `TooltipLines` (plain-text lines, client thread) | styled lines (colours, translation keys) without section signs | P1 |
| Crafting / smelting result, anvil, brewing events | `ItemCraftedEvent`, `AnvilUpdateEvent` … | missing | per-event keys, server thread | P2 |
| Explosions, projectile impact, sleeping, XP, advancements earned | many NeoForge events, a few Fabric | partial: `ProjectileBehavior` for mod projectiles | per-event keys | P2 |
| Client input, screen open/close, render stages | `ScreenEvents`, `WorldRenderEvents`, `HudRenderCallback`; `ScreenEvent.*`, `RenderLevelStageEvent`, `RenderGuiLayerEvent` | partial: `ClientEvents.TICK`, `FIELD_OF_VIEW`, `JOINED_WORLD`, `LEFT_WORLD`, `HudLayer`, `WorldPainter` | screen open/init/close events, key/mouse events, render stage keys | P1 |

## 3. Registries and content kinds

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Blocks, items, block items, block states | exists: `ContentRegistrar.block/item`, `BlockDefinition`, `BlockProperty` | — | — |
| Block entities, menus | exists: `BlockEntityDefinition`, `MenuLayout`, `PlayerView.openMenu` | custom menu slot logic, data slots | P1 |
| Entity types, models, animations, AI | exists: `EntityDefinition`, `EntityModelDefinition`, `AiGoal` | spawn placement rules for other mods' mobs | P2 |
| Effects, sounds, particles, game rules, advancement triggers | exists | — | — |
| Creative tabs | exists (vanilla tabs via `CreativeTab` enum); **this change:** mod tabs `ContentRegistrar.creativeTab(path, CreativeTabDefinition)` with icon and entries, shown on extra pages of the creative screen | per-item insertion position (`addAfter`) like `ItemGroupEvents.modifyEntriesEvent` / `BuildCreativeModeTabContentsEvent` | P0 |
| Tool and armour materials, tool tiers | missing | `ToolMaterialDefinition`, `ArmorMaterialDefinition`, `ItemDefinition.Builder.tool/armor` | P0 |
| Attributes (custom and modifiers) | partial: mob base attributes in `EntityDefinition` | `ContentRegistrar.attribute`, `LivingEntityView.attribute(id)` read/write, item attribute modifiers | P1 |
| Enchantments, damage types, dimensions, biomes from data | exists via mod jar `data/` (data-driven in 26.2) and `WorldgenRegistrar.biome` | code-defined enchantment effects | P2 |
| Fluids | missing | `FluidDefinition` (still/flowing pair, bucket, block), `WorldView.fluidAt` | P1 |
| Custom registries (mod-owned, synced) | missing | `RegistryRegistrar.registry(path, codec, sync)` → `ModRegistry<T>` | P1 |
| Data-driven custom registries (`DynamicRegistries`, `DataPackRegistryEvent`) | missing | same API with `fromData(codec)` | P2 |
| Villager trades, wandering trader offers | missing | `TradeRegistrar` | P2 |
| Composting, fuel values, flammability, strippable/tillable | missing | `ContentRegistrar.fuel(item, ticks)`, `BlockDefinition.Builder.flammable(...)` | P1 |

## 4. Data, tags, recipes, loot

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Data components on item stacks | partial: `ItemComponentKey` + `ComponentType` (int, string, boolean, …), `ItemStackRef` | read/write of vanilla components (custom name, damage, enchantments) | P1 |
| Tags: declare | exists via mod jar `data/<ns>/tags/...` | — | — |
| Tags: query (`TagKey`, `isIn`) and entries from code | **exists (this change):** `TagKey.block/item/entityType`, `TagView.contains/entries` (`WorldView.tags()`), `WorldView.isBlockIn`, `ItemStackRef.isIn`, `EntityView.isIn`; `ContentRegistrar.addToTag` | fluid and biome tags; tag events on reload | P0 |
| Recipes | partial: data-only from mod jar | recipe lookup (`RecipeView`), custom recipe types and serializers | P1 |
| Loot tables | partial: data-only from mod jar | loot table modification (`LootTableEvents.MODIFY` / global loot modifiers) | P1 |
| Data generation | missing | out of scope for the runtime; a Gradle data-gen task over the Native API | P2 |

## 5. World generation and biome modification

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Add features to biomes | exists: `WorldgenRegistrar.addFeature(BiomeSelector, step, feature)` | remove features | P1 |
| Spawns in biomes | exists: `NaturalSpawn`, `BiomeDefinition.spawn` | remove/replace spawns | P1 |
| Biome properties (colours, climate) of existing biomes | missing | `BiomeModification` with selectors | P2 |
| Custom terrain, density functions, features, structures | exists: `TerrainGenerator`, `NoiseFunction`, `CodeFeature`, `StructureDefinition` | — | — |

## 6. Networking, commands, permissions

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Play-phase payloads (both directions) | exists: `PayloadChannel`, `PayloadCodec` | — | — |
| Configuration-phase tasks | exists: `ConfigurationExchange` | — | — |
| Commands with arguments, permissions | exists: `CommandNode`, `PermissionNode` | more argument types (entity, item, text), suggestions | P1 |
| Client-side commands | missing | `ClientRegistrar.command` | P2 |

## 7. Client: screens, HUD, rendering, input

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Screens, container screens | exists: `ModScreen`, `ScreenBuilder`, `MenuLayout` | custom widgets | P1 |
| HUD layers | exists: `HudLayer`, `HudCanvas` | hide/replace vanilla layers | P2 |
| Key bindings | exists: `KeyBinding` | — | — |
| Entity and block entity renderers, models | exists: `EntityModelDefinition`, `BlockEntityPainter` | armour/feature layers on vanilla mobs | P1 |
| Block and item colour providers (tinting) | missing | `ClientRegistrar.blockColor/itemColor` | P1 |
| Block render layers (cutout, translucent) | missing (model JSON only) | `BlockDefinition.Builder.renderLayer` | P1 |
| Model loading / baked model plugins | missing | P2: needs a stable model abstraction first | P2 |
| World rendering stages, shaders | partial: `WorldPainter`, `ShaderPipelineDefinition`, post effects | named stages | P2 |

## 8. Resources, config, storage, transfer

| Capability | Poly Mod Loader status | Planned shape | Priority |
|---|---|---|---|
| Resource/data packs from the mod jar | exists | built-in optional packs | P2 |
| Reload listeners | exists: `ResourceReloadListener`, `BinaryReloadListener` | — | — |
| Config | partial: `ModContext.configDirectory()` only | typed config spec with sync (`ModConfigSpec`-like), config screen | P1 |
| Attachments on entities / worlds / block entities | exists: `EntityDataKey`, `WorldView.storage`, block entity `data()` | attachments on chunks and item stacks (components) | P1 |
| Transfer API / capabilities (items, fluids, energy) | partial: `InventoryView` on mod block entities and players | `TransferAccess` lookup by side for blocks, entities, items (Fabric `BlockApiLookup` / NeoForge `Capabilities`) with item and fluid storages | P1 |
| Fluids storage | missing | part of the transfer API | P1 |

## 9. Bytecode-level APIs: Mixin, access wideners, access transformers

| Capability | Poly Mod Loader status | Plan | Priority |
|---|---|---|---|
| Mixin (Fabric, NeoForge) | Fabric: done ([ADR-0020](adr/0020-fabric-mixin-and-runtime-fidelity.md)). Mixin 0.8.7 and MixinExtras run as a bridge transformation step after Poly Mod Loader's rules; built-in rules keep the vanilla calls mixins target, and a redirect a mixin already took is left out of that class (`POLY-TRANSFORM-011`). NeoForge/Forge: missing (`202`) | the FML bridge reuses the same Mixin service when the FML runtime exists | P0 for FML bridges |
| Access wideners / class tweakers (Fabric), access transformers (NeoForge) | Fabric: done (ADR-0018, ADR-0020: access lines, `inject-interface`, `extend-enum`). NeoForge/Forge access transformers: missing (`203`) | parse access transformers in the FML bridge and map them onto `AccessWidening` | P1 for FML bridges |
| Native mods' own transforms | partial: `poly.mod.json` `transforms` (string constant replacement only, `UNSAFE`) | expose more `MethodTransformAction`s to mods under a capability | P2 |

## 10. What cannot be expressed without Minecraft types or Mixin

Some mod features have no stable, version-independent shape and will not get a `PREVIEW` Native API:

1. **Arbitrary code injection into game methods** (Mixin `@Inject`/`@Redirect`/`@Overwrite` into any
   method). The Native API only offers named events and declared content. The plan is the Mixin
   service above for bridged mods, and for Native mods a `VERSION_BOUND` escape hatch: a mod module
   compiled against one Minecraft version (like the game layer, ADR-0010) whose classes may use game
   types directly, loaded only when the running version matches, and reported as version-bound in
   diagnostics. Its rules go through `TransformRuleDefinition` with owner, name, descriptor and failure
   policy, never through name-only matching.
2. **Direct use of vanilla classes and objects** (`ServerLevel`, `BlockState`, `ItemStack`, renderers,
   `Codec`s of vanilla types). Views (`WorldView`, `ItemStackRef`, …) cover the common operations; the
   rest goes through the `VERSION_BOUND` module above. Bridges see game types anyway because Fabric and
   NeoForge mods are compiled against them.
3. **Custom rendering beyond the provided canvases** (custom `RenderType`s with arbitrary vertex
   formats, baked model wrappers): `VERSION_BOUND` only until a model abstraction exists.
4. **Mutable event objects shared across listeners** (NeoForge `setCanceled(false)` after another
   listener cancelled, `receiveCanceled = true`): the Native convention returns results instead. The
   Forge bridge has to keep its own mutable event object and run its listeners inside one Poly listener
   per priority.
5. **Loader-specific services** (Fabric `ModContainer` internals, FML `IModBusEvent` lifecycle,
   `DeferredRegister` ordering): bridge-only.

## 11. What this change implements (Unreleased)

- Event priorities (`EventPriority`) and the result convention (`EventResult`, first decision wins).
- Server-side interaction events with results: use block, use item, use entity, attack entity.
- `BlockEvents.BREAK` (cancellable, before), `BlockEvents.PLACE` (cancellable) and `PLACED`.
- `ServerEvents.STARTING`, `STOPPED`; `WorldEvents.LOADED`, `UNLOADED`, `SAVING`;
  `PlayerEvents.RESPAWNED`, `COPY`; `EntityEvents.JOINED_WORLD`; `EntityEvents.DIED` now also fires
  for server players.
- Tags: `TagKey`, `TagView` (`WorldView.tags()`), `isBlockIn`/`isIn`, `ContentRegistrar.addToTag`.
- Mod creative tabs (`ContentRegistrar.creativeTab`, `CreativeTabDefinition`) on extra creative
  screen pages.
- Client item tooltips (`ClientEvents.ITEM_TOOLTIP`).
- `BuiltInRulesTest`: every built-in rule is applied to the real 26.2 classes for both sides in a
  unit test, so client-only rules are checked without a window.

Not done in this change: `PlayerEvents.TICK`, the client-side halves of the interaction events, a
person looking at the paged creative screen and the tooltip in a running client, and everything else
marked `missing` above.
