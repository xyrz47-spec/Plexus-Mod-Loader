package dev.polymodloader.game.layer.v26_1.content;

import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Maps Native API interaction results and hands onto the game's and back. */
public final class Interactions {

    private Interactions() {}

    /** The game result for {@code result}; {@code PASS} falls back to the game's default behaviour. */
    public static InteractionResult toGame(
            dev.polymodloader.api.world.InteractionResult result, Supplier<InteractionResult> gameDefault) {
        return switch (result) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case FAIL -> InteractionResult.FAIL;
            case PASS -> gameDefault.get();
        };
    }

    /**
     * The Native API result for a game result: a success that swings no arm is {@code CONSUME}, any
     * other success {@code SUCCESS}; "try with empty hand" is {@code PASS}.
     */
    public static dev.polymodloader.api.world.InteractionResult fromGame(InteractionResult result) {
        return switch (result) {
            case InteractionResult.Success success ->
                success.swingSource() == InteractionResult.SwingSource.NONE
                        ? dev.polymodloader.api.world.InteractionResult.CONSUME
                        : dev.polymodloader.api.world.InteractionResult.SUCCESS;
            case InteractionResult.Fail _ -> dev.polymodloader.api.world.InteractionResult.FAIL;
            default -> dev.polymodloader.api.world.InteractionResult.PASS;
        };
    }

    /** The Native API hand. */
    public static dev.polymodloader.api.entity.Hand hand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? dev.polymodloader.api.entity.Hand.MAIN_HAND
                : dev.polymodloader.api.entity.Hand.OFF_HAND;
    }
}
