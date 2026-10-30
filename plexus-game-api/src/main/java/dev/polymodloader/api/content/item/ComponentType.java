package dev.polymodloader.api.content.item;

import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * The value type of an {@link ItemComponentKey}.
 *
 * @param <T> Java type of the value
 */
@ApiStability(StabilityLevel.PREVIEW)
public final class ComponentType<T> {

    /** A whole number. */
    public static final ComponentType<Integer> INTEGER = new ComponentType<>("integer", Integer.class);

    /** {@code true} or {@code false}. */
    public static final ComponentType<Boolean> BOOLEAN = new ComponentType<>("boolean", Boolean.class);

    /** Text. */
    public static final ComponentType<String> STRING = new ComponentType<>("string", String.class);

    /** A {@link DataTree}; stored and returned as copies. */
    public static final ComponentType<DataTree> TREE = new ComponentType<>("tree", DataTree.class);

    private final String name;
    private final Class<T> valueClass;

    private ComponentType(String name, Class<T> valueClass) {
        this.name = name;
        this.valueClass = valueClass;
    }

    /** Java type of the value. */
    public Class<T> valueClass() {
        return valueClass;
    }

    @Override
    public String toString() {
        return name;
    }
}
