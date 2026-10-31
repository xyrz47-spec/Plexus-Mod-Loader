package org.quiltmc.loader.impl.util;

import dev.plexusmodloader.compat.fabric.loader.JvmEntrypointValue;
import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.LanguageAdapterException;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

/**
 * Plexus Mod Loader's stand-in for Quilt Loader's default language adapter, which {@code LanguageAdapter.getDefault()}
 * returns. Values use the JVM notation of RFC 0002 (a class, {@code Class::field}, {@code Class::method}) with the same
 * meaning as Fabric's default adapter and the same creation code ({@link JvmEntrypointValue}, ADR-0027 decision 4).
 * Classes are loaded with the game class loader.
 */
public final class DefaultLanguageAdapter implements LanguageAdapter {

    /** The instance {@code LanguageAdapter.getDefault()} returns. */
    public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

    private DefaultLanguageAdapter() {}

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        return JvmEntrypointValue.create(
                value,
                type,
                QuiltLoaderImpl.INSTANCE.plexusModLoaderRuntime().gameClassLoader(),
                (message, cause) -> message == null
                        ? new LanguageAdapterException(cause)
                        : new LanguageAdapterException(message, cause));
    }
}
