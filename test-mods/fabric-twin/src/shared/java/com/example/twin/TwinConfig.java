package com.example.twin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class both builds of the twin mod have, as Cloth Config's {@code AutoConfig}: registering a configuration a
 * second time fails, so a game that runs both builds' initialisation stops (ADR-0026 37).
 */
public final class TwinConfig {

    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private TwinConfig() {}

    /** Registers {@code name} for the build {@code build}; fails when it is registered already. */
    public static void register(String name, String build) {
        if (!REGISTERED.add(name)) {
            throw new IllegalStateException("Config '" + name + "' already registered");
        }
        System.out.println("[twin_mod] config " + name + " registered by the " + build + " build");
    }
}
