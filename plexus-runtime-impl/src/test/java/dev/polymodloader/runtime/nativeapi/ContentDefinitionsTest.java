package dev.polymodloader.runtime.nativeapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.client.DynamicTexture;
import dev.polymodloader.api.content.AdvancementTrigger;
import dev.polymodloader.api.content.block.BlockDefinition;
import dev.polymodloader.api.content.block.entity.BlockEntityDefinition;
import dev.polymodloader.api.content.block.entity.MenuLayout;
import dev.polymodloader.api.content.block.state.BlockProperty;
import dev.polymodloader.api.content.entity.EntityDefinition;
import dev.polymodloader.api.content.entity.NaturalSpawn;
import dev.polymodloader.api.content.entity.ProjectileDefinition;
import dev.polymodloader.api.content.entity.model.EntityModelDefinition;
import dev.polymodloader.api.content.entity.model.PartAnimation;
import dev.polymodloader.api.content.item.ComponentType;
import dev.polymodloader.api.content.item.ItemComponentKey;
import dev.polymodloader.api.content.particle.ParticleDefinition;
import dev.polymodloader.api.devtools.gametest.TestArea;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.permission.PermissionNode;
import dev.polymodloader.api.permission.PermissionResult;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.worldgen.BiomeDefinition;
import dev.polymodloader.api.worldgen.BiomeSelector;
import dev.polymodloader.api.worldgen.TerrainGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validation of the definitions mods build before declaring content. */
class ContentDefinitionsTest {

    @Test
    void choicePropertiesDefaultToTheirFirstValueAndRejectBadValues() {
        BlockProperty.ChoiceProperty color = BlockProperty.choice("color", "red", "green");
        assertEquals("red", color.defaultValue());
        assertEquals("green", color.withDefault("green").defaultValue());
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.choice("color", "red"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.choice("color", "red", "red"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.choice("color", "red", "Green"));
        assertThrows(IllegalArgumentException.class, () -> color.withDefault("blue"));
    }

    @Test
    void menuLayoutsSizeTheInventoryAndExcludeChestRows() {
        MenuLayout layout = MenuLayout.builder(176, 166)
                .slot(56, 35)
                .outputSlot(116, 35)
                .grid(8, 8, 3, 1)
                .progressBar(79, 34, MenuLayout.ProgressStyle.ARROW, "progress", "total")
                .build();
        assertEquals(5, layout.slots().size());
        assertTrue(layout.slots().get(1).outputOnly());
        assertEquals(84, layout.inventoryY(), "the inventory sits at the game's usual place");
        BlockEntityDefinition entity =
                BlockEntityDefinition.builder().menu(layout, "title").build();
        assertEquals(5, entity.inventorySize());
        assertEquals(
                27, BlockEntityDefinition.builder().inventory(3, "t").build().inventorySize());
        assertThrows(
                IllegalArgumentException.class,
                () -> MenuLayout.builder(176, 166).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MenuLayout.builder(100, 166).slot(0, 0).build());
    }

    @Test
    void spawnsAndProjectilesValidateTheirNumbers() {
        BiomeSelector plains = BiomeSelector.ids(ResourceId.minecraft("plains"));
        assertThrows(IllegalArgumentException.class, () -> new NaturalSpawn(plains, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NaturalSpawn(plains, 1, 3, 2));
        assertEquals(10, new NaturalSpawn(plains, 10, 1, 4).weight());
        ResourceId item = ResourceId.of("demo", "pebble");
        assertEquals(0.03, ProjectileDefinition.builder(item).build().gravity());
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectileDefinition.builder(item).damage(-1).build());
    }

    @Test
    void projectilesAndItemComponentsAreDeclaredOncePerId() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        demo.projectile(
                "pebble",
                ProjectileDefinition.builder(ResourceId.of("demo", "pebble")).build());
        ItemComponentKey<Integer> charge = demo.itemComponent("charge", ComponentType.INTEGER);
        assertEquals(ResourceId.of("demo", "charge"), charge.id());
        assertEquals(List.of(charge), registrations.itemComponents());
        assertEquals(1, registrations.projectiles().size());
        assertThrows(IllegalArgumentException.class, () -> demo.itemComponent("charge", ComponentType.STRING));
        assertThrows(
                IllegalArgumentException.class,
                () -> demo.projectile(
                        "pebble",
                        ProjectileDefinition.builder(ResourceId.of("demo", "pebble"))
                                .build()));
        registrations.close();
        assertThrows(IllegalStateException.class, () -> demo.itemComponent("late", ComponentType.BOOLEAN));
    }

    @Test
    void mobDefinitionsCarryGoalsAndRiding() {
        EntityDefinition definition = EntityDefinition.builder(ResourceId.of("demo", "textures/entity/x.png"))
                .goal(4, () -> mob -> false)
                .targetGoal(1, () -> mob -> false)
                .rideable(true)
                .noGravity()
                .notPushable()
                .build();
        assertEquals(2, definition.goals().size());
        assertTrue(definition.goals().get(1).target());
        assertEquals(EntityDefinition.Riding.STEERABLE, definition.riding());
        assertTrue(definition.noGravity());
        assertTrue(!definition.pushable());
        assertEquals(
                EntityDefinition.Riding.NONE,
                EntityDefinition.builder(ResourceId.of("demo", "t")).build().riding());
    }

