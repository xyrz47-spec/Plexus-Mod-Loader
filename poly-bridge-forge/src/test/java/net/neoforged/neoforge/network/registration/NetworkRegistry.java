package net.neoforged.neoforge.network.registration;

import java.util.HashMap;
import java.util.Map;

/** Test stand-in for NeoForge's {@code NetworkRegistry}: only the two maps {@code NeoForgeForeignPayloads} reads. */
public final class NetworkRegistry {

    /** Built-in payload id to codec. */
    protected static final Map<Object, Object> BUILTIN_PAYLOADS = new HashMap<>();

    /** Protocol to payload id to registration. */
    protected static final Map<Object, Map<Object, Object>> PAYLOAD_REGISTRATIONS = new HashMap<>();

    private NetworkRegistry() {}

    public static Map<Object, Object> builtIn() {
        return BUILTIN_PAYLOADS;
    }

    public static Map<Object, Map<Object, Object>> registrations() {
        return PAYLOAD_REGISTRATIONS;
    }
}
