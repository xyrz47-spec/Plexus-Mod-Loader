package com.example.forgeat;

import java.lang.reflect.Modifier;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Reads a private field and calls a private method of Minecraft 26.2. Both only work when the mod's access transformer
 * was applied; without it the JVM throws {@link IllegalAccessError}.
 */
@Mod("forge_at")
public final class ForgeAccessTransformerUser {

    public ForgeAccessTransformerUser(FMLJavaModLoadingContext context) {
        float[] table = Mth.SIN;
        String flag = SharedConstants.prefixDebugFlagName("ACCESS_TRANSFORMER");
        boolean isFinal;
        try {
            isFinal = Modifier.isFinal(Mth.class.getDeclaredField("SIN").getModifiers());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Mth.SIN is missing", e);
        }
        System.out.println(
                "[forge_at] Mth.SIN has " + table.length + " entries, debug flag " + flag + ", SIN final: " + isFinal);
    }
}
