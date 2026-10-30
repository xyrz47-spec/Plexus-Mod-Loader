package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaArgumentFileTest {

    @TempDir
    Path folder;

    /**
     * corpusQuiltClient on 26.2 failed with CreateProcess error=206: its command line was over Windows' limit. The same
     * arguments, longer than that limit and with spaces, backslashes and quotes, reach the JVM through the file.
     */
    @Test
    void theJvmReadsEveryArgumentFromTheFileWhateverItsLength() throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String value = "C:\\Program Files\\a b\\\"quoted\"\\" + "x".repeat(40_000);
        List<String> command = new ArrayList<>(List.of(java, "-Dpoly.argfile.test=" + value, "-XshowSettings:properties", "-version"));

        List<String> started = JavaArgumentFile.commandWithArgumentFile(command, folder.resolve("java.args"));

        assertEquals(2, started.size());
        assertTrue(started.get(1).startsWith("@"));
        Process process = new ProcessBuilder(started).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("poly.argfile.test = " + value), "the property did not arrive as written");
    }
}
