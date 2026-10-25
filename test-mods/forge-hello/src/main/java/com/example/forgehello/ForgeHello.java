package com.example.forgehello;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Forge mod written exactly as for MinecraftForge: FML constructs it and runs its mod bus listeners. */
@Mod(ForgeHello.MOD_ID)
public final class ForgeHello {

    static final String MOD_ID = "forge_hello";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final RegistryObject<Item> HELLO_ITEM =
            ITEMS.register("hello_item", () -> new Item(new Item.Properties().setId(ITEMS.key("hello_item"))));

    public ForgeHello(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        ITEMS.register(modBus);
        FMLCommonSetupEvent.getBus(modBus).addListener(this::commonSetup);
        LOGGER.info(
                "[forge_hello] Constructed by FML in module {}",
                getClass().getModule().getName());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(
                "[forge_hello] Common setup: item {} is registered", BuiltInRegistries.ITEM.getKey(HELLO_ITEM.get()));
        // How Forge mods ask whether another mod is installed (Physics Mod: getModFileById(id) != null). With mods of
        // other loaders in the same game, FML's mod list answers for them too (ADR-0025).
        LOGGER.info("[forge_hello] ModList.isLoaded(\"fabric_lib\") = {}", ModList.isLoaded("fabric_lib"));
        IModFileInfo file = ModList.getModFileById("fabric_lib");
        LOGGER.info(
                "[forge_hello] ModList.getModFileById(\"fabric_lib\") = {}",
                file == null
                        ? "null"
                        : file.getMods().getFirst().getDisplayName() + " " + file.versionString() + " (mod "
                                + file.getMods().getFirst().getModId() + ")");
        IModFileInfo alias = ModList.getModFileById("fabric_lib_alias");
        LOGGER.info(
                "[forge_hello] ModList.getModFileById(\"fabric_lib_alias\") = {}",
                alias == null ? "null" : "mod " + alias.getMods().getFirst().getModId());
        LOGGER.info(
                "[forge_hello] Poly mod minimal_native_mod: isLoaded = {}; getModContainerById(\"fabric_lib\") present"
                        + " = {}",
                ModList.isLoaded("minimal_native_mod"),
                ModList.getModContainerById("fabric_lib").isPresent());
    }
}
