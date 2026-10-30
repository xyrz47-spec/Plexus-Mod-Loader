package dev.polymodloader.compat.fabric.loader;

import java.util.Collection;
import java.util.Set;
import net.fabricmc.loader.api.MappingResolver;

/**
 * {@code FabricLoader.getMappingResolver()} for Minecraft 26.x, which is not obfuscated: as under Fabric Loader 0.19.5
 * without a mappings file, the runtime namespace is {@code official} and every name maps to itself. Names must be in
 * dot form, as Fabric Loader requires.
 *
 * <p>Fabric Loader's resolver over its empty mapping tree reports a namespace set holding only {@code null}; this one
 * reports {@code official}, the namespace the game runs in.
 *
 * <p>Thread: immutable, safe from any thread.
 */
final class OfficialMappingResolver implements MappingResolver {

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
