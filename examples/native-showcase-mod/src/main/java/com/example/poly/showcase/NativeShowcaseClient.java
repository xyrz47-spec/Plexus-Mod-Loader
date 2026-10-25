package com.example.poly.showcase;

import dev.polymodloader.api.client.CameraPerspective;
import dev.polymodloader.api.client.ClientEvents;
import dev.polymodloader.api.client.ClientRegistrar;
import dev.polymodloader.api.client.DynamicTexture;
import dev.polymodloader.api.client.KeyBinding;
import dev.polymodloader.api.client.KeyCodes;
import dev.polymodloader.api.client.ShaderPipeline;
import dev.polymodloader.api.client.ShaderPipelineDefinition;
import dev.polymodloader.api.client.ShaderUniform;
import dev.polymodloader.api.content.block.state.BlockStateView;
import dev.polymodloader.api.lifecycle.ClientModInitializer;
import dev.polymodloader.api.lifecycle.ModContext;
import dev.polymodloader.api.resource.ResourceId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Client side of the showcase: a key binding, a HUD layer, and the greeting handler. */
public final class NativeShowcaseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient(ModContext mod) {
        ClientRegistrar client = mod.client();
        KeyBinding showInfo = client.keyBinding("show_info", KeyCodes.K);
        // A pipeline with the mod's own core shader: assets/native_showcase/shaders/core/glow.vsh/.fsh.
        ShaderPipeline glow = client.shaderPipeline(
                "glow",
                ShaderPipelineDefinition.builder(ResourceId.of(NativeShowcaseMod.MOD_ID, "core/glow"))
                        .blend(ShaderPipelineDefinition.Blend.ADDITIVE)
                        .build());
        // A textured pipeline with the mod's own uniforms: core/pulse.fsh reads Sampler0 and
        // ModUniforms { float Strength; vec4 Tint; }. The values are asked for once per frame.
        AtomicLong frames = new AtomicLong();
        ShaderPipeline pulse = client.shaderPipeline(
                "pulse",
                ShaderPipelineDefinition.builder(ResourceId.of(NativeShowcaseMod.MOD_ID, "core/pulse"))
                        .texture(ResourceId.of(NativeShowcaseMod.MOD_ID, "textures/item/spark.png"))
                        .uniform("Strength", ShaderUniform.Type.FLOAT, () ->
                                new float[] {0.75f + 0.25f * (float) Math.sin(frames.incrementAndGet() / 20.0)})
                        .uniform("Tint", ShaderUniform.Type.VEC4, () -> new float[] {1.0f, 0.85f, 0.4f, 1.0f})
                        .build());
        // A small compass under the camera: a red line and label to the north, a box, a glowing quad.
        client.worldPainter("compass", canvas -> {
            double x = canvas.cameraX();
            double y = canvas.cameraY() - 1.5;
            double z = canvas.cameraZ();
            canvas.line(x, y, z, x, y, z - 2, 0xFFFF5555, 3);
            canvas.text("N", x, y + 0.3, z - 2.2, 0xFFFF5555, 1);
            canvas.box(x - 0.1, y - 0.1, z - 0.1, x + 0.1, y + 0.1, z + 0.1, 0xFFFFFFFF, 2, 0x40FFFFFF);
            canvas.quads(
                    glow,
                    quads -> quads.vertex(x - 0.6, y - 0.05, z - 0.6, 0x80FFD54F)
                            .vertex(x - 0.6, y - 0.05, z + 0.6, 0x80FFD54F)
                            .vertex(x + 0.6, y - 0.05, z + 0.6, 0x80FFD54F)
                            .vertex(x + 0.6, y - 0.05, z - 0.6, 0x80FFD54F));
            // The spark texture, tinted and pulsing through the pipeline's uniforms.
            canvas.quads(
                    pulse,
                    quads -> quads.vertex(x - 0.3, y + 0.05, z - 1.3, 0, 0, 0xFFFFFFFF)
                            .vertex(x - 0.3, y + 0.05, z - 0.7, 0, 1, 0xFFFFFFFF)
                            .vertex(x + 0.3, y + 0.05, z - 0.7, 1, 1, 0xFFFFFFFF)
                            .vertex(x + 0.3, y + 0.05, z - 1.3, 1, 0, 0xFFFFFFFF));
        });
        // The grinder shows what it grinds and how far along it is, under a spinning glow block.
        AtomicBoolean lampStateReported = new AtomicBoolean();
        client.blockEntityRenderer(AdvancedContent.GRINDER, (entity, canvas) -> {
            canvas.block(NativeShowcaseMod.GLOW_BLOCK, 0.5, 2.2, 0.5, 0.35f, canvas.animationTime() * 3 % 360);
            // A lamp model in a state other than its default: lit and blue.
            BlockStateView lamp = canvas.defaultState(AdvancedContent.LAMP)
                    .with(AdvancedContent.LIT, true)
                    .with(AdvancedContent.COLOR, "blue");
            canvas.block(lamp, 0.5, 2.7, 0.5, 0.25f, 0);
            if (lampStateReported.compareAndSet(false, true)) {
                NativeShowcaseMod.LOGGER.info("[{}] Grinder draws lamp state {}", NativeShowcaseMod.MOD_ID, lamp);
            }
            canvas.quads(
                    glow,
                    quads -> quads.vertex(0, 1.01, 0, 0x60FFD54F)
                            .vertex(0, 1.01, 1, 0x60FFD54F)
                            .vertex(1, 1.01, 1, 0x60FFD54F)
                            .vertex(1, 1.01, 0, 0x60FFD54F));
            entity.inventory()
                    .map(inventory -> inventory.get(0))
                    .filter(stack -> !stack.isEmpty())
                    .ifPresent(
                            stack -> canvas.item(stack.item(), 0.5, 1.3, 0.5, 0.5f, canvas.animationTime() * 4 % 360));
            int total = entity.data().getInt("total", AdvancedContent.GRIND_TICKS);
            int percent = total == 0 ? 0 : entity.data().getInt("progress", 0) * 100 / total;
            canvas.text("Grinding " + percent + "%", 0.5, 1.75, 0.5, 0.02f, 0xFFFFFFFF);
        });
        DynamicTexture noise = client.dynamicTexture("noise", 16, 16);
        AtomicBoolean noiseUploaded = new AtomicBoolean();
        AtomicBoolean fovReported = new AtomicBoolean();
        mod.events().register(ClientEvents.FIELD_OF_VIEW, fov -> {
            // The camera reports 0 before its first frame.
            if (fov > 0 && fovReported.compareAndSet(false, true)) {
                NativeShowcaseMod.LOGGER.info("[{}] Field of view event received", NativeShowcaseMod.MOD_ID);
            }
            return fov;
        });
        // Sparkly items (the item tag from TagContent) say so under their name.
        mod.events().register(ClientEvents.ITEM_TOOLTIP, (stack, lines, advanced) -> {
            if (stack.isIn(TagContent.SPARKLY)) {
                lines.add(1, "§bSparkly");
            }
        });
        AtomicBoolean firstFrame = new AtomicBoolean(true);
        client.hudLayer("status", canvas -> {
            String text = "Native Showcase";
            canvas.fill(2, 2, 6 + canvas.textWidth(text), 14, 0x80000000);
            canvas.drawText(text, 4, 4, 0xFFFFD54F);
            if (noiseUploaded.compareAndSet(false, true)) {
                for (int y = 0; y < noise.height(); y++) {
                    for (int x = 0; x < noise.width(); x++) {
                        noise.setPixel(x, y, ((x + y) % 2 == 0) ? 0xFFFFD54F : 0xFF5D4037);
                    }
                }
                noise.upload();
                NativeShowcaseMod.LOGGER.info(
                        "[{}] Dynamic texture uploaded: {}", NativeShowcaseMod.MOD_ID, noise.id());
            }
            canvas.drawTexture(noise.id(), 8 + canvas.textWidth(text), 2, 12, 12);
            if (firstFrame.compareAndSet(true, false)) {
                NativeShowcaseMod.LOGGER.info(
                        "[{}] HUD layer drew on a {}x{} screen",
                        NativeShowcaseMod.MOD_ID,
                        canvas.width(),
                        canvas.height());
            }
        });
        AtomicBoolean ridingReported = new AtomicBoolean();
        AtomicBoolean visitsReported = new AtomicBoolean();
        AtomicBoolean infoReported = new AtomicBoolean();
        AtomicBoolean moodReported = new AtomicBoolean();
        mod.events()
                .register(
                        ClientEvents.TICK,
                        view -> view.player().ifPresent(player -> {
                            if (moodReported.get()) {
                                return;
                            }
                            player.world().entitiesNear(player.x(), player.y(), player.z(), 16).stream()
                                    .filter(entity -> entity.type().equals(CreatureContent.GOLEM))
                                    .map(entity -> entity.data(OperationsContent.golemMood)
                                            .data()
                                            .getString("mood", ""))
                                    .filter(mood -> !mood.isEmpty())
                                    .findFirst()
                                    .ifPresent(mood -> {
                                        moodReported.set(true);
                                        NativeShowcaseMod.LOGGER.info(
                                                "[{}] Client sees golem mood: {}", NativeShowcaseMod.MOD_ID, mood);
                                    });
                        }));
        mod.resources()
                .onAssetReload(
                        "native_showcase_tips",
                        files -> NativeShowcaseMod.LOGGER.info(
                                "[{}] Loaded {} client tip(s)", NativeShowcaseMod.MOD_ID, files.size()));
        mod.events()
                .register(
                        ClientEvents.JOINED_WORLD,
                        view -> NativeShowcaseMod.LOGGER.info(
                                "[{}] Client joined a world as {}",
                                NativeShowcaseMod.MOD_ID,
                                view.player().map(player -> player.name()).orElse("?")));
        mod.events()
                .register(
                        ClientEvents.LEFT_WORLD,
                        view -> NativeShowcaseMod.LOGGER.info("[{}] Client left the world", NativeShowcaseMod.MOD_ID));
        mod.events().register(ClientEvents.TICK, view -> {
            view.player().ifPresent(player -> {
                int count = player.data(OperationsContent.visits).data().getInt("count", 0);
                if (count > 0 && visitsReported.compareAndSet(false, true)) {
                    NativeShowcaseMod.LOGGER.info("[{}] Client sees visits: {}", NativeShowcaseMod.MOD_ID, count);
                }
            });
            view.world().ifPresent(world -> {
                String motd = world.storage(OperationsContent.WORLD_INFO, OperationsContent.WORLD_INFO_OPTIONS)
                        .data()
                        .getString("motd", "");
                if (!motd.isEmpty() && infoReported.compareAndSet(false, true)) {
                    NativeShowcaseMod.LOGGER.info("[{}] Client sees world info: {}", NativeShowcaseMod.MOD_ID, motd);
                }
            });
        });
        mod.events().register(ClientEvents.TICK, view -> {
            view.player().flatMap(player -> player.vehicle()).ifPresent(vehicle -> {
                if (ridingReported.compareAndSet(false, true)) {
                    NativeShowcaseMod.LOGGER.info(
                            "[{}] Client sees the player riding {}", NativeShowcaseMod.MOD_ID, vehicle.type());
                }
            });
            if (showInfo.consumeClick()) {
                view.showActionBar("Native Showcase: key binding works");
            }
        });
        // The greeting opens a screen on the next tick, where a client view is available; the
        // screen closes itself after 40 ticks so automated runs continue.
        // After that the client asks the server to open the grinder's laid-out menu, and closes it
        // after another 40 ticks.
        AtomicReference<String> pendingGreeting = new AtomicReference<>();
        AtomicInteger screenTicks = new AtomicInteger(-1);
        mod.events().register(ClientEvents.TICK, view -> {
            String greeting = pendingGreeting.getAndSet(null);
            if (greeting != null) {
                view.openScreen(new ShowcaseScreen(greeting));
                screenTicks.set(0);
            } else if (screenTicks.get() >= 0) {
                int ticks = screenTicks.incrementAndGet();
                if (ticks == 40) {
                    view.closeScreen();
                    NativeShowcaseMod.greetings.sendToServer("open grinder");
                } else if (ticks == 80) {
                    NativeShowcaseMod.LOGGER.info(
                            "[{}] Closing the grinder menu (screen open: {})",
                            NativeShowcaseMod.MOD_ID,
                            view.isScreenOpen());
                    view.closeScreen();
                    view.setCameraPerspective(CameraPerspective.THIRD_PERSON_BACK);
                    NativeShowcaseMod.LOGGER.info(
                            "[{}] Camera perspective: {}", NativeShowcaseMod.MOD_ID, view.cameraPerspective());
                    view.setCameraPerspective(CameraPerspective.FIRST_PERSON);
                    boolean loaded = view.setPostEffect(ResourceId.of(NativeShowcaseMod.MOD_ID, "sepia"));
                    NativeShowcaseMod.LOGGER.info(
                            "[{}] Post effect sepia loaded: {}, active: {}",
                            NativeShowcaseMod.MOD_ID,
                            loaded,
                            view.postEffect().map(ResourceId::toString).orElse("none"));
                } else if (ticks == 100) {
                    view.clearPostEffect();
                    NativeShowcaseMod.LOGGER.info(
                            "[{}] Post effect cleared: {}",
                            NativeShowcaseMod.MOD_ID,
                            view.postEffect().isEmpty());
                    screenTicks.set(-1);
                    // Automated runs (clientWorldReloadTest) end the demo by leaving the world.
                    if (Boolean.getBoolean("native_showcase.leaveAfterDemo")) {
                        view.leaveWorld();
                    }
                }
            }
        });
        NativeShowcaseMod.handshake.clientAnswer(request -> "showcase 0.1.0");
        NativeShowcaseMod.greetings.onClientReceive(message -> {
            pendingGreeting.set(message);
            NativeShowcaseMod.LOGGER.info("[{}] Client received greeting: {}", NativeShowcaseMod.MOD_ID, message);
            NativeShowcaseMod.greetings.sendToServer("thanks");
        });
    }
}
