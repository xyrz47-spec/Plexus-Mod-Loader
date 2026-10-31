package dev.plexusmodloader.compat.fabric.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.plexusmodloader.compat.quilt.QuiltFabricModDependency;
import dev.plexusmodloader.compat.quilt.QuiltModDescription;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.util.version.StringVersion;
import org.quiltmc.loader.api.ModLicense;

/**
 * {@link ModMetadata} of a loaded mod: read from {@code fabric.mod.json} for Fabric mods, from {@code quilt.mod.json}
 * for Quilt mods ({@code quilt}, {@link #quilt}), built from what Plexus Mod Loader knows for Plexus Mods ({@code getType()}
 * is {@code plexus}) and for the built-in {@code minecraft}, {@code java} and {@code fabricloader} ({@code builtin}).
 */
final class FabricModMetadata implements ModMetadata {

    private final String type;
    private final String id;
    private final Version version;
    private final String name;
    private final String description;
    private final List<String> provides;
    private final ModEnvironment environment;
    private final List<ModDependency> dependencies;
    private final List<Person> authors;
    private final List<Person> contributors;
    private final ContactInformation contact;
    private final List<String> license;
    private final JsonElement icon;
    private final Map<String, CustomValue> custom;

    private FabricModMetadata(
            String type,
            String id,
            String version,
            String name,
            String description,
            List<String> provides,
            ModEnvironment environment,
            List<ModDependency> dependencies,
            List<Person> authors,
            List<Person> contributors,
            ContactInformation contact,
            List<String> license,
            JsonElement icon,
            Map<String, CustomValue> custom) {
        this.type = type;
        this.id = id;
        this.version = version(version);
        this.name = name;
        this.description = description;
        this.provides = List.copyOf(provides);
        this.environment = environment;
        this.dependencies = List.copyOf(dependencies);
        this.authors = authors;
        this.contributors = contributors;
        this.contact = contact;
        this.license = List.copyOf(license);
        this.icon = icon;
        this.custom = Map.copyOf(custom);
    }

    /** Metadata of a Fabric mod, from its {@code fabric.mod.json}. */
    static FabricModMetadata fabric(JsonObject root) {
        String id = text(root.get("id"), "?");
        Map<String, CustomValue> custom = new LinkedHashMap<>();
        if (root.get("custom") instanceof JsonObject object) {
            object.entrySet().forEach(entry -> custom.put(entry.getKey(), JsonCustomValue.of(entry.getValue())));
        }
        return new FabricModMetadata(
                "fabric",
                id,
                text(root.get("version"), "0"),
                text(root.get("name"), id),
                text(root.get("description"), ""),
                texts(root.get("provides")),
                environment(text(root.get("environment"), "*")),
                dependencies(root),
                FabricPeople.people(root.get("authors")),
                FabricPeople.people(root.get("contributors")),
                FabricPeople.contact(root.get("contact")),
                texts(root.get("license")),
                root.get("icon"),
                custom);
    }

    /**
     * Metadata of a Quilt mod as Fabric mods see it, by Quilt Loader's rules ({@code GeneralExt2FabricMetadata}): type
     * {@code quilt}; contributors with the {@code Owner} role are the authors, the others contributors; licences by
     * their IDs; only single {@code depends} and {@code breaks} entries ({@code ModDependency.Only}) become
     * dependencies; the custom values are the top-level entries of the whole {@code quilt.mod.json}.
     */
    static FabricModMetadata quilt(QuiltModDescription quilt) {
        List<Person> authors = new ArrayList<>();
        List<Person> contributors = new ArrayList<>();
        quilt.contributors().forEach((name, roles) -> {
            if (roles.contains("Owner")) {
                authors.add(FabricPeople.person(name));
            } else {
                contributors.add(FabricPeople.person(name));
            }
        });
        List<ModDependency> dependencies = new ArrayList<>();
        quilt.depends().forEach(read -> {
            if (read.quilt() instanceof org.quiltmc.loader.api.ModDependency.Only only) {
                dependencies.add(new QuiltFabricModDependency(true, only));
            }
        });
        quilt.breaks().forEach(read -> {
            if (read.quilt() instanceof org.quiltmc.loader.api.ModDependency.Only only) {
                dependencies.add(new QuiltFabricModDependency(false, only));
            }
        });
        Map<String, CustomValue> custom = new LinkedHashMap<>();
        quilt.root().entrySet().forEach(entry -> custom.put(entry.getKey(), JsonCustomValue.of(entry.getValue())));
        return new FabricModMetadata(
                "quilt",
                quilt.id(),
                quilt.version(),
                quilt.name().isEmpty() ? quilt.id() : quilt.name(),
                quilt.description(),
                quilt.provides().stream()
                        .map(QuiltModDescription.Provided::id)
                        .distinct()
                        .toList(),
                switch (quilt.environment()) {
                    case "client" -> ModEnvironment.CLIENT;
                    case "dedicated_server" -> ModEnvironment.SERVER;
                    default -> ModEnvironment.UNIVERSAL;
                },
                dependencies,
                List.copyOf(authors),
                List.copyOf(contributors),
                FabricPeople.contact(quilt.contact()),
                quilt.licenses().stream().map(ModLicense::id).toList(),
                quilt.icon(),
                custom);
    }

