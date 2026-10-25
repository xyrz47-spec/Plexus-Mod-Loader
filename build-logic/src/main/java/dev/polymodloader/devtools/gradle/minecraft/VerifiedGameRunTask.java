package dev.polymodloader.devtools.gradle.minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs the game headlessly in a fresh directory and fails unless every expected log line appears,
 * no unexpected one does, and every expected file exists afterwards. Used for the dedicated server boot smoke test (which
 * stops at the EULA check, so the EULA is never accepted for the user) and for the world
 * create-and-save test on Mojang's game test server.
 */
@DisableCachingByDefault(because = "Runs the game")
public abstract class VerifiedGameRunTask extends RunMinecraftTask {

    /** Substrings that must each appear in the game output. */
    @Input
    public abstract ListProperty<String> getExpectedOutput();

    /**
     * Substrings that must not appear in the game output, such as the line of an entrypoint that must
     * not run or the start of a stack trace.
     */
    @Input
    public abstract ListProperty<String> getUnexpectedOutput();

    /** Paths relative to the run directory that must exist after the game exits. */
    @Input
    public abstract ListProperty<String> getExpectedFiles();

    public VerifiedGameRunTask() {
        getUnexpectedOutput().convention(List.of());
        getExpectedFiles().convention(List.of());
    }

    @Override
    public void exec() {
        Path runDirectory = getRunDirectory().get().getAsFile().toPath();
        deleteRecursively(runDirectory);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        setStandardOutput(new TeeOutputStream(System.out, captured));
        setErrorOutput(new TeeOutputStream(System.err, captured));
        super.exec();

        String output = captured.toString(StandardCharsets.UTF_8);
        Path outputLog = runDirectory.resolve("verified-run-output.log");
        writeString(outputLog, output);
        List<String> problems = new ArrayList<>(
                outputProblems(output, getExpectedOutput().get(), getUnexpectedOutput().get()));
        for (String expected : getExpectedFiles().get()) {
            if (!Files.exists(runDirectory.resolve(expected))) {
                problems.add("missing file: " + expected);
            }
        }
        if (!problems.isEmpty()) {
            throw new GradleException(getName() + " failed: " + problems + ". Full output: " + outputLog);
        }
        getLogger()
                .lifecycle(
                        "{} passed ({} log lines, {} files verified)",
                        getName(),
                        getExpectedOutput().get().size(),
                        getExpectedFiles().get().size());
    }

    /**
     * What is wrong with {@code output}: each expected substring it lacks, and each unexpected
     * substring it has, with the first line that has it.
     */
    static List<String> outputProblems(String output, List<String> expected, List<String> unexpected) {
        List<String> problems = new ArrayList<>();
        for (String line : expected) {
            if (!output.contains(line)) {
                problems.add("missing log line: " + line);
            }
        }
        for (String text : unexpected) {
            output.lines()
                    .filter(line -> line.contains(text))
                    .findFirst()
                    .ifPresent(line -> problems.add("unexpected log line: " + line.strip()));
        }
        return problems;
    }

    static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean " + directory, e);
        }
    }

    private static void writeString(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