    @Test
    void worldgenIdsAreCheckedPerKind() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        TerrainGenerator flat = (x, z, column) -> column.set(column.minY(), ResourceId.minecraft("stone"));
        assertEquals(ResourceId.of("demo", "flat"), demo.chunkGenerator("flat", flat));
        assertEquals(
                ResourceId.of("demo", "flat"),
                demo.biome("flat", BiomeDefinition.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> demo.chunkGenerator("flat", flat));
        assertThrows(
                IllegalArgumentException.class,
                () -> demo.biome("flat", BiomeDefinition.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> demo.chunkGenerator("Bad Name", flat));
        assertEquals(1, registrations.chunkGenerators().size());
        assertEquals(1, registrations.biomes().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> BiomeDefinition.builder().climate(0.5f, 2.0f, true).build());
    }

    @Test
    void entityModelsCheckTheirPartTree() {
        EntityModelDefinition model = EntityModelDefinition.builder(32, 32)
                .part("body", part -> part.pivot(0, 16, 0).box(0, 0, -3, -3, -3, 6, 6, 6))
                .part(
                        "ring",
                        part -> part.parent("body")
                                .box(0, 12, -4, 0, -4, 8, 1, 8)
                                .animation(PartAnimation.SPIN))
                .build();
        assertEquals(2, model.parts().size());
        assertEquals("body", model.parts().get(1).parent().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityModelDefinition.builder(32, 32)
                        .part("ring", part -> part.parent("body"))
                        .part("body", part -> {})
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityModelDefinition.builder(32, 32)
                        .part("body", part -> {})
                        .part("body", part -> {})
                        .build());
        assertThrows(IllegalArgumentException.class, () -> ParticleDefinition.of(0));
    }

    @Test
    void clientVisualsCheckWhatTheyAttachTo() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        demo.block("plain", BlockDefinition.builder().build());
        demo.block(
                "machine",
                BlockDefinition.builder()
                        .blockEntity(BlockEntityDefinition.builder().build())
                        .build());
        demo.client().blockEntityRenderer(ResourceId.of("demo", "machine"), (entity, canvas) -> {});
        assertThrows(
                IllegalArgumentException.class,
                () -> demo.client().blockEntityRenderer(ResourceId.of("demo", "plain"), (entity, canvas) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> demo.client().blockEntityRenderer(ResourceId.of("demo", "machine"), (entity, canvas) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> registrations
                        .forMod("other")
                        .blockEntityRenderer(ResourceId.of("demo", "machine"), (e, c) -> {}));
        DynamicTexture texture = demo.client().dynamicTexture("map", 4, 2);
        assertEquals(ResourceId.of("demo", "dynamic/map"), texture.id());
        assertThrows(IndexOutOfBoundsException.class, () -> texture.setPixel(4, 0, 0));
        assertThrows(IllegalStateException.class, texture::upload, "no game texture yet");
        List<int[]> uploads = new java.util.ArrayList<>();
        registrations.dynamicTextures().getFirst().bind().accept(uploads::add);
        texture.setPixel(1, 1, 0xFF00FF00);
        texture.upload();
        assertEquals(0xFF00FF00, uploads.getFirst()[1 * 4 + 1]);
        assertThrows(IllegalArgumentException.class, () -> demo.client().dynamicTexture("huge", 5000, 1));
    }

    @Test
    void operationsRegistrationsValidateNames() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        NativeRegistrations.ModRegistrars demo = registrations.forMod("demo");
        demo.onDataReload("demo/spells", files -> {});
        demo.onAssetReload("demo_tips", files -> {});
        assertThrows(IllegalArgumentException.class, () -> demo.onDataReload("../escape", files -> {}));
        assertThrows(IllegalArgumentException.class, () -> demo.onDataReload("Upper", files -> {}));
        assertThrows(IllegalArgumentException.class, () -> demo.onDataReload("trailing/", files -> {}));
        assertEquals(2, registrations.reloadListeners().size());
        assertTrue(registrations.reloadListeners().get(1).assets());

        PermissionNode node = demo.node("teleport.others", 2);
        assertEquals("demo.teleport.others", node.id());
        assertThrows(IllegalArgumentException.class, () -> demo.node("teleport.others", 2));
        assertThrows(IllegalArgumentException.class, () -> demo.node("bad..dots", 2));
        assertThrows(IllegalArgumentException.class, () -> demo.node("ok", 5));
        demo.provider((player, permission) -> PermissionResult.UNDECIDED);
        assertEquals(1, registrations.permissionProviders().size());

        assertTrue(demo.entityData("mana", true).syncToOwner());
        assertThrows(IllegalArgumentException.class, () -> demo.entityData("mana", false));
        assertThrows(IllegalArgumentException.class, () -> TestArea.cube(49));
        demo.register("big", 100, TestArea.cube(9), test -> {});
        assertEquals(TestArea.cube(9), registrations.gameTests().getFirst().area());
    }

    @Test
    void advancementTriggersFireOnlyOnceBound() {
        NativeRegistrations registrations = new NativeRegistrations(GameSide.CLIENT);
        AdvancementTrigger trigger = registrations.forMod("demo").advancementTrigger("found_it");
        assertEquals(ResourceId.of("demo", "found_it"), trigger.id());
        PlayerView player = (PlayerView) java.lang.reflect.Proxy.newProxyInstance(
                PlayerView.class.getClassLoader(), new Class<?>[] {PlayerView.class}, (proxy, method, args) -> null);
        assertThrows(IllegalStateException.class, () -> trigger.trigger(player));
        List<PlayerView> fired = new java.util.ArrayList<>();
        registrations.advancementTriggers().getFirst().bind().accept(fired::add);
        trigger.trigger(player);
        assertEquals(List.of(player), fired);
        assertThrows(
                IllegalArgumentException.class,
                () -> registrations.forMod("demo").advancementTrigger("found_it"));
    }
}
