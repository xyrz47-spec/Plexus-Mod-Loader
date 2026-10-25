package dev.polymodloader.runtime.diagnostics;

import java.io.Serial;
import java.util.List;

/**
 * Stops the launch before the game starts. Carries every problem found in the failing phase so the
 * user can fix them all at once instead of one per launch.
 */
public final class PolyLaunchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String phase;
    private final transient List<String> problems;

    public PolyLaunchException(String phase, List<String> problems) {
        super(format(phase, problems));
        this.phase = phase;
        this.problems = List.copyOf(problems);
    }

    public PolyLaunchException(String phase, String problem, Throwable cause) {
        super(format(phase, List.of(problem)), cause);
        this.phase = phase;
        this.problems = List.of(problem);
    }

    /** The launch phase that failed. */
    public String phase() {
        return phase;
    }

    /** One line per problem, each starting with its error code. */
    public List<String> problems() {
        return problems;
    }

    private static String format(String phase, List<String> problems) {
        StringBuilder message = new StringBuilder("Poly Mod Loader launch stopped in phase ")
                .append(phase)
                .append(" with ")
                .append(problems.size())
                .append(" problem(s):");
        problems.forEach(
                problem -> message.append(System.lineSeparator()).append("  - ").append(problem));
        return message.toString();
    }
}
