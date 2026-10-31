package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import dev.plexusmodloader.compat.bridge.InjectionPointReplacement.Placement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Where Forge's patch moved the injection points other loaders' Mixins name (ADR-0021 G8). */
class ForgeInjectionPointsTest {

    private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";

    /**
     * Fabric API's {@code PlayerBlockBreakEvents} hook {@code ServerPlayerGameMode.destroyBlock} before {@code
     * Block.playerWillDestroy} (BEFORE, cancellable) and before {@code Block.destroy} (AFTER). Forge's patch calls both
     * inside {@code removeBlock}: BEFORE moves to the first call after the permission checks, before anything changes;
     * AFTER runs after each {@code removeBlock} that removed the block.
     */
    @Test
    void blockBreakingPointsFollowForgesRemoveBlock() {
        List<InjectionPointReplacement> points = ForgeInjectionPoints.all();

        InjectionPointReplacement before = point(points, "Block;playerWillDestroy(");
        assertEquals(GAME_MODE, before.targetClass());
        assertEquals("destroyBlock", before.method());
        assertEquals("Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z", before.replacement());
        assertEquals(Placement.SAME_PLACE, before.placement());

        InjectionPointReplacement after = point(points, "Block;destroy(");
        assertEquals(GAME_MODE, after.targetClass());
        assertEquals("destroyBlock", after.method());
        assertEquals("L" + GAME_MODE + ";removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", after.replacement());
        assertEquals(Placement.AFTER_TRUE_RESULT, after.placement());

        InjectionPointReplacement language = point(points, "Map;copyOf(");
        assertEquals("net/minecraft/locale/Language", language.targetClass());
        assertEquals("loadDefault", language.method());
        assertEquals(
                "Lnet/minecraftforge/server/LanguageHook;captureLanguageMap(Ljava/util/Map;)V", language.replacement());
        assertEquals(Placement.SAME_PLACE, language.placement());

        InjectionPointReplacement useOn = point(points, "Item;useOn(");
        assertEquals("net/minecraft/world/item/ItemStack", useOn.targetClass());
        assertEquals("useOn", useOn.method());
        assertEquals(
                "lambda$useOn$0(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                useOn.replacement());
        assertEquals(Placement.IN_METHOD, useOn.placement());

        InjectionPointReplacement bedOrientation = point(points, "BedBlock;getBedOrientation(");
        assertEquals("net/minecraft/world/entity/LivingEntity", bedOrientation.targetClass());
        assertEquals("getBedOrientation", bedOrientation.method());
        assertEquals(
                "Lnet/minecraft/world/level/block/state/BlockState;getBedDirection(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;",
                bedOrientation.replacement());
        assertEquals(Placement.SAME_PLACE, bedOrientation.placement());

        InjectionPointReplacement setOccupied = point(points, "Level;setBlock(");
        assertEquals("net/minecraft/world/entity/LivingEntity", setOccupied.targetClass());
        assertEquals("lambda$stopSleeping$0", setOccupied.method());
        assertEquals(
                "Lnet/minecraft/world/level/block/state/BlockState;setBedOccupied(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Z)V",
                setOccupied.replacement());
        assertEquals(Placement.SAME_PLACE, setOccupied.placement());

        InjectionPointReplacement saveStable = point(points, "DataProvider;saveStable(");
        assertEquals("net/minecraft/data/tags/TagsProvider", saveStable.targetClass());
        assertEquals("lambda$run$5", saveStable.method());
        assertEquals("lambda$run$6", saveStable.replacement());
        assertEquals(Placement.IN_METHOD, saveStable.placement());

        InjectionPointReplacement tagFileInit = point(points, "TagFile;<init>(");
        assertEquals("net/minecraft/data/tags/TagsProvider", tagFileInit.targetClass());
        assertEquals("lambda$run$5", tagFileInit.method());
        assertEquals(
                "lambda$run$6:Lnet/minecraft/tags/TagFile;<init>(Ljava/util/List;ZLjava/util/List;)V",
                tagFileInit.replacement());
        assertEquals(Placement.IN_METHOD, tagFileInit.placement());

        InjectionPointReplacement updateSwimming = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/Entity")
                        && p.method().equals("updateSwimming")
                        && p.replaced().contains("isInWater"))
                .findFirst()
                .orElseThrow();
        assertEquals("Lnet/minecraft/world/entity/Entity;isInWaterOrSwimmable()Z", updateSwimming.replacement());
        assertEquals(Placement.SAME_PLACE, updateSwimming.placement());

        InjectionPointReplacement isVisuallyCrawling = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/Entity")
                        && p.method().equals("isVisuallyCrawling")
                        && p.replaced().contains("isInWater"))
                .findFirst()
                .orElseThrow();
        assertEquals("Lnet/minecraft/world/entity/Entity;isInWaterOrSwimmable()Z", isVisuallyCrawling.replacement());
        assertEquals(Placement.SAME_PLACE, isVisuallyCrawling.placement());

