package com.example.twinuser;

import com.example.twin.TwinConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Needs twin-mod: sees it in Fabric Loader's mod list and uses its shared class, as Cloth Config's users do. */
public final class TwinUser implements ModInitializer {

    @Override
    public void onInitialize() {
        TwinConfig.register("twin_user", "Fabric twin user's");
        System.out.println("[twin_user] isModLoaded(\"twin-mod\") = "
                + FabricLoader.getInstance().isModLoaded("twin-mod")
                + "; isModLoaded(\"twin-mod-api\") = "
                + FabricLoader.getInstance().isModLoaded("twin-mod-api"));
    }
}
