package dev.plexusmodloader.runtime.dependency;

/**
 * A problem between mods, tied to the mod that declared the failing requirement.
 *
 * @param code stable error code
 * @param modId the mod the problem belongs to
 * @param message explanation with the offending IDs and versions
 */
public record DependencyProblem(DependencyProblemCode code, String modId, String message) {

    @Override
    public String toString() {
        return code.code() + " mod=" + modId + ": " + message;
    }
}
