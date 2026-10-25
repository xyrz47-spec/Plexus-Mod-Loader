package com.example.compat.mixin;

/**
 * Interfaces the mod's class tweaker injects into {@code MinecraftServer} ({@code inject-interface}); packed into the
 * mod jar. The compile-time stub of {@code MinecraftServer} implements them, as Loom's game jar does.
 */
public final class Injected {

    private Injected() {}

    /** A nested interface: the game class gets an {@code InnerClasses} entry for it. */
    public interface Greeting {
        default String fabricMixin$greeting() {
            return "hello from an injected interface";
        }
    }

    /** A generic interface: the game class's signature gets {@code Injected$Tagged<Ljava/lang/String;>}. */
    public interface Tagged<T> {
        default String fabricMixin$tag() {
            return "tagged";
        }
    }
}
