package dev.plexusmodloader.compat.fabric.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Names {@link PolyMixinService} to Mixin ({@code mixin.bootstrapService}). Everything else is done by the bridge. */
public final class PolyMixinServiceBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "Poly Mod Loader";
    }

    @Override
    public String getServiceClassName() {
        return PolyMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
        // The Fabric bridge prepares the launch before it starts Mixin.
    }
}
