package dev.plexusmodloader.game.layer.v26_3.content;

import dev.plexusmodloader.api.content.particle.ParticleDefinition;
import dev.plexusmodloader.game.layer.v26_3.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.ParticleDeclaration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers mod particle types; clients draw them with {@code client.PlexusParticle}. */
public final class Particles {

    private static final Map<ParticleType<?>, ParticleDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private Particles() {}

    public static void install(List<ParticleDeclaration> declarations) {
        for (ParticleDeclaration declaration : declarations) {
            ModParticleType type = new ModParticleType();
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, Views.id(declaration.id()), type);
            DEFINITIONS.put(type, declaration.definition());
            declaration.registered().run();
        }
    }

    /** Mod particle types with their definitions. */
    public static Map<ParticleType<?>, ParticleDefinition> definitions() {
        return Map.copyOf(DEFINITIONS);
    }

    /** A particle type without options; the game's constructor is protected. */
    public static final class ModParticleType extends SimpleParticleType {
        ModParticleType() {
            super(false);
        }
    }
}
