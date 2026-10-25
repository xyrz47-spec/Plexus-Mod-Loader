package com.example.poly.transformdemo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook for the {@code transform_demo_mod:tick_start_nanos} redirect in {@code poly.mod.json}: the first
 * {@code Util.getNanos()} call in {@code MinecraftServer.tickServer} comes here instead. Its descriptor
 * {@code ()J} matches the redirected call, so this class needs no Minecraft types at compile time.
 */
public final class TickHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("transform_demo_mod");
    private static final AtomicLong TICKS = new AtomicLong();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private TickHooks() {}

    /** Replaces {@code Util.getNanos()} at the start of a server tick; returns the same clock. */
    public static long tickStartNanos() {
        TICKS.incrementAndGet();
        if (REPORTED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[transform_demo_mod] tickServer start time comes from the mod hook; "
                            + "MinecraftServer.tickCount is public: {}",
                    tickCountIsPublic());
        }
        return System.nanoTime();
    }

    /** How many server ticks the hook has seen. */
    public static long ticks() {
        return TICKS.get();
    }

    /** Checks the {@code accessWidenings} entry: the field was private in the game jar. */
    private static boolean tickCountIsPublic() {
        try {
            Field field = Class.forName("net.minecraft.server.MinecraftServer", false, TickHooks.class.getClassLoader())
                    .getDeclaredField("tickCount");
            return Modifier.isPublic(field.getModifiers());
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            LOGGER.error("[transform_demo_mod] cannot inspect MinecraftServer.tickCount", e);
            return false;
        }
    }
}
