package dev.polymodloader.game.layer.v26_2.content;

import dev.polymodloader.api.content.block.entity.MenuLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu types of mod block entities with a {@link MenuLayout}, one per block, registered under the
 * block's ID so client and server agree on them. The game keeps {@code MenuType}'s constructor and
 * factory interface private; Poly Mod Loader creates the type reflectively.
 */
public final class PolyMenus {

    private static final Map<MenuType<?>, MenuLayout> LAYOUTS = new ConcurrentHashMap<>();

    private PolyMenus() {}

    static MenuType<?> register(Identifier id, MenuLayout layout) {
        MenuType<?>[] self = new MenuType<?>[1];
        MenuType<?> type =
                create((containerId, inventory) -> PolyMenu.forClient(self[0], containerId, inventory, layout));
        self[0] = type;
        Registry.register(BuiltInRegistries.MENU, id, type);
        LAYOUTS.put(type, layout);
        return type;
    }

    /** The layout of a Poly Mod Loader menu type, or null for the game's types. */
    public static MenuLayout layout(MenuType<?> type) {
        return LAYOUTS.get(type);
    }

    /** Creates a menu on the client's side of the open-menu packet. */
    @SuppressWarnings("unchecked")
    public static PolyMenu createForClient(MenuType<?> type, int containerId, Inventory inventory) {
        return ((MenuType<PolyMenu>) type).create(containerId, inventory);
    }

    private interface Factory {
        PolyMenu create(int containerId, Inventory inventory);
    }

    private static MenuType<?> create(Factory factory) {
        try {
            Class<?> supplier =
                    Class.forName(MenuType.class.getName() + "$MenuSupplier", false, MenuType.class.getClassLoader());
            Object proxy = Proxy.newProxyInstance(
                    MenuType.class.getClassLoader(), new Class<?>[] {supplier}, (instance, method, arguments) -> {
                        if (method.getName().equals("create") && arguments != null && arguments.length == 2) {
                            return factory.create((Integer) arguments[0], (Inventory) arguments[1]);
                        }
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == arguments[0];
                            case "toString" -> "PolyMenuSupplier";
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
            Constructor<?> constructor = MenuType.class.getDeclaredConstructor(supplier, FeatureFlagSet.class);
            constructor.setAccessible(true);
            return (MenuType<?>) constructor.newInstance(proxy, FeatureFlagSet.of());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("POLY-CONTENT-005 cannot create a menu type in this game version", e);
        }
    }
}
