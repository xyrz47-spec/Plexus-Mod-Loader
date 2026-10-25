package dev.polymodloader.runtime.metadata.version;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Semantic Versioning 2.0.0 version. Ordering follows SemVer precedence: build metadata is
 * ignored and a pre-release sorts before the matching release.
 *
 * @param major major version
 * @param minor minor version
 * @param patch patch version
 * @param preRelease dot-separated pre-release identifiers, empty for a release
 * @param buildMetadata build metadata after {@code +}, empty when absent
 */
public record SemanticVersion(int major, int minor, int patch, List<String> preRelease, String buildMetadata)
        implements Comparable<SemanticVersion> {

    private static final Pattern FORMAT = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
            + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    public SemanticVersion {
        preRelease = List.copyOf(preRelease);
    }

    /** Parses {@code text}, or returns empty when it is not a valid SemVer version. */
    public static Optional<SemanticVersion> parse(String text) {
        Matcher matcher = FORMAT.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            List<String> preRelease = matcher.group(4) == null
                    ? List.of()
                    : List.of(matcher.group(4).split("\\."));
            return Optional.of(new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    preRelease,
                    matcher.group(5) == null ? "" : matcher.group(5)));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        if (result == 0) {
            result = Integer.compare(patch, other.patch);
        }
        return result != 0 ? result : comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Boolean.compare(left.isEmpty(), right.isEmpty());
        }
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int result = compareIdentifier(left.get(i), right.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder()
                .append(major)
                .append('.')
                .append(minor)
                .append('.')
                .append(patch);
        if (!preRelease.isEmpty()) {
            text.append('-').append(String.join(".", preRelease));
        }
        if (!buildMetadata.isEmpty()) {
            text.append('+').append(buildMetadata);
        }
        return text.toString();
    }
}
