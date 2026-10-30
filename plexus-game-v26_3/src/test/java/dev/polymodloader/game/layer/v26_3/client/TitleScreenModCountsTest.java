package dev.polymodloader.game.layer.v26_3.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.game.version.layer.LoadedModCounts;
import dev.polymodloader.game.version.layer.ModCountLines;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Poly Mod Loader's language files read with the game's own reader ({@code Language.loadFromJson}). */
class TitleScreenModCountsTest {

    private static final LoadedModCounts COUNTS = new LoadedModCounts(2, 14, 3, 5, 7);

    @Test
    void englishLines() {
        Map<String, String> texts = TitleScreenModCounts.bundledTexts("en_us");

        assertEquals(
                List.of("Plexus mods: 2", "Fabric mods: 14", "Quilt mods: 5", "Forge mods: 3", "NeoForge mods: 7"),
                ModCountLines.lines(COUNTS, texts::get));
    }

    @Test
    void koreanLines() {
        Map<String, String> texts = TitleScreenModCounts.bundledTexts("ko_kr");

        assertEquals(
                List.of("Plexus 모드: 2개", "Fabric 모드: 14개", "Quilt 모드: 5개", "Forge 모드: 3개", "NeoForge 모드: 7개"),
                ModCountLines.lines(COUNTS, texts::get));
    }

    /** A language Poly Mod Loader has no file for shows the English lines, as Minecraft falls back to English. */
    @Test
    void otherLanguagesFallBackToEnglish() {
        Map<String, String> texts = TitleScreenModCounts.bundledTexts("de_de");

        assertEquals(List.of("Fabric mods: 1"), ModCountLines.lines(new LoadedModCounts(0, 1, 0), texts::get));
    }
}
