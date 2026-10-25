package dev.polymodloader.internal.transform.engine.fixture;

import java.util.ArrayList;
import java.util.List;

/** Javac-compiled class that {@code HookActionsTest} transforms; loops give it real stack map frames. */
public class HookTarget {

    public final List<String> log = new ArrayList<>();

    public HookTarget() {
        log.add("constructed");
    }

    public String describe(String input, long weight) {
        String out = input;
        while (weight > 0) {
            out = out + ".";
            weight--;
        }
        return out;
    }

    public void act(int times) {
        for (int i = 0; i < times; i++) {
            log.add("act");
        }
    }

    public static String label(int n) {
        return n > 0 ? "positive" : "other";
    }

    public int count() {
        return log.size();
    }
}
