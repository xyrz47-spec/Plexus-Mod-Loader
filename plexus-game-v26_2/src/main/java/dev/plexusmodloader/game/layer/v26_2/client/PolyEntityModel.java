package dev.plexusmodloader.game.layer.v26_2.client;

import dev.plexusmodloader.api.content.entity.model.EntityModelDefinition;
import dev.plexusmodloader.api.content.entity.model.KeyframeAnimation;
import dev.plexusmodloader.api.content.entity.model.ModelBox;
import dev.plexusmodloader.api.content.entity.model.ModelPartDefinition;
import dev.plexusmodloader.api.content.entity.model.PartAnimation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A box model built from a mod's {@link EntityModelDefinition}, animated by part and by keyframe
 * animations baked into the game's own {@link net.minecraft.client.animation.KeyframeAnimation}.
 */
final class PolyEntityModel extends EntityModel<PolyMobRenderState> {

    private static final float DEGREES = Mth.DEG_TO_RAD;

    private final Map<ModelPart, PartAnimation> animations;
    private final List<net.minecraft.client.animation.KeyframeAnimation> always;
    private final List<net.minecraft.client.animation.KeyframeAnimation> whileMoving;

    /** In {@link EntityModelDefinition#onDemandAnimations()} order. */
    private final List<net.minecraft.client.animation.KeyframeAnimation> onDemand;

    private final List<String> onDemandNames;

    private PolyEntityModel(
            ModelPart root, Map<ModelPart, PartAnimation> animations, EntityModelDefinition definition) {
        super(root);
        this.animations = animations;
        List<net.minecraft.client.animation.KeyframeAnimation> always = new ArrayList<>();
        List<net.minecraft.client.animation.KeyframeAnimation> whileMoving = new ArrayList<>();
        Map<String, net.minecraft.client.animation.KeyframeAnimation> byName = new LinkedHashMap<>();
        definition.animations().forEach((name, animation) -> {
            net.minecraft.client.animation.KeyframeAnimation baked =
                    toGame(animation).bake(root);
            switch (animation.trigger()) {
                case ALWAYS -> always.add(baked);
                case WHILE_MOVING -> whileMoving.add(baked);
                case ON_DEMAND -> byName.put(name, baked);
            }
        });
        this.always = List.copyOf(always);
        this.whileMoving = List.copyOf(whileMoving);
        this.onDemandNames = definition.onDemandAnimations();
        this.onDemand = onDemandNames.stream().map(byName::get).toList();
    }

    /** The game's form of {@code animation}: degrees to radians, y up to y down, scale factor to offset. */
    static AnimationDefinition toGame(KeyframeAnimation animation) {
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(animation.lengthSeconds());
        if (animation.looping()) {
            builder.looping();
        }
        for (KeyframeAnimation.Channel channel : animation.channels()) {
            Keyframe[] keyframes = channel.keyframes().stream()
                    .map(keyframe -> new Keyframe(
                            keyframe.time(),
                            switch (channel.target()) {
                                case ROTATION -> KeyframeAnimations.degreeVec(keyframe.x(), keyframe.y(), keyframe.z());
                                case POSITION -> KeyframeAnimations.posVec(keyframe.x(), keyframe.y(), keyframe.z());
                                case SCALE -> KeyframeAnimations.scaleVec(keyframe.x(), keyframe.y(), keyframe.z());
                            },
                            switch (keyframe.interpolation()) {
                                case LINEAR -> AnimationChannel.Interpolations.LINEAR;
                                case SMOOTH -> AnimationChannel.Interpolations.CATMULLROM;
                            }))
                    .toArray(Keyframe[]::new);
            AnimationChannel.Target target = switch (channel.target()) {
                case ROTATION -> AnimationChannel.Targets.ROTATION;
                case POSITION -> AnimationChannel.Targets.POSITION;
                case SCALE -> AnimationChannel.Targets.SCALE;
            };
            builder.addAnimation(channel.part(), new AnimationChannel(target, keyframes));
        }
        return builder.build();
    }

    static PolyEntityModel create(EntityModelDefinition definition) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> meshParts = new HashMap<>();
        for (ModelPartDefinition part : definition.parts()) {
            PartDefinition parent = part.parent().map(meshParts::get).orElse(mesh.getRoot());
            CubeListBuilder cubes = CubeListBuilder.create();
            for (ModelBox box : part.boxes()) {
                cubes.texOffs(box.u(), box.v())
                        .mirror(box.mirror())
                        .addBox(box.x(), box.y(), box.z(), box.width(), box.height(), box.depth());
            }
            meshParts.put(
                    part.name(),
                    parent.addOrReplaceChild(
                            part.name(),
                            cubes,
                            PartPose.offsetAndRotation(
                                    part.pivotX(),
                                    part.pivotY(),
                                    part.pivotZ(),
                                    part.rotationX() * DEGREES,
                                    part.rotationY() * DEGREES,
                                    part.rotationZ() * DEGREES)));
        }
        ModelPart root = LayerDefinition.create(mesh, definition.textureWidth(), definition.textureHeight())
                .bakeRoot();
        Map<String, ModelPart> baked = new HashMap<>();
        Map<ModelPart, PartAnimation> animations = new LinkedHashMap<>();
        for (ModelPartDefinition part : definition.parts()) {
            ModelPart parent = part.parent().map(baked::get).orElse(root);
            ModelPart modelPart = parent.getChild(part.name());
            baked.put(part.name(), modelPart);
            if (part.animation() != PartAnimation.NONE) {
                animations.put(modelPart, part.animation());
            }
        }
        return new PolyEntityModel(root, animations, definition);
    }

    @Override
    public void setupAnim(PolyMobRenderState state) {
        super.setupAnim(state);
        float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
        animations.forEach((part, animation) -> {
            switch (animation) {
                case HEAD -> {
                    part.xRot += state.xRot * DEGREES;
                    part.yRot += state.yRot * DEGREES;
                }
                case WALK_SWING -> part.xRot += swing;
                case WALK_SWING_OPPOSITE -> part.xRot -= swing;
                case BOB -> part.y += Mth.sin(state.ageInTicks * 0.1f) * 1.0f;
                case SPIN -> part.yRot += state.ageInTicks * 0.1f;
                case NONE -> {}
            }
        });
        for (net.minecraft.client.animation.KeyframeAnimation animation : always) {
            animation.apply((long) (state.ageInTicks * 50), 1.0f);
        }
        for (net.minecraft.client.animation.KeyframeAnimation animation : whileMoving) {
            animation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0f, 2.5f);
        }
        for (int i = 0; i < onDemand.size() && i < state.animations.length; i++) {
            onDemand.get(i).apply(state.animations[i], state.ageInTicks);
        }
    }

    /** Number of keyframe animations of every trigger. */
    int keyframeAnimations() {
        return always.size() + whileMoving.size() + onDemand.size();
    }

    List<String> onDemandNames() {
        return onDemandNames;
    }

    int animatedParts() {
        return animations.size();
    }
}
