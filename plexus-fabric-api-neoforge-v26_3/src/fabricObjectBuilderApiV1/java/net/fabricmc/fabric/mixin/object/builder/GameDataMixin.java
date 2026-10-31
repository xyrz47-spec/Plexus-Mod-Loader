package net.fabricmc.fabric.mixin.object.builder;

import net.fabricmc.fabric.impl.object.builder.FabricBlockEntityTypeImpl;
import net.fabricmc.fabric.impl.object.builder.FabricDefaultAttributeRegistryImpl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Plexus Mod Loader (ADR-0031), in place of Forgified Fabric API's FabricObjectBuilderImpl listeners on its NeoForge
// mod bus (FMLCommonSetupEvent, EntityAttributeModificationEvent). Fabric API applies attribute modifications and added
// valid blocks when the registries freeze after mod initialisation. Next to NeoForge, Fabric mods initialise after
// NeoForge's registration (postRegisterEvents, whose CommonHooks.modifyAttributes builds the attributes
// DefaultAttributes
// reads first) on both sides (ADR-0026 31), with the registries reopened and then frozen again with freezeData
// (NeoForgeRegistryOpening): that freezeData is the point. NeoForge's own freezeData right after its registration finds
// no listener and no added block yet. A freezeData before NeoForge's registration is left out: NeoForge's attributes
// are
// not bound then.
@Mixin(GameData.class)
abstract class GameDataMixin {
    @Unique
    private static boolean fabric$registrationEventsPosted;

    @Inject(method = "postRegisterEvents", at = @At("TAIL"))
    private static void markRegistrationEventsPosted(CallbackInfo info) {
        fabric$registrationEventsPosted = true;
    }

    @Inject(method = "freezeData", at = @At("TAIL"))
    private static void applyFabricObjectBuilders(CallbackInfo info) {
        if (!fabric$registrationEventsPosted) {
            return;
        }
        FabricDefaultAttributeRegistryImpl.invokeModify();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ((FabricBlockEntityTypeImpl) type).modifyValidBlocks();
        }
    }
}
