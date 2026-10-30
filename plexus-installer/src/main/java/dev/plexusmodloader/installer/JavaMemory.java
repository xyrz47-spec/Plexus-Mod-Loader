package dev.plexusmodloader.installer;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * The game's Java memory, written to the launcher profile's {@code javaArgs} as {@code -Xms} and {@code -Xmx}.
 * Either size may be absent; an absent maximum leaves the launcher's own default.
 *
 * @param maximumMegabytes {@code -Xmx} in MiB, or 0 for the launcher's default
 * @param minimumMegabytes {@code -Xms} in MiB, or 0 to leave it to the JVM
 */
record JavaMemory(long maximumMegabytes, long minimumMegabytes) {

    /** The launcher's default: the profile's {@code javaArgs} are left as they are. */
    static final JavaMemory LAUNCHER_DEFAULT = new JavaMemory(0, 0);

    /** Below this the game does not start with mods. */
    static final long SMALLEST_MAXIMUM = 1024;

    JavaMemory {
        if (maximumMegabytes < 0 || minimumMegabytes < 0) {
            throw new IllegalArgumentException("Memory cannot be negative");
        }
        if (maximumMegabytes != 0 && maximumMegabytes < SMALLEST_MAXIMUM) {
            throw new IllegalArgumentException("The maximum memory must be at least 1G; " + format(maximumMegabytes)
                    + " is too little for Minecraft");
        }
        if (maximumMegabytes != 0 && minimumMegabytes > maximumMegabytes) {
            throw new IllegalArgumentException("The minimum memory (" + format(minimumMegabytes)
                    + ") is larger than the maximum (" + format(maximumMegabytes) + ")");
        }
    }

    /** Parses the sizes the user typed, such as {@code 4G}, {@code 4096M} or {@code 4096} (MiB); blank is absent. */
    static JavaMemory parse(String maximum, String minimum) {
        return new JavaMemory(megabytes(maximum), megabytes(minimum));
    }

    boolean isLauncherDefault() {
        return maximumMegabytes == 0 && minimumMegabytes == 0;
    }

    /** {@code -Xms} and {@code -Xmx}, for the sizes that are set. */
    List<String> arguments() {
        List<String> arguments = new ArrayList<>();
        if (minimumMegabytes != 0) {
            arguments.add("-Xms" + format(minimumMegabytes));
        }
        if (maximumMegabytes != 0) {
            arguments.add("-Xmx" + format(maximumMegabytes));
        }
        return arguments;
    }

    /**
     * The profile's new {@code javaArgs}: {@code existing} without its {@code -Xms}/{@code -Xmx}, then these sizes.
     * Other arguments the player set are kept.
     */
    String mergeInto(String existing) {
        List<String> arguments = new ArrayList<>();
        for (String argument :
                existing == null ? new String[0] : existing.strip().split("\\s+")) {
            if (!argument.isEmpty() && !argument.startsWith("-Xmx") && !argument.startsWith("-Xms")) {
                arguments.add(argument);
            }
        }
        arguments.addAll(arguments());
        return String.join(" ", arguments);
    }

    /** A warning when the maximum is more than this PC has, or null. */
    String warning(OptionalLong physicalMegabytes) {
        if (maximumMegabytes == 0 || physicalMegabytes.isEmpty()) {
            return null;
        }
        if (maximumMegabytes > physicalMegabytes.getAsLong()) {
            return "The maximum memory " + format(maximumMegabytes) + " is more than this PC has ("
                    + format(physicalMegabytes.getAsLong()) + "). The game may not start; choose less.";
        }
        return null;
    }

    /** {@code 4096} → {@code 4G}, {@code 1536} → {@code 1536M}. */
    static String format(long megabytes) {
        return megabytes % 1024 == 0 ? megabytes / 1024 + "G" : megabytes + "M";
    }

    /** This PC's memory in MiB, when the Java runtime can tell. */
    static OptionalLong physicalMegabytes() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return OptionalLong.of(os.getTotalMemorySize() / (1024 * 1024));
            }
        } catch (LinkageError e) {
            // A runtime without the jdk.management module: no warning and no recommendation.
        }
        return OptionalLong.empty();
    }

    /** A maximum to suggest for a modded game on a PC with {@code physicalMegabytes}, or 0 for the default. */
    static long recommendedMaximum(OptionalLong physicalMegabytes) {
        if (physicalMegabytes.isEmpty()) {
            return 0;
        }
        long physical = physicalMegabytes.getAsLong();
        if (physical >= 15 * 1024) {
            return 6 * 1024;
        }
        if (physical >= 7 * 1024) {
            return 4 * 1024;
        }
        return 0;
    }

    private static long megabytes(String size) {
        if (size == null || size.isBlank()) {
            return 0;
        }
        String value = size.strip().toUpperCase(Locale.ROOT);
        if (value.endsWith("B")) {
            value = value.substring(0, value.length() - 1);
        }
        long unit = 1;
        if (value.endsWith("G")) {
            unit = 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("M")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            long number = Long.parseLong(value.strip());
            if (number < 0 || number > 1024 * 1024) {
                throw new IllegalArgumentException("Memory size " + size + " is out of range");
            }
            return number * unit;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Memory size " + size + " is not a size like 4G or 4096M", e);
        }
    }
}
