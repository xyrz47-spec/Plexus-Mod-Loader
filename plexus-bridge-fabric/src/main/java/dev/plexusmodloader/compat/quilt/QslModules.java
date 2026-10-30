package dev.plexusmodloader.compat.quilt;

import java.util.Set;

/**
 * The mod IDs of the Quilt Standard Libraries (QSL) and Quilted Fabric API (QFAPI), neither of which has a release for
 * Minecraft 26.x (ADR-0027 decision 6, {@code PLEXUS-COMPAT-308}). This is the only place the list lives.
 *
 * <p>Sources, read 2026-10-24:
 *
 * <ul>
 *   <li>QSL module IDs: every {@code library/<library>/<module>/build.gradle} {@code qslModule { id = "..." }} of
 *       QuiltMC/quilt-standard-libraries, the union of branch {@code 1.21.5} (default, commit {@code fef35afe5fb0}),
 *       {@code 1.21} ({@code 871ca7abf109}), {@code 1.20.6} ({@code ad2808d1a02b}), {@code 1.20} ({@code
 *       6552aac60de2}) and {@code 1.19.4} ({@code ff5d1124c9eb}). The older branches add {@code quilt_advancement},
 *       {@code quilt_entity}, {@code quilt_entity_networking}, {@code quilt_point_of_interest}, {@code quilt_vehicle}
 *       and {@code quilt_villager}. {@code quilt_registry_entry_attachment} also {@code provides} {@code
 *       quilt_registry_entry_attachments}.
 *   <li>{@code qsl}: the parent ID QSL's {@code QmjBuilder} writes into each module's {@code modmenu.parent}, and the
 *       ID of the bundled QSL jar.
 *   <li>QFAPI: the umbrella {@code quilted_fabric_api} ({@code src/main/resources/quilt.mod.json}) and its modules,
 *       whose IDs are {@code quilted_} + the Fabric API module name with {@code _} for {@code -} (for example {@code
 *       fabric-api-base/src/main/resources/quilt.mod.json}: {@code quilted_fabric_api_base}) on branch {@code 1.20.1}
 *       of QuiltMC/quilted-fabric-api (commit {@code db482fe3067e}). Every Fabric API module name starts with {@code
 *       fabric-}, so the prefix {@code quilted_fabric_} names exactly these modules.
 * </ul>
 *
 * <p>Other {@code quilt_*} IDs ({@code quilt_loader}, or a mod's own {@code quilt_nested_lib}) are not QSL.
 */
final class QslModules {

    private static final Set<String> IDS = Set.of(
            "qsl",
            "quilted_fabric_api",
            "quilt_advancement",
            "quilt_base",
            "quilt_biome",
            "quilt_block_content_registry",
            "quilt_block_entity",
            "quilt_block_extensions",
            "quilt_chat",
            "quilt_client_command",
            "quilt_command",
            "quilt_crash_info",
            "quilt_data_callback",
            "quilt_datafixerupper",
            "quilt_dimension",
            "quilt_entity",
            "quilt_entity_events",
            "quilt_entity_extensions",
            "quilt_entity_multipart",
            "quilt_entity_networking",
            "quilt_entity_rendering",
            "quilt_item_content_registry",
            "quilt_item_extensions",
            "quilt_item_group",
            "quilt_item_setting",
            "quilt_lifecycle_events",
            "quilt_networking",
            "quilt_point_of_interest",
            "quilt_recipe",
            "quilt_registry",
            "quilt_registry_entry_attachment",
            "quilt_registry_entry_attachments",
            "quilt_resource_loader",
            "quilt_screen",
            "quilt_status_effect",
            "quilt_surface_rule",
            "quilt_tags",
            "quilt_testing",
            "quilt_tooltip",
            "quilt_vehicle",
            "quilt_villager");

    /** The ID prefix of Quilted Fabric API's modules. */
    private static final String QFAPI_MODULE_PREFIX = "quilted_fabric_";

    private QslModules() {}

    /** Whether {@code id} names a module of QSL or Quilted Fabric API. */
    static boolean isQslModule(String id) {
        return IDS.contains(id) || id.startsWith(QFAPI_MODULE_PREFIX);
    }
}
