package dev.plexusmodloader.compat.quilt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.plexusmodloader.compat.fabric.mixin.MixinConfigEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModDependencyIdentifier;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.VersionRange;

/**
 * A loaded mod's metadata as Quilt Loader's API shows it ({@code ModContainer.metadata()}, ADR-0027 decision 4).
 *
 * <ul>
 *   <li>A Quilt mod's is its {@code quilt.mod.json} as Quilt Loader reads it ({@link #quilt}): licences, contributors
 *       with their roles, the {@code depends} and {@code breaks} trees, provided IDs with their versions, and as custom
 *       values ({@link #values()}) the top-level entries of the whole file.
 *   <li>Every other mod's is made by the rules of Quilt Loader's {@code FabricModMetadataWrapper} from what the Fabric
 *       Loader API shows of it ({@link #other}): authors get the role {@code Author} and contributors {@code
 *       Contributor}, licences become licence objects ({@code ModLicense.fromIdentifierOrDefault}), Fabric's
 *       dependency intervals become Quilt ranges, and the {@code custom} values are the values.
 * </ul>
 *
 * <p>It implements Quilt Loader's {@link ModMetadataToBeMovedToPlugins} (deprecated there, but what Quilt Loader's own
 * metadata implements and mods cast to) for the Mixin configurations, access wideners and environment.
 *
 * <p>Thread: immutable.
 */
@SuppressWarnings("deprecation")
public final class QuiltModMetadata implements ModMetadataToBeMovedToPlugins {

    /** One provided ID. */
    private record Provided(String group, String id, Version version) implements ProvidedMod {}

    private final String id;
    private final String group;
    private final Version version;
    private final String name;
    private final String description;
    private final List<ModLicense> licenses;
    private final List<ModContributor> contributors;
    private final Map<String, String> contact;
    private final List<ModDependency> depends;
    private final List<ModDependency> breaks;
    private final List<ProvidedMod> provides;
    private final IntFunction<String> icon;
    private final Map<String, LoaderValue> values;
    private final List<MixinConfigEntry> mixins;
    private final List<String> accessWideners;
    private final ModEnvironment environment;

    private QuiltModMetadata(
            String id,
            String group,
            Version version,
            String name,
            String description,
            List<ModLicense> licenses,
            List<ModContributor> contributors,
            Map<String, String> contact,
            List<ModDependency> depends,
            List<ModDependency> breaks,
            List<ProvidedMod> provides,
            IntFunction<String> icon,
            Map<String, LoaderValue> values,
            List<MixinConfigEntry> mixins,
            List<String> accessWideners,
            ModEnvironment environment) {
        this.id = id;
        this.group = group;
        this.version = version;
        this.name = name;
        this.description = description;
        this.licenses = List.copyOf(licenses);
        this.contributors = List.copyOf(contributors);
        this.contact = Collections.unmodifiableMap(new LinkedHashMap<>(contact));
        this.depends = List.copyOf(depends);
        this.breaks = List.copyOf(breaks);
        this.provides = List.copyOf(provides);
        this.icon = icon;
        this.values = values;
        this.mixins = List.copyOf(mixins);
        this.accessWideners = List.copyOf(accessWideners);
        this.environment = environment;
    }

    /** A Quilt mod's metadata, from its {@code quilt.mod.json}. */
    public static QuiltModMetadata quilt(QuiltModDescription mod) {
        Version version = Version.of(mod.version());
        List<ModContributor> contributors = new ArrayList<>();
        mod.contributors().forEach((person, roles) -> contributors.add(ModContributor.of(person, roles)));
        List<ProvidedMod> provides = new ArrayList<>();
        mod.provides()
                .forEach(provided ->
                        provides.add(new Provided(provided.group(), provided.id(), Version.of(provided.version()))));
        TreeMap<Integer, String> icons = new TreeMap<>();
        if (mod.icon() instanceof JsonPrimitive single) {
            icons.put(Integer.MIN_VALUE, single.getAsString());
        } else if (mod.icon() instanceof JsonObject sizes) {
            sizes.entrySet()
                    .forEach(entry -> icons.put(
                            Integer.parseInt(entry.getKey()), entry.getValue().getAsString()));
        }
        return new QuiltModMetadata(
                mod.id(),
                mod.group(),
                version,
                mod.name().isEmpty() ? mod.id() : mod.name(),
                mod.description(),
                mod.licenses(),
                contributors,
                mod.contact(),
                mod.depends().stream().map(QuiltDependencyReader.Read::quilt).toList(),
                mod.breaks().stream().map(QuiltDependencyReader.Read::quilt).toList(),
                provides,
                size -> icon(icons, size),
                QuiltLoaderValue.entries(mod.root(), QuiltModJsonReader.FILE_NAME + ":"),
                mod.mixins(),
                mod.accessWideners(),
                switch (mod.environment()) {
                    case "client" -> ModEnvironment.CLIENT;
                    case "dedicated_server" -> ModEnvironment.SERVER;
                    default -> ModEnvironment.UNIVERSAL;
                });
    }

