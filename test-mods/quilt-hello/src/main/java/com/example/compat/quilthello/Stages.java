package com.example.compat.quilthello;

import java.util.ArrayList;
import java.util.List;

/** The entrypoint stages of this mod in the order they ran. */
final class Stages {

    private static final List<String> RAN = new ArrayList<>();

    private Stages() {}

    static synchronized void ran(String stage) {
        RAN.add(stage);
    }

    static synchronized String order() {
        return String.join(" -> ", RAN);
    }
}
