package dev.plexusmodloader.api.content;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A mod's own creative inventory tab, declared with {@code ContentRegistrar.creativeTab}. Created
 * with {@link #builder()}; immutable. The tab's title is the translation of {@code
 * itemGroup.<mod id>.<path>} from the mod's language files.
 *
 * <p>The game's creative screen has room for its own tabs only, so Poly Mod Loader puts mod tabs on extra
 * pages (ten per page, in declaration order) and adds page buttons to the screen. Items can still be
 * listed in the game's tabs with {@link CreativeTab}.
 *
 * @param icon item shown on the tab; empty for the first entry
 * @param entries items listed in the tab, in this order: mod items or the game's
 */
@ApiStability(StabilityLevel.PREVIEW)
public record CreativeTabDefinition(Optional<ResourceId> icon, List<ResourceId> entries) {

    public CreativeTabDefinition {
        Objects.requireNonNull(icon, "icon");
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A creative tab needs at least one entry");
        }
    }

    /** The icon: the declared one or the first entry. */
    public ResourceId iconOrFirstEntry() {
        return icon.orElse(entries.getFirst());
    }

    /** A builder for a tab without entries yet. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link CreativeTabDefinition}. */
    public static final class Builder {

        private Optional<ResourceId> icon = Optional.empty();
        private final List<ResourceId> entries = new ArrayList<>();

        private Builder() {}

        /** Sets the item shown on the tab. */
        public Builder icon(ResourceId item) {
            this.icon = Optional.of(item);
            return this;
        }

        /** Adds an item to the end of the tab. */
        public Builder entry(ResourceId item) {
            entries.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /** Adds items to the end of the tab, in order. */
        public Builder entries(ResourceId... items) {
            for (ResourceId item : items) {
                entry(item);
            }
            return this;
        }

        /**
         * Builds the definition.
         *
         * @throws IllegalArgumentException when no entry was added
         */
        public CreativeTabDefinition build() {
            return new CreativeTabDefinition(icon, entries);
        }
    }
}
