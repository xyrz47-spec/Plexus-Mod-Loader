package dev.plexusmodloader.game.layer.v26_2.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ItemTooltipsTest {

    @Test
    void linesReadAndChangeTheGamesComponents() {
        List<Component> components = new ArrayList<>(List.of(Component.literal("Spark"), Component.literal("Rare")));
        ItemTooltips.ComponentLines lines = new ItemTooltips.ComponentLines(components);

        lines.add(1, "§bSparkly");
        lines.add("Last");
        lines.remove(2);

        assertEquals(3, lines.size());
        assertEquals("Spark", lines.get(0));
        assertEquals("§bSparkly", components.get(1).getString());
        assertEquals("Last", lines.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> lines.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> lines.add(5, "far"));
    }
}
