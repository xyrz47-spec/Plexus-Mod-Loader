package dev.polymodloader.tools.tickprobe;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Collects the duration of every timed call. Wrapped methods call {@link #record(long)}; the agent
 * writes the durations since the last {@link #mark()}, one nanosecond value per line, when it is told
 * to or when the JVM exits. The agent puts this class on the boot class path so every class loader,
 * Poly Mod Loader's game class loader included, sees the same one. With {@link #recordEvents()}, every call is also
 * committed as a {@link TimedCallEvent} for a Java Flight Recorder profile.
 */
public final class TickRecorder {

    private static long[] durations = new long[1 << 16];
    private static int count;
    private static int markedAt;
    private static volatile boolean events;

    private TickRecorder() {}

    /** Records the time since {@code startNanos} ({@link System#nanoTime()}). */
    public static synchronized void record(long startNanos) {
        long duration = System.nanoTime() - startNanos;
        if (count == durations.length) {
            durations = Arrays.copyOf(durations, durations.length * 2);
        }
        if (events) {
            commitEvent(duration, count);
        }
        durations[count++] = duration;
    }

    /** From now on, also commits every recorded call as a {@link TimedCallEvent} (agent argument {@code jfr=true}). */
    public static void recordEvents() {
        events = true;
    }

    // Separate, so TimedCallEvent (and jdk.jfr) is loaded only when events are on.
    private static void commitEvent(long duration, int index) {
        TimedCallEvent event = new TimedCallEvent();
        event.callNanos = duration;
        event.index = index;
        event.commit();
    }

    /** The durations recorded so far, in call order. */
    public static synchronized long[] durations() {
        return Arrays.copyOf(durations, count);
    }

    /** Makes {@link #write(Path)} leave out every call recorded so far; returns how many that is. */
    public static synchronized int mark() {
        markedAt = count;
        return markedAt;
    }

    /** The durations recorded since the last {@link #mark()}, or since the start. */
    public static synchronized long[] durationsSinceMark() {
        return Arrays.copyOfRange(durations, markedAt, count);
    }

    /**
     * Writes the durations since the last {@link #mark()} to {@code file}, one per line, and returns
     * how many were written. The file appears complete or not at all: it is written beside the target
     * and then moved into place.
     */
    public static int write(Path file) throws IOException {
        long[] snapshot = durationsSinceMark();
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try (Writer writer = Files.newBufferedWriter(partial, StandardCharsets.UTF_8)) {
            for (long duration : snapshot) {
                writer.write(Long.toString(duration));
                writer.write('\n');
            }
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return snapshot.length;
    }
}
