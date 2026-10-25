package com.example.compat.apiuser;

import java.util.function.Supplier;

/** An entrypoint of a custom key, found by FabricLoader.getEntrypoints. */
public final class Greeting implements Supplier<String> {

    @Override
    public String get() {
        return "hello through a custom entrypoint";
    }
}