    /** Metadata of a mod Plexus Mod Loader knows without a {@code fabric.mod.json}. */
    static FabricModMetadata other(String type, String id, String version, String name, List<String> provides) {
        return new FabricModMetadata(
                type,
                id,
                version,
                name,
                "",
                provides,
                ModEnvironment.UNIVERSAL,
                List.of(),
                List.of(),
                List.of(),
                ContactInformation.EMPTY,
                List.of(),
                null,
                Map.of());
    }

    private static Version version(String text) {
        try {
            return Version.parse(text);
        } catch (VersionParsingException | RuntimeException e) {
            return new StringVersion(text);
        }
    }

    private static ModEnvironment environment(String text) {
        return switch (text) {
            case "client" -> ModEnvironment.CLIENT;
            case "server" -> ModEnvironment.SERVER;
            default -> ModEnvironment.UNIVERSAL;
        };
    }

    private static List<ModDependency> dependencies(JsonObject root) {
        List<ModDependency> dependencies = new ArrayList<>();
        for (ModDependency.Kind kind : ModDependency.Kind.values()) {
            if (!(root.get(kind.getKey()) instanceof JsonObject declared)) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : declared.entrySet()) {
                List<VersionPredicate> predicates = new ArrayList<>();
                List<String> requirements =
                        entry.getValue().isJsonArray() ? texts(entry.getValue()) : List.of(text(entry.getValue(), "*"));
                try {
                    for (String requirement : requirements) {
                        predicates.add(VersionPredicate.parse(requirement));
                    }
                } catch (VersionParsingException e) {
                    // The bridge already blocked a mod with an unreadable required predicate; skip soft ones.
                    continue;
                }
                dependencies.add(new FabricModDependency(kind, entry.getKey(), List.copyOf(predicates)));
            }
        }
        return dependencies;
    }

    private static String text(JsonElement json, String fallback) {
        return json != null && json.isJsonPrimitive() ? json.getAsString() : fallback;
    }

    /** A text, or an array of texts. */
    private static List<String> texts(JsonElement json) {
        List<String> values = new ArrayList<>();
        if (json instanceof JsonArray array) {
            array.forEach(element -> {
                if (element.isJsonPrimitive()) {
                    values.add(element.getAsString());
                }
            });
        } else if (json != null && json.isJsonPrimitive()) {
            values.add(json.getAsString());
        }
        return values;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getProvides() {
        return provides;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Collection<Person> getAuthors() {
        return authors;
    }

    @Override
    public Collection<Person> getContributors() {
        return contributors;
    }

    @Override
    public ContactInformation getContact() {
        return contact;
    }

    @Override
    public Collection<String> getLicense() {
        return license;
    }

    /**
     * The icon for {@code size}: the only one, or of several (an object of size to path) the smallest at least
     * {@code size} wide, otherwise the largest.
     */
    @Override
    public Optional<String> getIconPath(int size) {
        if (icon == null || icon.isJsonNull()) {
            return Optional.empty();
        }
        if (icon.isJsonPrimitive()) {
            return Optional.of(icon.getAsString());
        }
        if (!(icon instanceof JsonObject sizes) || sizes.isEmpty()) {
            return Optional.empty();
        }
        TreeMap<Integer, String> bySize = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : sizes.entrySet()) {
            try {
                bySize.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
            } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
                // Not a size entry; Fabric rejects such metadata, Plexus Mod Loader ignores the entry.
            }
        }
        if (bySize.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Integer, String> chosen = bySize.ceilingEntry(size);
        return Optional.of((chosen != null ? chosen : bySize.lastEntry()).getValue());
    }

    @Override
    public boolean containsCustomValue(String key) {
        return custom.containsKey(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return custom.get(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return custom;
    }

    @Override
    @Deprecated
    public boolean containsCustomElement(String key) {
        return containsCustomValue(key);
    }
}
