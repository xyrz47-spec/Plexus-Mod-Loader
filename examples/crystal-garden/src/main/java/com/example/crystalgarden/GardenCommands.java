package com.example.crystalgarden;

import dev.polymodloader.api.command.CommandArgumentType;
import dev.polymodloader.api.command.CommandNode;
import dev.polymodloader.api.command.CommandRegistrar;
import dev.polymodloader.api.world.StoredData;

/**
 * {@code /garden stats} for everyone, {@code /garden grow <pos>} for players with the
 * {@code crystal_garden:grow} permission.
 */
final class GardenCommands {

    private GardenCommands() {}

    static void register(CommandRegistrar commands) {
        commands.register(CommandNode.literal("garden")
                .then(CommandNode.literal("stats").executes(context -> {
                    StoredData stats = context.source()
                            .world()
                            .storage(CrystalGardenMod.GARDEN_STATS, CrystalGardenMod.GARDEN_STATS_OPTIONS);
                    int harvests = stats.data().getInt("harvests", 0);
                    String mine = context.source()
                            .player()
                            .map(player -> ", you harvested "
                                    + player.data(CrystalGardenMod.harvested)
                                            .data()
                                            .getInt("count", 0)
                                    + " crystal(s)")
                            .orElse("");
                    context.source().sendMessage("Crystal Garden: " + harvests + " harvest(s) in this world" + mine);
                    return harvests;
                }))
                .then(CommandNode.literal("grow")
                        .requiresPermission(CrystalGardenMod.growPermission)
                        .then(CommandNode.argument("pos", CommandArgumentType.blockPos())
                                .executes(context -> {
                                    if (!CrystalSprout.ripen(context.source().world(), context.getBlockPos("pos"))) {
                                        context.source().sendFailure("There is no crystal sprout there");
                                        return 0;
                                    }
                                    context.source().sendMessage("The crystal sprout is ripe");
                                    return 1;
                                }))));
    }
}
