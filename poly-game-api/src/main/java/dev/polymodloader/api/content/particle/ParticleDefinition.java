package dev.polymodloader.api.content.particle;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * A particle type. Clients draw it with the sprites listed in
 * {@code assets/<mod id>/particles/<path>.json} (textures under {@code textures/particle/}), changing
 * sprite over its life when several are listed. Spawn it with
 * {@link dev.polymodloader.api.world.WorldView#spawnParticles}.
 *
 * @param lifetimeTicks how long one particle lives
 * @param gravity downward acceleration (0 floats, 0.04 like falling dust)
 * @param scale size multiplier (1 is the game's default)
 * @param glowing drawn at full brightness, also in the dark
 * @param tint colour multiplied into the sprite, {@code 0xRRGGBB} ({@code 0xFFFFFF} keeps it)
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ParticleDefinition(int lifetimeTicks, float gravity, float scale, boolean glowing, int tint) {

    public ParticleDefinition {
        if (lifetimeTicks < 1 || scale <= 0) {
            throw new IllegalArgumentException("lifetimeTicks must be >= 1 and scale > 0");
        }
    }

    /** Lives {@code lifetimeTicks}, no gravity, default size, normal lighting, no tint. */
    public static ParticleDefinition of(int lifetimeTicks) {
        return new ParticleDefinition(lifetimeTicks, 0, 1, false, 0xFFFFFF);
    }

    /** A copy with this downward acceleration (0 floats). */
    public ParticleDefinition withGravity(float value) {
        return new ParticleDefinition(lifetimeTicks, value, scale, glowing, tint);
    }

    /** A copy with this size multiplier. */
    public ParticleDefinition withScale(float value) {
        return new ParticleDefinition(lifetimeTicks, gravity, value, glowing, tint);
    }

    /** A copy drawn at full brightness, also in the dark. */
    public ParticleDefinition asGlowing() {
        return new ParticleDefinition(lifetimeTicks, gravity, scale, true, tint);
    }

    /** A copy with this colour, {@code 0xRRGGBB}, multiplied into the sprite. */
    public ParticleDefinition withTint(int rgb) {
        return new ParticleDefinition(lifetimeTicks, gravity, scale, glowing, rgb);
    }
}
