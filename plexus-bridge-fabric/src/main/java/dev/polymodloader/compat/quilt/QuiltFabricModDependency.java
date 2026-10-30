package dev.polymodloader.compat.quilt;

import java.util.List;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * One {@code depends} or {@code breaks} entry of a Quilt mod as Fabric mods see it, by Quilt Loader's rules ({@code
 * GeneralExt2FabricMetadata.Quilt2FabricModDependency}): a version matches when Quilt's range accepts it, read as a
 * Quilt version. Like Quilt Loader's, it has no Fabric version predicates or intervals to show.
 *
 * @param depends {@code true} for {@code depends}, {@code false} for {@code breaks}
 * @param quilt the Quilt entry
 */
public record QuiltFabricModDependency(boolean depends, org.quiltmc.loader.api.ModDependency.Only quilt)
        implements ModDependency {

    @Override
    public Kind getKind() {
        return depends ? Kind.DEPENDS : Kind.BREAKS;
    }

    @Override
    public String getModId() {
        return quilt.id().id();
    }

    @Override
    public boolean matches(Version version) {
        return quilt.matches(org.quiltmc.loader.api.Version.of(version.getFriendlyString()));
    }

    @Override
    public List<VersionPredicate> getVersionRequirements() {
        return List.of();
    }

    @Override
    public List<VersionInterval> getVersionIntervals() {
        return List.of();
    }
}
