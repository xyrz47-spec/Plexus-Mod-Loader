package dev.plexusmodloader.game.layer.v26_1.client;

import dev.plexusmodloader.game.layer.v26_1.entity.PlexusMob;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Draws a mod mob with the player-shaped model and the mod's 64×64 skin texture. */
final class PlexusHumanoidRenderer
        extends HumanoidMobRenderer<PlexusMob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private final Identifier texture;

    PlexusHumanoidRenderer(EntityRendererProvider.Context context, Identifier texture) {
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
