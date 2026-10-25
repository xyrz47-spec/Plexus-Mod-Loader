package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private void download(URI url, Path target, String expectedSha1) {
        HttpRequest request =
                HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(10)).build();
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
            try {
                HttpResponse<InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " while downloading " + url);
                }
                try (InputStream body = response.body()) {
                    Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                String actualSha1 = hash(temporary, "SHA-1");
                if (!expectedSha1.equalsIgnoreCase(actualSha1)) {
                    throw new IllegalStateException("SHA-1 mismatch for " + url + ": expected " + expectedSha1
                            + " but downloaded " + actualSha1);
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + url, e);
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
