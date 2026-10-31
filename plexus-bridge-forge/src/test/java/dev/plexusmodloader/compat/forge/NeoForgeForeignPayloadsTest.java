package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.ForeignPayloads;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.List;
import java.util.Map;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.payload.StandInRegisterPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ADR-0026 40: Fabric API's networking and NeoForge's on one connection. Fabric API's codec owns {@code
 * minecraft:register} on the wire; NeoForge's own register payload is written with NeoForge's codec, and NeoForge's
 * channel checks leave Fabric's payloads alone (before, NeoForge refused to send Fabric API's registry sync and the
 * client could not join). Stand-ins for NeoForge's {@code NetworkRegistry} and Minecraft's payload type.
 */
class NeoForgeForeignPayloadsTest {

    private static final Object FABRIC_CODEC = "Fabric API's minecraft:register codec";
    private static final Object NEOFORGE_CODEC = "NeoForge's minecraft:register codec";
    private static final Object CONFIGURATION = "CONFIGURATION";

    /** Fabric API's own object for {@code minecraft:register}. */
    private record FabricRegistration() implements CustomPacketPayload {
        @Override
        public Type type() {
            return new Type("minecraft:register");
        }
    }

    /** A payload of a Fabric mod's own channel. */
    private record FabricModPayload() implements CustomPacketPayload {
        @Override
        public Type type() {
            return new Type("fabric:registry/sync");
        }
    }

    /** A payload of a NeoForge mod's registered channel. */
    private record NeoForgeModPayload() implements CustomPacketPayload {
        @Override
        public Type type() {
            return new Type("neoforge_hello:ping");
        }
    }

    @BeforeEach
    void installHost() {
        NetworkRegistry.builtIn().clear();
        NetworkRegistry.registrations().clear();
        NetworkRegistry.builtIn().put("minecraft:register", NEOFORGE_CODEC);
        NetworkRegistry.registrations().put(CONFIGURATION, Map.of("neoforge_hello:ping", "registration"));
        NeoForgeForeignPayloads.install(NeoForgeForeignPayloadsTest.class.getClassLoader());
    }

    @Test
    void neoForgesOwnRegisterPayloadIsWrittenWithNeoForgesCodec() {
        assertSame(NEOFORGE_CODEC, ForeignPayloads.encoderFor(new StandInRegisterPayload(), FABRIC_CODEC));
        assertSame(FABRIC_CODEC, ForeignPayloads.encoderFor(new FabricRegistration(), FABRIC_CODEC));
        assertSame(FABRIC_CODEC, ForeignPayloads.encoderFor(new FabricModPayload(), FABRIC_CODEC));
    }

    @Test
    void neoForgesChannelChecksLeaveOtherLoadersPayloadsAlone() {
        assertFalse(ForeignPayloads.foreign(new StandInRegisterPayload(), CONFIGURATION));
        assertFalse(ForeignPayloads.foreign(new NeoForgeModPayload(), CONFIGURATION));
        assertTrue(ForeignPayloads.foreign(new FabricRegistration(), CONFIGURATION));
        assertTrue(ForeignPayloads.foreign(new FabricModPayload(), CONFIGURATION));
        assertTrue(ForeignPayloads.foreign(new NeoForgeModPayload(), "PLAY"));
    }

    @Test
    void theRulesStartNeoForgesFourChecksAndEndItsBundleFilterWithTheGameLayersHooks() {
        List<TransformRuleDefinition> rules =
                NeoForgeForeignPayloads.rules("26.2", "dev/plexusmodloader/game/layer/v26_2");

        assertEquals(5, rules.size());
        assertTrue(rules.stream()
                .anyMatch(rule -> rule.ruleId().equals("plexus:neoforge_bundle_filter")
                        && rule.action().describe().contains("keepOtherLoadersBundledPayloads before each return")));
        assertTrue(rules.stream()
                .allMatch(rule ->
                        rule.action().describe().contains("dev.plexusmodloader.game.layer.v26_2.HostPayloadHooks")));
    }
}
