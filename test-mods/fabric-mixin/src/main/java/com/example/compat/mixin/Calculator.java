package com.example.compat.mixin;

/** A mod class the mod's own mixins change: {@code sum} becomes 2 + 30, {@code product} becomes (4 * 5) * 10. */
public final class Calculator {

    private Calculator() {}

    public static int sum() {
        return add(2, 3);
    }

    public static int product() {
        return multiply(4, 5);
    }

    static int add(int a, int b) {
        return a + b;
    }

    static int multiply(int a, int b) {
        return a * b;
    }
}
