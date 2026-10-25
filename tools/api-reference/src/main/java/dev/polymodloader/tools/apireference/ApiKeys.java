package dev.polymodloader.tools.apireference;

import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Names of public API elements that stay the same across releases, for {@link ApiSinceMain}: a type by its binary name,
 * a member by its type, name and erased parameter types. Releases before the rename to Poly Mod Loader (ADR-0023) used
 * the package {@code dev.polyloader}; their names are read as {@code dev.polymodloader}.
 */
final class ApiKeys {

    private static final String OLD_PREFIX = "dev.polyloader.";
    private static final String PREFIX = "dev.polymodloader.";

    private ApiKeys() {}

    /** {@code dev.polymodloader.api.content.item.ItemDefinition$Builder}. */
    static String type(Elements elements, TypeElement type) {
        return normalize(elements.getBinaryName(type).toString());
    }

    /**
     * {@code <type>#<name>(<erased parameter types>)} for a constructor ({@code <init>}) or method, {@code
     * <type>#<name>} for a field or enum constant.
     */
    static String member(Elements elements, Types types, TypeElement owner, Element member) {
        String type = type(elements, owner);
        if (member instanceof ExecutableElement executable) {
            String name = executable.getKind() == ElementKind.CONSTRUCTOR
                    ? "<init>"
                    : executable.getSimpleName().toString();
            String parameters = executable.getParameters().stream()
                    .map(parameter -> normalize(types.erasure(parameter.asType()).toString()))
                    .collect(Collectors.joining(","));
            return type + "#" + name + "(" + parameters + ")";
        }
        return type + "#" + member.getSimpleName();
    }

    static String normalize(String name) {
        return name.replace(OLD_PREFIX, PREFIX);
    }
}
