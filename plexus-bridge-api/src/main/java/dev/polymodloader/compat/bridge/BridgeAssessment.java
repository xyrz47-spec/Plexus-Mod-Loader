package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Result of analysing one foreign mod jar.
 *
 * @param loader loader name, e.g. {@code Fabric}
 * @param modId the mod's own ID
 * @param version the mod's version text
 * @param status overall status: {@link CompatibilityStatus#BLOCKED} if any finding blocks
 * @param findings every feature Poly Mod Loader noticed
 * @param entrypoints entrypoints Poly Mod Loader calls when the mod is loadable
 * @param providedIds further IDs this jar answers to (Fabric {@code provides}, extra FML {@code [[mods]]})
 * @param dependencies relationships with other mods, checked by the runtime across loaders
 * @param nestedJars paths of jars inside this jar that the loader treats as mods of their own (Fabric
 *     {@code jars}); the runtime extracts and assesses them
 * @param accessWidenings access widenings the mod asks for (Fabric class tweakers), applied when it loads
 *     after the runtime has checked their targets
 * @param injectedInterfaces interfaces the mod adds to game classes (Fabric class tweakers), which its bridge
 *     applies; the runtime checks that the class and the interface exist
 * @param providedVersions the version of each provided ID that does not have the mod's own version (Quilt {@code
 *     provides} objects with a {@code version}); every other provided ID has {@code version}
 * @param loadOnlyIfRequired whether the mod, when it is nested in another mod's jar, loads only while a loaded mod
 *     needs it (Quilt {@code load_type} {@code if_required}); a directly installed mod is always a candidate
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgeAssessment(
        String loader,
        String modId,
        String version,
        CompatibilityStatus status,
        List<CompatibilityFinding> findings,
        List<BridgedEntrypoint> entrypoints,
        List<String> providedIds,
        List<BridgedDependency> dependencies,
        List<String> nestedJars,
        List<BridgedAccessWidening> accessWidenings,
        List<BridgedInterfaceInjection> injectedInterfaces,
        Map<String, String> providedVersions,
        boolean loadOnlyIfRequired) {

    public BridgeAssessment {
        findings = List.copyOf(findings);
        entrypoints = List.copyOf(entrypoints);
        providedIds = List.copyOf(providedIds);
        dependencies = List.copyOf(dependencies);
        nestedJars = List.copyOf(nestedJars);
        accessWidenings = List.copyOf(accessWidenings);
        injectedInterfaces = List.copyOf(injectedInterfaces);
        providedVersions = Map.copyOf(providedVersions);
    }

    /** An assessment whose provided IDs all have the mod's version and that loads whenever it can. */
    public BridgeAssessment(
            String loader,
            String modId,
            String version,
            CompatibilityStatus status,
            List<CompatibilityFinding> findings,
            List<BridgedEntrypoint> entrypoints,
            List<String> providedIds,
            List<BridgedDependency> dependencies,
            List<String> nestedJars,
            List<BridgedAccessWidening> accessWidenings,
            List<BridgedInterfaceInjection> injectedInterfaces) {
        this(
                loader,
                modId,
                version,
                status,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                accessWidenings,
                injectedInterfaces,
                Map.of(),
                false);
    }

    /** Builds an assessment without declared relationships; the status follows from the findings. */
    public static BridgeAssessment of(
            String loader,
            String modId,
            String version,
            List<CompatibilityFinding> findings,
            List<BridgedEntrypoint> entrypoints,
            CompatibilityStatus statusWhenNothingBlocks) {
        return of(loader, modId, version, findings, entrypoints, List.of(), List.of(), statusWhenNothingBlocks);
    }

    /** Builds an assessment without nested jars; the status follows from the findings. */
    public static BridgeAssessment of(
            String loader,
            String modId,
            String version,
            List<CompatibilityFinding> findings,
            List<BridgedEntrypoint> entrypoints,
            List<String> providedIds,
            List<BridgedDependency> dependencies,
            CompatibilityStatus statusWhenNothingBlocks) {
        return of(
                loader,
                modId,
                version,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                List.of(),
                statusWhenNothingBlocks);
    }

    /** Builds an assessment whose status follows from the findings. */
    public static BridgeAssessment of(
            String loader,
            String modId,
            String version,
            List<CompatibilityFinding> findings,
            List<BridgedEntrypoint> entrypoints,
            List<String> providedIds,
            List<BridgedDependency> dependencies,
            List<String> nestedJars,
            CompatibilityStatus statusWhenNothingBlocks) {
        boolean blocked = findings.stream().anyMatch(CompatibilityFinding::blocking);
        return new BridgeAssessment(
                loader,
                modId,
                version,
                blocked ? CompatibilityStatus.BLOCKED : statusWhenNothingBlocks,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                List.of(),
                List.of());
    }

    /** This assessment with {@code widenings} as its access widenings (replacing any it had). */
    public BridgeAssessment withAccessWidenings(List<BridgedAccessWidening> widenings) {
        return new BridgeAssessment(
                loader,
                modId,
                version,
                status,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                widenings,
                injectedInterfaces,
                providedVersions,
                loadOnlyIfRequired);
    }

    /** This assessment with {@code injections} as its interface injections (replacing any it had). */
    public BridgeAssessment withInjectedInterfaces(List<BridgedInterfaceInjection> injections) {
        return new BridgeAssessment(
                loader,
                modId,
                version,
                status,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                accessWidenings,
                injections,
                providedVersions,
                loadOnlyIfRequired);
    }

    /** This assessment with {@code added} findings; a blocking one makes it {@link CompatibilityStatus#BLOCKED}. */
    public BridgeAssessment withFindings(List<CompatibilityFinding> added) {
        if (added.isEmpty()) {
            return this;
        }
        List<CompatibilityFinding> all = new ArrayList<>(findings);
        all.addAll(added);
        boolean blocked = added.stream().anyMatch(CompatibilityFinding::blocking);
        return new BridgeAssessment(
                loader,
                modId,
                version,
                blocked ? CompatibilityStatus.BLOCKED : status,
                all,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                accessWidenings,
                injectedInterfaces,
                providedVersions,
                loadOnlyIfRequired);
    }

    /**
     * This assessment with {@code versions} as the versions of provided IDs that differ from the mod's (replacing any it
     * had).
     */
    public BridgeAssessment withProvidedVersions(Map<String, String> versions) {
        return new BridgeAssessment(
                loader,
                modId,
                version,
                status,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                accessWidenings,
                injectedInterfaces,
                versions,
                loadOnlyIfRequired);
    }

    /** This assessment, loading when nested only while a loaded mod needs it ({@code onlyIfRequired}). */
    public BridgeAssessment withLoadOnlyIfRequired(boolean onlyIfRequired) {
        return new BridgeAssessment(
                loader,
                modId,
                version,
                status,
                findings,
                entrypoints,
                providedIds,
                dependencies,
                nestedJars,
                accessWidenings,
                injectedInterfaces,
                providedVersions,
                onlyIfRequired);
    }

    /** The version of {@code id}: a provided version if the mod declares one for it, otherwise the mod's version. */
    public String versionOf(String id) {
        return providedVersions.getOrDefault(id, version);
    }

    /** The mod ID and every provided ID. */
    public List<String> allIds() {
        List<String> ids = new ArrayList<>();
        ids.add(modId);
        ids.addAll(providedIds);
        return ids;
    }

    /** The codes of the blocking findings, for messages about mods that depend on this one. */
    public List<String> blockingCodes() {
        return findings.stream()
                .filter(CompatibilityFinding::blocking)
                .map(CompatibilityFinding::code)
                .distinct()
                .toList();
    }
}
