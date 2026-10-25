package com.example.compat.quilthello;

import org.quiltmc.loader.api.Requires;
import org.quiltmc.loader.api.minecraft.ClientOnly;
import org.quiltmc.loader.api.minecraft.DedicatedServerOnly;

/**
 * Members Quilt Loader strips by their annotations: on a server the {@code @ClientOnly} method goes, and the field that
 * {@code @Requires} a mod nobody installed goes everywhere. The stripped fields have no initializer, since Quilt Loader
 * removes the field, not the code that would assign it.
 */
public final class SidedMembers {

    @Requires("absent_mod")
    public static String requiresAbsentMod;

    @Requires("quilt_loader")
    public static String requiresQuiltLoader;

    private SidedMembers() {}

    @ClientOnly
    public static String clientOnly() {
        return "client";
    }

    @DedicatedServerOnly
    public static String dedicatedServerOnly() {
        return "server";
    }
}
