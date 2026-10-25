package com.example.compat.launcherextra;

/** Only in {@code extra-lib.jar}, which the mod adds to the class path itself. */
public final class Extra {

    private Extra() {}

    public static String greeting() {
        return "hello-from-extra-lib";
    }
}
