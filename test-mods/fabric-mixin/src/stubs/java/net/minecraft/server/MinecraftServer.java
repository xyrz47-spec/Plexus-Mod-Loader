package net.minecraft.server;

import com.example.compat.mixin.Injected;

/**
 * Compile-time stand-in for the 26.2 class the mixins target; never packaged. It implements the interfaces the mod's
 * class tweaker injects, as Loom's game jar does.
 */
public abstract class MinecraftServer implements Injected.Greeting, Injected.Tagged<String> {

    public String getServerModName() {
        throw new AssertionError("stub");
    }
}
