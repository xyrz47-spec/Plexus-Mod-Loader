package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.entity.EntityDefinition;
import dev.polymodloader.api.content.entity.model.EntityModelDefinition;
import dev.polymodloader.api.content.entity.model.KeyframeAnimation;
import dev.polymodloader.api.content.entity.model.PartAnimation;
import dev.polymodloader.api.content.particle.ParticleDefinition;
import dev.polymodloader.api.resource.ResourceId;

/** Visual content: a flying mob with its own box model and keyframe animations, and a particle type. */
final class VisualContent {

    static final ResourceId WISP = ResourceId.of(NativeShowcaseMod.MOD_ID, "wisp");
    static final ResourceId SPARKLE = ResourceId.of(NativeShowcaseMod.MOD_ID, "sparkle");

    private VisualContent() {}

    static void declare(ContentRegistrar content) {
        content.entity(
                "wisp",
                EntityDefinition.builder(ResourceId.of(NativeShowcaseMod.MOD_ID, "textures/entity/wisp.png"))
                        .size(0.6f, 0.8f)
                        .maxHealth(6)
                        .noGravity()
                        .flying(0.5)
                        .spawnEgg(CreativeTab.SPAWN_EGGS)
                        .model(EntityModelDefinition.builder(32, 32)
                                .part(
                                        "body",
                                        part -> part.pivot(0, 16, 0)
                                                .box(0, 0, -3, -3, -3, 6, 6, 6)
                                                .animation(PartAnimation.BOB))
                                .part(
                                        "ring",
                                        part -> part.parent("body")
                                                .pivot(0, 0, 0)
                                                .box(0, 12, -4, 0, -4, 8, 1, 8)
                                                .animation(PartAnimation.SPIN))
                                // Keyframe animations: a slow pulse all the time, a wave the server starts.
                                .animation(
                                        "pulse",
                                        KeyframeAnimation.builder(2)
                                                .looping()
                                                .trigger(KeyframeAnimation.Trigger.ALWAYS)
                                                .smooth()
                                                .scale("body", 0, 1, 1, 1)
                                                .scale("body", 1, 1.15f, 1.15f, 1.15f)
                                                .scale("body", 2, 1, 1, 1)
                                                .build())
                                .animation(
                                        "wave",
                                        KeyframeAnimation.builder(1)
                                                .rotation("ring", 0, 0, 0, 0)
                                                .rotation("ring", 0.5f, 45, 0, 0)
                                                .rotation("ring", 1, 0, 0, 0)
                                                .position("ring", 0, 0, 0, 0)
                                                .position("ring", 0.5f, 0, 3, 0)
                                                .position("ring", 1, 0, 0, 0)
                                                .build())
                                .build())
                        .build());
        content.particle("sparkle", ParticleDefinition.of(16).asGlowing().withTint(0xFFD54F));
    }
}