    /**
     * Another loader's mod (or a built-in one), from what the Fabric Loader API shows of it.
     *
     * @param group the Maven group Quilt shows: {@code loader.fabric} for Fabric mods, {@code builtin} for built-in
     *     ones, {@code loader.<loader>} for the rest
     * @param fabric its Fabric Loader metadata
     * @param mixins its Mixin configurations (a Fabric mod's), or none
     * @param accessWideners its class tweaker paths (a Fabric mod's), or none
     */
    public static QuiltModMetadata other(
            String group,
            net.fabricmc.loader.api.metadata.ModMetadata fabric,
            List<MixinConfigEntry> mixins,
            List<String> accessWideners) {
        Version version = Version.of(fabric.getVersion().getFriendlyString());
        Map<String, List<String>> roles = new LinkedHashMap<>();
        for (Person author : fabric.getAuthors()) {
            roles.computeIfAbsent(author.getName(), unused -> new ArrayList<>()).add("Author");
        }
        for (Person contributor : fabric.getContributors()) {
            roles.computeIfAbsent(contributor.getName(), unused -> new ArrayList<>())
                    .add("Contributor");
        }
        List<ModContributor> contributors = new ArrayList<>();
        roles.forEach((person, list) -> contributors.add(ModContributor.of(person, list)));
        List<ModDependency> depends = new ArrayList<>();
        List<ModDependency> breaks = new ArrayList<>();
        for (net.fabricmc.loader.api.metadata.ModDependency dependency : fabric.getDependencies()) {
            List<ModDependency> target = switch (dependency.getKind()) {
                case DEPENDS -> depends;
                case BREAKS -> breaks;
                default -> null;
            };
            if (target == null) {
                continue;
            }
            VersionRange range = range(dependency.getVersionIntervals());
            if (range.isEmpty()) {
                // Quilt Loader leaves out a range that accepts nothing (and warns); so does Plexus Mod Loader.
                continue;
            }
            target.add(ModDependency.Only.of(
                    ModDependencyIdentifier.of("", dependency.getModId()), range, "Fabric Dependency", null, false));
        }
        List<ProvidedMod> provides = new ArrayList<>();
        fabric.getProvides().forEach(provided -> provides.add(new Provided("", provided, version)));
        Map<String, LoaderValue> values = new LinkedHashMap<>();
        fabric.getCustomValues()
                .forEach((key, value) -> values.put(key, QuiltLoaderValue.of(json(value), "location not supported")));
        return new QuiltModMetadata(
                fabric.getId(),
                group,
                version,
                fabric.getName(),
                fabric.getDescription(),
                fabric.getLicense().stream()
                        .map(ModLicense::fromIdentifierOrDefault)
                        .toList(),
                contributors,
                fabric.getContact().asMap(),
                depends,
                breaks,
                provides,
                // Fabric's lookup picks by size the same way.
                size -> fabric.getIconPath(size).orElse(null),
                Collections.unmodifiableMap(values),
                mixins,
                accessWideners,
                fabric.getEnvironment());
    }

    /** Quilt Loader's choice: the only icon, or the smallest at least {@code size} wide, otherwise the largest. */
    private static String icon(TreeMap<Integer, String> icons, int size) {
        if (icons.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, String> chosen = icons.ceilingEntry(size);
        return (chosen != null ? chosen : icons.lastEntry()).getValue();
    }

    private static VersionRange range(List<VersionInterval> intervals) {
        List<org.quiltmc.loader.api.VersionInterval> quilt = new ArrayList<>();
        for (VersionInterval interval : intervals) {
            Version min = interval.getMin() == null
                    ? null
                    : Version.of(interval.getMin().getFriendlyString());
            Version max = interval.getMax() == null
                    ? null
                    : Version.of(interval.getMax().getFriendlyString());
            quilt.add(org.quiltmc.loader.api.VersionInterval.of(
                    min, interval.isMinInclusive(), max, interval.isMaxInclusive()));
        }
        return VersionRange.ofIntervals(quilt);
    }

    /** A Fabric custom value as JSON. */
    private static JsonElement json(CustomValue value) {
        return switch (value.getType()) {
            case OBJECT -> {
                JsonObject object = new JsonObject();
                value.getAsObject().forEach(entry -> object.add(entry.getKey(), json(entry.getValue())));
                yield object;
            }
            case ARRAY -> {
                JsonArray array = new JsonArray();
                value.getAsArray().forEach(element -> array.add(json(element)));
                yield array;
            }
            case STRING -> new JsonPrimitive(value.getAsString());
            case NUMBER -> new JsonPrimitive(value.getAsNumber());
            case BOOLEAN -> new JsonPrimitive(value.getAsBoolean());
            case NULL -> JsonNull.INSTANCE;
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Collection<ModLicense> licenses() {
        return licenses;
    }

    @Override
    public Collection<ModContributor> contributors() {
        return contributors;
    }

    @Override
    public String getContactInfo(String key) {
        return contact.get(key);
    }

    @Override
    public Map<String, String> contactInfo() {
        return contact;
    }

    @Override
    public Collection<ModDependency> depends() {
        return depends;
    }

    @Override
    public Collection<ModDependency> breaks() {
        return breaks;
    }

    @Override
    public Collection<? extends ProvidedMod> provides() {
        return provides;
    }

    /**
     * The icon for {@code size}, as Quilt Loader picks it: the only one, or of several the smallest at least {@code
     * size} wide, otherwise the largest; {@code null} without one.
     */
    @Override
    public String icon(int size) {
        return icon.apply(size);
    }

    @Override
    public boolean containsValue(String key) {
        return values.containsKey(key);
    }

    @Override
    public LoaderValue value(String key) {
        return values.get(key);
    }

    @Override
    public Map<String, LoaderValue> values() {
        return values;
    }

    /** Its Mixin configurations for {@code side}, as Quilt Loader keeps them. */
    @Override
    public Collection<String> mixins(EnvType side) {
        return mixins.stream()
                .filter(entry -> entry.environment().equals("*")
                        || entry.environment().equals(side == EnvType.CLIENT ? "client" : "server"))
                .map(MixinConfigEntry::config)
                .toList();
    }

    @Override
    public Collection<String> accessWideners() {
        return accessWideners;
    }

    @Override
    public ModEnvironment environment() {
        return environment;
    }

    @Override
    public String toString() {
        return id + " " + version.raw();
    }
}
