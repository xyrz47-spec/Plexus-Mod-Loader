package dev.polymodloader.compat.forge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.InvokeHookBeforeReturn;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shows Poly Mod Loader's branding lines (how many mods each loader loaded) above NeoForge's own at the bottom left of
 * the title screen (ADR-0026), as {@link ForgeTitleBranding} does for MinecraftForge. NeoForge's patch replaces the
 * vanilla version text, which the version adapter's rule {@code poly:client_title_mod_counts} draws the lines over, with
 * its branding: the patched {@code TitleScreen.extractRenderState} calls {@code BrandingControl.forEachLine(true, true,
 * consumer)}, which hands the consumer each line of {@code BrandingControl.getBrandings(includeMinecraft, reverse)} with
 * its index; the title screen draws index {@code i} at {@code height - (10 + i * (lineHeight + 1))}, so with {@code
 * reverse} the list runs bottom to top. Checked with {@code javap} against NeoForge 26.1.0.19-beta's, 26.1.2.122's and
 * 26.2.0.93's universal jars and the client jar 26.2.0.93's installer patched.
 *
 * <p>The rule {@link #RULE_ID} hooks the list itself ({@code getBrandings} returns through {@link #withModCounts}), not
 * the title screen's call, so every caller of {@code BrandingControl} gets the lines above NeoForge's: mods that draw the
 * title screen's branding themselves do so through it. FancyMenu 3.9.14 turns the title screen's call off
 * ({@code @WrapWithCondition} returning {@code false}) and draws NeoForge's branding lines from {@code forEachLine} in its
 * own layout, so a hook on the title screen's call drew nothing under FancyMenu (corpusNeoForgeClient, 2026-10-24). The
 * rule is optional ({@link TransformFailurePolicy#DISABLE_RULE}): if a NeoForge release builds its branding differently,
 * the rule is off and the title screen shows NeoForge's lines only. The target is in NeoForge's universal jar, which
 * the host hands the planner ({@code NeoForgeGameHost.transformTargetJars}).
 *
 * <p>Thread: {@link #withModCounts} runs on the render thread; {@link #install} once on the launching thread before.
 */
public final class NeoForgeTitleBranding {

    /** The rule that adds the lines to NeoForge's branding. */
    public static final String RULE_ID = "poly:client_title_mod_counts_neoforge";

    static final String TITLE_SCREEN = "net/minecraft/client/gui/screens/TitleScreen";
    static final String BRANDING_CONTROL = "net/neoforged/neoforge/internal/BrandingControl";
    static final String GET_BRANDINGS = "(ZZ)Ljava/util/List;";

    private static volatile Supplier<List<String>> lines = List::of;
    private static volatile Consumer<String> log = message -> {};
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private NeoForgeTitleBranding() {}

    /** The rule for NeoForge's client of {@code gameVersion}: {@code BrandingControl.getBrandings} returns through the hook. */
    static TransformRuleDefinition rule(String gameVersion) {
        return new TransformRuleDefinition(
                RULE_ID,
                "poly",
                gameVersion,
                Set.of(GameSide.CLIENT),
                new MethodTarget(BRANDING_CONTROL, "getBrandings", GET_BRANDINGS),
                new InvokeHookBeforeReturn(NeoForgeTitleBranding.class.getName().replace('.', '/'), "withModCounts"),
                TransformFailurePolicy.DISABLE_RULE);
    }

    /**
     * Sets the lines to show (top to bottom) and where to report the first time they are handed out.
     *
     * @param brandingLines called each time NeoForge's branding is asked for
     */
    static void install(Supplier<List<String>> brandingLines, Consumer<String> reporter) {
        lines = Objects.requireNonNull(brandingLines, "brandingLines");
        log = Objects.requireNonNull(reporter, "reporter");
    }

    /**
     * What {@code BrandingControl.getBrandings(includeMinecraft, reverse)} returns: NeoForge's lines with Poly Mod
     * Loader's above them. NeoForge's list runs top to bottom, or bottom to top with {@code reverse}; Poly Mod Loader's
     * lines come first in the first case and last (bottom one first) in the second.
     *
     * @param brandings NeoForge's list, as {@code getBrandings} was about to return it
     * @return a new list, or {@code brandings} itself when there are no lines to add
     */
    public static List<String> withModCounts(boolean includeMinecraft, boolean reverse, List<String> brandings) {
        List<String> polyLines = lines.get();
        List<String> cleanedBrandings = new ArrayList<>(brandings.size());
        for (String b : brandings) {
            String stripped = stripModCounts(b);
            if (!stripped.isEmpty()) {
                cleanedBrandings.add(stripped);
            }
        }
        if (polyLines.isEmpty()) {
            return List.copyOf(cleanedBrandings);
        }
        List<String> all = new ArrayList<>(cleanedBrandings.size() + polyLines.size());
        if (reverse) {
            all.addAll(cleanedBrandings);
            all.addAll(polyLines.reversed());
        } else {
            all.addAll(polyLines);
            all.addAll(cleanedBrandings);
        }
        if (REPORTED.compareAndSet(false, true)) {
            log.accept("Title screen: " + polyLines.size() + " line(s) of mod counts drawn above NeoForge's branding: "
                    + String.join(" | ", polyLines));
        }
        return List.copyOf(all);
    }

    /** Strips NeoForge's mod counts from branding lines. */
    static String stripModCounts(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\s*\\(\\d+\\s+mods?\\s+loaded\\)", "")
                .replaceAll(",\\s*\\d+\\s+mods?", "")
                .trim();
        if (cleaned.matches("(?i).*\\b\\d+\\s+mods?\\s+(loaded|active).*")) {
            return "";
        }
        return cleaned;
    }
}
