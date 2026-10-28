package dev.polymodloader.compat.forge.modlauncher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.jarhandling.SecureJar;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR-0021 G8: with one Mixin for Forge and Fabric mods, MixinExtras comes from Poly Mod Loader's class path, where it
 * defines the classes it generates ({@code LocalRefImpl} next to {@code GeneratedImplDummy}). A MixinExtras module in
 * FML's GAME layer would hold that package in the game class loader, so the game's classes would not find them. Carry On
 * 2.12.0 for 26.3 nests mixinextras-forge 0.5.5, newer than Forge 66.1.0's 0.5.4, and FML's JarJar selection then put
 * its MixinExtras-0.5.5.jar into the GAME layer.
 */
class SharedMixinExtrasTest {

    /**
     * A GAME layer jar as FML gives it (securemodules' own jars need Forge's unsafe library): its module name, file and
     * entries. A jar FML's JarJar selection opened inside another jar ({@code nested}) is the root of its own file
     * system, named by its URI only.
     */
    private static SecureJar jar(String fileName, boolean nested, String moduleName, String... entries) {
        Set<String> files = Set.of(entries);
        URI uri = nested
                ? URI.create("jar:roimfs:jar-jar-8/" + fileName + "!/")
                : Path.of(fileName).toAbsolutePath().toUri();
        SecureJar.ModuleDataProvider data = (SecureJar.ModuleDataProvider) Proxy.newProxyInstance(
                SharedMixinExtrasTest.class.getClassLoader(),
                new Class<?>[] {SecureJar.ModuleDataProvider.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "name" -> moduleName;
                    case "uri" -> uri;
                    case "findFile" ->
                        files.contains((String) arguments[0])
                                ? Optional.of(URI.create(uri + "/" + arguments[0]))
                                : Optional.empty();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (SecureJar) Proxy.newProxyInstance(
                SharedMixinExtrasTest.class.getClassLoader(),
                new Class<?>[] {SecureJar.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "name" -> moduleName;
                    case "moduleDataProvider" -> data;
                    case "getPrimaryPath" ->
                        nested
                                ? Path.of(fileName).toAbsolutePath().getRoot()
                                : Path.of(fileName).toAbsolutePath();
                    case "toString" -> fileName;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static List<SecureJar> gameJars() {
        return List.of(
                jar("carryon-forge-26.3-2.12.0.jar", false, "carryon", "tschipp/carryon/CarryOnCommon.class"),
                // As FML's JarJar selection finds them inside Carry On: MixinExtras itself, then its Forge platform
                // jar.
                jar(
                        "MixinExtras-0.5.5.jar",
                        true,
                        "MixinExtras",
                        "com/llamalad7/mixinextras/MixinExtrasBootstrap.class",
                        "com/llamalad7/mixinextras/sugar/impl/ref/generated/GeneratedImplDummy.class"),
                jar(
                        "mixinextras-forge-0.5.5.jar",
                        true,
                        "mixinextras",
                        "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class"));
    }

    @Test
    void aMixinExtrasGameLibraryIsLeftOutWhenPolyModLoadersClassPathHasMixinExtras() {
        List<String> log = new ArrayList<>();

        List<SecureJar> kept = SharedMixinExtras.withoutMixinExtras(gameJars(), true, log::add);

        assertEquals(
                List.of("carryon", "mixinextras"),
                kept.stream().map(SecureJar::name).toList());
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.getFirst().contains("MixinExtras (MixinExtras-0.5.5.jar)"), log.getFirst());
    }

    @Test
    void everyGameJarStaysWithoutMixinExtrasOnPolyModLoadersClassPath() {
        List<String> log = new ArrayList<>();

        List<SecureJar> kept = SharedMixinExtras.withoutMixinExtras(gameJars(), false, log::add);

        assertEquals(
                List.of("carryon", "MixinExtras", "mixinextras"),
                kept.stream().map(SecureJar::name).toList());
        assertEquals(List.of(), log);
    }
}
