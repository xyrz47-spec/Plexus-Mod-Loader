package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * Changes the string a constant-returning method returns.
 *
 * <p><b>Precondition:</b> the method body, ignoring labels, line numbers, and frames, is exactly
 * {@code LDC expectedConstant; ARETURN}. Any other body — including the same shape with a different
 * constant — rejects the rule instead of guessing.
 *
 * @param expectedConstant the constant the method returns today
 * @param replacementConstant the constant it returns after the transformation
 */
@ApiStability(StabilityLevel.INTERNAL)
public record ReplaceReturnedStringConstant(String expectedConstant, String replacementConstant)
        implements MethodTransformAction {

    public ReplaceReturnedStringConstant {
        Objects.requireNonNull(expectedConstant, "expectedConstant");
        Objects.requireNonNull(replacementConstant, "replacementConstant");
    }

    @Override
    public String describe() {
        return "return \"" + replacementConstant + "\" instead of \"" + expectedConstant + "\"";
    }
}
