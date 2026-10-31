package dev.plexusmodloader.compat.fabric.mixin;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Bridges Fabric's {@code AttributeSupplierBuilderAccessor.getBuilder()} to the host loader's (Forge/NeoForge)
 * {@code Map} field on {@code AttributeSupplier.Builder}.
 *
 * <p>The game class loader defines its own copy of this class ({@link #classFile}): {@code wrap} returns a Guava type,
 * and the game's code must link to the game's Guava (the host's), not to the copy Plexus Mod Loader's loader sees. A call
 * from the game to this loader's class failed with a loader constraint violation on {@code ImmutableMap$Builder}.
 */
public final class AttributeBuilderBridge {

    private static final String NAME = AttributeBuilderBridge.class.getName();

    private AttributeBuilderBridge() {}

    /**
     * The class file of this class or of its nested builder, for the game class loader to define; {@code null} for any
     * other name.
     *
     * @param binaryName binary class name, such as {@code dev.plexusmodloader.compat.fabric.mixin.AttributeBuilderBridge}
     * @throws UncheckedIOException when Plexus Mod Loader's own jar cannot be read
     */
    public static byte[] classFile(String binaryName) {
        if (!binaryName.equals(NAME) && !binaryName.equals(NAME + "$ForwardingBuilder")) {
            return null;
        }
        String resource = binaryName.substring(binaryName.lastIndexOf('.') + 1) + ".class";
        try (InputStream input = AttributeBuilderBridge.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new UncheckedIOException(new IOException(resource + " is not next to " + NAME));
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> ImmutableMap.Builder<K, V> wrap(Map<?, ?> target) {
        return new ForwardingBuilder<>((Map<K, V>) target);
    }

    private static final class ForwardingBuilder<K, V> extends ImmutableMap.Builder<K, V> {
        private final Map<K, V> target;

        ForwardingBuilder(Map<K, V> target) {
            this.target = target;
        }

        @Override
        public ImmutableMap.Builder<K, V> put(K key, V value) {
            if (target != null) {
                target.put(key, value);
            }
            return super.put(key, value);
        }

        @Override
        public ImmutableMap.Builder<K, V> put(Map.Entry<? extends K, ? extends V> entry) {
            if (target != null) {
                target.put(entry.getKey(), entry.getValue());
            }
            return super.put(entry);
        }

        @Override
        public ImmutableMap.Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
            if (target != null) {
                target.putAll(map);
            }
            return super.putAll(map);
        }

        @Override
        public ImmutableMap.Builder<K, V> putAll(Iterable<? extends Map.Entry<? extends K, ? extends V>> entries) {
            if (target != null) {
                for (Map.Entry<? extends K, ? extends V> entry : entries) {
                    target.put(entry.getKey(), entry.getValue());
                }
            }
            return super.putAll(entries);
        }
    }
}
