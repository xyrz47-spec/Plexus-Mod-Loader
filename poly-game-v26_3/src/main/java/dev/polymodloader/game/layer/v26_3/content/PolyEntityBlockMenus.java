package dev.polymodloader.game.layer.v26_3.content;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** The game's chest-style menu types by row count. */
final class PolyEntityBlockMenus {

    private PolyEntityBlockMenus() {}

    static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
