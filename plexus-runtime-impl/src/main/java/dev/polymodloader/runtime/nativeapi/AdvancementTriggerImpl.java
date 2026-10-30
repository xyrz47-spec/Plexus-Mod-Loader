package dev.polymodloader.runtime.nativeapi;

import dev.polymodloader.api.content.AdvancementTrigger;
import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import java.util.Objects;
import java.util.function.Consumer;

/** An advancement trigger that fires the game's criterion once the game layer registered it. */
final class AdvancementTriggerImpl implements AdvancementTrigger {

    private final ResourceId id;
    private volatile Consumer<PlayerView> fire;

    AdvancementTriggerImpl(ResourceId id) {
        this.id = id;
    }

    void bind(Consumer<PlayerView> trigger) {
        this.fire = Objects.requireNonNull(trigger, "trigger");
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public void trigger(PlayerView player) {
        Objects.requireNonNull(player, "player");
        Consumer<PlayerView> current = fire;
        if (current == null) {
            throw new IllegalStateException("PLEXUS-CONTENT-002 advancement trigger " + id + " is not registered yet");
        }
        current.accept(player);
    }
}
