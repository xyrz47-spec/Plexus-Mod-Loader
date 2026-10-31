package dev.plexusmodloader.compat.fabric.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

/**
 * Mixin's log lines in the game log, through SLF4J. Mixin formats with {@code {}} placeholders like SLF4J, and passes
 * a throwable as the last argument, which SLF4J prints with its stack trace.
 */
final class MixinLog extends LoggerAdapterAbstract {

    private static final Map<String, MixinLog> LOGGERS = new ConcurrentHashMap<>();

    private final Logger logger;

    private MixinLog(String name) {
        super(name);
        this.logger = LoggerFactory.getLogger(name.equals("mixin") ? "Mixin" : "Mixin/" + name);
    }

    static MixinLog get(String name) {
        return LOGGERS.computeIfAbsent(name, MixinLog::new);
    }

    @Override
    public String getType() {
        return "SLF4J (Plexus Mod Loader)";
    }

    @Override
    public void catching(Level level, Throwable t) {
        log(level, "Catching", t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        switch (level) {
            case FATAL, ERROR -> logger.error(message, params);
            case WARN -> logger.warn(message, params);
            case INFO -> logger.info(message, params);
            case DEBUG -> logger.debug(message, params);
            case TRACE -> logger.trace(message, params);
        }
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        switch (level) {
            case FATAL, ERROR -> logger.error(message, t);
            case WARN -> logger.warn(message, t);
            case INFO -> logger.info(message, t);
            case DEBUG -> logger.debug(message, t);
            case TRACE -> logger.trace(message, t);
        }
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        logger.debug("Throwing", t);
        return t;
    }
}
