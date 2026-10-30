package dev.plexusmodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class VerifiedGameRunTaskTest {

    private static final String OUTPUT = """
            [main/INFO]: [quilt_hello] main ran
            [main/INFO]: All 1 required tests passed
            """;

    @Test
    void outputWithEveryExpectedLineAndNoUnexpectedOneHasNoProblems() {
        assertEquals(
                List.of(),
                VerifiedGameRunTask.outputProblems(
                        OUTPUT, List.of("[quilt_hello] main ran", "tests passed"), List.of("Quilt entrypoint ran")));
    }

    @Test
    void missingExpectedLinesAreProblems() {
        assertEquals(
                List.of("missing log line: [quilt_hello] init ran"),
                VerifiedGameRunTask.outputProblems(OUTPUT, List.of("[quilt_hello] init ran"), List.of()));
    }

    @Test
    void anUnexpectedTextIsAProblemNamingTheFirstLineThatHasIt() {
        String output = OUTPUT + "  [main/INFO]: [quilt_hello] main ran again\n";
        assertEquals(
                List.of("unexpected log line: [main/INFO]: [quilt_hello] main ran"),
                VerifiedGameRunTask.outputProblems(output, List.of(), List.of("main ran")));
    }
}
