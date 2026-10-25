package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.io.Serial;

/** Thrown when a transformation fails in a way that must stop the launch. */
@ApiStability(StabilityLevel.INTERNAL)
public final class TransformException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient TransformProblemCode code;

    public TransformException(TransformProblem problem) {
        super(problem.toString());
        this.code = problem.code();
    }

    public TransformException(TransformProblem problem, Throwable cause) {
        super(problem.toString(), cause);
        this.code = problem.code();
    }

    public TransformException(AccessWideningProblem problem) {
        super(problem.toString());
        this.code = problem.code();
    }

    /** The stable code of the problem behind this exception. */
    public TransformProblemCode code() {
        return code;
    }
}
