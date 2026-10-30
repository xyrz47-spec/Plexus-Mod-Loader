package dev.plexusmodloader.game.layer.v1_21_11.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModCreativeTabsTest {

    @Test
    void modTabsFillTheLeftFiveColumnsOfBothRowsPageByPage() {
        assertEquals(new ModCreativeTabs.Position(1, true, 0), ModCreativeTabs.position(0));
        assertEquals(new ModCreativeTabs.Position(1, true, 4), ModCreativeTabs.position(4));
        assertEquals(new ModCreativeTabs.Position(1, false, 0), ModCreativeTabs.position(5));
        assertEquals(new ModCreativeTabs.Position(1, false, 4), ModCreativeTabs.position(9));
        assertEquals(new ModCreativeTabs.Position(2, true, 0), ModCreativeTabs.position(10));
        assertEquals(new ModCreativeTabs.Position(3, false, 1), ModCreativeTabs.position(26));
        assertThrows(IllegalArgumentException.class, () -> ModCreativeTabs.position(-1));
    }

    @Test
    void columnsNeverReachTheSharedRightHandTabs() {
        for (int index = 0; index < 100; index++) {
            int column = ModCreativeTabs.position(index).column();
            assertEquals(true, column >= 0 && column < ModCreativeTabs.FIRST_SHARED_COLUMN, "index " + index);
        }
    }

    @Test
    void pagesCountTheGamesOwnPage() {
        assertEquals(1, ModCreativeTabs.pageCount(0));
        assertEquals(2, ModCreativeTabs.pageCount(1));
        assertEquals(2, ModCreativeTabs.pageCount(10));
        assertEquals(3, ModCreativeTabs.pageCount(11));
    }
}
