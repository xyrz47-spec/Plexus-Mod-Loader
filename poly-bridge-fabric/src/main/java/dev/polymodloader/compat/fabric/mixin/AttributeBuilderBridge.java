package dev.polymodloader.compat.fabric.mixin;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Bridges Fabric's {@code AttributeSupplierBuilderAccessor.getBuilder()} to the host loader's (Forge/NeoForge)
 * {@code Map} field on {@code AttributeSupplier.Builder}.
 */
public final class AttributeBuilderBridge {

    private AttributeBuilderBridge() {}

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
