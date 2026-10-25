package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/** Compile-time stand-in for Fabric API's {@code ServerLifecycleEvents}; never packaged. */
public final class ServerLifecycleEvents {

    public static final Event<ServerStarted> SERVER_STARTED = null;

    private ServerLifecycleEvents() {}

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }
}
