package dev.plexusmodloader.game.layer.v1_21_11.client;

import dev.plexusmodloader.api.client.ModScreen;
import dev.plexusmodloader.api.client.ScreenBuilder;
import dev.plexusmodloader.api.client.TextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The game screen that shows a mod's {@link ModScreen}. */
final class PlexusScreen extends Screen {

    private final ModScreen screen;
    private final List<Label> labels = new ArrayList<>();

    private record Label(String text, int x, int y, int argb) {}

    PlexusScreen(ModScreen screen) {
        super(Component.literal(screen.title()));
        this.screen = screen;
    }

    @Override
    protected void init() {
        labels.clear();
        screen.init(new Builder());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), label.argb(), true);
        }
        screen.render(new ClientHooks.Canvas(graphics, font));
    }

    @Override
    public boolean isPauseScreen() {
        return screen.pausesGame();
    }

    /** Called whenever this screen stops being shown: Esc, {@code closeScreen}, or another screen. */
    @Override
    public void removed() {
        super.removed();
        screen.onClosed();
    }

    private final class Builder implements ScreenBuilder {

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int textWidth(String text) {
            return font.width(text);
        }

        @Override
        public void label(String text, int x, int y, int argb) {
            labels.add(new Label(text, x, y, argb));
        }

        @Override
        public void button(String label, int x, int y, int buttonWidth, int buttonHeight, Runnable onPress) {
            addRenderableWidget(Button.builder(Component.literal(label), button -> onPress.run())
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build());
        }

        @Override
        public TextInput textInput(int x, int y, int inputWidth, String initialValue) {
            EditBox box = new EditBox(font, x, y, inputWidth, 20, Component.literal(screen.title()));
            box.setValue(initialValue);
            addRenderableWidget(box);
            return new TextInput() {
                @Override
                public String value() {
                    return box.getValue();
                }

                @Override
                public void setValue(String value) {
                    box.setValue(value);
                }
            };
        }

        @Override
        public void close() {
            onClose();
        }
    }
}
