package dev.plexusmodloader.game.layer.v26_1;

import dev.plexusmodloader.game.version.layer.ModResourcePack;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backs up a world before it loads with mods removed or at other versions than last time, when the
 * policy in {@code config/poly/backups.properties} allows it (the default). The mods a world was last
 * loaded with are recorded in {@code <world>/poly/mods.properties}. Backups use the game's own world
 * backup (a zip in the {@code backups} directory, as from the Edit World screen).
 */
final class WorldBackups {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Path POLICY = Path.of("config", "poly", "backups.properties");
    private static final String ON_MOD_CHANGE = "backup-on-mod-change";
    private static final String KEEP = "max-backups-per-world";

    private WorldBackups() {}

    static void beforeLoading(MinecraftServer server) {
        LayerState.context().ifPresent(context -> {
            Map<String, String> current = new TreeMap<>();
            for (ModResourcePack pack : context.resourcePacks()) {
                current.put(pack.modId(), pack.version());
            }
            Path record =
                    server.getWorldPath(LevelResource.ROOT).resolve("poly").resolve("mods.properties");
            try {
                Map<String, String> previous = read(record);
                List<String> changes = changes(previous, current);
                if (!changes.isEmpty()) {
                    Properties policy = policy();
                    if (Boolean.parseBoolean(
                            policy.getProperty(ON_MOD_CHANGE, "true").trim())) {
                        LevelStorageSource.LevelStorageAccess storage = storage(server);
                        long size = storage.makeWorldBackup();
                        prune(
                                storage.parent().getBackupPath(),
                                storage.getLevelId(),
                                Integer.parseInt(policy.getProperty(KEEP, "10").trim()));
                        LOGGER.info(
                                "[Poly Mod Loader] Backed up the world before loading it with changed mods {} ({} bytes)",
                                changes,
                                size);
                    } else {
                        LOGGER.warn(
                                "[Poly Mod Loader] The world's mods changed {}; backups are off in {}",
                                changes,
                                POLICY);
                    }
                }
                write(record, current);
            } catch (IOException | ReflectiveOperationException e) {
                LOGGER.error("[Poly Mod Loader] PLEXUS-BACKUP-001 world backup check failed; loading continues", e);
            }
        });
    }

    /**
     * Mods that were removed or changed version since the world was last loaded, as
     * {@code id old->new}. New mods are not a change: they cannot break existing data.
     */
    static List<String> changes(Map<String, String> previous, Map<String, String> current) {
        List<String> changes = new ArrayList<>();
        previous.forEach((modId, version) -> {
            String now = current.get(modId);
            if (now == null) {
                changes.add(modId + " " + version + "->removed");
            } else if (!now.equals(version)) {
                changes.add(modId + " " + version + "->" + now);
            }
        });
        return changes;
    }

    /** Deletes the oldest backups of this world beyond {@code keep} (0 keeps all). */
    static void prune(Path directory, String levelId, int keep) throws IOException {
        if (keep <= 0 || !Files.isDirectory(directory)) {
            return;
        }
        String suffix = "_" + levelId + ".zip";
        List<Path> backups;
        try (Stream<Path> files = Files.list(directory)) {
            // File names start with the backup time, so name order is age order.
            backups = files.filter(file -> file.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .toList();
        }
        for (Path old : backups.subList(0, Math.max(0, backups.size() - keep))) {
            Files.delete(old);
            LOGGER.info("[Poly Mod Loader] Deleted old world backup {}", old.getFileName());
        }
    }

    private static Properties policy() throws IOException {
        Properties policy = new Properties();
        if (Files.exists(POLICY)) {
            try (InputStream input = Files.newInputStream(POLICY)) {
                policy.load(input);
            }
        } else {
            Files.createDirectories(POLICY.getParent());
            Files.writeString(POLICY, """
                    # Poly Mod Loader world backups. When a world is about to load with mods removed or at other versions
                    # than last time, Poly Mod Loader first saves a zip of it to the backups directory.
                    %s=true
                    # How many backups of each world to keep; older ones are deleted (0 keeps all).
                    %s=10
                    """.formatted(ON_MOD_CHANGE, KEEP));
        }
        return policy;
    }

    private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server)
            throws ReflectiveOperationException {
        Field field = MinecraftServer.class.getDeclaredField("storageSource");
        field.setAccessible(true);
        return (LevelStorageSource.LevelStorageAccess) field.get(server);
    }

    private static Map<String, String> read(Path record) throws IOException {
        Map<String, String> mods = new TreeMap<>();
        if (Files.exists(record)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(record)) {
                properties.load(input);
            }
            properties.forEach((key, value) -> mods.put(key.toString(), value.toString()));
        }
        return mods;
    }

    private static void write(Path record, Map<String, String> mods) throws IOException {
        Files.createDirectories(record.getParent());
        Properties properties = new Properties();
        properties.putAll(mods);
        try (OutputStream output = Files.newOutputStream(record)) {
            properties.store(output, "Mods this world was last loaded with (written by Poly Mod Loader)");
        }
    }
}
