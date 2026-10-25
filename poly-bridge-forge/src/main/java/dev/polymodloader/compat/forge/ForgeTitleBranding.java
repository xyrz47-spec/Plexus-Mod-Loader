package dev.polymodloader.compat.forge;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

/**
 * Shows Poly Mod Loader's branding lines (how many mods each loader loaded) above MinecraftForge's own at the bottom
 * left of the title screen. Forge's patch replaces the vanilla version text, which the version adapter's rule {@code
 * poly:client_title_mod_counts} draws the lines over, with its branding: the patched {@code
 * TitleScreen.extractRenderState} hands a consumer that draws one line to {@code BrandingControl.forEachLine(boolean,
 * boolean, ObjIntConsumer)}, which calls it with each line and its index counted up from the bottom; the consumer draws
 * index {@code i} at {@code height - (10 + i * (lineHeight + 1))}, at x = 2, with the title screen's fade. Checked with
 * {@code javap} against Forge's patched client classes and its universal jar of every release in {@code
 * forge-releases.properties} (26.1-62.0.9, 26.1.1-63.0.2, 26.1.2-64.1.5, 26.2-65.1.5): {@code extractRenderState} makes
 * that call once, with {@code (true, true, consumer)}.
 *
 * <p>The rule {@link #RULE_ID} redirects that call to {@link #forEachLine}, which lets Forge draw its lines and then
 * hands Forge's consumer Poly Mod Loader's lines with the next indexes, so they sit right above Forge's, look like them
 * and never overlap them. The rule is optional ({@link TransformFailurePolicy#DISABLE_RULE}): if a Forge release draws
 * its branding differently, the rule is off and the title screen shows Forge's lines only.
 *
 * <p>Thread: {@link #forEachLine} runs on the render thread; {@link #install} once on the launching thread before.
 */
public final class ForgeTitleBranding {

    /** The rule that redirects Forge's branding call. */
    public static final String RULE_ID = "poly:client_title_mod_counts_forge";

    /** The version adapter's rule on the same method, for vanilla's version text; it does not apply to Forge's patch. */
    static final String VANILLA_RULE_ID = "poly:client_title_mod_counts";

    static final String TITLE_SCREEN = "net/minecraft/client/gui/screens/TitleScreen";
    static final String BRANDING_CONTROL = "net/minecraftforge/internal/BrandingControl";

    private static final MethodType FOR_EACH_LINE =
            MethodType.methodType(void.class, boolean.class, boolean.class, ObjIntConsumer.class);

    private static volatile Supplier<List<String>> lines = List::of;
    private static volatile Consumer<String> log = message -> {};
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static volatile MethodHandle forgeForEachLine;

    private ForgeTitleBranding() {}

    /**
     * The rule for Forge's patched client of {@code gameVersion}: the redirect of {@code BrandingControl.forEachLine}
     * in {@code TitleScreen.extractRenderState}. It names the version adapter's rule on the same method as earlier, so
     * the planner combines the two instead of rejecting both; that one's call is not in Forge's patch, so it is off.
     */
    static TransformRuleDefinition rule(String gameVersion) {
        return new TransformRuleDefinition(
                RULE_ID,
                "poly",
                gameVersion,
                Set.of(GameSide.CLIENT),
                new MethodTarget(
                        TITLE_SCREEN, "extractRenderState", "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
                new RedirectCall(
                        new MethodTarget(BRANDING_CONTROL, "forEachLine", FOR_EACH_LINE.toMethodDescriptorString()),
                        0,
                        ForgeTitleBranding.class.getName().replace('.', '/'),
                        "forEachLine"),
                TransformFailurePolicy.DISABLE_RULE,
                List.of(VANILLA_RULE_ID));
    }

    /**
     * Sets the lines to show (top to bottom) and where to report the first time they are drawn.
     *
     * @param brandingLines called each time the title screen draws its branding
     */
    static void install(Supplier<List<String>> brandingLines, Consumer<String> reporter) {
        lines = Objects.requireNonNull(brandingLines, "brandingLines");
        log = Objects.requireNonNull(reporter, "reporter");
    }

    /**
     * Replaces the patched title screen's call to {@code BrandingControl.forEachLine(includeMinecraft, reverse, line)}:
     * calls it, then {@code line} with Poly Mod Loader's lines, bottom one first, at the indexes after Forge's.
     *
     * @throws IllegalStateException when Forge's {@code BrandingControl.forEachLine} cannot be found from the title
     *     screen's class loader, which the unchanged call would have failed on as well
     */
    public static void forEachLine(boolean includeMinecraft, boolean reverse, ObjIntConsumer<String> line) {
        int[] next = {0};
        ObjIntConsumer<String> forgeLine = (text, index) -> {
            line.accept(text, index);
            next[0] = Math.max(next[0], index + 1);
        };
        try {
            forgeForEachLine(line).invokeExact(includeMinecraft, reverse, forgeLine);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Forge's BrandingControl.forEachLine failed", e);
        }
        drawAbove(lines.get(), next[0], line);
    }

    /** Hands {@code line} the lines (top to bottom) at the indexes from {@code firstIndex} up, the bottom one first. */
    static void drawAbove(List<String> polyLines, int firstIndex, ObjIntConsumer<String> line) {
        for (int i = 0; i < polyLines.size(); i++) {
            line.accept(polyLines.get(polyLines.size() - 1 - i), firstIndex + i);
        }
        if (!polyLines.isEmpty() && REPORTED.compareAndSet(false, true)) {
            log.accept("Title screen: " + polyLines.size() + " line(s) of mod counts drawn above MinecraftForge's"
                    + " branding: " + String.join(" | ", polyLines));
        }
    }

    /**
     * Forge's {@code forEachLine}, found once from the class loader of the title screen's consumer (a lambda of {@code
     * TitleScreen}, so the loader that defines Forge's game layer). Forge's universal jar is an automatic module, so
     * the class is public in an exported package.
     */
    private static MethodHandle forgeForEachLine(Object titleScreenLambda) {
        MethodHandle handle = forgeForEachLine;
        if (handle == null) {
            try {
                Class<?> brandingControl = Class.forName(
                        BRANDING_CONTROL.replace('/', '.'),
                        true,
                        titleScreenLambda.getClass().getClassLoader());
                handle = MethodHandles.publicLookup().findStatic(brandingControl, "forEachLine", FOR_EACH_LINE);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "POLY-COMPAT-210 Forge's " + BRANDING_CONTROL.replace('/', '.') + ".forEachLine" + FOR_EACH_LINE
                                + " is not reachable from the title screen",
                        e);
            }
            forgeForEachLine = handle;
        }
        return handle;
    }
}
