package com.example.compat.mixin;

import com.example.compat.mixin.mixins.MthAccessor;
import com.example.compat.mixin.mixins.SharedConstantsInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Uses what the mod's mixins changed. Every line it prints depends on a mixin having been applied: without Mixin
 * the invoker and accessor interfaces are not implemented and the calculator returns 5 and 20.
 */
public final class MixinUser implements ModInitializer {

    @Override
    public void onInitialize() {
        String changed = SharedConstantsInvoker.callPrefixDebugFlagName("POLY_MIXIN");
        String unchanged = SharedConstantsInvoker.callPrefixDebugFlagName("PLAIN");
        System.out.println("[fabric_mixin] @Invoker and @Inject on SharedConstants: " + changed + ", " + unchanged);
        // Lithium's sine_lut mixin drops the table (under Fabric Loader too), so it may be null next to Lithium.
        float[] sin = MthAccessor.getSin();
        System.out.println("[fabric_mixin] @Accessor: Mth.SIN "
                + (sin == null ? "was removed by another mod's mixin" : "has " + sin.length + " entries"));
        System.out.println("[fabric_mixin] @ModifyArg on a mod class: sum = " + Calculator.sum()
                + "; MixinExtras @WrapOperation: product = " + Calculator.product());
        // Looked up by name: this test mod compiles against a few game stubs, not the game.
        java.lang.reflect.Field stub = enumConstant("net.minecraft.world.Difficulty", "POLY_MIXIN_TEST");
        System.out.println("[fabric_mixin] extend-enum: Difficulty.POLY_MIXIN_TEST "
                + (stub == null ? "is missing" : "is an enum constant: " + stub.isEnumConstant()));
        System.out.println("[fabric_mixin] mixinextras is loaded: "
                + FabricLoader.getInstance().isModLoaded("mixinextras"));
    }

    private static java.lang.reflect.Field enumConstant(String type, String name) {
        try {
            return Class.forName(type).getField(name);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
