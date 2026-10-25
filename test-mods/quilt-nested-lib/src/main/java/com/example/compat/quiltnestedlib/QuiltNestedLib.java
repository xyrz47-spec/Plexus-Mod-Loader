package com.example.compat.quiltnestedlib;

import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import org.quiltmc.loader.api.QuiltLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ready once its entrypoint ran; reports where Quilt Loader's API says it comes from (inside quilt-hello's jar). */
public final class QuiltNestedLib implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_nested_lib");
    private static volatile boolean ready;

    public static String status() {
        return ready ? "ready" : "not ready";
    }

    @Override
    public void onInitialize() {
        ready = true;
        List<Path> source = QuiltLoader.getModContainer("quilt_nested_lib")
                .orElseThrow()
                .getSourcePaths()
                .getFirst();
        LOGGER.info(
                "[quilt_nested_lib] initialised; source path has {} part(s), the last is {}",
                source.size(),
                source.getLast().toString().replace('\\', '/'));
    }
}
