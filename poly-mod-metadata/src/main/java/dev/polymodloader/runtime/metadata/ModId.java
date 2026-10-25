package dev.polymodloader.runtime.metadata;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A technical mod identifier: a lowercase letter, then lowercase letters, digits, or underscores,
 * 2 to 64 characters in total. Distinct from the human-readable mod name.
 *
 * @param value the identifier text
 */
public record ModId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    public ModId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid mod ID '" + value + "': expected " + FORMAT.pattern());
        }
    }

    /** Parses {@code value}, or returns empty when it is not a valid mod ID. */
    public static Optional<ModId> parse(String value) {
        return FORMAT.matcher(value).matches() ? Optional.of(new ModId(value)) : Optional.empty();
    }

    /** The regular expression mod IDs must match, for error messages. */
    public static String format() {
        return FORMAT.pattern();
    }

    @Override
    public String toString() {
        return value;
    }
}
