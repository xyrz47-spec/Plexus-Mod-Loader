package dev.polymodloader.runtime.metadata;

/**
 * One entry of {@code replacesSystems} in {@code poly.mod.json}.
 *
 * @param systemId ID of the system to replace, e.g. {@code poly:game_brand}
 * @param providerClass fully qualified class implementing {@code SystemProvider}
 * @param override true when this mod intentionally overrides a replacement from a mod it depends on
 */
public record SystemReplacementDeclaration(String systemId, String providerClass, boolean override) {}
