package dev.polymodloader.api.stability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link StabilityLevel} of a Poly Mod Loader type. The annotation is {@link Documented} so the
 * level appears in JavaDoc and IntelliJ quick documentation, and retained at run time so tools such
 * as the API capability inspector can report it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PACKAGE})
@ApiStability(StabilityLevel.PREVIEW)
public @interface ApiStability {
    /** The stability level of the annotated element. */
    StabilityLevel value();
}
