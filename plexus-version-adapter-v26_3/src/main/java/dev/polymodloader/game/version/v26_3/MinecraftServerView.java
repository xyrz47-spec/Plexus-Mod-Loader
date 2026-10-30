package dev.polymodloader.game.version.v26_3;

import dev.polymodloader.api.event.server.ServerView;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * {@link ServerView} over a Minecraft 26.3 {@code net.minecraft.server.MinecraftServer}. Members are
 * resolved once with exact names and descriptors (design doc §5.5); if any signature differs the
 * resolution fails with a clear error instead of picking a similar method.
 */
final class MinecraftServerView implements ServerView {

    private static final String SERVER_CLASS = "net.minecraft.server.MinecraftServer";

    /** Method handles for one loaded {@code MinecraftServer} class. */
    record Accessors(MethodHandle tickCount, MethodHandle playerCount, MethodHandle motd, MethodHandle dedicated) {

        static Accessors resolve(Class<?> anyServerClass) {
            Class<?> serverClass = anyServerClass;
            while (serverClass != null && !serverClass.getName().equals(SERVER_CLASS)) {
                serverClass = serverClass.getSuperclass();
            }
            if (serverClass == null) {
                throw new IllegalStateException(anyServerClass.getName() + " does not extend " + SERVER_CLASS);
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                return new Accessors(
                        lookup.findVirtual(serverClass, "getTickCount", MethodType.methodType(int.class)),
                        lookup.findVirtual(serverClass, "getPlayerCount", MethodType.methodType(int.class)),
                        lookup.findVirtual(serverClass, "getMotd", MethodType.methodType(String.class)),
                        lookup.findVirtual(serverClass, "isDedicatedServer", MethodType.methodType(boolean.class)));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(
                        "PLEXUS-EVENT-002 Minecraft 26.3 server members did not match the adapter: " + e.getMessage(),
                        e);
            }
        }
    }

    private final Object server;
    private final Accessors accessors;

    MinecraftServerView(Object server, Accessors accessors) {
        this.server = server;
        this.accessors = accessors;
    }

    @Override
    public int tickCount() {
        return (int) invoke(accessors.tickCount());
    }

    @Override
    public int playerCount() {
        return (int) invoke(accessors.playerCount());
    }

    @Override
    public String motd() {
        return (String) invoke(accessors.motd());
    }

    @Override
    public boolean dedicated() {
        return (boolean) invoke(accessors.dedicated());
    }

    private Object invoke(MethodHandle handle) {
        try {
            return handle.invoke(server);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Minecraft server accessor failed", e);
        }
    }
}
