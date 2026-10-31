package dev.plexusmodloader.runtime.metadata.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One entry of a mod's {@code "minecraft"} list: which Minecraft versions the mod runs on.
 *
 * <ul>
 *   <li>an exact version ID, for example {@code 26.2} or a snapshot ID — matches only that version
 *   <li>a wildcard: {@code 26.*} matches {@code 26.1}, {@code 26.2.1} …; {@code 26.2.*} matches
 *       {@code 26.2} and its hotfixes; {@code *} matches every version
 *   <li>a range of space-separated comparators that must all match, for example {@code >=26.2 <27}
 *       (operators {@code >=}, {@code >}, {@code <=}, {@code <}, {@code =})
 * </ul>
 *
 * <p>Wildcards and ranges compare release versions (dot-separated numbers, missing parts count as
 * 0) and never match snapshots or other non-numeric version IDs; list those exactly. Native mods that
 * use only the Native API can use a wildcard or range, because Plexus Mod Loader's game layer for each version
 * implements the same API.
 */
public final class MinecraftVersionPattern {

    private static final Pattern RELEASE = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");
    private static final Pattern VERSION_ID = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._+-]*");

    private enum Operator {
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        GREATER(">"),
        LESS("<"),
        EQUAL("=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        boolean accepts(int comparison) {
            return switch (this) {
                case GREATER_OR_EQUAL -> comparison >= 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case GREATER -> comparison > 0;
                case LESS -> comparison < 0;
                case EQUAL -> comparison == 0;
            };
        }
    }

    private record Comparator(Operator operator, int[] version) {}

    private final String text;
    private final Kind kind;
    private final List<Comparator> comparators;

    private enum Kind {
        EXACT,
        PREFIX,
        ANY,
        RANGE
    }

    private MinecraftVersionPattern(String text, Kind kind, List<Comparator> comparators) {
        this.text = text;
        this.kind = kind;
        this.comparators = List.copyOf(comparators);
    }

    /** Parses {@code text}, or returns empty when it is blank or uses unsupported syntax. */
    public static Optional<MinecraftVersionPattern> parse(String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.equals("*")) {
            return Optional.of(new MinecraftVersionPattern(trimmed, Kind.ANY, List.of()));
        }
        if (trimmed.endsWith(".*")) {
            String prefix = trimmed.substring(0, trimmed.length() - 2);
            return RELEASE.matcher(prefix).matches()
                    ? Optional.of(new MinecraftVersionPattern(prefix, Kind.PREFIX, List.of()))
                    : Optional.empty();
        }
        char first = trimmed.charAt(0);
        if (first != '>' && first != '<' && first != '=') {
            return VERSION_ID.matcher(trimmed).matches()
                    ? Optional.of(new MinecraftVersionPattern(trimmed, Kind.EXACT, List.of()))
                    : Optional.empty();
        }
        List<Comparator> comparators = new ArrayList<>();
        for (String token : trimmed.split("\\s+")) {
            Operator operator = null;
            for (Operator candidate : Operator.values()) {
                if (token.startsWith(candidate.symbol)) {
                    operator = candidate;
                    break;
                }
            }
            if (operator == null) {
                return Optional.empty();
            }
            String version = token.substring(operator.symbol.length());
            if (!RELEASE.matcher(version).matches()) {
                return Optional.empty();
            }
            comparators.add(new Comparator(operator, numbers(version)));
        }
        return Optional.of(new MinecraftVersionPattern(trimmed, Kind.RANGE, comparators));
    }

    /** Whether the game version {@code gameVersion} (as in the game jar's {@code version.json}) matches. */
    public boolean matches(String gameVersion) {
        return switch (kind) {
            case ANY -> true;
            case EXACT -> text.equals(gameVersion);
            case PREFIX -> gameVersion.equals(text) || gameVersion.startsWith(text + ".");
            case RANGE -> {
                if (!RELEASE.matcher(gameVersion).matches()) {
                    yield false;
                }
                int[] version = numbers(gameVersion);
                yield comparators.stream()
                        .allMatch(comparator -> comparator.operator().accepts(compare(version, comparator.version())));
            }
        };
    }

    /** Whether any of {@code patterns} matches {@code gameVersion}; unparsable entries never match. */
    public static boolean anyMatches(List<String> patterns, String gameVersion) {
        return patterns.stream()
                .map(MinecraftVersionPattern::parse)
                .flatMap(Optional::stream)
                .anyMatch(pattern -> pattern.matches(gameVersion));
    }

    private static int[] numbers(String version) {
        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return kind == Kind.PREFIX ? text + ".*" : text;
    }
}
