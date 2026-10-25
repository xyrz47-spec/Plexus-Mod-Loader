package dev.polymodloader.game.layer.v26_2.client;

import dev.polymodloader.game.layer.v26_2.content.ModCreativeTabs;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the creative screen one page at a time when mods added creative tabs ({@link ModCreativeTabs}):
 * the screen's tab loops (drawing, hovering, clicking) see only the current page's tabs, and two
 * buttons above the tabs turn the page. Without mod tabs nothing changes. Client thread only.
 */
public final class CreativeTabPaging {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final MethodHandle LEFT_POS;
    private static final MethodHandle TOP_POS;
    private static final MethodHandle IMAGE_WIDTH;
    private static final MethodHandle SELECTED_TAB;
    private static final MethodHandle SELECT_TAB;
    private static final MethodHandle ADD_RENDERABLE_WIDGET;
    private static final MethodHandle REBUILD_WIDGETS;

    static {
        try {
            // All protected or private in 26.2 (javap); no public accessor exists.
            MethodHandles.Lookup container =
                    MethodHandles.privateLookupIn(AbstractContainerScreen.class, MethodHandles.lookup());
            LEFT_POS = container.findGetter(AbstractContainerScreen.class, "leftPos", int.class);
            TOP_POS = container.findGetter(AbstractContainerScreen.class, "topPos", int.class);
            IMAGE_WIDTH = container.findGetter(AbstractContainerScreen.class, "imageWidth", int.class);
            MethodHandles.Lookup creative =
                    MethodHandles.privateLookupIn(CreativeModeInventoryScreen.class, MethodHandles.lookup());
            SELECTED_TAB =
                    creative.findStaticGetter(CreativeModeInventoryScreen.class, "selectedTab", CreativeModeTab.class);
            SELECT_TAB = creative.findVirtual(
                    CreativeModeInventoryScreen.class,
                    "selectTab",
                    MethodType.methodType(void.class, CreativeModeTab.class));
            MethodHandles.Lookup screen = MethodHandles.privateLookupIn(Screen.class, MethodHandles.lookup());
            ADD_RENDERABLE_WIDGET = screen.findVirtual(
                    Screen.class,
                    "addRenderableWidget",
                    MethodType.methodType(GuiEventListener.class, GuiEventListener.class));
            REBUILD_WIDGETS = screen.findVirtual(Screen.class, "rebuildWidgets", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** The page the creative screen shows; 0 is the game's tabs. */
    private static int page;

    private CreativeTabPaging() {}

    /**
     * Replaces {@code CreativeModeTabs.tabs()} in the creative screen's tab loops: the tabs on the
     * current page, plus the game's right-hand tabs, which every page shows.
     */
    public static List<CreativeModeTab> onListTabs() {
        List<CreativeModeTab> tabs = CreativeModeTabs.tabs();
        if (!ModCreativeTabs.hasModTabs()) {
            return tabs;
        }
        return tabs.stream().filter(CreativeTabPaging::isOnCurrentPage).toList();
    }

    private static boolean isOnCurrentPage(CreativeModeTab tab) {
        int tabPage = ModCreativeTabs.pageOf(tab);
        if (tabPage == 0 && tab.column() >= ModCreativeTabs.FIRST_SHARED_COLUMN) {
            return true;
        }
        return tabPage == page;
    }

    /** Before the return of {@code CreativeModeInventoryScreen.init()}: page buttons. */
    public static void onScreenInit(CreativeModeInventoryScreen screen) {
        if (!ModCreativeTabs.hasModTabs()) {
            return;
        }
        CreativeModeTab selected = (CreativeModeTab) invoke(SELECTED_TAB);
        int selectedPage = ModCreativeTabs.pageOf(selected);
        // A shared right-hand tab (search, inventory) keeps the page the player was on.
        if (selectedPage != 0 || selected.column() < ModCreativeTabs.FIRST_SHARED_COLUMN) {
            page = selectedPage;
        }
        int pages = ModCreativeTabs.pageCount();
        int left = (int) invoke(LEFT_POS, screen);
        int top = (int) invoke(TOP_POS, screen);
        int width = (int) invoke(IMAGE_WIDTH, screen);
        Component pageText = Component.literal("Page " + (page + 1) + " / " + pages);
        // Above the top row of tabs, which starts 28 pixels above the panel.
        Button previous = Button.builder(Component.literal("<"), button -> turn(screen, -1))
                .bounds(left, top - 50, 20, 20)
                .tooltip(Tooltip.create(pageText))
                .build();
        Button next = Button.builder(Component.literal(">"), button -> turn(screen, 1))
                .bounds(left + width - 20, top - 50, 20, 20)
                .tooltip(Tooltip.create(pageText))
                .build();
        previous.active = page > 0;
        next.active = page < pages - 1;
        invoke(ADD_RENDERABLE_WIDGET, screen, previous);
        invoke(ADD_RENDERABLE_WIDGET, screen, next);
        if (REPORTED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[Poly Mod Loader] Creative screen shows mod tabs on {} extra page(s) with page buttons",
                    pages - 1);
        }
    }

    /** Turns the page, selects its first tab, and rebuilds the screen's widgets. */
    private static void turn(CreativeModeInventoryScreen screen, int delta) {
        int target = Math.max(0, Math.min(ModCreativeTabs.pageCount() - 1, page + delta));
        if (target == page) {
            return;
        }
        page = target;
        CreativeModeTab first = page == 0
                ? CreativeModeTabs.getDefaultTab()
                : onListTabs().stream()
                        .filter(tab -> ModCreativeTabs.pageOf(tab) == page)
                        .findFirst()
                        .orElse(CreativeModeTabs.getDefaultTab());
        invoke(SELECT_TAB, screen, first);
        invoke(REBUILD_WIDGETS, screen);
    }

    private static Object invoke(MethodHandle handle, Object... arguments) {
        try {
            return handle.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Creative screen accessor " + handle + " failed", e);
        }
    }
}
