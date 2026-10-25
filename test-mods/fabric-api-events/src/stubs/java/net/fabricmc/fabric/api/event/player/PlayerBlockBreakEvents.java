package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Compile-time stand-in for Fabric API's {@code PlayerBlockBreakEvents}; never packaged. */
public final class PlayerBlockBreakEvents {

    public static final Event<Before> BEFORE = null;
    public static final Event<After> AFTER = null;
    public static final Event<Canceled> CANCELED = null;

    private PlayerBlockBreakEvents() {}

    @FunctionalInterface
    public interface Before {
        boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }

    @FunctionalInterface
    public interface After {
        void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }

    @FunctionalInterface
    public interface Canceled {
        void onBlockBreakCanceled(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }
}
