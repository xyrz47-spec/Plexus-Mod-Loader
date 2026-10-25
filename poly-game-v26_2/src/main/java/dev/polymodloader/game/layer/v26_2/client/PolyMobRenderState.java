package dev.polymodloader.game.layer.v26_2.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** What a mod mob's box model needs per frame: the living-entity state plus on-demand animations. */
final class PolyMobRenderState extends LivingEntityRenderState {

    /** Copies of the mob's on-demand animation states, in the model's on-demand order. */
    AnimationState[] animations = new AnimationState[0];
}
