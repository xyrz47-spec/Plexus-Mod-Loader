package dev.plexusmodloader.compat.fabric.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Names {@link PlexusMixinService} to Mixin ({@code mixin.bootstrapService}). Everything else is done by the bridge. */
public final class PlexusMixinServiceBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "Plexus Mod Loader";
    }

    @Override
    public String getServiceClassName() {
        return PlexusMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
        // The Fabric bridge prepares the launch before it starts Mixin.
    }
}