        InjectionPointReplacement travelInFluid = point(points, "LivingEntity;travelInLava(");
        assertEquals("net/minecraft/world/entity/LivingEntity", travelInFluid.targetClass());
        assertEquals("travelInFluid", travelInFluid.method());
        assertEquals(
                "travelInFluid(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/material/FluidState;)V",
                travelInFluid.replacement());
        assertEquals(Placement.IN_METHOD, travelInFluid.placement());

        InjectionPointReplacement checkInWater = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/vehicle/boat/AbstractBoat")
                        && p.method().equals("checkInWater"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z",
                checkInWater.replacement());
        assertEquals(Placement.SAME_PLACE, checkInWater.placement());

        InjectionPointReplacement isUnderwater = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/vehicle/boat/AbstractBoat")
                        && p.method().equals("isUnderwater"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z",
                isUnderwater.replacement());
        assertEquals(Placement.SAME_PLACE, isUnderwater.placement());

        InjectionPointReplacement standingSwimming = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/Entity")
                        && p.method().equals("updateSwimming")
                        && p.replaced().contains("FluidState;is("))
                .findFirst()
                .orElseThrow();
        assertEquals("Lnet/minecraft/world/entity/Entity;canStartSwimming()Z", standingSwimming.replacement());
        assertEquals(Placement.SAME_PLACE, standingSwimming.placement());

        InjectionPointReplacement customDrowning = point(points, "LivingEntity;isEyeInFluid(");
        assertEquals("net/minecraft/world/entity/LivingEntity", customDrowning.targetClass());
        assertEquals("baseTick", customDrowning.method());
        assertEquals(
                "Lnet/minecraftforge/common/ForgeHooks;onLivingBreathe(Lnet/minecraft/world/entity/LivingEntity;II)V",
                customDrowning.replacement());
        assertEquals(Placement.SAME_PLACE, customDrowning.placement());

        InjectionPointReplacement jumpInFluid = point(points, "LivingEntity;jumpInLiquid(");
        assertEquals("net/minecraft/world/entity/LivingEntity", jumpInFluid.targetClass());
        assertEquals("aiStep", jumpInFluid.method());
        assertEquals(
                "Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/minecraftforge/fluids/FluidType;)V",
                jumpInFluid.replacement());
        assertEquals(Placement.SAME_PLACE, jumpInFluid.placement());

        InjectionPointReplacement furnaceFuel = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity")
                        && p.method().equals("consumeFuel"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                furnaceFuel.replacement());
        assertEquals(Placement.SAME_PLACE, furnaceFuel.placement());

        InjectionPointReplacement brewingRemainder = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/level/block/entity/BrewingStandBlockEntity")
                        && p.method().equals("doBrew"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                brewingRemainder.replacement());
        assertEquals(Placement.SAME_PLACE, brewingRemainder.placement());

        InjectionPointReplacement craftingItem = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/item/crafting/CraftingRecipe")
                        && p.replaced().contains("ItemStack;getItem()"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                craftingItem.replacement());
        assertEquals(Placement.SAME_PLACE, craftingItem.placement());

        InjectionPointReplacement craftingRemainder = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/item/crafting/CraftingRecipe")
                        && p.replaced().contains("Item;getCraftingRemainder()"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;",
                craftingRemainder.replacement());
        assertEquals(Placement.SAME_PLACE, craftingRemainder.placement());

        InjectionPointReplacement enchantmentCheck = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/item/enchantment/EnchantmentHelper")
                        && p.method().equals("lambda$getAvailableEnchantmentResults$0"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/world/item/ItemStack;canApplyAtEnchantingTable(Lnet/minecraft/core/Holder;)Z",
                enchantmentCheck.replacement());
        assertEquals(Placement.SAME_PLACE, enchantmentCheck.placement());

        InjectionPointReplacement dynamicRegistries = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/data/registries/RegistryPatchGenerator")
                        && p.method().equals("lambda$createLookup$0"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Lnet/minecraft/resources/RegistryDataLoader;getWorldGenAndDimensionStream()Ljava/util/stream/Stream;",
                dynamicRegistries.replacement());
        assertEquals(Placement.SAME_PLACE, dynamicRegistries.placement());
    }

    /**
     * {@code ServerPlayer.drop(boolean)}: vanilla drops the removed stack with {@code drop(ItemStack, boolean,
     * boolean)}; Forge calls {@code ForgeHooks.onPlayerTossEvent}, which makes that call itself (Puzzles Lib's item
     * toss event runs before it). {@code PersistentEntitySectionManager.addEntity}: Forge fires its join event and
     * moves the vanilla body, with {@code SectionPos.asLong}, into {@code addEntityWithoutEvent} of the same
     * descriptor (Architectury's entity add event runs there).
     */
    @Test
    void tossAndEntityAddPointsFollowForgesHooks() {
        List<InjectionPointReplacement> points = ForgeInjectionPoints.all();

        InjectionPointReplacement toss = point(points, "ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;ZZ)");
        assertEquals("net/minecraft/server/level/ServerPlayer", toss.targetClass());
        assertEquals("drop", toss.method());
        assertEquals(
                "Lnet/minecraftforge/common/ForgeHooks;onPlayerTossEvent(Lnet/minecraft/world/entity/player/Player;"
                        + "Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
                toss.replacement());
        assertEquals(Placement.SAME_PLACE, toss.placement());

        InjectionPointReplacement addEntity = point(points, "SectionPos;asLong(");
        assertEquals("net/minecraft/world/level/entity/PersistentEntitySectionManager", addEntity.targetClass());
        assertEquals("addEntity", addEntity.method());
        assertEquals(
                "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", addEntity.replacement());
        assertEquals(Placement.IN_METHOD, addEntity.placement());
    }

    /**
     * Forge 26.3-66.1.0's data generators read {@code RegistryDataLoader.getWorldAndDimensionRegistries()} ({@code
     * WORLD_REGISTRIES} followed by {@code DIMENSION_REGISTRIES}) where vanilla reads {@code WORLD_REGISTRIES}, which
     * Fabric API's registry sync redirects in both places.
     */
    @Test
    void worldRegistryReadsFollowForgesWorldAndDimensionList() {
        List<InjectionPointReplacement> points = ForgeInjectionPoints.all().stream()
                .filter(point -> point.replaced()
                        .equals("Lnet/minecraft/resources/RegistryDataLoader;WORLD_REGISTRIES:Ljava/util/List;"))
                .toList();

        assertEquals(
                List.of(
                        "net/minecraft/data/registries/RegistriesDatapackGenerator.forWorldLayer",
                        "net/minecraft/data/registries/RegistryPatchGenerator.lambda$createWorldLookup$0"),
                points.stream()
                        .map(point -> point.targetClass() + "." + point.method())
                        .toList());
        for (InjectionPointReplacement point : points) {
            assertEquals(
                    "Lnet/minecraft/resources/RegistryDataLoader;getWorldAndDimensionRegistries()Ljava/util/List;",
                    point.replacement());
            assertEquals(Placement.FIELD_THEN_HOST_ENTRIES, point.placement());
        }
    }

    /**
     * Forge's {@code Mob.setTarget} (26.2-65.1.5, 26.3-66.1.0) assigns the target of its {@code LivingChangeTargetEvent}
     * where vanilla assigns {@code asValidTarget(target)}; Puzzles Lib 26.3 modifies that value.
     */
    @Test
    void mobTargetFollowsForgesChangeTargetEvent() {
        InjectionPointReplacement setTarget = point(ForgeInjectionPoints.all(), "Mob;asValidTarget(");
        assertEquals("net/minecraft/world/entity/Mob", setTarget.targetClass());
        assertEquals("setTarget", setTarget.method());
        assertEquals(
                "Lnet/minecraftforge/event/entity/living/LivingChangeTargetEvent;getNewTarget()"
                        + "Lnet/minecraft/world/entity/LivingEntity;",
                setTarget.replacement());
        assertEquals(Placement.SAME_PLACE, setTarget.placement());
    }

    @Test
    void neoForgePointsFollowLanguageAndConfigurationPatches() {
        List<InjectionPointReplacement> points = NeoForgeInjectionPoints.all();

        InjectionPointReplacement loadDefault = point(points, "Map;copyOf(");
        assertEquals("net/minecraft/locale/Language", loadDefault.targetClass());
        assertEquals("loadDefault", loadDefault.method());
        assertEquals(
                "Lnet/neoforged/neoforge/server/LanguageHook;captureLanguageMap(Ljava/util/Map;Ljava/util/Map;)V",
                loadDefault.replacement());
        assertEquals(Placement.SAME_PLACE, loadDefault.placement());

        InjectionPointReplacement parseTranslations = point(points, "Class;getResourceAsStream(");
        assertEquals("net/minecraft/locale/Language", parseTranslations.targetClass());
        assertEquals("parseTranslations", parseTranslations.method());
        assertEquals(
                "parseTranslations(Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/lang/String;)V",
                parseTranslations.replacement());
        assertEquals(Placement.IN_METHOD, parseTranslations.placement());

        InjectionPointReplacement startConfiguration = point(points, "SynchronizeRegistriesTask;<init>(");
        assertEquals(
                "net/minecraft/server/network/ServerConfigurationPacketListenerImpl", startConfiguration.targetClass());
        assertEquals("startConfiguration", startConfiguration.method());
        assertEquals("runConfiguration()V", startConfiguration.replacement());
        assertEquals(Placement.IN_METHOD, startConfiguration.placement());

        InjectionPointReplacement useOn = point(points, "Item;useOn(");
        assertEquals("net/minecraft/world/item/ItemStack", useOn.targetClass());
        assertEquals("useOn", useOn.method());
        assertEquals(
                "lambda$useOn$0(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
                useOn.replacement());
        assertEquals(Placement.IN_METHOD, useOn.placement());

        InjectionPointReplacement destroyBlock = point(points, "Block;destroy(");
        assertEquals(GAME_MODE, destroyBlock.targetClass());
        assertEquals("destroyBlock", destroyBlock.method());
        assertEquals(
                "L" + GAME_MODE + ";removeBlock(Lnet/minecraft/core/BlockPos;"
                        + "Lnet/minecraft/world/level/block/state/BlockState;ZLnet/minecraft/world/item/ItemStack;)Z",
                destroyBlock.replacement());
        assertEquals(Placement.AFTER_TRUE_RESULT, destroyBlock.placement());

        InjectionPointReplacement canGlide = point(points, "EquipmentSlot;VALUES:");
        assertEquals("net/minecraft/world/entity/LivingEntity", canGlide.targetClass());
        assertEquals("canGlide", canGlide.method());
        assertEquals("canGlide(Z)Z", canGlide.replacement());
        assertEquals(Placement.IN_METHOD, canGlide.placement());

        InjectionPointReplacement canBeAffected = point(points, "canBeAffected(");
        assertEquals("net/minecraft/world/entity/LivingEntity", canBeAffected.targetClass());
        assertEquals("forceAddEffect", canBeAffected.method());
        assertEquals(
                "Lnet/neoforged/neoforge/common/CommonHooks;canMobEffectBeApplied(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
                canBeAffected.replacement());
        assertEquals(Placement.SAME_PLACE, canBeAffected.placement());

        InjectionPointReplacement spawnSprint = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/Entity")
                        && p.method().equals("spawnSprintParticle"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/particles/BlockParticleOption;",
                spawnSprint.replacement());
        assertEquals(Placement.SAME_PLACE, spawnSprint.placement());

        InjectionPointReplacement checkFall = points.stream()
                .filter(p -> p.targetClass().equals("net/minecraft/world/entity/LivingEntity")
                        && p.method().equals("checkFallDamage"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                "(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/particles/BlockParticleOption;",
                checkFall.replacement());
        assertEquals(Placement.SAME_PLACE, checkFall.placement());

        // NeoForge moved openMenu's body, with its closeContainer() and vanilla send(Packet), into its overload.
        for (String replaced : List.of(
                "ServerPlayer;closeContainer()V",
                "ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V")) {
            InjectionPointReplacement openMenu = point(points, replaced);
            assertEquals("net/minecraft/server/level/ServerPlayer", openMenu.targetClass());
            assertEquals("openMenu", openMenu.method());
            assertEquals(
                    "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
                    openMenu.replacement());
            assertEquals(Placement.IN_METHOD, openMenu.placement());
        }

        // NeoForge calls its registry snapshot where vanilla wrapped System.out and System.err, at the end of the
        // bootstrap; Fabric API's BootstrapMixin (fabric-registry-sync-v0) injects there.
        InjectionPointReplacement bootStrap = point(points, "Bootstrap;wrapStreams()V");
        assertEquals("net/minecraft/server/Bootstrap", bootStrap.targetClass());
        assertEquals("bootStrap", bootStrap.method());
        assertEquals("Lnet/neoforged/neoforge/registries/GameData;vanillaSnapshot()V", bootStrap.replacement());
        assertEquals(Placement.SAME_PLACE, bootStrap.placement());
        assertFalse(
                points.stream()
                        .anyMatch(p -> p.targetClass().equals("net/minecraft/client/gui/Gui")
                                || p.targetClass().equals("net/minecraft/client/resources/model/ModelManager")
                                || p.targetClass().equals("net/minecraft/client/multiplayer/ClientPacketListener")),
                "the 1.2.3 client points named calls vanilla does not make");
    }

    /**
     * Against Forge's patched client classes ({@code -PforgePatchedClient=<forge-<version>-client.jar>}): each declared
     * method no longer makes the replaced call and makes the replacement call, and each helper of an {@code
     * AFTER_TRUE_RESULT} point makes the replaced call. Run for every pinned Forge release whose client jar is at hand.
     */
    @Test
    @EnabledIfSystemProperty(named = "plexus.forgePatchedClient", matches = ".+")
    void everyPointHasItsShapeInForgesPatchedClient() throws IOException {
        try (JarFile jar = new JarFile(
                Path.of(System.getProperty("plexus.forgePatchedClient")).toFile())) {
            for (InjectionPointReplacement point : ForgeInjectionPoints.all()) {
                ClassNode target = read(jar, point.targetClass()).orElseThrow();
                // For IN_METHOD, name(descriptor) or name of the method that took the body (it may be an overload of
                // the same name, as travelInFluid); with ":target" it makes another call there.
                String moved = point.placement() == Placement.IN_METHOD
                        ? point.replacement().replaceFirst(":.*$", "")
                        : null;
                // Every overload of that name (ServerPlayer.drop): none makes the replaced call, one the replacement.
                List<MethodNode> methods = target.methods.stream()
                        .filter(candidate ->
                                candidate.name.equals(point.method()) && (moved == null || !isMethod(candidate, moved)))
                        .toList();
                if (point.placement() == Placement.FIELD_THEN_HOST_ENTRIES
                        && methods.stream().noneMatch(method -> makes(method, point.replacement()))
                        && (methods.isEmpty()
                                || methods.stream().anyMatch(method -> makes(method, point.replaced())))) {
                    // A release without this change: forWorldLayer and getWorldAndDimensionRegistries are 26.3's.
                    continue;
                }
                assertFalse(methods.isEmpty(), "no " + point.targetClass() + "." + point.method());
                assertTrue(
                        methods.stream().noneMatch(method -> makes(method, point.replaced())),
                        point + ": the replaced call is still there");
                if (moved != null) {
                    if (!point.replacement().contains(":")) {
                        assertTrue(
                                target.methods.stream()
                                        .anyMatch(candidate ->
                                                isMethod(candidate, moved) && makes(candidate, point.replaced())),
                                point + ": the method that took the body does not make the call");
                    }
                    continue;
                }
                assertTrue(
                        methods.stream().anyMatch(method -> makes(method, point.replacement())),
                        point + ": the replacement is not there");
                if (point.placement() == Placement.AFTER_TRUE_RESULT) {
                    String helper = point.replacement();
                    String name = helper.substring(helper.indexOf(';') + 1, helper.indexOf('('));
                    String descriptor = helper.substring(helper.indexOf('('));
                    MethodNode helperMethod = target.methods.stream()
                            .filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("no helper " + helper));
                    assertTrue(descriptor.endsWith(")Z"), helper);
                    assertTrue(makes(helperMethod, point.replaced()), point + ": the helper does not make the call");
                    assertEquals(0, helperMethod.access & Opcodes.ACC_STATIC, helper + " is an instance method");
                }
            }
        }
    }

    private static InjectionPointReplacement point(List<InjectionPointReplacement> points, String replaced) {
        return points.stream()
                .filter(point -> point.replaced().contains(replaced))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no point replacing " + replaced));
    }

    /** Whether {@code method} is {@code spec}: {@code name(descriptor)}, or a bare name. */
    private static boolean isMethod(MethodNode method, String spec) {
        int parenthesis = spec.indexOf('(');
        return parenthesis < 0
                ? method.name.equals(spec)
                : method.name.equals(spec.substring(0, parenthesis)) && method.desc.equals(spec.substring(parenthesis));
    }

    /** Whether {@code method} has the call or field access {@code target} (Mixin's notation). */
    private static boolean makes(MethodNode method, String target) {
        String owner = target.substring(1, target.indexOf(';'));
        String rest = target.substring(target.indexOf(';') + 1);
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && (call.name + call.desc).equals(rest)) {
                return true;
            }
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(owner)
                    && (field.name + ":" + field.desc).equals(rest)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ClassNode> read(JarFile jar, String internalName) throws IOException {
        ZipEntry entry = jar.getEntry(internalName + ".class");
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, 0);
            return Optional.of(node);
        }
    }
}
