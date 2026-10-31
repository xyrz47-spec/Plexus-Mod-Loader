package dev.plexusmodloader.game.layer.v26_1.client;

import dev.plexusmodloader.game.layer.v26_1.LayerState;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import dev.plexusmodloader.game.version.layer.ModCountLines;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How many mods each loader loaded, at the bottom left of the title screen, above the version text ({@link
 * ModCountLines}). The rule {@code plexus:client_title_mod_counts} redirects the title screen's one call that draws the
 * version text to {@link #onVersionText}; under MinecraftForge, whose patch draws its own branding there instead, the
 * Forge bridge draws {@link #lines()} above Forge's lines (through {@code GameLayer.brandingLines()}).
 *
 * <p>Texts: the game's current language first ({@link Language}, so a resource pack can change them), then Plexus Mod
 * Loader's own file for the selected language over its English one. The lines are worked out again when the language
 * changes (a new {@link Language} after the resource reload, or another selected code).
 *
 * <p>Thread: render thread.
 */
public final class TitleScreenModCounts {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    /** The lines for one language. */
    private record Translated(Language language, String languageCode, List<String> lines) {}

    private static volatile Translated translated;

    private TitleScreenModCounts() {}

    /**
     * Replaces {@code TitleScreen.extractRenderState}'s call {@code graphics.text(font, versionText, x, y, color)}:
     * draws that line, then the mod counts above it, one line each, with the same x, font and color (which carries the
     * screen's fade), stepping up by the font's line height plus one, as the version text sits above the screen's edge.
     */
    public static void onVersionText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
        List<String> lines = lines();
        int step = font.lineHeight + 1;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y - (lines.size() - i) * step, color);
        }
        if (!lines.isEmpty() && REPORTED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[Plexus Mod Loader] Title screen: {} line(s) of mod counts drawn above the version text: {}",
                    lines.size(),
                    String.join(" | ", lines));
        }
    }

    /** The lines in the game's current language, top to bottom; empty when no mod is loaded. */
    public static List<String> lines() {
        LoadedModCounts counts =
                LayerState.context().map(GameLayerContext::modCounts).orElse(LoadedModCounts.NONE);
        if (counts.equals(LoadedModCounts.NONE)) {
            return List.of();
        }
        Language language = Language.getInstance();
        String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
        Translated current = translated;
        if (current == null
                || current.language() != language
                || !current.languageCode().equals(languageCode)) {
            Map<String, String> bundled = bundledTexts(languageCode);
            current = new Translated(
                    language,
                    languageCode,
                    ModCountLines.lines(
                            counts, key -> language.has(key) ? language.getOrDefault(key) : bundled.get(key)));
            translated = current;
        }
        return current.lines();
    }

    /** Plexus Mod Loader's own texts for {@code languageCode}, over its English ones; English for a language it has no file for. */
    static Map<String, String> bundledTexts(String languageCode) {
        Map<String, String> texts = new HashMap<>();
        readBundled(ModCountLines.DEFAULT_LANGUAGE, texts);
        if (!languageCode.equals(ModCountLines.DEFAULT_LANGUAGE)) {
            readBundled(languageCode, texts);
        }
        return texts;
    }

    private static void readBundled(String languageCode, Map<String, String> texts) {
        Optional<InputStream> file = ModCountLines.bundledLanguage(languageCode);
        if (file.isEmpty()) {
            return;
        }
        try (InputStream in = file.get()) {
            Language.loadFromJson(in, texts::put);
        } catch (IOException e) {
            throw new UncheckedIOException("Plexus Mod Loader's language file " + languageCode + " cannot be read", e);
        }
    }
}
