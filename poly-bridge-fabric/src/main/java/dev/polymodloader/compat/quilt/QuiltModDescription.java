package dev.polymodloader.compat.quilt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.polymodloader.compat.fabric.mixin.MixinConfigEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.quiltmc.loader.api.ModLicense;

/**
 * A {@code quilt.mod.json} as Quilt Loader reads it (RFC 0002, ADR-0027 decision 1): Quilt's own meaning, not converted
 * to Fabric's. Made by {@link QuiltModJsonReader} only when Quilt Loader would accept the file.
 *
 * @param id {@code quilt_loader.id}
 * @param group {@code quilt_loader.group} (Maven group)
 * @param version {@code quilt_loader.version}
 * @param name {@code metadata.name}, or {@code ""}
 * @param description {@code metadata.description}, or {@code ""}
 * @param contributors {@code metadata.contributors}: name to roles, in file order
 * @param contact {@code metadata.contact}: kind to address, in file order
 * @param licenses {@code metadata.license}, as Quilt Loader's licence objects
 * @param icon {@code metadata.icon} (a path text, or an object of size to path), or {@code null}
 * @param provides {@code quilt_loader.provides}
 * @param entrypoints {@code quilt_loader.entrypoints}: key to declarations, in file order
 * @param jars the {@code quilt_loader.jars} entries Quilt Loader opens (entries with a {@code ..} part are left out, as
 *     Quilt Loader skips them)
 * @param depends {@code quilt_loader.depends}
 * @param breaks {@code quilt_loader.breaks}
 * @param loadType {@code quilt_loader.load_type}: {@code always}, {@code if_possible} or {@code if_required} (default)
 * @param mixins the top-level {@code mixin} configurations, with the side each applies to
 * @param accessWideners the top-level {@code access_widener} paths
 * @param environment {@code minecraft.environment}: {@code *} (also for {@code ""} or absent), {@code client} or {@code
 *     dedicated_server}
 * @param intermediateMappings {@code quilt_loader.intermediate_mappings}
 * @param languageAdapters {@code quilt_loader.language_adapters}
 * @param root the whole file, for the Quilt API's custom values ({@code ModMetadata.values()})
 */
public record QuiltModDescription(
        String id,
        String group,
        String version,
        String name,
        String description,
        Map<String, List<String>> contributors,
        Map<String, String> contact,
        List<ModLicense> licenses,
        JsonElement icon,
        List<Provided> provides,
        Map<String, List<Entrypoint>> entrypoints,
        List<String> jars,
        List<QuiltDependencyReader.Read> depends,
        List<QuiltDependencyReader.Read> breaks,
        String loadType,
        List<MixinConfigEntry> mixins,
        List<String> accessWideners,
        String environment,
        String intermediateMappings,
        Map<String, String> languageAdapters,
        JsonObject root) {

    /**
     * One {@code provides} entry.
     *
     * @param group its Maven group (the mod's own when the entry names none)
     * @param id the provided ID
     * @param version its version (the mod's own when the entry names none)
     */
    public record Provided(String group, String id, String version) {}

    /**
     * One entrypoint declaration.
     *
     * @param adapter the language adapter, {@code default} for a text entry
     * @param value the class, {@code Class::field} or {@code Class::method}
     */
    public record Entrypoint(String adapter, String value) {}

    /** A licence written as an object ({@code name}, {@code id}, {@code url}, optional {@code description}). */
    public record License(String name, String id, String url, String description) implements ModLicense {}

    public QuiltModDescription {
        contributors = Collections.unmodifiableMap(new LinkedHashMap<>(contributors));
        contact = Collections.unmodifiableMap(new LinkedHashMap<>(contact));
        licenses = List.copyOf(licenses);
        provides = List.copyOf(provides);
        Map<String, List<Entrypoint>> copied = new LinkedHashMap<>();
        entrypoints.forEach((key, list) -> copied.put(key, List.copyOf(list)));
        entrypoints = Collections.unmodifiableMap(copied);
        jars = List.copyOf(jars);
        depends = List.copyOf(depends);
        breaks = List.copyOf(breaks);
        mixins = List.copyOf(mixins);
        accessWideners = List.copyOf(accessWideners);
        languageAdapters = Collections.unmodifiableMap(new LinkedHashMap<>(languageAdapters));
        root = root.deepCopy();
    }

    /** Whether the mod, when nested, loads only while a loaded mod requires it ({@code load_type} {@code if_required}). */
    public boolean loadOnlyIfRequired() {
        return loadType.equals("if_required");
    }
}
