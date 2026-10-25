package com.example.poly.showcase;

import dev.polymodloader.api.content.ContentRegistrar;
import dev.polymodloader.api.content.CreativeTab;
import dev.polymodloader.api.content.effect.EffectCategory;
import dev.polymodloader.api.content.effect.EffectDefinition;
import dev.polymodloader.api.content.entity.EntityBehavior;
import dev.polymodloader.api.content.entity.EntityDefinition;
import dev.polymodloader.api.content.entity.EntityInteraction;
import dev.polymodloader.api.content.rule.GameRuleCategory;
import dev.polymodloader.api.content.rule.GameRuleKey;
import dev.polymodloader.api.event.EventRegistry;
import dev.polymodloader.api.event.entity.EntityEvents;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.InteractionResult;
import dev.polymodloader.api.worldgen.BiomeSelector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entities and gameplay: a friendly mob with a spawn egg, a healing status effect, two game rules,
 * a sound event, and damage and death listeners.
 */
final class CreatureContent {

    static final ResourceId GOLEM = ResourceId.of(NativeShowcaseMod.MOD_ID, "golem_friend");
    static final ResourceId GOLEM_EGG = ResourceId.of(NativeShowcaseMod.MOD_ID, "golem_friend_spawn_egg");
    static final ResourceId GLOWING_HEART = ResourceId.of(NativeShowcaseMod.MOD_ID, "glowing_heart");
    static final ResourceId CHIME = ResourceId.of(NativeShowcaseMod.MOD_ID, "chime");
    static final AtomicInteger GOLEM_DEATHS = new AtomicInteger();
    static volatile ResourceId lastGolemDamageType = ResourceId.minecraft("generic");

    static GameRuleKey<Boolean> golemsGreetPlayers;
    static GameRuleKey<Integer> sparkMultiplier;

    private CreatureContent() {}

    static void declare(ContentRegistrar content, EventRegistry events) {
        content.entity(
                "golem_friend",
                EntityDefinition.builder(ResourceId.of(NativeShowcaseMod.MOD_ID, "textures/entity/golem_friend.png"))
                        .maxHealth(20)
                        .movementSpeed(0.25)
                        .panicWhenHurt()
                        .temptedBy(NativeShowcaseMod.SPARK)
                        .spawnEgg(CreativeTab.SPAWN_EGGS)
                        // Plains, and desert: the game test world of Minecraft 26.3 and later is desert.
                        .spawnsNaturally(
                                BiomeSelector.ids(ResourceId.minecraft("plains"), ResourceId.minecraft("desert")),
                                5,
                                1,
                                2)
                        .goal(4, SeekGlowBlockGoal::new)
                        .rideable(true)
                        .behavior(new GolemBehavior())
                        .build());
        content.effect(
                "glowing_heart", new EffectDefinition(EffectCategory.BENEFICIAL, 0xFFD54F, 20, (entity, amplifier) -> {
                    if (entity.health() < entity.maxHealth()) {
                        entity.setHealth(entity.health() + 1 + amplifier);
                    }
                }));
        golemsGreetPlayers = content.booleanRule("golems_greet_players", true, GameRuleCategory.MOBS);
        sparkMultiplier = content.integerRule("spark_multiplier", 1, 1, 8, GameRuleCategory.DROPS);
        content.sound("chime");

        // Damage above 100 at once never reaches a golem friend; golem deaths are counted.
        events.register(EntityEvents.ALLOW_DAMAGE, (entity, damage) -> {
            if (entity.type().equals(GOLEM)) {
                lastGolemDamageType = damage.type();
            }
            return !(entity.type().equals(GOLEM) && damage.amount() > 100);
        });
        // Golem friends shrug off half of all magic damage.
        events.register(
                EntityEvents.MODIFY_DAMAGE,
                (entity, damage) -> entity.type().equals(GOLEM) && damage.type().equals(ResourceId.minecraft("magic"))
                        ? damage.amount() / 2
                        : damage.amount());
        events.register(EntityEvents.DIED, (entity, cause) -> {
            if (entity.type().equals(GOLEM)) {
                GOLEM_DEATHS.incrementAndGet();
                NativeShowcaseMod.LOGGER.info("[{}] A golem friend died ({})", NativeShowcaseMod.MOD_ID, cause.type());
            }
        });
    }

    /** Using a golem friend with an empty hand makes it chime and heal itself. */
    static final class GolemBehavior implements EntityBehavior {
        @Override
        public InteractionResult onInteract(EntityInteraction interaction) {
            if (!interaction.world().isClientSide()) {
                interaction.entity().addEffect(GLOWING_HEART, 100, 0);
                interaction.world().playSound(interaction.entity().blockPos(), CHIME, 1.0f, 1.0f);
                interaction.player().sendMessage("The golem friend glows");
            }
            return InteractionResult.SUCCESS;
        }
    }
}
