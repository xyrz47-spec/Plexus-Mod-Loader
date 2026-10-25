package dev.polymodloader.game.version.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModCountLinesTest {

    private static final LoadedModCounts ALL_LOADERS = new LoadedModCounts(2, 14, 3, 5, 7);

    @Test
    void englishLinesForEveryLoaderTopToBottom() throws IOException {
        Map<String, String> english = bundled("en_us");

        assertEquals(
                List.of("Poly mods: 2", "Fabric mods: 14", "Quilt mods: 5", "Forge mods: 3", "NeoForge mods: 7"),
                ModCountLines.lines(ALL_LOADERS, english::get));
    }

    @Test
    void koreanLines() throws IOException {
        Map<String, String> korean = bundled("ko_kr");

        assertEquals(
                List.of("Poly 모드: 2개", "Fabric 모드: 14개", "Quilt 모드: 5개", "Forge 모드: 3개", "NeoForge 모드: 7개"),
                ModCountLines.lines(ALL_LOADERS, korean::get));
    }

    @Test
    void bothBundledLanguagesHaveEveryKey() throws IOException {
        for (String language : List.of("en_us", "ko_kr")) {
            assertEquals(
                    List.of(
                            ModCountLines.POLY_KEY,
                            ModCountLines.FABRIC_KEY,
                            ModCountLines.QUILT_KEY,
                            ModCountLines.FORGE_KEY,
                            ModCountLines.NEOFORGE_KEY),
                    List.copyOf(bundled(language).keySet()),
                    language);
        }
    }

    @Test
    void loadersWithoutModsHaveNoLine() throws IOException {
        Map<String, String> english = bundled("en_us");

        assertEquals(List.of("Fabric mods: 1"), ModCountLines.lines(new LoadedModCounts(0, 1, 0), english::get));
        assertEquals(
                List.of("Poly mods: 1", "Forge mods: 5"),
                ModCountLines.lines(new LoadedModCounts(1, 0, 5), english::get));
        assertEquals(List.of("Quilt mods: 2"), ModCountLines.lines(new LoadedModCounts(0, 0, 0, 2), english::get));
        assertEquals(
                List.of("Poly mods: 1", "NeoForge mods: 4"),
                ModCountLines.lines(new LoadedModCounts(1, 0, 0, 0, 4), english::get));
        assertEquals(List.of(), ModCountLines.lines(LoadedModCounts.NONE, english::get));
    }

    @Test
    void aKeyWithoutTextFallsBackToEnglish() {
        assertEquals(
                List.of("Poly mods: 2", "Fabric mods: 14", "Quilt mods: 5", "Forge mods: 3", "NeoForge mods: 7"),
                ModCountLines.lines(ALL_LOADERS, key -> null));
    }

    /** A resource pack's text replaces Poly Mod Loader's; text that cannot take the count is not shown. */
    @Test
    void textThatDoesNotFormatFallsBackToEnglish() {
        Map<String, String> pack = Map.of(
                ModCountLines.POLY_KEY, "Native: %s",
                ModCountLines.FABRIC_KEY, "Fabric: %d",
                ModCountLines.FORGE_KEY, "Forge: %s and %s");

        assertEquals(
                List.of("Native: 2", "Fabric: 14", "Quilt mods: 5", "Forge mods: 3", "NeoForge mods: 7"),
                ModCountLines.lines(ALL_LOADERS, pack::get));
    }

    @Test
    void onlyLanguageCodesOpenAFile() throws IOException {
        assertTrue(ModCountLines.bundledLanguage("../lang/en_us").isEmpty());
        assertTrue(ModCountLines.bundledLanguage("EN_US").isEmpty());
        assertTrue(ModCountLines.bundledLanguage("xx_yy").isEmpty());
        try (InputStream in = ModCountLines.bundledLanguage("en_us").orElseThrow()) {
            assertTrue(in.readAllBytes().length > 0);
        }
    }

    @Test
    void countsCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new LoadedModCounts(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LoadedModCounts(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new LoadedModCounts(0, 0, 0, 0, -1));
    }

    private static Map<String, String> bundled(String language) throws IOException {
        try (InputStream in = ModCountLines.bundledLanguage(language).orElseThrow()) {
            return new Gson()
                    .fromJson(
                            new InputStreamReader(in, StandardCharsets.UTF_8),
                            new TypeToken<java.util.LinkedHashMap<String, String>>() {}.getType());
        }
    }
}
