package dev.polymodloader.compat.quilt;

import java.util.Collection;
import java.util.Set;
import org.quiltmc.loader.api.MappingResolver;

/**
 * {@code QuiltLoader.getMappingResolver()} for Minecraft 26.x, which is not obfuscated: the runtime namespace is {@code
 * official} and every name maps to itself, with the meaning of the Fabric bridge's resolver (ADR-0027 decision 4).
 * Names must be in dot form, as Quilt Loader requires.
 *
 * <p>Thread: immutable, safe from any thread.
 */
final class QuiltOfficialMappingResolver implements MappingResolver {

    static final String OFFICIAL = "official";

    @Override
    public Collection<String> getNamespaces() {
        return Set.of(OFFICIAL);
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return OFFICIAL;
    }

    @Override
    public String mapClassName(String namespace, String className) {
        return dotted(className);
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        return dotted(className);
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        dotted(owner);
        return name;
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        dotted(owner);
        return name;
    }

    private static String dotted(String className) {
        if (className.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
        }
        return className;
    }
}
