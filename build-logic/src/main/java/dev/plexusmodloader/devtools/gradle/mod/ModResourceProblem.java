package dev.plexusmodloader.devtools.gradle.mod;

/**
 * One problem {@link ModResourceChecker} found in a mod's resources.
 *
 * @param code stable error code, {@code PLEXUS-RESOURCE-004} to {@code PLEXUS-RESOURCE-008}
 * @param path resource path of the file, such as {@code assets/ruby_mod/items/ruby.json}
 * @param message what is wrong and how to fix it
 */
public record ModResourceProblem(String code, String path, String message) {

    @Override
    public String toString() {
        return code + " " + path + ": " + message;
    }
}
