package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuiltLoaderReferenceTaskTest {

    @Test
    void aRunThatReachesTheEulaCheckStarted() {
        QuiltLoaderReferenceTask.Outcome outcome = QuiltLoaderReferenceTask.Outcome.of(
                "[main/INFO]: Loading 11 mods:\n[Server thread/WARN]: Failed to load eula.txt\n"
                        + "[Server thread/INFO]: You need to agree to the EULA in order to run the server.\n",
                true,
                "");

        assertTrue(outcome.started());
        assertEquals("STARTS\tloads 11 mods, reaches the EULA check", outcome.line());
    }

    @Test
    void aRunThatStopsKeepsQuiltLoadersErrorLines() {
        QuiltLoaderReferenceTask.Outcome outcome = QuiltLoaderReferenceTask.Outcome.of(
                "[main/INFO]: Loading Minecraft 26.2 with Quilt Loader 0.31.0-beta.4\n"
                        + "[main/ERROR]: Incompatible mod set!\n"
                        + "Unhandled game exception\n",
                true,
                "Mod 'Example' (example) 1.0.0 requires any version of quilt_resource_loader, which is missing!\n");

        assertFalse(outcome.started());
        assertEquals(
                "STOPS\tstops before the EULA check: [main/ERROR]: Incompatible mod set! | Unhandled game exception"
                        + " | Mod 'Example' (example) 1.0.0 requires any version of quilt_resource_loader, which is"
                        + " missing!",
                outcome.line());
    }

    @Test
    void aRunKilledAfterTheTimeoutSaysSo() {
        QuiltLoaderReferenceTask.Outcome outcome = QuiltLoaderReferenceTask.Outcome.of("", false, "");

        assertEquals("STOPS\tkilled after the timeout: no error line", outcome.line());
    }
}
