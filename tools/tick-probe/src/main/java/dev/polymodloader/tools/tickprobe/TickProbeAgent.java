package dev.polymodloader.tools.tickprobe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Agent entry point. Arguments: {@code out=<file>,target=<owner>.<method><descriptor>}, for example
 * {@code out=ticks.txt,target=net/minecraft/gametest/framework/GameTestServer.tickServer(Ljava/util/function/BooleanSupplier;)V}.
 * The target class is wrapped by {@link TickWrapper} when any class loader defines it; the durations
 * are written to {@code out} when the JVM exits. With {@code control=stdin} the agent also takes the
 * commands {@code mark} and {@code dump} on standard input. With {@code jfr=true} every timed call is also a Java Flight
 * Recorder event ({@link TimedCallEvent}), so a profile can be split into slow and ordinary calls. A target that never loads, or that has no such
 * method, is reported on standard error and leaves an empty file, so a broken run cannot pass as one
 * with fast ticks.
 */
public final class TickProbeAgent {

    private TickProbeAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        Map<String, String> options = parse(arguments);
        Path out = Path.of(require(options, "out"));
        String target = require(options, "target");
        int dot = target.indexOf('.');
        int paren = target.indexOf('(');
        if (dot < 0 || paren < dot) {
            throw new IllegalArgumentException("target must look like owner.method(descriptor), got " + target);
        }
        String owner = target.substring(0, dot);
        String method = target.substring(dot + 1, paren);
        String descriptor = target.substring(paren);
        // The recorder must be the same class for every loader, so it alone goes on the boot class
        // path; the agent's own classes stay with the application class loader.
        try {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(recorderJar().toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot put the tick recorder on the boot class path", e);
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(
                    ClassLoader loader,
                    String className,
                    Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) {
                if (!owner.equals(className)) {
                    return null;
                }
                try {
                    byte[] wrapped = TickWrapper.wrap(classfileBuffer, method, descriptor).orElse(null);
                    System.err.println(wrapped == null
                            ? "[tick-probe] " + owner + " has no method " + method + descriptor + "; nothing is timed"
                            : "[tick-probe] timing " + owner + "." + method + descriptor);
                    return wrapped;
                } catch (RuntimeException e) {
                    // The JVM would drop this silently; the run must show that timing is missing.
                    System.err.println("[tick-probe] cannot wrap " + owner + ": " + e);
                    return null;
                }
            }
        });
        String jfr = options.getOrDefault("jfr", "false");
        switch (jfr) {
            case "true" -> RecorderAccess.recordEvents();
            case "false" -> {}
            default -> throw new IllegalArgumentException("jfr must be true or false, got " + jfr);
        }
        AtomicBoolean written = new AtomicBoolean();
        String control = options.getOrDefault("control", "none");
        switch (control) {
            case "stdin" -> Thread.ofPlatform()
                    .daemon()
                    .name("tick-probe-control")
                    .start(() -> readCommands(out, written));
            case "none" -> {}
            default -> throw new IllegalArgumentException("control must be stdin or none, got " + control);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeOnce(out, written), "tick-probe-writer"));
    }

    /**
     * Reads commands, one per line, from standard input: {@code mark} drops every call recorded so
     * far from the output, and {@code dump} writes the output file now. A measuring task uses them to
     * time a window of a run whose process it then stops without a normal exit, as the client needs.
     */
    private static void readCommands(Path out, AtomicBoolean written) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                switch (line.trim()) {
                    case "mark" -> System.err.println("[tick-probe] mark after " + RecorderAccess.mark() + " calls");
                    case "dump" -> writeOnce(out, written);
                    case "" -> {}
                    default -> System.err.println("[tick-probe] unknown command: " + line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("[tick-probe] cannot read commands: " + e);
        }
    }

    /** Writes the output file unless a {@code dump} already did, so the file keeps the dumped window. */
    private static void writeOnce(Path out, AtomicBoolean written) {
        if (!written.compareAndSet(false, true)) {
            return;
        }
        try {
            int calls = RecorderAccess.write(out);
            System.err.println("[tick-probe] wrote " + calls + " calls to " + out);
        } catch (IOException e) {
            System.err.println("[tick-probe] cannot write " + out + ": " + e);
        }
    }

    /** A temporary jar holding only {@code TickRecorder} and its event, copied from this agent's own jar. */
    private static Path recorderJar() throws IOException {
        Path jar = Files.createTempFile("tick-recorder", ".jar");
        jar.toFile().deleteOnExit();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : List.of(
                    "dev/polymodloader/tools/tickprobe/TickRecorder.class",
                    "dev/polymodloader/tools/tickprobe/TimedCallEvent.class")) {
                byte[] bytes;
                try (InputStream input = TickProbeAgent.class.getClassLoader().getResourceAsStream(entry)) {
                    if (input == null) {
                        throw new IOException(entry + " is missing from the tick probe jar");
                    }
                    bytes = input.readAllBytes();
                }
                output.putNextEntry(new JarEntry(entry));
                output.write(bytes);
                output.closeEntry();
            }
        }
        return jar;
    }

    private static Map<String, String> parse(String arguments) {
        Map<String, String> options = new LinkedHashMap<>();
        if (arguments == null) {
            return options;
        }
        for (String part : arguments.split(",")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                options.put(part.substring(0, equals), part.substring(equals + 1));
            }
        }
        return options;
    }

    private static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tick probe argument " + name + "=... is required");
        }
        return value;
    }

    /** Touches {@link TickRecorder} only after the jar is on the boot class path. */
    private static final class RecorderAccess {

        private RecorderAccess() {}

        static int write(Path out) throws IOException {
            return TickRecorder.write(out);
        }

        static int mark() {
            return TickRecorder.mark();
        }

        static void recordEvents() {
            TickRecorder.recordEvents();
        }
    }
}
