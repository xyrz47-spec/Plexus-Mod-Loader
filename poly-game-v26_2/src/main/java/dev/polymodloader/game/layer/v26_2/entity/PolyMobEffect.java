package dev.polymodloader.game.layer.v26_2.entity;

import dev.polymodloader.api.content.effect.EffectDefinition;
import dev.polymodloader.api.entity.LivingEntityView;
import dev.polymodloader.game.layer.v26_2.Views;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** A mod status effect that runs the mod's behaviour every {@code tickInterval} ticks. */
final class PolyMobEffect extends MobEffect {

    private final EffectDefinition definition;

    PolyMobEffect(MobEffectCategory category, int color, EffectDefinition definition) {
        super(category, color);
        this.definition = definition;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return remainingDuration % definition.tickInterval() == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        definition.behavior().onTick((LivingEntityView) Views.entity(entity), amplifier);
        return true;
    }
}
