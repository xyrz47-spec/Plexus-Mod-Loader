package dev.polymodloader.game.version.layer;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The lines the title screen shows above the game's version text: how many mods each loader loaded ({@link
 * LoadedModCounts}). One line per loader that loaded at least one mod, top to bottom Poly, Fabric, Quilt, Forge,
 * NeoForge; then, when Poly Mod Loader left mods out because it cannot run them on the host loader's patched game
 * ({@code PLEXUS-COMPAT-224}, ADR-0030), a line that says so, so that a player who never reads the log sees that those
 * mods are missing and that Poly Mod Loader, not the mods, has to change. Nothing when no mod is loaded or left out.
 *
 * <p>Each line is a translation key of Poly Mod Loader's own language files ({@code assets/poly/lang/<language>.json}
 * in this module, {@link #bundledLanguage}) whose text takes the count as its one argument ({@code %s}), as Minecraft's
 * translations do. The game layer looks a key up in the game's current language first, so a resource pack can change
 * the text, and in Poly Mod Loader's file for that language otherwise. A key without text, or text that does not
 * format, falls back to the English line.
 *
 * <p>Thread: any; the methods keep no state.
 */
@ApiStability(StabilityLevel.INTERNAL)
public final class ModCountLines {

    /** Key of the Plexus mods line, {@code "Plexus mods: %s"} in English. */
    public static final String PLEXUS_KEY = "poly.title.mod_count.poly";
    /** Key of the Plexus mods line (legacy alias). */
    public static final String POLY_KEY = PLEXUS_KEY;
    /** Key of the Fabric mods line, {@code "Fabric mods: %s"} in English. */
    public static final String FABRIC_KEY = "poly.title.mod_count.fabric";
    /** Key of the Quilt mods line, {@code "Quilt mods: %s"} in English. */
    public static final String QUILT_KEY = "poly.title.mod_count.quilt";
    /** Key of the Forge mods line, {@code "Forge mods: %s"} in English. */
    public static final String FORGE_KEY = "poly.title.mod_count.forge";
    /** Key of the NeoForge mods line, {@code "NeoForge mods: %s"} in English. */
    public static final String NEOFORGE_KEY = "poly.title.mod_count.neoforge";
    /** Key of the left-out mods line, {@code "Not loaded, Plexus Mod Loader can't run them yet: %s"} in English. */
    public static final String HOST_INCOMPATIBLE_KEY = "poly.title.mod_count.host_incompatible";
    /** The language whose file every other language falls back to, as Minecraft's own. */
    public static final String DEFAULT_LANGUAGE = "en_us";

    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z0-9_-]+");

    private ModCountLines() {}

    /**
     * Returns the lines for {@code counts}, top to bottom.
     *
     * @param translations the text of a key in the language to show, or {@code null} when that language has none
     */
    public static List<String> lines(LoadedModCounts counts, Function<String, String> translations) {
        List<String> lines = new ArrayList<>(6);
        add(lines, PLEXUS_KEY, "Plexus mods: %s", counts.polyMods(), translations);
        add(lines, FABRIC_KEY, "Fabric mods: %s", counts.fabricMods(), translations);
        add(lines, QUILT_KEY, "Quilt mods: %s", counts.quiltMods(), translations);
        add(lines, FORGE_KEY, "Forge mods: %s", counts.forgeMods(), translations);
        add(lines, NEOFORGE_KEY, "NeoForge mods: %s", counts.neoForgeMods(), translations);
        add(
                lines,
                HOST_INCOMPATIBLE_KEY,
                "Not loaded, Plexus Mod Loader can't run them yet: %s",
                counts.hostIncompatibleMods(),
                translations);
        return List.copyOf(lines);
    }

    /**
     * Opens Poly Mod Loader's language file for {@code languageCode} (such as {@code en_us} or {@code ko_kr}), a JSON
     * object from translation key to text in Minecraft's language file format. The caller closes the stream.
     *
     * @return empty when Poly Mod Loader has no file for that language, or the code is not a language code
     */
    public static Optional<InputStream> bundledLanguage(String languageCode) {
        if (!LANGUAGE_CODE.matcher(languageCode).matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                ModCountLines.class.getResourceAsStream("/assets/poly/lang/" + languageCode + ".json"));
    }

    private static void add(
            List<String> lines, String key, String english, int count, Function<String, String> translations) {
        if (count <= 0) {
            return;
        }
        String text = translations.apply(key);
        if (text != null) {
            try {
                lines.add(String.format(Locale.ROOT, text, count));
                return;
            } catch (IllegalFormatException e) {
                // A resource pack's text that does not take one count; the English line is shown instead.
            }
        }
        lines.add(String.format(Locale.ROOT, english, count));
    }
}
