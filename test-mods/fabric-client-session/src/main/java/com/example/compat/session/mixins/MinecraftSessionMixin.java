package com.example.compat.session.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays {@value #TICKS} client ticks in the world, then leaves it with "Save and Quit to Title", stays on the title
 * screen for {@value #TITLE_TICKS} ticks (so runs also draw the title screen, where Poly Mod Loader shows the mod counts
 * per loader) and stops the client, so a run with many mods shows the world ticking, saving and closing, not just
 * opening.
 *
 * <p>With {@code -Dfabric_client_session.physicsProbe=true} it also breaks a block and kills a zombie in front of the
 * player and saves screenshots before and after ({@code screenshots/physics-*.png}), so a person can see whether a mod
 * that draws block debris or entity pieces (Physics Mod) draws them. With {@code
 * -Dfabric_client_session.titleScreenshot=true} it saves {@code screenshots/title.png} on the title screen.
 *
 * <p>If opening the world asks for a backup first, {@link BackupConfirmScreenMixin} answers it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSessionMixin {

    @Unique
    private static final int TICKS = 200;

    @Unique
    private static final int TITLE_TICKS = 40;

    @Unique
    private static final boolean PHYSICS_PROBE = Boolean.getBoolean("fabric_client_session.physicsProbe");

    @Unique
    private static final boolean TITLE_SCREENSHOT = Boolean.getBoolean("fabric_client_session.titleScreenshot");

    @Unique
    private int session$ticks;

    @Unique
    private boolean session$leaving;

    @Unique
    private int session$titleTicks;

    @Unique
    private boolean session$stopped;

    @Unique
    private BlockPos session$probeBlock;

    @Inject(method = "tick", at = @At("TAIL"))
    private void session$tick(CallbackInfo info) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!session$leaving) {
            if (minecraft.level != null && minecraft.player != null) {
                ++session$ticks;
                if (PHYSICS_PROBE) {
                    session$physicsProbe(minecraft);
                }
                if (session$ticks == TICKS) {
                    session$leaving = true;
                    System.out.println(
                            "[fabric_client_session] " + TICKS + " client ticks in the world; save and quit");
                    // The pause menu's "Save and Quit to Title": saves the integrated server, then opens the title
                    // screen.
                    minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                }
            }
        } else if (!session$stopped && minecraft.level == null) {
            ++session$titleTicks;
            if (TITLE_SCREENSHOT && session$titleTicks == TITLE_TICKS / 2) {
                session$screenshot(minecraft, "title.png");
            }
            if (session$titleTicks == TITLE_TICKS) {
                session$stopped = true;
                System.out.println("[fabric_client_session] left the saved world; stopping the client");
                minecraft.stop();
            }
        }
    }

    /**
     * Places a block and a zombie in front of the player, then breaks and kills them, with screenshots around it.
     * Commands, so the same code runs on every supported Minecraft version.
     */
    @Unique
    private void session$physicsProbe(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        switch (session$ticks) {
            case 40 -> {
                BlockPos block = BlockPos.containing(minecraft
                        .player
                        .getEyePosition()
                        .add(minecraft.player.getLookAngle().scale(4)));
                session$probeBlock = block;
                session$command(server, "setblock " + session$at(block) + " minecraft:stone");
                session$command(server, "summon minecraft:zombie " + session$at(block.above()) + " {NoAI:1b}");
                System.out.println("[fabric_client_session] physics probe: stone and a zombie at " + block);
            }
            case 70 -> session$screenshot(minecraft, "physics-before.png");
            case 80 -> {
                session$command(server, "kill @e[type=minecraft:zombie,distance=..64]");
                session$command(server, "setblock " + session$at(session$probeBlock) + " minecraft:air destroy");
                System.out.println("[fabric_client_session] physics probe: block broken and zombie killed");
            }
            case 83 -> session$screenshot(minecraft, "physics-after-3.png");
            case 90 -> session$screenshot(minecraft, "physics-after-10.png");
            case 110 -> session$screenshot(minecraft, "physics-after-30.png");
            default -> {}
        }
    }

    @Unique
    private static String session$at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Unique
    private static void session$command(IntegratedServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    /** The client's main render target: {@code Minecraft.getMainRenderTarget()} (26.1) or the game renderer's (26.2). */
    @Unique
    private static RenderTarget session$renderTarget(Minecraft minecraft) {
        try {
            return (RenderTarget)
                    Minecraft.class.getMethod("getMainRenderTarget").invoke(minecraft);
        } catch (NoSuchMethodException e) {
            try {
                return (RenderTarget) minecraft
                        .gameRenderer
                        .getClass()
                        .getMethod("mainRenderTarget")
                        .invoke(minecraft.gameRenderer);
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException("no main render target on this Minecraft version", inner);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the main render target", e);
        }
    }

    @Unique
    private static void session$screenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, session$renderTarget(minecraft), 1, message -> {});
        System.out.println("[fabric_client_session] screenshot " + name);
    }
}
