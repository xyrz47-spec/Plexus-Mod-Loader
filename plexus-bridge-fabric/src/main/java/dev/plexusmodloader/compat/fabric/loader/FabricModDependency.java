package dev.plexusmodloader.compat.fabric.loader;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** One {@code depends}/{@code recommends}/... entry; it matches when any of its predicates does, as in Fabric. */
record FabricModDependency(Kind kind, String modId, List<VersionPredicate> predicates) implements ModDependency {

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public boolean matches(Version version) {
        return predicates.stream().anyMatch(predicate -> predicate.test(version));
    }

    @Override
    public List<VersionPredicate> getVersionRequirements() {
        return predicates;
    }

    @Override
    public List<VersionInterval> getVersionIntervals() {
        List<VersionInterval> intervals = new ArrayList<>();
        for (VersionPredicate predicate : predicates) {
            intervals = VersionInterval.or(intervals, predicate.getInterval());
        }
        return intervals;
    }
}
