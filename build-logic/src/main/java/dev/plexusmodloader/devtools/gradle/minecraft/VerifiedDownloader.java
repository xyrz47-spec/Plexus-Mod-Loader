package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Downloads files from Mojang's distribution servers and rejects any file whose hash differs from
 * the hash published in the version manifest. Files that already exist with the expected hash are
 * reused, so repeated runs work offline.
 */
final class VerifiedDownloader {

    private static final int ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Downloads {@code url} to {@code target} unless it already exists with the expected SHA-1. */
    void downloadSha1Verified(URI url, String expectedSha1, Path target) {
        if (Files.isRegularFile(target) && expectedSha1.equalsIgnoreCase(hash(target, "SHA-1"))) {
            return;
        }
        download(url, target, expectedSha1);
    }

    /**
     * Downloads an asset object. Asset files are content-addressed by their SHA-1, so an existing
     * file with the expected size is trusted without re-hashing (thousands of files).
     */
    void downloadAssetObject(URI url, String expectedSha1, long expectedSize, Path target) {
        try {
            if (Files.isRegularFile(target) && Files.size(target) == expectedSize) {
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        download(url, target, expectedSha1);
    }

    String fetchString(URI url) {
        HttpRequest request =
                HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(1)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " while fetching " + url);
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to fetch " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching " + url, e);
        }
    }

    /**
     * Downloads with a few attempts: a fresh machine (CI) fetches thousands of asset objects at once, and an I/O error
     * or a 429/5xx answer from the server is retried. A wrong hash or another HTTP status is not.
     */
    private void download(URI url, Path target, String expectedSha1) {
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    downloadOnce(url, target, expectedSha1);
                    return;
                } catch (IOException e) {
                    if (attempt == ATTEMPTS) {
                        throw e;
                    }
                    Thread.sleep(RETRY_DELAY.multipliedBy(attempt));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + url + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + url, e);
        }
    }

    private void downloadOnce(URI url, Path target, String expectedSha1) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(10)).build();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                response.body().close();
                String message = "HTTP " + status + " while downloading " + url;
                if (status == 429 || status >= 500) {
                    throw new IOException(message);
                }
                throw new IllegalStateException(message);
            }
            try (InputStream body = response.body()) {
                Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            String actualSha1 = hash(temporary, "SHA-1");
            if (!expectedSha1.equalsIgnoreCase(actualSha1)) {
                throw new IllegalStateException("SHA-1 mismatch for " + url + ": expected " + expectedSha1
                        + " but downloaded " + actualSha1);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileSystemException e) {
                // Another writer (a thread, or a task of another project sharing the cache) moved the same file in
                // first, and Windows refuses to replace a file in that state. Its file is as good as ours.
                if (!hasSha1(target, expectedSha1)) {
                    throw e;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean hasSha1(Path file, String expectedSha1) {
        try {
            return Files.isRegularFile(file) && expectedSha1.equalsIgnoreCase(hash(file, "SHA-1"));
        } catch (UncheckedIOException e) {
            return false;
        }
    }

    static String hash(Path file, String algorithm) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to hash " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
