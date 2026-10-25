package net.fabricmc.loader.impl.util;

import dev.polymodloader.compat.fabric.loader.JvmEntrypointValue;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;

/**
 * Poly Mod Loader's stand-in for Fabric Loader's default language adapter, which {@code LanguageAdapter.getDefault()}
 * returns. A value is a class name ({@code a.Main}, created with its no-argument constructor), a static field
 * ({@code a.Main::INSTANCE}), or a method or constructor ({@code a.Main::<init>}) turned into the requested
 * functional interface ({@code a.Main::init}); an instance method is bound to a new instance made with the class's
 * no-argument constructor, as Fabric Loader 0.19.5 does ({@link JvmEntrypointValue}, shared with Quilt's stand-in).
 * Classes are loaded with the game class loader, as Fabric loads them with its target class loader.
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
                FabricLoaderImpl.INSTANCE.polyModLoaderRuntime().gameClassLoader(),
                (message, cause) -> message == null
                        ? new LanguageAdapterException(cause)
                        : new LanguageAdapterException(message, cause));
    }
}
