package com.example.compat.loaderuser;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.impl.launch.knot.Knot;

/**
 * Uses Fabric Loader's own launcher Knot, which Poly Mod Loader does not provide (it provides Fabric Loader's public API and
 * stand-ins of the internal classes mods use); the link check must block it.
 */
public final class LoaderUser implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println(Knot.class.getName());
    }
}
