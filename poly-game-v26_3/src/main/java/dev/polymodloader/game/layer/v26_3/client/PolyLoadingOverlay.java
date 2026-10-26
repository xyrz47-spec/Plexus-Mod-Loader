package dev.polymodloader.game.layer.v26_3.client;

import dev.polymodloader.game.layer.v26_3.LayerState;
import dev.polymodloader.game.version.layer.GameLayerContext;
import dev.polymodloader.game.version.layer.LoadedModCounts;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poly Mod Loader's dedicated loading screen and live mod count overlay for Minecraft 26.3.
 * Replaces the vanilla/host loader loading overlay rendering with Poly's custom UI.
 */
public final class PolyLoadingOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private static final Field RELOAD_FIELD;
    private static final Field ON_FINISH_FIELD;
    private static final Field MINECRAFT_FIELD;

    static {
        Field reload = null;
        Field onFinish = null;
        Field mc = null;
        try {
            reload = LoadingOverlay.class.getDeclaredField("reload");
            reload.setAccessible(true);
            onFinish = LoadingOverlay.class.getDeclaredField("onFinish");
            onFinish.setAccessible(true);
            mc = LoadingOverlay.class.getDeclaredField("minecraft");
            mc.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[Poly Mod Loader] LoadingOverlay reflection lookup: {}", e.getMessage());
        }
        RELOAD_FIELD = reload;
        ON_FINISH_FIELD = onFinish;
        MINECRAFT_FIELD = mc;
    }

    private static volatile LoadingOverlay lastOverlay;
    private static volatile float currentProgress;
    private static volatile long fadeInStart = -1L;
    private static volatile long fadeOutStart = -1L;
    private static volatile boolean finishedSignaled;

    private PolyLoadingOverlay() {}

    /**
     * Intercepts {@code LoadingOverlay.extractRenderState}: renders Poly's dedicated loading screen
     * and custom mod count overlay, cancelling the vanilla/host loader rendering.
     *
     * @return {@code true} to cancel original method body, {@code false} to fall back
     */
    public static boolean onExtractRenderState(
            LoadingOverlay overlay, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        try {
            return renderCustomOverlay(overlay, graphics);
        } catch (Throwable t) {
            LOGGER.error("[Poly Mod Loader] Failed to render Poly loading screen, falling back to default", t);
            return false;
        }
    }

    private static boolean renderCustomOverlay(LoadingOverlay overlay, GuiGraphicsExtractor graphics) {
        if (lastOverlay != overlay) {
            lastOverlay = overlay;
            currentProgress = 0.0f;
            fadeInStart = -1L;
            fadeOutStart = -1L;
            finishedSignaled = false;
        }

        long now = System.currentTimeMillis();
        if (fadeInStart == -1L) {
            fadeInStart = now;
        }

        ReloadInstance reload = getReload(overlay);
        float actualProgress = reload != null ? reload.getActualProgress() : 1.0f;
        currentProgress = Mth.clamp(currentProgress * 0.95f + actualProgress * 0.05f, 0.0f, 1.0f);

        if (reload != null && reload.isDone()) {
            if (!finishedSignaled) {
                finishedSignaled = true;
                Consumer<Optional<Throwable>> onFinish = getOnFinish(overlay);
                if (onFinish != null) {
                    onFinish.accept(Optional.empty());
                }
                if (fadeOutStart == -1L) {
                    fadeOutStart = now;
                }
            }
        }

        float fadeOutAnim = fadeOutStart > 0L ? (now - fadeOutStart) / 800.0f : -1.0f;
        if (fadeOutAnim >= 1.0f) {
            Minecraft mc = getMinecraft(overlay);
            if (mc != null && mc.gui != null) {
                mc.gui.setOverlay(null);
            }
            return true;
        }

        float alpha = 1.0f;
        if (fadeOutAnim >= 0.0f) {
            alpha = 1.0f - Mth.clamp(fadeOutAnim, 0.0f, 1.0f);
        }
        int a = (int) (alpha * 255.0f);
        if (a <= 0) {
            return true;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.nextStratum();

        // 1. Sleek dark background
        int bgColor = (a << 24) | 0x0E131F;
        graphics.fill(0, 0, width, height, bgColor);

        Minecraft mc = getMinecraft(overlay);
        Font font = mc != null ? mc.font : null;
        int centerY = height / 2;

        if (font != null) {
            // 2. Brand logo & title
            graphics.centeredText(font, "\u00A7l\u00A7eP  O  L  Y", width / 2, centerY - 45, (a << 24) | 0xFFFFFF);
            graphics.centeredText(
                    font, "\u00A77Next-Gen Unified Mod Loader", width / 2, centerY - 28, (a << 24) | 0x94A3B8);
        }

        // 3. Progress bar
        int barWidth = Math.min(260, width - 40);
        int barHeight = 6;
        int barX = (width - barWidth) / 2;
        int barY = centerY;

        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, (a << 24) | 0x334155);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, (a << 24) | 0x1E293B);

        int filled = (int) (barWidth * currentProgress);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, (a << 24) | 0xF59E0B);
        }

        if (font != null) {
            int pct = (int) (currentProgress * 100);
            graphics.centeredText(font, "\u00A78" + pct + "%", width / 2, barY + 10, (a << 24) | 0x64748B);

            // 4. Poly Mod Count Overlay
            LoadedModCounts counts =
                    LayerState.context().map(GameLayerContext::modCounts).orElse(LoadedModCounts.NONE);
            List<String> badges = new ArrayList<>();
            if (counts.fabricMods() > 0) badges.add("\u00A7aFabric: \u00A7f" + counts.fabricMods());
            if (counts.forgeMods() > 0) badges.add("\u00A76Forge: \u00A7f" + counts.forgeMods());
            if (counts.neoForgeMods() > 0) badges.add("\u00A7cNeoForge: \u00A7f" + counts.neoForgeMods());
            if (counts.quiltMods() > 0) badges.add("\u00A7dQuilt: \u00A7f" + counts.quiltMods());
            if (counts.polyMods() > 0) badges.add("\u00A7bPoly: \u00A7f" + counts.polyMods());

            String modSummary = badges.isEmpty()
                    ? "\u00A78Poly Mod Loader active"
                    : "\u00A77Active Mods:  " + String.join("  \u00A78|  ", badges);

            int strW = font.width(modSummary);
            int boxPadding = 10;
            int boxX = (width - strW) / 2 - boxPadding;
            int boxY = height - 42;
            int boxW = strW + boxPadding * 2;
            int boxH = 20;

            graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, ((int) (a * 0.75f) << 24) | 0x111827);
            graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, ((int) (a * 0.5f) << 24) | 0x374151);
            graphics.centeredText(font, modSummary, width / 2, boxY + 6, (a << 24) | 0xFFFFFF);

            if (counts.hostIncompatibleMods() > 0) {
                graphics.centeredText(
                        font,
                        "\u00A7e\u26A0 " + counts.hostIncompatibleMods() + " mod(s) isolated for compatibility",
                        width / 2,
                        boxY - 14,
                        (a << 24) | 0xFBBF24);
            }

            if (REPORTED.compareAndSet(false, true)) {
                LOGGER.info(
                        "[Poly Mod Loader] Custom loading screen & mod count overlay active (Fabric: {}, Forge: {}, NeoForge: {}, Quilt: {}, Poly: {})",
                        counts.fabricMods(),
                        counts.forgeMods(),
                        counts.neoForgeMods(),
                        counts.quiltMods(),
                        counts.polyMods());
            }
        }

        return true;
    }

    private static ReloadInstance getReload(LoadingOverlay overlay) {
        if (RELOAD_FIELD == null) return null;
        try {
            return (ReloadInstance) RELOAD_FIELD.get(overlay);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Optional<Throwable>> getOnFinish(LoadingOverlay overlay) {
        if (ON_FINISH_FIELD == null) return null;
        try {
            return (Consumer<Optional<Throwable>>) ON_FINISH_FIELD.get(overlay);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Minecraft getMinecraft(LoadingOverlay overlay) {
        if (MINECRAFT_FIELD != null) {
            try {
                return (Minecraft) MINECRAFT_FIELD.get(overlay);
            } catch (IllegalAccessException ignored) {
            }
        }
        return Minecraft.getInstance();
    }
}
