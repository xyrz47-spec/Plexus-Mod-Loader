package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifiedDownloaderTest {

    private static final byte[] OBJECT = "asset object".repeat(4096).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path cache;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int failuresLeft;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            boolean fail;
            synchronized (this) {
                fail = failuresLeft > 0;
                if (fail) {
                    failuresLeft--;
                }
            }
            if (fail) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, OBJECT.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(OBJECT);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Several Gradle tasks fill one shared asset folder at once on a fresh machine (CI). */
    @Test
    void manyWritersOfTheSameAssetObjectAllSucceed() throws Exception {
        String sha1 = sha1(OBJECT);
        Path target = cache.resolve("objects").resolve(sha1.substring(0, 2)).resolve(sha1);
        int writers = 48;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(writers)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                VerifiedDownloader downloader = new VerifiedDownloader();
                results.add(executor.submit(() -> {
                    start.await();
                    downloader.downloadAssetObject(url(), sha1, OBJECT.length, target);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        }
        assertArrayEquals(OBJECT, Files.readAllBytes(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(List.of(target), files.toList(), "no .part files are left behind");
        }
    }

    @Test
    void aServerErrorIsRetried() {
        failuresLeft = 2;
        String sha1 = sha1(OBJECT);
        Path target = cache.resolve(sha1);
        new VerifiedDownloader().downloadAssetObject(url(), sha1, OBJECT.length, target);
        assertEquals(3, requests.get());
    }

    private URI url() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/o");
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
