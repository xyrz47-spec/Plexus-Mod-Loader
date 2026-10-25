package dev.polymodloader.internal.transform;

import java.util.regex.Pattern;

/** Validation shared by actions that call a static hook method. */
final class HookReference {

    private static final Pattern INTERNAL_NAME = Pattern.compile("[^./;\\[]+(/[^./;\\[]+)*");

    private HookReference() {}

    static void validate(String hookOwnerInternalName, String hookMethodName) {
        if (!INTERNAL_NAME.matcher(hookOwnerInternalName).matches()) {
            throw new IllegalArgumentException("Hook owner must be a JVM internal name: " + hookOwnerInternalName);
        }
        if (hookMethodName.isEmpty() || hookMethodName.contains(".") || hookMethodName.contains("/")) {
            throw new IllegalArgumentException("Invalid hook method name: " + hookMethodName);
        }
    }
}
