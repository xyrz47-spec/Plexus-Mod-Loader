package dev.plexusmodloader.compat.fabric;

import dev.plexusmodloader.compat.bridge.BridgedAccessWidening;
import dev.plexusmodloader.compat.bridge.BridgedInterfaceInjection;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Checks one class tweaker (access widener) file a Fabric-family mod names, during assessment: Fabric mods name one in
 * {@code accessWidener}, Quilt mods one or more in {@code access_widener} (ADR-0018, ADR-0027). The file is read with
 * Fabric Loader's grammar ({@link ClassTweakerFile}).
 */
public final class ClassTweakerCheck {

    /** What a class tweaker asks for: access widenings and interface injections. */
    public record ClassTweaks(List<BridgedAccessWidening> widenings, List<BridgedInterfaceInjection> injections) {

        public static final ClassTweaks NONE = new ClassTweaks(List.of(), List.of());

        public ClassTweaks {
            widenings = List.copyOf(widenings);
            injections = List.copyOf(injections);
        }
    }

    private ClassTweakerCheck() {}

    /**
     * Reads the class tweaker at {@code path} and returns its access widenings and interface injections, or nothing
     * when the file blocks the mod:
     *
     * <ul>
     *   <li>{@code PLEXUS-COMPAT-125}: the file is missing, unreadable, or has lines the loader refuses
     *   <li>{@code PLEXUS-COMPAT-126}: its namespace is not {@code official}; 26.2 has no other names
     *   <li>{@code PLEXUS-COMPAT-129} (not blocking): what will be widened, which interfaces and enum constants are
     *       added (the bridge adds them when the classes load, before Mixin, as Fabric Loader does; ADR-0020)
     * </ul>
     *
     * @param metadataFile the metadata file that names it, for messages ({@code fabric.mod.json})
     * @param loaderName the loader whose behaviour the messages compare with ({@code Fabric Loader})
     */
    public static ClassTweaks check(
            String path,
            String metadataFile,
            String loaderName,
            ModJarContents jar,
            List<CompatibilityFinding> findings) {
        Optional<byte[]> bytes;
        try {
            bytes = jar.read(path);
        } catch (UncheckedIOException e) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-125", true, "class tweaker", path + " cannot be read: " + e.getCause()));
            return ClassTweaks.NONE;
        }
        if (bytes.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-125",
                    true,
                    "class tweaker",
                    path + " is named in " + metadataFile + " but not in the jar"));
            return ClassTweaks.NONE;
        }
        ClassTweakerFile file = ClassTweakerFile.parse(path, new String(bytes.get(), StandardCharsets.UTF_8));
        boolean blocked = false;
        if (!file.problems().isEmpty()) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-125",
                    true,
                    "class tweaker",
                    file.problems().size() + " line(s) " + loaderName + " would refuse: "
                            + examples(file.problems().stream()
                                    .map(problem -> file.where(problem.line()) + " " + problem.message())
                                    .toList())));
            blocked = true;
        }
        ClassTweakerFile.Header header = file.header();
        if (header != null && !header.namespace().equals(ClassTweakerFile.RUNTIME_NAMESPACE)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-126",
                    true,
                    "class tweaker",
                    path + " names classes in the '" + header.namespace() + "' namespace; Minecraft 26.2 runs"
                            + " with Mojang's names ('" + ClassTweakerFile.RUNTIME_NAMESPACE + "') and has no"
                            + " mappings to translate other names, so " + loaderName + " refuses it too"));
            blocked = true;
        }
        if (blocked || header == null) {
            return ClassTweaks.NONE;
        }
        List<BridgedAccessWidening> widenings = file.accessWidenings();
        List<BridgedInterfaceInjection> injections = file.injectedInterfaces().stream()
                .map(line ->
                        new BridgedInterfaceInjection(file.where(line.line()), line.target(), line.interfaceName()))
                .toList();
        long transitive = file.entries().stream()
                .filter(ClassTweakerFile.Entry::transitive)
                .count();
        findings.add(new CompatibilityFinding(
                "PLEXUS-COMPAT-129",
                false,
                "class tweaker",
                path + " (" + header.format() + ", " + header.namespace() + "): "
                        + file.entries().size()
                        + " line(s), " + transitive + " of them transitive, become " + widenings.size()
                        + " access widening(s)"
                        + (injections.isEmpty() ? "" : " and " + injections.size() + " interface injection(s)")
                        + (file.enumExtensions().isEmpty()
                                ? ""
                                : ", and " + file.enumExtensions().size() + " enum constant(s) are added")
                        + "; their targets are checked against this game before the mod loads"));
        return new ClassTweaks(widenings, injections);
    }

    /** Up to five items joined for a message, with the number of the rest. */
    static String examples(List<String> items) {
        int shown = 5;
        return String.join("; ", items.stream().limit(shown).toList())
                + (items.size() > shown ? "; and " + (items.size() - shown) + " more" : "");
    }
}
