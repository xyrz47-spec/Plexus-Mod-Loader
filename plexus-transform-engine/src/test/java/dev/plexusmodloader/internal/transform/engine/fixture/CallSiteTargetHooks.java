package dev.plexusmodloader.internal.transform.engine.fixture;

import java.util.ArrayList;
import java.util.List;

/** Hooks for {@link CallSiteTarget}; defined by the same class loader as the transformed target. */
public final class CallSiteTargetHooks {

    /** What the call observers saw, in order. */
    public static final List<String> SEEN = new ArrayList<>();

    private CallSiteTargetHooks() {}

    /** Observes {@code Math.abs(int)} before it runs; the call stays. */
    public static void seeAbs(int n) {
        SEEN.add("abs " + n);
    }

    /** Observes {@code StringBuilder.append(String)} before it runs: two stack slots. */
    public static void seeAppend(StringBuilder builder, String text) {
        SEEN.add("append '" + text + "' to '" + builder + "'");
    }

    /** Observes the first {@code List.clear()} after it ran. */
    public static void afterClear(List<String> log) {
        SEEN.add("cleared, size " + log.size());
    }

    /** Replaces {@code Math.abs(int)}: static, so no receiver. */
    public static int abs(int n) {
        return Math.abs(n) + 1000;
    }

    /** Replaces {@code String.trim()}: the receiver comes first. */
    public static String trim(String receiver) {
        return receiver.trim() + "!";
    }

    public static void visitHeadFirst(CallSiteTarget target, String who) {
        target.log.add("head1");
    }

    public static void visitHeadSecond(CallSiteTarget target, String who) {
        target.log.add("head2");
    }

    public static String visitReturnFirst(CallSiteTarget target, String who, String returned) {
        target.log.add("return1");
        return returned + "1";
    }

    public static String visitReturnSecond(CallSiteTarget target, String who, String returned) {
        target.log.add("return2");
        return returned + "2";
    }

    /** Replaces the {@code toUpperCase()} call inside {@code visit}. */
    public static String shout(String receiver) {
        return receiver.toUpperCase() + "?";
    }
}
