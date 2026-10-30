package dev.plexusmodloader.compat.fabric.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Mixin's global properties, kept in {@link MixinLaunch#properties} as Fabric Loader keeps them in a map. Found by
 * Mixin through {@link java.util.ServiceLoader}; Mixin's own two services need LaunchWrapper or ModLauncher, which
 * Poly Mod Loader does not have, so this is the one that loads.
 */
public final class PlexusMixinGlobalProperties implements IGlobalPropertyService {

    private record Key(String name) implements IPropertyKey {
        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) MixinLaunch.current().properties.get(name(key));
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            MixinLaunch.current().properties.remove(name(key));
        } else {
            MixinLaunch.current().properties.put(name(key), value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) MixinLaunch.current().properties.getOrDefault(name(key), defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = MixinLaunch.current().properties.get(name(key));
        return value != null ? value.toString() : defaultValue;
    }

    private static String name(IPropertyKey key) {
        return ((Key) key).name();
    }
}
