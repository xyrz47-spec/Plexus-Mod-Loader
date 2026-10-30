package dev.plexusmodloader.compat.fabric.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributeBuilderBridgeTest {

    @Test
    void wrapUpdatesTargetMapOnPutAndPutAll() {
        Map<String, Integer> target = new HashMap<>();
        ImmutableMap.Builder<String, Integer> builder = AttributeBuilderBridge.wrap(target);

        assertNotNull(builder);

        builder.put("alpha", 1);
        assertEquals(Map.of("alpha", 1), target);

        builder.putAll(Map.of("beta", 2, "gamma", 3));
        assertEquals(Map.of("alpha", 1, "beta", 2, "gamma", 3), target);

        ImmutableMap<String, Integer> built = builder.build();
        assertEquals(Map.of("alpha", 1, "beta", 2, "gamma", 3), built);
    }
}
