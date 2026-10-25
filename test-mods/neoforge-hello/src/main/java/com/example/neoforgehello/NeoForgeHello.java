package com.example.neoforgehello;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A NeoForge mod written exactly as for NeoForge: FML constructs it and runs its mod bus listeners. */
@Mod(NeoForgeHello.MOD_ID)
public final class NeoForgeHello {

    static final String MOD_ID = "neoforge_hello";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredItem<Item> HELLO_ITEM = ITEMS.registerSimpleItem("hello_item");

    public NeoForgeHello(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        LOGGER.info(
                "[neoforge_hello] Constructed by FML in module {} ({})",
                getClass().getModule().getName(),
                container.getModId());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(
                "[neoforge_hello] Common setup: item {} is registered",
                BuiltInRegistries.ITEM.getKey(HELLO_ITEM.get()));
        LOGGER.info(
                "[neoforge_hello] ModList.isLoaded(\"neoforge\") = {}",
                ModList.get().isLoaded("neoforge"));
        // How NeoForge mods ask whether another mod is installed. With Poly Mods and other loaders' mods in the same
        // game, FML's mod list answers for them too (ADR-0025); a mod container stays FML's own.
        IModFileInfo polyMod = ModList.get().getModFileById("native_lib");
        LOGGER.info(
                "[neoforge_hello] Poly Mod native_lib: isLoaded = {}; getModFileById = {}; getModContainerById present"
                        + " = {}",
                ModList.get().isLoaded("native_lib"),
                polyMod == null
                        ? "null"
                        : polyMod.getMods().getFirst().getDisplayName() + " " + polyMod.versionString() + " (mod "
                                + polyMod.getMods().getFirst().getModId() + ")",
                ModList.get().getModContainerById("native_lib").isPresent());
        // How mods read files of their own jar (Yumi's mod file system, which Lambda Dynamic Lights finds its default
        // configuration through, visits the mod file's contents).
        var contents = ModList.get().getModFileById(MOD_ID).getFile().getContents();
        int[] files = {0};
        boolean[] ownToml = {false};
        contents.visitContent((path, resource) -> {
            files[0]++;
            ownToml[0] |= path.equals("META-INF/neoforge.mods.toml");
        });
        LOGGER.info(
                "[neoforge_hello] own jar contents: {} file(s) visited, neoforge.mods.toml visited: {}, containsFile: {}",
                files[0],
                ownToml[0],
                contents.containsFile("META-INF/neoforge.mods.toml"));
        // Multi-loader libraries (Yumi, which Lambda Dynamic Lights uses) tell the loader by which loader API classes
        // exist; under NeoForge, Fabric Loader's are not there.
        LOGGER.info(
                "[neoforge_hello] Fabric Loader API visible: {}", classExists("net.fabricmc.loader.api.FabricLoader"));
        LOGGER.info(
                "[neoforge_hello] ModList.isLoaded(\"not_installed_lib\") = {}; getModFileById = {}",
                ModList.get().isLoaded("not_installed_lib"),
                ModList.get().getModFileById("not_installed_lib"));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
