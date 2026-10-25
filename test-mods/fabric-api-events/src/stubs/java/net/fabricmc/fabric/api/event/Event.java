package net.fabricmc.fabric.api.event;

/** Compile-time stand-in for Fabric API's {@code Event}; never packaged. */
public abstract class Event<T> {

    public final T invoker() {
        throw new UnsupportedOperationException("stub");
    }

    public abstract void register(T listener);
}
