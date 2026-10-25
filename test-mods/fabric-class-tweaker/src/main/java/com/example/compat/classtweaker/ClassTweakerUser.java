package com.example.compat.classtweaker;

import java.lang.reflect.Modifier;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;

/**
 * Reads a private field and calls a private method of Minecraft 26.2. Both only work when the mod's class
 * tweaker was applied; without it the JVM throws {@link IllegalAccessError}.
 */
public final class ClassTweakerUser implements ModInitializer {

    @Override
    public void onInitialize() {
        float[] table = Mth.SIN;
        String flag = SharedConstants.prefixDebugFlagName("CLASS_TWEAKER");
        boolean isFinal;
        try {
            isFinal = Modifier.isFinal(Mth.class.getDeclaredField("SIN").getModifiers());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Mth.SIN is missing", e);
        }
        System.out.println("[fabric_class_tweaker] Mth.SIN has " + table.length + " entries, debug flag " + flag
                + ", SIN final: " + isFinal);
    }
}
