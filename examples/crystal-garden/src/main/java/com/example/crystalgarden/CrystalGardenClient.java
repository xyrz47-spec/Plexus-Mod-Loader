package com.example.crystalgarden;

import dev.polymodloader.api.client.ClientEvents;
import dev.polymodloader.api.client.ClientRegistrar;
import dev.polymodloader.api.client.KeyBinding;
import dev.polymodloader.api.client.KeyCodes;
import dev.polymodloader.api.lifecycle.ClientModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client side: answers the join check, draws a growth bar over every sprout and a harvest counter on the HUD,
 * and toggles the counter with a key. Listed under {@code entrypoints.client}; a dedicated server never loads
 * this class.
 */
public final class CrystalGardenClient implements ClientModInitializer {

    @Override
    public void onInitializeClient(ModContext mod) {
        CrystalGardenMod.handshake.clientAnswer(request -> CrystalGardenMod.MOD_ID + " " + mod.modVersion());

        ClientRegistrar client = mod.client();

        // A growth bar and the stage over each sprout, from the block entity data the server syncs.
        client.blockEntityRenderer(CrystalGardenMod.CRYSTAL_SPROUT, (sprout, canvas) -> {
            int stage = sprout.world().stateAt(sprout.pos()).get(CrystalSprout.STAGE);
            if (stage >= CrystalSprout.RIPE) {
                canvas.item(CrystalGardenMod.CRYSTAL, 0.5, 1.3, 0.5, 0.4f, canvas.animationTime() * 4 % 360);
                canvas.text("Ripe!", 0.5, 1.7, 0.5, 0.02f, 0xFFE1BEE7);
                return;
            }
            int percent = sprout.data().getInt("growth", 0) * 100 / CrystalSprout.TICKS_PER_STAGE;
            canvas.text("Stage " + stage + " · " + percent + "%", 0.5, 1.2, 0.5, 0.015f, 0xFFFFFFFF);
        });

        // The HUD shows the player's own harvest count (entity data synced to its owner).
        KeyBinding toggle = client.keyBinding("toggle_hud", KeyCodes.G);
        AtomicBoolean hudVisible = new AtomicBoolean(true);
        AtomicInteger harvested = new AtomicInteger();
        mod.events().register(ClientEvents.TICK, view -> {
            while (toggle.consumeClick()) {
                hudVisible.set(!hudVisible.get());
            }
            view.player()
                    .ifPresent(player -> harvested.set(
                            player.data(CrystalGardenMod.harvested).data().getInt("count", 0)));
        });
        client.hudLayer("harvest_counter", canvas -> {
            if (!hudVisible.get()) {
                return;
            }
            String text = "Crystals: " + harvested.get();
            canvas.fill(2, 2, 8 + canvas.textWidth(text), 14, 0x80000000);
            canvas.drawText(text, 5, 4, 0xFFE1BEE7);
        });
    }
}
