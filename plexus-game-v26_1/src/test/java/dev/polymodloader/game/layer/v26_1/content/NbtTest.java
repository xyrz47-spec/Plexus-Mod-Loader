package dev.polymodloader.game.layer.v26_1.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.api.data.DataTree;
import org.junit.jupiter.api.Test;

class NbtTest {

    @Test
    void everyValueTypeSurvivesTheRoundTrip() {
        DataTree tree = DataTree.create()
                .putInt("count", 7)
                .putLong("since", 1_234_567_890_123L)
                .putDouble("ratio", 0.25)
                .putBoolean("powered", true)
                .putString("owner", "Steve")
                .putBytes("raw", new byte[] {1, 2, 3});
        tree.child("nested").putInt("depth", 2).child("deeper").putString("leaf", "yes");

        DataTree copy = Nbt.fromTag(Nbt.toTag(tree));

        assertEquals(tree, copy);
        assertEquals(2, copy.findChild("nested").orElseThrow().getInt("depth", 0));
        assertEquals("yes", copy.child("nested").child("deeper").getString("leaf", ""));
    }
}
