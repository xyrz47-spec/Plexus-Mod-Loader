package dev.plexusmodloader.compat.quilt;

import java.util.concurrent.atomic.AtomicBoolean;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.log.LogHandler;
import org.quiltmc.loader.impl.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends Quilt Loader's log ({@code org.quiltmc.loader.impl.util.log.Log}, which the packed API classes write to) to
 * SLF4J under the logger {@code Quilt Loader}, so it reaches the game's log like Poly Mod Loader's own lines (ADR-0027
 * decision 4). Installed once per process ({@link #install}); what Quilt's built-in handler buffered before is
 * replayed.
 *
 * <p>Thread: any.
 */
final class QuiltLogForwarder implements LogHandler {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Logger LOGGER = LoggerFactory.getLogger(Log.NAME);

    private QuiltLogForwarder() {}

    /** Makes Quilt's {@code Log} write to SLF4J; does nothing after the first call. */
    static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            Log.init(new QuiltLogForwarder(), true);
        }
    }

    @Override
    public void log(
            long time,
            LogLevel level,
            LogCategory category,
            String message,
            Throwable exception,
            boolean fromReplay,
            boolean wasSuppressed) {
        String text = "[" + category + "] " + message;
        switch (level) {
            case ERROR -> LOGGER.error(text, exception);
            case WARN -> LOGGER.warn(text, exception);
            case INFO -> LOGGER.info(text, exception);
            case DEBUG -> LOGGER.debug(text, exception);
            case TRACE -> LOGGER.trace(text, exception);
        }
    }

    @Override
    public boolean shouldLog(LogLevel level, LogCategory category) {
        return switch (level) {
            case ERROR -> LOGGER.isErrorEnabled();
            case WARN -> LOGGER.isWarnEnabled();
            case INFO -> LOGGER.isInfoEnabled();
            case DEBUG -> LOGGER.isDebugEnabled();
            case TRACE -> LOGGER.isTraceEnabled();
        };
    }

    @Override
    public void close() {}
}
