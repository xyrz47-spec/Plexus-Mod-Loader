package dev.polymodloader.game.layer.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.world.StorageOptions;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class OperationsTest {

    private static final ResourceId ID = ResourceId.of("demo", "info");

    @Test
    void olderStorageIsMigratedAndTheVersionKeyStaysHidden() {
        StorageOptions options = StorageOptions.DEFAULT.withVersion(2, (data, from) -> {
            data.putString("motd", data.getString("message", ""));
            data.remove("message");
            data.putInt("migrations", data.getInt("migrations", 0) + 1);
        });
        CompoundTag saved = new CompoundTag();
        saved.putString("message", "hello");
        saved.putInt(WorldServices.VERSION_KEY, 1);

        DataTree loaded = WorldServices.load(saved, ID, options);
        assertEquals("hello", loaded.getString("motd", ""));
        assertFalse(loaded.has(WorldServices.VERSION_KEY), "mods never see the version key");

        CompoundTag written = WorldServices.save(loaded, options);
        assertEquals(2, written.getIntOr(WorldServices.VERSION_KEY, 0));
        DataTree again = WorldServices.load(written, ID, options);
        assertEquals(1, again.getInt("migrations", 0), "current data is not migrated again");
    }

    @Test
    void pruningKeepsTheNewestBackupsOfOneWorld(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws java.io.IOException {
        for (String name : List.of(
                "2026-01-01_10-00-00_world.zip",
                "2026-01-02_10-00-00_world.zip",
                "2026-01-03_10-00-00_world.zip",
                "2026-01-01_10-00-00_other.zip")) {
            java.nio.file.Files.writeString(directory.resolve(name), "zip");
        }
        WorldBackups.prune(directory, "world", 2);
        try (var files = java.nio.file.Files.list(directory)) {
            assertEquals(
                    List.of(
                            "2026-01-01_10-00-00_other.zip",
                            "2026-01-02_10-00-00_world.zip",
                            "2026-01-03_10-00-00_world.zip"),
                    files.map(file -> file.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void backupsTriggerOnRemovedOrChangedModsOnly() {
        Map<String, String> before = Map.of("a", "1.0", "b", "2.0");
        assertEquals(List.of(), WorldBackups.changes(before, Map.of("a", "1.0", "b", "2.0", "c", "1.0")));
        List<String> changes = WorldBackups.changes(before, Map.of("a", "1.1"));
        assertTrue(changes.contains("a 1.0->1.1"), changes.toString());
        assertTrue(changes.contains("b 2.0->removed"), changes.toString());
        assertEquals(List.of(), WorldBackups.changes(Map.of(), Map.of("a", "1.0")), "a new world changes nothing");
    }
}
