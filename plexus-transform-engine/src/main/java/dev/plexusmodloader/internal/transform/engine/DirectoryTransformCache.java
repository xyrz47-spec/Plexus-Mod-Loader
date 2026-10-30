package dev.plexusmodloader.internal.transform.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * File-system {@link TransformCache}. Each entry is {@code <digest>.polyclass}: a 32-byte SHA-256 of
 * the payload followed by the transformed class file. An entry whose checksum does not match (a
 * damaged or partially written file) is deleted and reported as a miss, so corruption can never feed
 * wrong bytes into the JVM (design doc §15.2). Writes go through a temporary file and an atomic move.
 *
 * <p>The cache keeps to a size limit. A hit marks its entry as used (its modification time). When a
 * write takes the cache over the limit, the least recently used entries are deleted until it is at 90%
 * of the limit; the entry just written is kept. Old entries are never wrong, only unused, because keys
 * cover every input (see {@link TransformCacheKey}), so deleting them only costs a later
 * re-transformation.
 */
public final class DirectoryTransformCache implements TransformCache {

    /** The default size limit: 256 MiB. */
    public static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

    private static final int CHECKSUM_LENGTH = 32;
    private static final String SUFFIX = ".polyclass";

    private final Path directory;
    private final Consumer<String> warnings;
    private final long maxBytes;

    /** Bytes of all entries, counted on the first write; -1 before that. Guarded by {@code this}. */
    private long totalBytes = -1;

    /** A cache with the {@link #DEFAULT_MAX_BYTES default size limit}. */
    public DirectoryTransformCache(Path directory, Consumer<String> warnings) {
        this(directory, warnings, DEFAULT_MAX_BYTES);
    }

    /**
     * @param directory cache directory, created on first write
     * @param warnings receives a message for every damaged entry, failed write, or failed clean-up
     * @param maxBytes the most bytes the entries may take
     * @throws IllegalArgumentException when {@code maxBytes} is not positive
     */
    public DirectoryTransformCache(Path directory, Consumer<String> warnings, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("the transform cache size limit must be positive, got " + maxBytes);
        }
        this.directory = directory;
        this.warnings = warnings;
        this.maxBytes = maxBytes;
    }

    @Override
    public Optional<byte[]> get(TransformCacheKey key) {
        Path entry = entryPath(key);
        if (!Files.isRegularFile(entry)) {
            return Optional.empty();
        }
        try {
            byte[] stored = Files.readAllBytes(entry);
            if (stored.length > CHECKSUM_LENGTH) {
                byte[] checksum = Arrays.copyOfRange(stored, 0, CHECKSUM_LENGTH);
                byte[] payload = Arrays.copyOfRange(stored, CHECKSUM_LENGTH, stored.length);
                if (Arrays.equals(checksum, sha256(payload))) {
                    markUsed(entry, key);
                    return Optional.of(payload);
                }
            }
            warnings.accept("PLEXUS-TRANSFORM-008 damaged cache entry for " + key.className() + " (" + entry
                    + ") was discarded; the class is transformed again");
            Files.deleteIfExists(entry);
        } catch (IOException e) {
            warnings.accept("PLEXUS-TRANSFORM-008 unreadable cache entry for " + key.className() + ": " + e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void put(TransformCacheKey key, byte[] transformed) {
        Path entry = entryPath(key);
        try {
            Files.createDirectories(directory);
            if (totalBytes < 0) {
                totalBytes = entries().stream().mapToLong(CacheEntry::size).sum();
            }
            long replaced = Files.isRegularFile(entry) ? Files.size(entry) : 0;
            Path temporary = Files.createTempFile(directory, key.digest(), ".tmp");
            try {
                Files.write(
                        temporary,
                        ByteBuffer.allocate(CHECKSUM_LENGTH + transformed.length)
                                .put(sha256(transformed))
                                .put(transformed)
                                .array());
                Files.move(temporary, entry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            totalBytes += CHECKSUM_LENGTH + transformed.length - replaced;
        } catch (IOException e) {
            warnings.accept("PLEXUS-TRANSFORM-009 could not write cache entry for " + key.className() + ": " + e);
            return;
        }
        if (totalBytes > maxBytes) {
            prune(entry);
        }
    }

    /** The most bytes the entries may take. */
    public long maxBytes() {
        return maxBytes;
    }

    Path entryPath(TransformCacheKey key) {
        return directory.resolve(key.digest() + SUFFIX);
    }

    /** Deletes least recently used entries, except {@code keep}, until the cache is at 90% of the limit. */
    private void prune(Path keep) {
        long target = maxBytes / 10 * 9;
        List<CacheEntry> entries;
        try {
            entries = entries();
        } catch (IOException e) {
            warnings.accept(
                    "PLEXUS-TRANSFORM-009 could not list the cache to keep it under " + maxBytes + " bytes: " + e);
            return;
        }
        long total = entries.stream().mapToLong(CacheEntry::size).sum();
        entries.sort(Comparator.comparing(CacheEntry::lastUsed));
        int deleted = 0;
        for (CacheEntry candidate : entries) {
            if (total <= target) {
                break;
            }
            if (candidate.path().equals(keep)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(candidate.path())) {
                    deleted++;
                }
                total -= candidate.size();
            } catch (IOException e) {
                warnings.accept("PLEXUS-TRANSFORM-009 could not delete old cache entry " + candidate.path() + ": " + e);
            }
        }
        totalBytes = total;
        if (total > maxBytes) {
            warnings.accept("PLEXUS-TRANSFORM-009 the transform cache is still " + total + " bytes after removing "
                    + deleted + " old entr(ies); its limit is " + maxBytes + " bytes");
        }
    }

    private List<CacheEntry> entries() throws IOException {
        List<CacheEntry> entries = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return entries;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path path : stream) {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                entries.add(new CacheEntry(path, attributes.size(), attributes.lastModifiedTime()));
            }
        }
        return entries;
    }

    /** Records a hit so the entry counts as recently used. */
    private void markUsed(Path entry, TransformCacheKey key) {
        try {
            Files.setLastModifiedTime(entry, FileTime.from(Instant.now()));
        } catch (IOException e) {
            warnings.accept("PLEXUS-TRANSFORM-009 could not mark cache entry for " + key.className()
                    + " as used; it may be removed early: " + e);
        }
    }

    private record CacheEntry(Path path, long size, FileTime lastUsed) {}

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
