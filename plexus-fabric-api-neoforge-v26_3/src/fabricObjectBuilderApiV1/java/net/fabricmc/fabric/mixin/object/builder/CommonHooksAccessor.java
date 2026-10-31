package net.fabricmc.fabric.mixin.object.builder;

import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Plexus Mod Loader (ADR-0031): NeoForge's default attributes, which DefaultAttributes.getSupplier reads before the
// vanilla ones (NeoForge adds its attributes to every living entity type there); CommonHooks.getAttributesView is an
// unmodifiable view of the same map.
@Mixin(CommonHooks.class)
public interface CommonHooksAccessor {
    @Accessor("FORGE_ATTRIBUTES")
    static Map<EntityType<? extends LivingEntity>, AttributeSupplier> getForgeAttributes() {
        throw new AssertionError("mixin dummy");
    }
}
