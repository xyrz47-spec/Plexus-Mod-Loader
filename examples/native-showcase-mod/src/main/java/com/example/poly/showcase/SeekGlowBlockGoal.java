package com.example.poly.showcase;

import dev.polymodloader.api.content.entity.AiGoal;
import dev.polymodloader.api.entity.MobView;
import dev.polymodloader.api.world.BlockPos;
import dev.polymodloader.api.world.WorldView;

/** Walks to a glow block within six blocks; looks again once a second. One instance per mob. */
final class SeekGlowBlockGoal implements AiGoal {

    private static final int RADIUS = 6;

    private BlockPos target;
    private int cooldown;

    @Override
    public boolean canStart(MobView mob) {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 20;
        target = find(mob.world(), mob.blockPos());
        return target != null && distanceSquared(mob, target) > 4;
    }

    @Override
    public boolean shouldContinue(MobView mob) {
        return mob.isNavigating() && mob.world().blockAt(target).equals(NativeShowcaseMod.GLOW_BLOCK);
    }

    @Override
    public void start(MobView mob) {
        mob.navigateTo(target.x() + 0.5, target.y(), target.z() + 0.5, 1.0);
    }

    @Override
    public void tick(MobView mob) {
        mob.lookAt(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5);
    }

    @Override
    public void stop(MobView mob) {
        mob.stopNavigating();
    }

    static double distanceSquared(MobView mob, BlockPos pos) {
        double dx = mob.x() - (pos.x() + 0.5);
        double dz = mob.z() - (pos.z() + 0.5);
        return dx * dx + dz * dz;
    }

    private static BlockPos find(WorldView world, BlockPos center) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (world.blockAt(pos).equals(NativeShowcaseMod.GLOW_BLOCK)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
