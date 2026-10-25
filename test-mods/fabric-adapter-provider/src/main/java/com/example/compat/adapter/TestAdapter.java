package com.example.compat.adapter;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/** Creates an entrypoint by calling the static {@code create()} of the named class, like Kotlin's object adapter. */
public final class TestAdapter implements LanguageAdapter {

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        try {
            Class<?> owner = Class.forName(value, true, TestAdapter.class.getClassLoader());
            return type.cast(owner.getMethod("create").invoke(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new LanguageAdapterException("poly_test adapter cannot create " + value, e);
        }
    }
}
