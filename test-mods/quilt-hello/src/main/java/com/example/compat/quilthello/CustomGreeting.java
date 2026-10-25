package com.example.compat.quilthello;

/** A user entrypoint ({@code quilt_hello:custom}); nothing runs it, the mod gets it through Quilt Loader's API. */
public final class CustomGreeting implements Greeting {

    @Override
    public String greet() {
        return "hello through a Quilt custom entrypoint";
    }
}
