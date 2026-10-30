package dev.plexusmodloader.game.layer.v26_3.client;

import dev.plexusmodloader.game.layer.v26_3.entity.PolyMob;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Draws a mod mob with the player-shaped model and the mod's 64×64 skin texture. */
final class PolyHumanoidRenderer
        extends HumanoidMobRenderer<PolyMob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private final Identifier texture;

    PolyHumanoidRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.texture = texture;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }
}
