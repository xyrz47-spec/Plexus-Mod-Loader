package dev.plexusmodloader.compat.quilt;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgedAccessWidening;
import dev.plexusmodloader.compat.bridge.BridgedDependency;
import dev.plexusmodloader.compat.bridge.BridgedEntrypoint;
import dev.plexusmodloader.compat.bridge.BridgedInterfaceInjection;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.CompatibilityStatus;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import dev.plexusmodloader.compat.bridge.ModLoaderBridge;
import dev.plexusmodloader.compat.fabric.BundledVersions;
import dev.plexusmodloader.compat.fabric.ClassTweakerCheck;
import dev.plexusmodloader.compat.fabric.FabricBridge;
import dev.plexusmodloader.compat.fabric.MixinConfigCheck;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.quiltmc.loader.api.Version;

/**
 * Quilt bridge (ADR-0027): assesses {@code quilt.mod.json} with Quilt Loader's meaning ({@link QuiltModJsonReader},
 * {@link QuiltVersionSpecifier}, {@link QuiltDependencyReader}) and reports what the runtime needs: entrypoints, Mixin
 * configurations, access wideners, nested jars, dependencies and provided IDs. It is a sibling of {@link FabricBridge}
 * in the same jar: Quilt mods run on the Fabric bridge's Mixin, class tweaker and entrypoint machinery, which the
 * Fabric bridge owns, so this bridge's launch hooks ({@link #prepareLaunch}, {@link #classTransformer}, {@link
 * #beforeGame}) keep their defaults.
 *
 * <p>Besides {@link QuiltModJsonReader}'s findings ({@code 300..304}, {@code 306}, {@code 308}, {@code 309}), the
 * assessment adds {@code PLEXUS-COMPAT-305} (blocking) when {@code minecraft.environment} is for the other side, and the
 * Fabric family's Mixin ({@code 131}, {@code 132}, {@code 136}) and class tweaker ({@code 125}, {@code 126}, {@code
 * 129}) findings. Entrypoints run: {@code main} (common), {@code client} on the client, {@code server} on the dedicated
 * server; {@code pre_launch} and {@code preLaunch} are reported in the {@code preLaunch} phase for the link check.
 */
public final class QuiltBridge implements ModLoaderBridge {

    private static final Map<String, String> PHASES = Map.of(
            "main",
            "common",
            "client",
            "client",
            "server",
            "server",
            "pre_launch",
            "preLaunch",
            "preLaunch",
            "preLaunch");

    @Override
    public String loaderName() {
        return "Quilt";
    }

    @Override
    public String metadataPath() {
        return QuiltModJsonReader.FILE_NAME;
    }

    /** Quilt Loader reads nested jars with all its plugins: a nested Fabric mod is a mod of its own too. */
    @Override
    public List<String> nestedMetadataPaths() {
        return List.of(QuiltModJsonReader.FILE_NAME, "fabric.mod.json");
    }

    /**
     * {@code quilt_loader} itself, the Fabric Loader API it provides ({@code fabricloader}), and MixinExtras, which
     * Quilt Loader nests; all at the versions this jar carries.
     */
    @Override
    public Map<String, String> builtInMods() {
        return Map.of(
                "quilt_loader",
                BundledVersions.QUILT_LOADER,
                "fabricloader",
                BundledVersions.FABRIC_LOADER,
                "mixinextras",
                BundledVersions.MIXIN_EXTRAS);
    }

    /** Quilt Loader's API keeps static state ({@code QuiltLoaderImpl.INSTANCE}); one copy for every mod. */
    @Override
    public List<String> sharedLibraryPackages() {
        return List.of("org.quiltmc.loader.");
    }

    @Override
    public List<String> runtimePackages() {
        return List.of("net/fabricmc/", "org/quiltmc/loader/");
    }

    /**
     * The Fabric bridge's, the packed Quilt Loader API, and the three internal classes the bridge provides itself
     * (ADR-0027 decision 4). Quilt mods that use other Quilt Loader classes are blocked by the link check ({@code
     * 111}).
     */
    @Override
    public List<String> providedPackages() {
        List<String> packages = new ArrayList<>(new FabricBridge().providedPackages());
        packages.add("org/quiltmc/loader/api/");
        packages.add("org/quiltmc/loader/impl/QuiltLoaderImpl");
        packages.add("org/quiltmc/loader/impl/game/GameProvider");
        packages.add("org/quiltmc/loader/impl/util/DefaultLanguageAdapter");
        return List.copyOf(packages);
    }

    @Override
    public BridgeAssessment assess(String metadata, GameSide side) {
        return assess(metadata, side, ModJarContents.NONE);
    }

