package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** night-config 3.7.4 (Forge 26.2-65.1.5's) leaves the writer of a new empty config open; the patch closes it. */
class NightConfigEmptyFilePatchTest {

    private static final String CONFIG_FORMAT = "com.electronwill.nightconfig.core.ConfigFormat";
    private static final String WRITER_SUPPLIER = "com.electronwill.nightconfig.core.utils.WriterSupplier";

    @TempDir
    Path directory;

    @Test
    void theRealConfigFormatLeavesTheWriterOpenAndThePatchClosesIt() throws Exception {
        TrackedWriter leaked = new TrackedWriter();
        initEmptyFile(new NightConfigLoader(false), leaked, null);
        assertFalse(leaked.closed, "night-config 3.7.4 is expected to leave the writer open");

        TrackedWriter closed = new TrackedWriter();
        initEmptyFile(new NightConfigLoader(true), closed, null);
        assertTrue(closed.closed);
    }

    @Test
    void theWriterIsClosedWhenWritingFailsAndTheFailureStays() throws Exception {
        TrackedWriter writer = new TrackedWriter();
        writer.failOnClose = true;
        IOException failure = new IOException("init failed");

        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class, () -> initEmptyFile(new NightConfigLoader(true), writer, failure));

        assertSame(failure, thrown.getCause());
        assertTrue(writer.closed);
        assertEquals("close failed", failure.getSuppressed()[0].getMessage());
    }

    /** What failed on Windows: replacing the new file right after creating it ({@code REPLACE_ATOMIC}). */
    @Test
    void aNewEmptyFileCanBeReplacedRightAway() throws Exception {
        Class<?> format = new NightConfigLoader(true).loadClass(CONFIG_FORMAT);
        Object instance = Proxy.newProxyInstance(format.getClassLoader(), new Class<?>[] {format}, defaults(null));
        Path file = directory.resolve("server.toml");
        Files.createFile(file);
        format.getMethod("initEmptyFile", Path.class).invoke(instance, file);
        Path replacement = Files.writeString(directory.resolve("server.toml.new.tmp"), "a = 1\n");

        Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        assertEquals("a = 1\n", Files.readString(file));
    }

    @Test
    void anotherClassOrAnAlreadyClosingConfigFormatIsLeftAsItIs() throws IOException {
        byte[] original = classBytes(CONFIG_FORMAT);
        byte[] patched = NightConfigEmptyFilePatch.apply(original);

        assertNotEquals(original.length, patched.length);
        assertArrayEquals(patched, NightConfigEmptyFilePatch.apply(patched));
        byte[] other = classBytes(WRITER_SUPPLIER);
        assertSame(other, NightConfigEmptyFilePatch.apply(other));
    }

    /** Calls the default {@code initEmptyFile(WriterSupplier)} of a {@code ConfigFormat} from {@code loader}. */
    private static void initEmptyFile(ClassLoader loader, Writer writer, IOException failure) throws Exception {
        Class<?> format = loader.loadClass(CONFIG_FORMAT);
        Class<?> supplierType = loader.loadClass(WRITER_SUPPLIER);
        Object instance = Proxy.newProxyInstance(loader, new Class<?>[] {format}, defaults(failure));
        Object supplier =
                Proxy.newProxyInstance(loader, new Class<?>[] {supplierType}, (proxy, method, arguments) -> writer);
        format.getMethod("initEmptyFile", supplierType).invoke(instance, supplier);
    }

    /** A {@code ConfigFormat} running its default methods; {@code initEmptyFile(Writer)} throws {@code failure}. */
    private static InvocationHandler defaults(IOException failure) {
        return (proxy, method, arguments) -> {
            if (failure != null && isInitWithWriter(method)) {
                throw failure;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, arguments);
            }
            throw new UnsupportedOperationException(method.getName());
        };
    }

    private static boolean isInitWithWriter(Method method) {
        return method.getName().equals("initEmptyFile")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == Writer.class;
    }

    private static byte[] classBytes(String className) throws IOException {
        try (InputStream input = NightConfigEmptyFilePatchTest.class
                .getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (input == null) {
                throw new IOException(className + " is not on the test class path");
            }
            return input.readAllBytes();
        }
    }

    /** Defines night-config's classes itself, {@code ConfigFormat} patched or as it is. */
    private static final class NightConfigLoader extends ClassLoader {

        private final boolean patched;

        NightConfigLoader(boolean patched) {
            super(NightConfigEmptyFilePatchTest.class.getClassLoader());
            this.patched = patched;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.electronwill.nightconfig.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes;
                    try {
                        bytes = classBytes(name);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                    if (patched && name.equals(CONFIG_FORMAT)) {
                        bytes = NightConfigEmptyFilePatch.apply(bytes);
                    }
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                return loaded;
            }
        }
    }

    private static final class TrackedWriter extends StringWriter {

        boolean closed;
        boolean failOnClose;

        @Override
        public void close() throws IOException {
            closed = true;
            if (failOnClose) {
                throw new IOException("close failed");
            }
        }
    }
}
