package dev.polymodloader.internal.transform.engine;

/** Hook target for {@code ReplaceConstantReturnWithStaticCall} tests. */
public final class TestHooks {

    private TestHooks() {}

    /** Receivers seen by {@link #onEnter(Object)}. */
    public static final java.util.List<Object> ENTERED = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static String brand() {
        return "from-hook";
    }

    public static void onEnter(Object receiver) {
        ENTERED.add(receiver);
    }
}
