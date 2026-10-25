package com.example.neoforgesession;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test mod, the NeoForge counterpart of {@code fabric-client-session} (NeoForge mods do not run together with Fabric
 * mods, ADR-0026): plays {@value #TICKS} client ticks in the world, then leaves it with "Save and Quit to Title", stays
 * on the title screen for {@value #TITLE_TICKS} ticks (so runs also draw the title screen, where Poly Mod Loader shows
 * the mod counts per loader) and stops the client. Written with NeoForge's own events only.
 *
 * <p>If opening the world asks for a backup first (a world with mod data packs on Minecraft 26.1), it answers as a
 * player who joins without one. If NeoForge shows its mod loading screen with warnings only (no errors), it logs them and
 * proceeds, as a player clicks "Proceed"; with errors it leaves the screen, as NeoForge offers no way on.
 */
@Mod(value = NeoForgeClientSession.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientSession {

    static final String MOD_ID = "neoforge_client_session";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int TICKS = 200;
    private static final int TITLE_TICKS = 40;

    private int ticks;
    private boolean leaving;
    private int titleTicks;
    private boolean stopped;
    private boolean answeredBackup;
    private boolean answeredWarnings;

    public NeoForgeClientSession() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, this::screenOpened);
        LOGGER.info("[{}] waiting for the world", MOD_ID);
    }

    private void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!leaving) {
            if (minecraft.level != null && minecraft.player != null && ++ticks == TICKS) {
                leaving = true;
                LOGGER.info("[{}] {} client ticks in the world; save and quit", MOD_ID, TICKS);
                // The pause menu's "Save and Quit to Title": saves the integrated server, then opens the title screen.
                minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            }
        } else if (!stopped && minecraft.level == null && ++titleTicks == TITLE_TICKS) {
            stopped = true;
            LOGGER.info("[{}] left the saved world; stopping the client", MOD_ID);
            minecraft.stop();
        }
    }

    private void screenOpened(ScreenEvent.Init.Post event) {
        if (!answeredWarnings && event.getScreen() instanceof LoadingErrorScreen loading) {
            answeredWarnings = true;
            proceedPastWarnings(loading);
            return;
        }
        if (answeredBackup || !(event.getScreen() instanceof BackupConfirmScreen screen)) {
            return;
        }
        answeredBackup = true;
        LOGGER.info(
                "[{}] the world asks for a backup before loading ({}); joining without one",
                MOD_ID,
                screen.getTitle().getString());
        BackupConfirmScreen.Listener proceed = proceedListener(screen);
        // After this screen is set: proceeding opens the next one.
        Minecraft.getInstance().execute(() -> proceed.proceed(false, false));
    }

    /**
     * NeoForge's loading screen with only warnings has a "Proceed" button that runs the next screen task; this does the
     * same. NeoForge's module is automatic, so its private fields are open to reflection.
     */
    private static void proceedPastWarnings(LoadingErrorScreen screen) {
        try {
            List<?> errors =
                    (List<?>) field(LoadingErrorScreen.class, "modLoadErrors").get(screen);
            List<?> warnings =
                    (List<?>) field(LoadingErrorScreen.class, "modLoadWarnings").get(screen);
            LOGGER.info(
                    "[{}] NeoForge's loading screen: {} error(s), {} warning(s): {}",
                    MOD_ID,
                    errors.size(),
                    warnings.size(),
                    warnings);
            if (!errors.isEmpty()) {
                return;
            }
            Runnable next =
                    (Runnable) field(LoadingErrorScreen.class, "nextScreenTask").get(screen);
            LOGGER.info("[{}] proceeding past the warnings, as a player does", MOD_ID);
            Minecraft.getInstance().execute(next);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NeoForge's LoadingErrorScreen cannot be read", e);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** The screen's protected {@code onProceed}; the game's module is automatic, so it is open to reflection. */
    private static BackupConfirmScreen.Listener proceedListener(BackupConfirmScreen screen) {
        try {
            Field field = BackupConfirmScreen.class.getDeclaredField("onProceed");
            field.setAccessible(true);
            return (BackupConfirmScreen.Listener) field.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BackupConfirmScreen.onProceed cannot be read", e);
        }
    }
}
