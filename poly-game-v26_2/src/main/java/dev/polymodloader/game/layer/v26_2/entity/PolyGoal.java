package dev.polymodloader.game.layer.v26_2.entity;

import dev.polymodloader.api.content.entity.AiGoal;
import dev.polymodloader.api.content.entity.GoalControl;
import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.game.layer.v26_2.Views;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Runs a mod's {@link AiGoal} in the game's goal scheduler. */
final class PolyGoal extends Goal {

    private final Mob mob;
    private final AiGoal goal;

    PolyGoal(Mob mob, AiGoal goal) {
        this.mob = mob;
        this.goal = goal;
        EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);
        for (GoalControl control : goal.controls()) {
            flags.add(Goal.Flag.valueOf(control.name()));
        }
        setFlags(flags);
    }

    private MobView view() {
        return (MobView) Views.entity(mob);
    }

    @Override
    public boolean canUse() {
        return goal.canStart(view());
    }

    @Override
    public boolean canContinueToUse() {
        return goal.shouldContinue(view());
    }

    @Override
    public void start() {
        goal.start(view());
    }

    @Override
    public void stop() {
        goal.stop(view());
    }

    @Override
    public void tick() {
        goal.tick(view());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public String toString() {
        return "PolyGoal[" + goal.getClass().getName() + "]";
    }
}
