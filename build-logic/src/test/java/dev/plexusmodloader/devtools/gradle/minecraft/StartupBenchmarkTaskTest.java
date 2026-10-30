package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartupBenchmarkTaskTest {

    @Test
    void theReportHasExitAndReadyTimesForEveryVariant() {
        Map<String, List<BenchmarkLaunch.Timing>> measured = new LinkedHashMap<>();
        measured.put(
                "vanilla",
                List.of(new BenchmarkLaunch.Timing(6.0, 4.0), new BenchmarkLaunch.Timing(7.0, 5.0),
                        new BenchmarkLaunch.Timing(8.0, 6.0)));
        measured.put("connector", List.of(new BenchmarkLaunch.Timing(20.0, 15.0)));
        Map<String, BenchmarkLaunch.Timing> warmups =
                Map.of("vanilla", new BenchmarkLaunch.Timing(9.0, 7.0), "connector", new BenchmarkLaunch.Timing(60, 50));

        JsonObject report = JsonParser.parseString(StartupBenchmarkTask.report(
                        measured, warmups, 3, "25.0.3", "4g", "  \"reference\": {\"name\": \"connector\"},\n"))
                .getAsJsonObject();

        assertEquals(7.0, report.get("vanillaMedian").getAsDouble());
        assertEquals(1.0, report.get("vanillaStdDev").getAsDouble(), 1e-9);
        assertEquals(5.0, report.get("vanillaReadyMedian").getAsDouble());
        assertEquals(3, report.getAsJsonArray("vanillaReadySeconds").size());
        assertEquals(15.0, report.get("connectorReadyMedian").getAsDouble());
        assertEquals(60.0, report.getAsJsonObject("connectorWarmup").get("seconds").getAsDouble());
        assertEquals("4g", report.get("heapSize").getAsString());
        assertTrue(report.has("reference"));
    }
}
