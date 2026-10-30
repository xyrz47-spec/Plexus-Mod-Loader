package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mojang's official mappings of an obfuscated Minecraft version (before 26.1, ADR-0019), in the ProGuard
 * format of {@code downloads.client_mappings}: the Mojang name of each class, field and method, and the
 * obfuscated name the jar uses.
 *
 * <p>Read on the developer's machine only; the mappings and anything made with them are never committed or
 * published (ADR-0019 point 3). Lines for inlined frames (a method name with a dot) are skipped.
 */
final class MojangMappings {

    private static final Pattern CLASS_LINE = Pattern.compile("^(\\S+) -> (\\S+):$");
    private static final Pattern METHOD_LINE =
            Pattern.compile("^\\s+(?:\\d+:\\d+:)?(\\S+) ([^\\s(]+)\\(([^)]*)\\)(?::\\d+(?::\\d+)?)? -> (\\S+)$");
    private static final Pattern FIELD_LINE = Pattern.compile("^\\s+(\\S+) ([^\\s(]+) -> (\\S+)$");
    private static final Pattern DESCRIPTOR_CLASS = Pattern.compile("L([^;]+);");

    /** Internal class names, obfuscated to Mojang. */
    private final Map<String, String> classNames = new HashMap<>();
    /** Per obfuscated class: obfuscated {@code name + descriptor} to the Mojang name. */
    private final Map<String, Map<String, String>> methodNames = new HashMap<>();
    /** Per obfuscated class: obfuscated field name to the Mojang name. */
    private final Map<String, Map<String, String>> fieldNames = new HashMap<>();

    private MojangMappings() {}

    static MojangMappings read(Path file) {
        try {
            return parse(Files.readAllLines(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the mappings " + file, e);
        }
    }

    static MojangMappings parse(List<String> lines) {
        // Members name their types with Mojang names, so classes are read first and members second.
        Map<String, String> obfuscatedOf = new HashMap<>();
        for (String line : lines) {
            Matcher type = CLASS_LINE.matcher(line);
            if (!line.startsWith("#") && type.matches()) {
                obfuscatedOf.put(internal(type.group(1)), internal(type.group(2)));
            }
        }
        MojangMappings mappings = new MojangMappings();
        obfuscatedOf.forEach((named, obfuscated) -> mappings.classNames.put(obfuscated, named));
        String owner = null;
        for (String line : lines) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher type = CLASS_LINE.matcher(line);
            if (type.matches()) {
                owner = internal(type.group(2));
                continue;
            }
            if (owner == null) {
                continue;
            }
            Matcher method = METHOD_LINE.matcher(line);
            if (method.matches()) {
                if (method.group(2).contains(".")) {
                    continue;
                }
                String descriptor = "(" + parameters(method.group(3), obfuscatedOf) + ")"
                        + descriptor(method.group(1), obfuscatedOf);
                mappings.methodNames
                        .computeIfAbsent(owner, ignored -> new HashMap<>())
                        .putIfAbsent(method.group(4) + descriptor, method.group(2));
                continue;
            }
            Matcher field = FIELD_LINE.matcher(line);
            if (field.matches()) {
                mappings.fieldNames
                        .computeIfAbsent(owner, ignored -> new HashMap<>())
                        .putIfAbsent(field.group(3), field.group(2));
            }
        }
        return mappings;
    }

    /** The Mojang name of an obfuscated internal class name; names the mappings do not list stay as they are. */
    String className(String obfuscated) {
        return classNames.getOrDefault(obfuscated, obfuscated);
    }

    /** The Mojang name of a method declared by {@code obfuscatedOwner}, or its obfuscated name if not listed. */
    String methodName(String obfuscatedOwner, String name, String obfuscatedDescriptor) {
        return methodNames
                .getOrDefault(obfuscatedOwner, Map.of())
                .getOrDefault(name + obfuscatedDescriptor, name);
    }

    /** The Mojang name of a field declared by {@code obfuscatedOwner}, or its obfuscated name if not listed. */
    String fieldName(String obfuscatedOwner, String name) {
        return fieldNames.getOrDefault(obfuscatedOwner, Map.of()).getOrDefault(name, name);
    }

    /** Whether the mappings list this obfuscated class. */
    boolean listsClass(String obfuscated) {
        return classNames.containsKey(obfuscated);
    }

    /** The obfuscated names of every listed class, including ones the jar no longer has. */
    Set<String> obfuscatedClasses() {
        return Collections.unmodifiableSet(classNames.keySet());
    }

    /** Whether some listed class has this Mojang name. */
    boolean namesClass(String mojangName) {
        return classNames.containsValue(mojangName);
    }

    /** The Mojang name of a method the mappings list for {@code obfuscatedOwner}, or null if they do not list it. */
    String listedMethod(String obfuscatedOwner, String name, String obfuscatedDescriptor) {
        return methodNames.getOrDefault(obfuscatedOwner, Map.of()).get(name + obfuscatedDescriptor);
    }

    /** The Mojang name of a field the mappings list for {@code obfuscatedOwner}, or null if they do not list it. */
    String listedField(String obfuscatedOwner, String name) {
        return fieldNames.getOrDefault(obfuscatedOwner, Map.of()).get(name);
    }

    /** A descriptor or signature with every obfuscated class name replaced by its Mojang name. */
    String descriptor(String obfuscated) {
        Matcher matcher = DESCRIPTOR_CLASS.matcher(obfuscated);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("L" + className(matcher.group(1)) + ";"));
        }
        return matcher.appendTail(out).toString();
    }

    int classCount() {
        return classNames.size();
    }

    private static String internal(String javaName) {
        return javaName.replace('.', '/');
    }

    private static String parameters(String list, Map<String, String> obfuscatedOf) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String type : list.split(",")) {
            out.append(descriptor(type, obfuscatedOf));
        }
        return out.toString();
    }

    /** The obfuscated descriptor of a Java type written with Mojang names, such as {@code net.minecraft.A[]}. */
    private static String descriptor(String javaType, Map<String, String> obfuscatedOf) {
        String type = javaType;
        StringBuilder arrays = new StringBuilder();
        while (type.endsWith("[]")) {
            arrays.append('[');
            type = type.substring(0, type.length() - 2);
        }
        String element =
                switch (type) {
                    case "void" -> "V";
                    case "boolean" -> "Z";
                    case "byte" -> "B";
                    case "char" -> "C";
                    case "short" -> "S";
                    case "int" -> "I";
                    case "long" -> "J";
                    case "float" -> "F";
                    case "double" -> "D";
                    default -> "L" + obfuscatedOf.getOrDefault(internal(type), internal(type)) + ";";
                };
        return arrays + element;
    }
}
