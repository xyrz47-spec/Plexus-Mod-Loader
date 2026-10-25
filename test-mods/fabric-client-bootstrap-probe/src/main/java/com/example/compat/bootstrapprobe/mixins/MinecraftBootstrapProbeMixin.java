package com.example.compat.bootstrapprobe.mixins;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the client in {@code Minecraft.<init>} right before it creates the game window ({@code new Window}, once in
 * 26.1, 26.1.2 and 26.2, javap). Minecraft's mod initialization point comes earlier in the constructor (its {@code
 * Thread.currentThread()} call, where the mod loaders' initialization and Fabric API's {@code afterModInit}, which freezes
 * the built-in registries, run), so the client's mod initialization and the constructor up to the window can be checked
 * without a window (ADR-0026 38). Only with {@code -Dfabric_client_bootstrap_probe.stop=true}.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftBootstrapProbeMixin {

    @Unique
    private static final boolean STOP = Boolean.getBoolean("fabric_client_bootstrap_probe.stop");

    @Inject(method = "<init>", at = @At(value = "NEW", target = "com/mojang/blaze3d/platform/Window"))
    private void fabric_client_bootstrap_probe$beforeWindow(CallbackInfo info) {
        if (!STOP) {
            return;
        }
        System.out.println("[fabric_client_bootstrap_probe] Minecraft.<init> reached the game window after mod"
                + " initialization; registries frozen: root = " + frozen(BuiltInRegistries.REGISTRY) + ", item = "
                + frozen(BuiltInRegistries.ITEM) + ", block = " + frozen(BuiltInRegistries.BLOCK) + "; items: "
                + BuiltInRegistries.ITEM.size());
        System.out.flush();
        System.exit(0);
    }

    @Unique
    private static String frozen(Registry<?> registry) {
        if (!(registry instanceof MappedRegistry<?>)) {
            return "not a MappedRegistry";
        }
        try {
            Field field = MappedRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            return String.valueOf(field.getBoolean(registry));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unreadable (" + e + ")";
        }
    }
}
