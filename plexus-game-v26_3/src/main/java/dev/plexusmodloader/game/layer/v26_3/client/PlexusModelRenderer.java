package dev.plexusmodloader.game.layer.v26_3.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plexusmodloader.game.layer.v26_3.entity.PlexusMob;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Draws a mod mob with its own box model and texture. */
final class PlexusModelRenderer extends MobRenderer<PlexusMob, PlexusMobRenderState, PlexusEntityModel> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private final Identifier texture;
    private final String typeName;
    private final AtomicBoolean reported = new AtomicBoolean();
    private final Set<String> reportedAnimations = ConcurrentHashMap.newKeySet();

    PlexusModelRenderer(
            EntityRendererProvider.Context context,
            PlexusEntityModel model,
            Identifier texture,
            float shadow,
            String typeName) {
        super(context, model, shadow);
        this.texture = texture;
        this.typeName = typeName;
    }

    @Override
    public PlexusMobRenderState createRenderState() {
        return new PlexusMobRenderState();
    }

    @Override
    public void extractRenderState(PlexusMob mob, PlexusMobRenderState state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        AnimationState[] source = mob.animationStates();
        if (state.animations.length != source.length) {
            state.animations = new AnimationState[source.length];
            for (int i = 0; i < source.length; i++) {
                state.animations[i] = new AnimationState();
            }
        }
        for (int i = 0; i < source.length; i++) {
            state.animations[i].copyFrom(source[i]);
        }
    }

    @Override
    public Identifier getTextureLocation(PlexusMobRenderState state) {
        return texture;
    }

    @Override
    public void submit(
            PlexusMobRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (reported.compareAndSet(false, true)) {
            LOGGER.info(
                    "[Poly Mod Loader] Drew mod mob model {} ({} animated part(s), {} keyframe animation(s))",
                    typeName,
                    getModel().animatedParts(),
                    getModel().keyframeAnimations());
        }
        List<String> names = getModel().onDemandNames();
        for (int i = 0; i < state.animations.length && i < names.size(); i++) {
            if (state.animations[i].isStarted() && reportedAnimations.add(names.get(i))) {
                LOGGER.info("[Poly Mod Loader] Played mod mob animation {} of {}", names.get(i), typeName);
            }
        }
    }
}
