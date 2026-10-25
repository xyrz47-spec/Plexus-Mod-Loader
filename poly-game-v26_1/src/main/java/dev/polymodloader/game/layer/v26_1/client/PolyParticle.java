package dev.polymodloader.game.layer.v26_1.client;

import dev.polymodloader.api.content.particle.ParticleDefinition;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A mod particle: a sprite from the particle's sprite set, animated over its life. */
final class PolyParticle extends SingleQuadParticle {

    private final SpriteSet sprites;
    private final boolean glowing;

    private PolyParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xa,
            double ya,
            double za,
            SpriteSet sprites,
            ParticleDefinition definition) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.glowing = definition.glowing();
        this.lifetime = definition.lifetimeTicks();
        this.gravity = definition.gravity();
        this.quadSize *= definition.scale();
        int tint = definition.tint();
        setColor(((tint >> 16) & 0xFF) / 255.0f, ((tint >> 8) & 0xFF) / 255.0f, (tint & 0xFF) / 255.0f);
        setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return glowing ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    /** Creates {@link PolyParticle}s of one mod particle type. */
    static final class Provider implements ParticleProvider<SimpleParticleType> {

        private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

        private final SpriteSet sprites;
        private final ParticleDefinition definition;
        private final String typeName;
        private final AtomicBoolean reported = new AtomicBoolean();

        Provider(SpriteSet sprites, ParticleDefinition definition, String typeName) {
            this.sprites = sprites;
            this.definition = definition;
            this.typeName = typeName;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xAux,
                double yAux,
                double zAux,
                RandomSource random) {
            if (reported.compareAndSet(false, true)) {
                LOGGER.info("[Poly Mod Loader] Created mod particle {}", typeName);
            }
            return new PolyParticle(level, x, y, z, xAux, yAux, zAux, sprites, definition);
        }
    }
}
