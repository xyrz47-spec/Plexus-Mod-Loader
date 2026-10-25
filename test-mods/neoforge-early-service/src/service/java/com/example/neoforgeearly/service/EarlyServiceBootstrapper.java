package com.example.neoforgeearly.service;

import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

/**
 * The early service of {@code neoforge-early-service}: FML runs every {@link GraphicsBootstrapper} it finds in early
 * service jars before it discovers mods, on servers too. This one only reports that it ran.
 */
public final class EarlyServiceBootstrapper implements GraphicsBootstrapper {

    @Override
    public String name() {
        return "neoforge_early_service";
    }

    @Override
    public void bootstrap(String[] arguments) {
        System.out.println("[neoforge_early_service] early service ran before mod discovery");
    }
}
