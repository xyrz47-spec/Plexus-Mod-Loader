package dev.polymodloader.runtime.discovery;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Poly Mod Loader's own builds of Fabric API modules for a host loader (ADR-0031): a module that cannot run as written
 * on the host's patched game ({@code POLY-COMPAT-224}) is replaced by the build implemented on that host's hooks, when
 * Poly Mod Loader has one for that exact host release. The builds ship inside Poly Mod Loader's jars as resources,
 * listed in {@value #INDEX}: a key {@code <host key>.<mod id>} ({@link #hostKey}) names the jar resource. A replacement
 * is a mod of its own (no container), assessed by its bridge as any mod file is; the host check then runs on it too.
 *
 * <p>Not thread-safe; the runtime calls it once, on the launch thread.
 */
public final class HostModules {

    /** The index of host modules on the class path. */
    public static final String INDEX = "META-INF/polymodloader/host-modules.properties";

    private static final String BLOCKED = "POLY-COMPAT-224";

    private HostModules() {}

    /**
     * The result of {@link #replaceLeftOut}.
     *
     * @param mods the mods, each replaced one in its original's place
     * @param replaced for each replacement, the original's jar and the replacement's jar
     */
    public record Result(List<ForeignMod> mods, List<Path[]> replaced) {
        public Result {
            mods = List.copyOf(mods);
            replaced = List.copyOf(replaced);
        }
    }

    /**
     * {@code mods} with each one left out for not fitting {@code hostName}'s patch replaced by the host module of the
     * same ID that {@code loader}'s index lists for that host, extracted into {@code directory}.
     */
    public static Result replaceLeftOut(
            String hostName,
            List<ForeignMod> mods,
            ForeignModResolver bridges,
            GameSide side,
            Path directory,
            ClassLoader loader) {
        Properties index = index(loader);
        if (index.isEmpty()) {
            return new Result(mods, List.of());
        }
        String host = hostKey(hostName);
        List<ForeignMod> result = new ArrayList<>();
        List<Path[]> replaced = new ArrayList<>();
        for (ForeignMod mod : mods) {
            String resource = index.getProperty(host + "." + mod.assessment().modId());
            Optional<ForeignMod> replacement = resource == null || !leftOutForTheHost(mod)
                    ? Optional.empty()
                    : extract(loader, resource, directory)
                            .map(ModFileScanner::classify)
                            .flatMap(file -> bridges.assess(file, side))
                            .filter(found -> found.assessment()
                                    .modId()
                                    .equals(mod.assessment().modId()));
            if (replacement.isPresent()) {
                result.add(replacement.get());
                replaced.add(new Path[] {mod.jar(), replacement.get().jar()});
            } else {
                result.add(mod);
            }
        }
        return new Result(result, replaced);
    }

    /** {@code NeoForge 26.2.0.93} becomes {@code neoforge-26.2.0.93}: the host's name as an index key. */
    static String hostKey(String hostName) {
        return hostName.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static boolean leftOutForTheHost(ForeignMod mod) {
        for (CompatibilityFinding finding : mod.assessment().findings()) {
            if (finding.blocking() && finding.code().equals(BLOCKED)) {
                return true;
            }
        }
        return false;
    }

    private static Properties index(ClassLoader loader) {
        Properties index = new Properties();
        try (InputStream input = loader.getResourceAsStream(INDEX)) {
            if (input != null) {
                index.load(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + INDEX, e);
        }
        return index;
    }

    /** The jar resource {@code resource}, stored under its content hash so that a changed build is a new file. */
    private static Optional<Path> extract(ClassLoader loader, String resource, Path directory) {
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                return Optional.empty();
            }
            byte[] bytes = input.readAllBytes();
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            Path target = directory.resolve(sha256(bytes).substring(0, 16)).resolve(name);
            if (!Files.isRegularFile(target) || Files.size(target) != bytes.length) {
                Files.createDirectories(target.getParent());
                Path partial = Files.createTempFile(target.getParent(), name, ".partial");
                Files.write(partial, bytes);
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return Optional.of(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract Poly Mod Loader's host module " + resource, e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
