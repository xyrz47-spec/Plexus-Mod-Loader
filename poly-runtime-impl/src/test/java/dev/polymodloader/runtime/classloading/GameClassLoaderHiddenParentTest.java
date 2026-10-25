package dev.polymodloader.runtime.classloading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.polymodloader.internal.transform.engine.ClassTransformExecutor;
import dev.polymodloader.internal.transform.engine.TransformPlan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A loader's API that Poly Mod Loader's class path carries for its bridge (Fabric Loader's) is not visible to game and
 * mod code in a launch where that loader runs no mods, as under the loader that does run (NeoForge has no Fabric
 * Loader). Multi-loader libraries tell the loader by such classes: Yumi, which Lambda Dynamic Lights uses, took the
 * NeoForge client for a Fabric one and looked for its configuration file through Fabric Loader (2026-10-24).
 */
class GameClassLoaderHiddenParentTest {

    @Test
    void hiddenPrefixesAreNotTakenFromTheParent() throws Exception {
        ClassTransformExecutor executor =
                new ClassTransformExecutor(new TransformPlan(Map.of(), List.of()), Optional.empty(), entry -> {});
        try (GameClassLoader loader = new GameClassLoader(List.of(), getClass().getClassLoader(), executor)) {
            assertSame(Test.class, loader.loadClass(Test.class.getName()), "visible before hiding");

            loader.hideFromParent(List.of("org/junit/jupiter/"));

            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.junit.jupiter.api.Assertions"));
            assertNull(loader.getResource("org/junit/jupiter/api/Assertions.class"));
            assertFalse(loader.getResources("org/junit/jupiter/api/Assertions.class")
                    .hasMoreElements());
            // Other parent classes stay visible.
            assertNotNull(loader.loadClass("org.opentest4j.AssertionFailedError"));
        }
    }
}
