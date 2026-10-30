package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBenchmarkTaskTest {

    @Test
    void summaryCountsFramesSlowerThanThirtyFps() {
        long[] frames = new long[100];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = 10_000_000L; // 10 ms
        }
        frames[17] = 40_000_000L; // one 40 ms frame, out of order
        FrameBenchmarkTask.RunResult result = FrameBenchmarkTask.summarise(frames);

        assertEquals(100, result.frames());
        assertEquals(10.0, result.p50Millis(), 1e-9);
        assertEquals(10.0, result.p95Millis(), 1e-9);
        assertEquals(40.0, result.maxMillis(), 1e-9);
        assertEquals(1.0, result.over33MillisPercent(), 1e-9);
        // 100 frames in 1.03 s.
        assertEquals(100 / 1.03, result.meanFps(), 1e-6);
    }

    @Test
    void progressLineIsOneJsonObjectPerRun() {
        FrameBenchmarkTask.RunResult result = new FrameBenchmarkTask.RunResult(10, 100.0, 9.5, 12.0, 15.0, 20.0, 0.0);
        String line = FrameBenchmarkTask.progressLine("poly", -1, result);

        assertTrue(line.startsWith("{\"variant\": \"poly\", \"run\": -1, \"result\": {\"frames\": 10,"), line);
        assertTrue(line.endsWith("}}\n"), line);
        assertEquals(1, line.lines().count());
    }

    @Test
    void optionsUncapTheFrameRateAndKeepRenderingWithoutFocus() {
        assertTrue(FrameBenchmarkTask.OPTIONS.contains("enableVsync:false\n"));
        assertTrue(FrameBenchmarkTask.OPTIONS.contains("maxFps:260\n"));
        assertTrue(FrameBenchmarkTask.OPTIONS.contains("pauseOnLostFocus:false\n"));
        assertTrue(FrameBenchmarkTask.OPTIONS.contains("onboardAccessibility:false\n"));
    }
}
