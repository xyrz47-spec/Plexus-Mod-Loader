package dev.polymodloader.tools.tickprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TickWrapperTest {

    /** A tick method with a loop, so the copied body keeps its own stack map frames. */
    public static class Sample {
        public int ticks;

        protected void tick(BooleanSupplier hasTime) {
            for (int i = 0; i < 3 && hasTime.getAsBoolean(); i++) {
                ticks++;
            }
        }

        public long scaled(long value, int factor) {
            return value * factor;
        }
    }

    @Test
    void wrappedMethodRunsTheOriginalBodyAndRecordsEachCall(@TempDir Path directory) throws Exception {
        // Calls recorded by other tests stay out of the written file.
        int before = TickRecorder.mark();
        Class<?> type = load(TickWrapper.wrap(bytes(), "tick", "(Ljava/util/function/BooleanSupplier;)V")
                .orElseThrow());
        Object sample = type.getConstructor().newInstance();
        Method tick = type.getDeclaredMethod("tick", BooleanSupplier.class);
        tick.setAccessible(true);

        BooleanSupplier always = () -> true;
        tick.invoke(sample, always);
        tick.invoke(sample, always);

        assertEquals(6, type.getField("ticks").getInt(sample), "the original body ran twice");
        assertEquals(before + 2, TickRecorder.durations().length, "each call was recorded");
        assertTrue(Modifier.isProtected(tick.getModifiers()), "the wrapper keeps the original access");
        Method renamed = type.getDeclaredMethod("tick" + TickWrapper.RENAMED_SUFFIX, BooleanSupplier.class);
        assertTrue(Modifier.isPrivate(renamed.getModifiers()));

        Path out = directory.resolve("ticks.txt");
        assertEquals(2, TickRecorder.write(out));
        List<String> lines = Files.readAllLines(out);
        assertEquals(2, lines.size(), "only the calls since the mark are written");
        assertTrue(lines.stream().allMatch(line -> Long.parseLong(line) >= 0));
        assertTrue(Files.notExists(directory.resolve("ticks.txt.partial")), "the file is moved into place");

        TickRecorder.mark();
        tick.invoke(sample, always);
        assertEquals(1, TickRecorder.write(out), "a later mark starts a new window");
    }

    @Test
    void eventsCarryEachCallsDurationWhenTurnedOn(@TempDir Path directory) throws Exception {
        Path dump = directory.resolve("calls.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("poly.TickProbe.Call");
            recording.start();
            TickRecorder.recordEvents();
            TickRecorder.record(System.nanoTime() - 5_000_000L);
            recording.stop();
            recording.dump(dump);
        }
        List<RecordedEvent> events = RecordingFile.readAllEvents(dump);
        assertEquals(1, events.size(), "one event per recorded call");
        RecordedEvent event = events.getFirst();
        long recorded = TickRecorder.durations()[(int) event.getLong("index")];
        assertEquals(recorded, event.getDuration("callNanos").toNanos(), "the event has the recorded duration");
        assertTrue(recorded >= 5_000_000L);
    }

    @Test
    void valuesAndWideArgumentsPassThrough() throws Exception {
        Class<?> type = load(TickWrapper.wrap(bytes(), "scaled", "(JI)J").orElseThrow());
        Object sample = type.getConstructor().newInstance();

        assertEquals(42L, type.getMethod("scaled", long.class, int.class).invoke(sample, 21L, 2));
    }

    @Test
    void missingMethodsAreNotWrapped() throws IOException {
        assertTrue(TickWrapper.wrap(bytes(), "tick", "()V").isEmpty());
        assertTrue(TickWrapper.wrap(bytes(), "absent", "(Ljava/util/function/BooleanSupplier;)V").isEmpty());
    }

    private static byte[] bytes() throws IOException {
        String resource = Sample.class.getName().replace('.', '/') + ".class";
        try (InputStream input = TickWrapperTest.class.getClassLoader().getResourceAsStream(resource)) {
            return input.readAllBytes();
        }
    }

    private static Class<?> load(byte[] classFile) throws ClassNotFoundException {
        String name = Sample.class.getName();
        ClassLoader loader = new ClassLoader(TickWrapperTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(className)) {
                    if (!className.equals(name)) {
                        return super.loadClass(className, resolve);
                    }
                    Class<?> loaded = findLoadedClass(className);
                    return loaded != null ? loaded : defineClass(className, classFile, 0, classFile.length);
                }
            }
        };
        return loader.loadClass(name);
    }
}
