package dev.plexusmodloader.game.layer.v26_1.content;

import dev.plexusmodloader.api.content.item.ItemBehavior;
import dev.plexusmodloader.api.content.item.ItemUse;
import dev.plexusmodloader.game.layer.v26_1.Views;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A mod item whose use forwards to the mod's {@link ItemBehavior}. */
final class PolyItem extends Item {

    private final ItemBehavior behavior;

    PolyItem(Properties properties, ItemBehavior behavior) {
        super(properties);
        this.behavior = behavior;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return Interactions.toGame(
                behavior.onUse(new ItemUse(
                        Views.world(level),
                        Views.player(player),
                        ItemComponents.ref(player.getItemInHand(hand), () -> {}))),
                () -> super.use(level, player, hand));
    }
}
