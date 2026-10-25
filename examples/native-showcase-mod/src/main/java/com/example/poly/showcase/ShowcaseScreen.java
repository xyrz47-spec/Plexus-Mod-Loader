package com.example.poly.showcase;

import dev.polymodloader.api.client.HudCanvas;
import dev.polymodloader.api.client.ModScreen;
import dev.polymodloader.api.client.ScreenBuilder;
import dev.polymodloader.api.client.TextInput;
import java.util.concurrent.atomic.AtomicBoolean;

/** A small mod screen: a greeting label, a name input, and a close button. */
final class ShowcaseScreen implements ModScreen {

    private final String greeting;
    private final AtomicBoolean firstFrame = new AtomicBoolean(true);
    private TextInput name;

    ShowcaseScreen(String greeting) {
        this.greeting = greeting;
    }

    @Override
    public String title() {
        return "Native Showcase";
    }

    @Override
    public void init(ScreenBuilder screen) {
        int centre = screen.width() / 2;
        screen.label(greeting, centre - screen.textWidth(greeting) / 2, 40, 0xFFFFFFFF);
        name = screen.textInput(centre - 100, 70, 200, "PolyDev");
        screen.button("Close", centre - 50, 100, 100, 20, screen::close);
        NativeShowcaseMod.LOGGER.info(
                "[{}] Screen opened at {}x{}", NativeShowcaseMod.MOD_ID, screen.width(), screen.height());
    }

    @Override
    public void render(HudCanvas canvas) {
        if (firstFrame.compareAndSet(true, false)) {
            NativeShowcaseMod.LOGGER.info("[{}] Screen rendered", NativeShowcaseMod.MOD_ID);
        }
    }

    @Override
    public void onClosed() {
        NativeShowcaseMod.LOGGER.info(
                "[{}] Screen closed, name field was '{}'", NativeShowcaseMod.MOD_ID, name.value());
    }
}
