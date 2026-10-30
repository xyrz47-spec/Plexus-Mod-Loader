package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.client.KeyBinding;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.KeyState;

/** A key binding that reads the game's key state once the game layer created the mapping. */
final class KeyBindingImpl implements KeyBinding {

    private final ResourceId id;
    private volatile KeyState state;

    KeyBindingImpl(ResourceId id) {
        this.id = id;
    }

    void bind(KeyState keyState) {
        this.state = keyState;
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public boolean isDown() {
        KeyState current = state;
        return current != null && current.isDown();
    }

    @Override
    public boolean consumeClick() {
        KeyState current = state;
        return current != null && current.consumeClick();
    }
}
