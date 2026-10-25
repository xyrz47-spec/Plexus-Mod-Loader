package dev.polymodloader.api.content.item;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * A data component that mods attach to item stacks, such as a charge level or an owner's name. The
 * value is saved with the stack, copied with it, shown in {@code /give} syntax as
 * {@code <id>=<value>}, and synchronised to clients. Stacks with different values do not stack.
 * Obtain keys from {@link dev.polymodloader.api.content.ContentRegistrar#itemComponent}.
 *
 * @param id component ID, {@code <mod id>:<path>}
 * @param type value type
 * @param <T> Java type of the value
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ItemComponentKey<T>(ResourceId id, ComponentType<T> type) {

    public ItemComponentKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
