package dev.plexusmodloader.internal.transform.engine.fixture;

import dev.plexusmodloader.internal.transform.engine.TestHooks;

/** Hooks for {@link HookTarget}; defined by the same class loader as the transformed target. */
public final class HookTargetHooks {

    private HookTargetHooks() {}

    public static String describe(HookTarget target, String input, long weight) {
        return input.equals("intercept") ? "intercepted:" + weight : null;
    }

    public static boolean act(HookTarget target, int times) {
        target.log.add("hook:" + times);
        return times < 0;
    }

    public static void label(int n) {
        TestHooks.ENTERED.add("label:" + n);
    }

    public static void constructed(HookTarget target) {
        target.log.add("after-constructor");
    }

    public static String labelReturned(int n, String returned) {
        return returned + "!" + n;
    }

    public static int countReturned(HookTarget target, int returned) {
        return returned + 100;
    }

    public static Integer countOverride(HookTarget target) {
        return target.log.contains("override") ? 42 : null;
    }
}
