package dev.polymodloader.api.client;

import dev.polymodloader.api.entity.PlayerView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.api.world.WorldView;
import java.util.Optional;

/** The client's state during a callback. Valid only during the call, on the client thread. */
@ApiStability(StabilityLevel.PREVIEW)
public interface ClientView {

    /** The local player, when in a world. */
    Optional<PlayerView> player();

    /** The client's copy of the current world, when in one. */
    Optional<WorldView> world();

    /** Shows a message above the hotbar (the action bar). */
    void showActionBar(String text);

    /** Opens {@code screen}, replacing any open screen. */
    void openScreen(ModScreen screen);

    /** Closes the open screen, if any, returning to the game. */
    void closeScreen();

    /** Whether any screen (menu, inventory, chat, mod screen) is open. */
    boolean isScreenOpen();

    /** The camera perspective. */
    CameraPerspective cameraPerspective();

    /** Changes the camera perspective, as F5 does. */
    void setCameraPerspective(CameraPerspective perspective);

    /**
     * Applies a full-screen post effect from {@code assets/<namespace>/post_effect/<path>.json}, with
     * its shaders, until cleared. The game also replaces it when the camera entity or perspective
     * changes (as when spectating a creeper).
     *
     * @return whether the effect loaded; failures are logged by the game
     */
    boolean setPostEffect(ResourceId effect);

    /** Removes the post effect. */
    void clearPostEffect();

    /** The active post effect, if any. */
    Optional<ResourceId> postEffect();

    /**
     * Leaves the world like "Save and Quit to Title" (single player) or "Disconnect" (servers). Call
     * it from a tick, not while drawing.
     */
    void leaveWorld();
}
