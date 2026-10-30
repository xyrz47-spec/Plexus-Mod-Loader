package dev.plexusmodloader.internal.transform.engine.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Javac-compiled class that {@code CallSiteAndAccessTest} transforms: calls to redirect, a method
 * several rules change together, and private or final members to widen.
 */
public class CallSiteTarget {

    public final List<String> log = new ArrayList<>();

    private final int locked;

    public CallSiteTarget() {
        this(1);
    }

    /** A private constructor to make extendable; the public one calls it with invokespecial. */
    private CallSiteTarget(int locked) {
        this.locked = locked;
    }

    /** Two static calls to {@code Math.abs(int)}. */
    public static int twice(int n) {
        return Math.abs(n) + Math.abs(n * 10);
    }

    /** A virtual call to {@code String.trim()}. */
    public String greet(String name) {
        StringBuilder text = new StringBuilder();
        text.append("hi ").append(name);
        return text.toString().trim();
    }

    /** Two {@code List.clear()} calls (interface, receiver only, void) around additions. */
    public void tidy() {
        log.add("a");
        log.clear();
        log.add("b");
        log.clear();
        log.add("c");
    }

    /** Several rules change this method together. */
    public String visit(String who) {
        for (int i = 0; i < 1; i++) {
            log.add("body:" + who);
        }
        return who.toUpperCase();
    }

    private int secret() {
        return 7;
    }

    public int callSecret() {
        return secret();
    }

    public int locked() {
        return locked;
    }

    /** An interface field, which must stay final even when made mutable. */
    public interface Limits {
        List<String> NAMES = List.of("limit");
    }

    /** A final nested class to make extendable. */
    public static final class Sealed {

        public String name() {
            return "sealed";
        }
    }
}
