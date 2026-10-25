package com.example.twin.neoforge;

import com.example.twin.TwinConfig;
import net.neoforged.fml.common.Mod;

/** The NeoForge build's mod class: FML constructs it, and it registers the configuration, as Cloth Config's does. */
@Mod("twin_mod")
public final class TwinNeoForge {

    public TwinNeoForge() {
        TwinConfig.register("example", "NeoForge");
    }
}
