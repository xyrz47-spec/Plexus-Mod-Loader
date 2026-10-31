package dev.plexusmodloader.game.layer.v26_1.content;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v26_1.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.CreativeTabDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers mod creative tabs and places them on pages: the game's screen has fourteen tab slots, all
 * used by the game, so page 0 is the game's tabs and mod tabs fill pages 1, 2, … with ten tabs each
 * (columns 0–4 of the top and bottom rows; the game's right-hand tabs — saved hotbars, search, operator
 * items, inventory — stay on every page). The client's creative screen shows one page at a time
 * ({@code client.CreativeTabPaging}).
 */
public final class ModCreativeTabs {

    /** Mod tabs per page: five in the top row, five in the bottom row. */
    public static final int TABS_PER_PAGE = 10;
    /** The first column of the game's right-hand tabs, which every page shows. */
    public static final int FIRST_SHARED_COLUMN = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");
    /** Mod tab → page (1 or more); the game's tabs are not in it. Written once while registries freeze. */
    private static final Map<CreativeModeTab, Integer> PAGES = new IdentityHashMap<>();
    /** Mod tab → its items, in order. Guarded by {@link #PAGES}. */
    private static final Map<CreativeModeTab, List<Item>> ENTRIES = new IdentityHashMap<>();

    private static volatile int pageCount = 1;

    private ModCreativeTabs() {}

    /**
     * Where the mod tab with index {@code index} (in declaration order) goes.
     *
     * @param page 1 or more
     * @param top whether it is in the top row
     * @param column 0–4
     */
    public record Position(int page, boolean top, int column) {}

    /** The position of the {@code index}-th mod tab. */
    public static Position position(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0: " + index);
        }
        int slot = index % TABS_PER_PAGE;
        return new Position(1 + index / TABS_PER_PAGE, slot < TABS_PER_PAGE / 2, slot % (TABS_PER_PAGE / 2));
    }

    /** Pages including the game's own (page 0) when {@code modTabs} mod tabs exist. */
    public static int pageCount(int modTabs) {
        return 1 + (modTabs + TABS_PER_PAGE - 1) / TABS_PER_PAGE;
    }

    /**
     * Registers the declared tabs; called just before the game freezes its registries, after items and
     * spawn eggs exist. Entries and icons that are not registered items are reported ({@code
     * PLEXUS-CONTENT-008}) and left out; a tab left without entries is not registered.
     */
    public static void install(List<CreativeTabDeclaration> declarations) {
        int index = 0;
        for (CreativeTabDeclaration declaration : declarations) {
            List<Item> entries = new ArrayList<>();
            for (ResourceId entry : declaration.definition().entries()) {
                item(declaration, entry, "entry").ifPresent(entries::add);
            }
            if (entries.isEmpty()) {
                LOGGER.error(
                        "[Plexus Mod Loader] PLEXUS-CONTENT-008 mod={} creative tab {} has no registered item; it is left out",
                        declaration.ownerModId(),
                        declaration.id());
                continue;
            }
            Item icon = declaration
                    .definition()
                    .icon()
                    .flatMap(id -> item(declaration, id, "icon"))
                    .orElse(entries.getFirst());
            Position position = position(index++);
            Identifier id = Views.id(declaration.id());
            // The game's item generator takes a protected Output type, so the tab starts empty and its
            // entries are appended after each build (appendEntries), as for the game's own tabs.
            CreativeModeTab tab = CreativeModeTab.builder(
                            position.top() ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM, position.column())
                    .title(Component.translatable("itemGroup." + id.getNamespace() + "." + id.getPath()))
                    .icon(() -> new ItemStack(icon))
                    .build();
            Registry.register(
                    BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, id), tab);
            synchronized (PAGES) {
                PAGES.put(tab, position.page());
                ENTRIES.put(tab, List.copyOf(entries));
            }
            declaration.registered().run();
        }
        if (index > 0) {
            pageCount = pageCount(index);
            LOGGER.info(
                    "[Plexus Mod Loader] Registered {} creative tab(s) from mods on {} extra page(s)",
                    index,
                    pageCount - 1);
        }
    }

    private static Optional<Item> item(CreativeTabDeclaration declaration, ResourceId id, String role) {
        Identifier identifier = Views.id(id);
        if (BuiltInRegistries.ITEM.containsKey(identifier)) {
            return Optional.of(BuiltInRegistries.ITEM.getValue(identifier));
        }
        LOGGER.error(
                "[Plexus Mod Loader] PLEXUS-CONTENT-008 mod={} creative tab {} {} {} is not a registered item; it is left out",
                declaration.ownerModId(),
                declaration.id(),
                role,
                id);
        return Optional.empty();
    }

    /**
     * From the return hook of {@code CreativeModeTab.buildContents}: fills a mod tab, which the game
     * built empty, with its entries (also for the search tab).
     */
    public static void appendEntries(CreativeModeTab tab) {
        List<Item> items;
        synchronized (PAGES) {
            items = ENTRIES.get(tab);
        }
        if (items == null) {
            return;
        }
        try {
            for (Item item : items) {
                tab.getDisplayItems().add(new ItemStack(item));
                tab.getSearchTabDisplayItems().add(new ItemStack(item));
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.error(
                    "[Plexus Mod Loader] PLEXUS-CONTENT-003 creative tab contents are read-only in this version; "
                            + "a mod tab stays empty: {}",
                    e.toString());
        }
    }

    /** Whether mods added creative tabs. */
    public static boolean hasModTabs() {
        return pageCount > 1;
    }

    /** Number of pages, the game's included. */
    public static int pageCount() {
        return pageCount;
    }

    /** The page of {@code tab}: 0 for the game's tabs. */
    public static int pageOf(CreativeModeTab tab) {
        synchronized (PAGES) {
            return PAGES.getOrDefault(tab, 0);
        }
    }

    /**
     * Head of {@code CreativeModeTabs.validate()}: with mod tabs, checks that no two tabs share a
     * position on the same page and returns {@code true} so the game's check, which does not know
     * pages, is skipped. Without mod tabs it returns {@code false} and the game checks as usual.
     *
     * @throws IllegalArgumentException for two tabs at one position of a page, as the game's check
     */
    public static boolean validate() {
        if (!hasModTabs()) {
            return false;
        }
        Map<String, String> taken = new HashMap<>();
        for (ResourceKey<CreativeModeTab> key : BuiltInRegistries.CREATIVE_MODE_TAB.registryKeySet()) {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(key);
            String name = tab.getDisplayName().getString();
            String position = pageOf(tab) + "/" + tab.row() + "/" + tab.column();
            String previous = taken.put(position, name);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate position: " + name + " vs. " + previous);
            }
        }
        return true;
    }
}
