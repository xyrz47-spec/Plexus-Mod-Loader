package dev.plexusmodloader.runtime.metadata.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A version requirement written as space-separated comparators that must all match, for example
 * {@code ">=0.1.0 <0.2.0"}. Supported operators: {@code >=}, {@code >}, {@code <=}, {@code <},
 * {@code =}. A bare version means {@code =}. {@code *} matches every version.
 */
public final class VersionRange {

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

    private record Comparator(Operator operator, SemanticVersion version) {}

    private final String text;
    private final List<Comparator> comparators;

    private VersionRange(String text, List<Comparator> comparators) {
        this.text = text;
        this.comparators = List.copyOf(comparators);
    }

    /** Parses {@code text}, or returns empty when it uses unsupported syntax. */
    public static Optional<VersionRange> parse(String text) {
        String trimmed = text.strip();
        if (trimmed.equals("*")) {
            return Optional.of(new VersionRange(trimmed, List.of()));
        }
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        List<Comparator> comparators = new ArrayList<>();
        for (String token : trimmed.split("\\s+")) {
            Operator operator = Operator.EQUAL;
            String versionText = token;
            for (Operator candidate : Operator.values()) {
                if (token.startsWith(candidate.symbol)) {
                    operator = candidate;
                    versionText = token.substring(candidate.symbol.length());
                    break;
                }
            }
            Optional<SemanticVersion> version = SemanticVersion.parse(versionText);
            if (version.isEmpty()) {
                return Optional.empty();
            }
            comparators.add(new Comparator(operator, version.get()));
        }
        return Optional.of(new VersionRange(trimmed, comparators));
    }

    /** Returns whether {@code version} satisfies every comparator in this range. */
    public boolean contains(SemanticVersion version) {
        return comparators.stream()
                .allMatch(comparator -> comparator.operator().accepts(version.compareTo(comparator.version())));
    }

    @Override
    public String toString() {
        return text;
    }
}
