package com.example.compat.nesteddeep;

/** A class that only exists inside a jar nested in a nested jar. */
public final class DeepLib {

    private DeepLib() {}

    public static String depth() {
        return "two levels down";
    }
}
