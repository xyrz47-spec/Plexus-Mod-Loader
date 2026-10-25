package com.example.compat.adapteruser;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Has no public constructor: only the poly_test adapter, through create(), can make it. */
public final class AdapterUser implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("fabric_adapter_user");

    private AdapterUser() {}

    public static AdapterUser create() {
        return new AdapterUser();
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[fabric_adapter_user] created by the poly_test language adapter");
    }
}