    @Override
    public BridgeAssessment assess(String metadata, GameSide side, ModJarContents jar) {
        boolean json5;
        try {
            json5 = jar.read(QuiltModJsonReader.JSON5_FILE_NAME).isPresent();
        } catch (UncheckedIOException e) {
            json5 = true;
        }
        QuiltModJsonReader.Result read = QuiltModJsonReader.read(metadata, json5);
        if (read.description().isEmpty()) {
            return BridgeAssessment.of(
                    loaderName(),
                    read.id(),
                    read.version(),
                    read.findings(),
                    List.of(),
                    CompatibilityStatus.EXPERIMENTAL);
        }
        QuiltModDescription mod = read.description().get();
        List<CompatibilityFinding> findings = new ArrayList<>(read.findings());
        if ((mod.environment().equals("client") && side == GameSide.DEDICATED_SERVER)
                || (mod.environment().equals("dedicated_server") && side == GameSide.CLIENT)) {
            findings.add(new CompatibilityFinding(
                    "PLEXUS-COMPAT-305",
                    true,
                    "environment",
                    "minecraft.environment is " + mod.environment() + ", process is " + side));
        }
        List<BridgedInterfaceInjection> injections = new ArrayList<>();
        if (!mod.mixins().isEmpty()) {
            injections.addAll(
                    MixinConfigCheck.check(mod.mixins(), metadataPath(), "Quilt Loader", side, jar, findings));
        }
        List<BridgedAccessWidening> widenings = new ArrayList<>();
        for (String path : mod.accessWideners()) {
            ClassTweakerCheck.ClassTweaks tweaks =
                    ClassTweakerCheck.check(path, metadataPath(), "Quilt Loader", jar, findings);
            widenings.addAll(tweaks.widenings());
            injections.addAll(tweaks.injections());
        }
        List<BridgedDependency> dependencies = new ArrayList<>();
        mod.depends().forEach(dependency -> dependencies.add(dependency.bridged()));
        mod.breaks().forEach(dependency -> dependencies.add(dependency.bridged()));
        List<String> providedIds = new ArrayList<>();
        Map<String, String> providedVersions = new LinkedHashMap<>();
        for (QuiltModDescription.Provided provided : mod.provides()) {
            if (provided.id().equals(mod.id()) || providedIds.contains(provided.id())) {
                continue;
            }
            providedIds.add(provided.id());
            if (!provided.version().equals(mod.version())) {
                providedVersions.put(provided.id(), provided.version());
            }
        }
        return BridgeAssessment.of(
                        loaderName(),
                        mod.id(),
                        mod.version(),
                        findings,
                        entrypoints(mod, side),
                        providedIds,
                        dependencies,
                        mod.jars(),
                        CompatibilityStatus.EXPERIMENTAL)
                .withAccessWidenings(widenings)
                .withInjectedInterfaces(injections)
                .withProvidedVersions(providedVersions)
                .withLoadOnlyIfRequired(mod.loadOnlyIfRequired());
    }

    /** The entrypoints Plexus Mod Loader runs or links on {@code side}, in file order. */
    private static List<BridgedEntrypoint> entrypoints(QuiltModDescription mod, GameSide side) {
        List<BridgedEntrypoint> entrypoints = new ArrayList<>();
        mod.entrypoints().forEach((key, declarations) -> {
            String phase = PHASES.get(key);
            if (phase == null) {
                return;
            }
            boolean applies = phase.equals("common")
                    || phase.equals("preLaunch")
                    || (phase.equals("client") && side == GameSide.CLIENT)
                    || (phase.equals("server") && side == GameSide.DEDICATED_SERVER);
            if (!applies) {
                return;
            }
            for (QuiltModDescription.Entrypoint declaration : declarations) {
                entrypoints.add(new BridgedEntrypoint(phase, declaration.value(), declaration.adapter()));
            }
        });
        return entrypoints;
    }

    /** Quilt Loader's ordering ({@link Version#of}: semantic versions by their parts, FlexVer otherwise). */
    @Override
    public int compareVersions(String first, String second) {
        try {
            return Version.of(first).compareTo(Version.of(second));
        } catch (RuntimeException e) {
            return first.compareTo(second);
        }
    }

    /**
     * Calls a Quilt mod's {@code main}, {@code client} or {@code server} entrypoint through Fabric's initializer
     * interfaces, with the code and the entrypoint store the Fabric bridge uses (ADR-0027 decision 9).
     */
    @Override
    public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) throws Exception {
        FabricLoaderImpl.INSTANCE.plexusModLoaderRuntime().invokeModEntrypoint(entrypoint);
    }
}
