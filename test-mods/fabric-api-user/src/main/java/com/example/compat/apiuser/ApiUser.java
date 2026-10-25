package com.example.compat.apiuser;

import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calls the parts of FabricLoader that mods use most and logs what it gets. */
public final class ApiUser implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_api_user");

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        LOGGER.info(
                "[fabric_api_user] environment {}, Minecraft {}",
                loader.getEnvironmentType(),
                loader.getModContainer("minecraft")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString());
        ModContainer nativeLib = loader.getModContainer("native_lib").orElseThrow();
        LOGGER.info(
                "[fabric_api_user] Poly Mod native_lib {} is loaded: {}",
                nativeLib.getMetadata().getVersion().getFriendlyString(),
                loader.isModLoaded("native_lib"));
        // A Forge mod in the same game (when Forge runs, ADR-0025): visible to Fabric mods as Fabric Loader's mod.
        LOGGER.info(
                "[fabric_api_user] Forge mod forge_hello is loaded: {} ({})",
                loader.isModLoaded("forge_hello"),
                loader.getModContainer("forge_hello")
                        .map(container -> container.getMetadata().getType() + " "
                                + container.getMetadata().getVersion().getFriendlyString() + ", "
                                + container.getRootPaths().size() + " root path(s)")
                        .orElse("no container"));
        int answer = loader.getModContainer("fabric_api_user")
                .orElseThrow()
                .getMetadata()
                .getCustomValue("fabric_api_user:settings")
                .getAsObject()
                .get("answer")
                .getAsNumber()
                .intValue();
        LOGGER.info("[fabric_api_user] custom value answer = {}", answer);
        @SuppressWarnings("rawtypes")
        List<Supplier> greetings = loader.getEntrypoints("fabric_api_user:greeting", Supplier.class);
        LOGGER.info(
                "[fabric_api_user] {} custom entrypoint(s): {}",
                greetings.size(),
                greetings.getFirst().get());
        boolean same = loader.getEntrypoints("main", ModInitializer.class).contains(this);
        LOGGER.info("[fabric_api_user] the main entrypoint FabricLoader returns is this instance: {}", same);
        loader.getObjectShare()
                .whenAvailable(
                        "fabric_api_user:answer",
                        (key, value) -> LOGGER.info("[fabric_api_user] object share delivered {} = {}", key, value));
        loader.getObjectShare().put("fabric_api_user:answer", answer);
        LOGGER.info("[fabric_api_user] config directory exists: {}", Files.isDirectory(loader.getConfigDir()));
    }
}
