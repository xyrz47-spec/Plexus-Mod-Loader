package dev.polymodloader.devtools.gradle.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates the {@code rules} blocks of Mojang's version manifest the way the official launcher
 * does: no rules means allowed; otherwise the last matching rule decides. Launcher "features"
 * (demo user, custom resolution, quick play) are always treated as disabled.
 */
final class LibraryRules {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([a-z_]+)}");

    private LibraryRules() {}

    static boolean allows(JsonArray rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            if (matches(rule)) {
                allowed = rule.get("action").getAsString().equals("allow");
            }
        }
        return allowed;
    }

    private static boolean matches(JsonObject rule) {
        if (rule.has("features")) {
            return false;
        }
        if (rule.has("os")) {
            JsonObject os = rule.getAsJsonObject("os");
            if (os.has("name") && !os.get("name").getAsString().equals(MinecraftDistributionInstaller.currentOsName())) {
                return false;
            }
            if (os.has("arch") && os.get("arch").getAsString().equals("x86")) {
                return System.getProperty("os.arch").equals("x86");
            }
        }
        return true;
    }

    /** Resolves a manifest argument list, keeping every allowed argument. */
    static List<String> resolveArguments(JsonArray template, Map<String, String> variables) {
        List<String> resolved = new ArrayList<>();
        for (String argument : allowedArguments(template)) {
            resolved.add(substitute(argument, variables));
        }
        return resolved;
    }

    /**
     * Resolves {@code --option value} style game arguments and drops options whose value refers to
     * a variable Poly Mod Loader does not supply (for example {@code --clientId} or {@code --xuid}).
     */
    static List<String> resolveOptionArguments(JsonArray template, Map<String, String> variables) {
        List<String> arguments = allowedArguments(template);
        List<String> resolved = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            boolean hasValue = argument.startsWith("--")
                    && i + 1 < arguments.size()
                    && !arguments.get(i + 1).startsWith("--");
            if (!hasValue) {
                resolved.add(substitute(argument, variables));
                continue;
            }
            String value = arguments.get(++i);
            if (hasUnknownVariable(value, variables)) {
                continue;
            }
            resolved.add(argument);
            resolved.add(substitute(value, variables));
        }
        return resolved;
    }

    private static List<String> allowedArguments(JsonArray template) {
        List<String> arguments = new ArrayList<>();
        for (JsonElement element : template) {
            if (element.isJsonPrimitive()) {
                arguments.add(element.getAsString());
                continue;
            }
            JsonObject conditional = element.getAsJsonObject();
            if (!allows(conditional.getAsJsonArray("rules"))) {
                continue;
            }
            JsonElement value = conditional.get("value");
            if (value.isJsonArray()) {
                value.getAsJsonArray().forEach(item -> arguments.add(item.getAsString()));
            } else {
                arguments.add(value.getAsString());
            }
        }
        return arguments;
    }

    private static boolean hasUnknownVariable(String value, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(value);
        while (matcher.find()) {
            if (!variables.containsKey(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String substitute(String value, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = variables.get(matcher.group(1));
            if (replacement == null) {
                throw new IllegalStateException("Unsupported launcher variable ${" + matcher.group(1) + "} in " + value);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
