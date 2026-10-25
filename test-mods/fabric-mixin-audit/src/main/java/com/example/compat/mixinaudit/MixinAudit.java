package com.example.compat.mixinaudit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;

/**
 * Diagnosis harness, not a Poly Mod Loader feature (Quilt full support Q6). With {@code -Dpoly.mixinAudit=true} it
 * loads, without initialising, every class a Mixin configuration of this side targets, so Mixin applies every mixin of
 * every installed mod there, and reports each class Mixin fails; then it stops the JVM before the game's main class
 * runs, so a client audit opens no window. Without the property it does nothing.
 */
public final class MixinAudit implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        if (!Boolean.getBoolean("poly.mixinAudit")) {
            return;
        }
        ClassLoader game = Thread.currentThread().getContextClassLoader();
        try {
            // The first game class Mixin transforms makes it select and prepare the configurations of this phase.
            Class.forName("net.minecraft.SharedConstants", false, game);
        } catch (ReflectiveOperationException | LinkageError e) {
            System.out.println("[poly_mixin_audit] could not load net.minecraft.SharedConstants: " + e);
        }
        Map<String, List<String>> targets = new TreeMap<>();
        int configs = 0;
        for (IMixinConfig mixinConfig : preparedConfigs()) {
            configs++;
            for (String target : mixinConfig.getTargets()) {
                targets.computeIfAbsent(target.replace('/', '.'), key -> new ArrayList<>())
                        .add(mixinConfig.getName() + (mixinConfig.isRequired() ? " (required)" : ""));
            }
        }
        int failed = 0;
        int absent = 0;
        for (Map.Entry<String, List<String>> target : targets.entrySet()) {
            try {
                Class.forName(target.getKey(), false, game);
            } catch (ClassNotFoundException e) {
                absent++;
            } catch (Throwable e) {
                if (isMixinFailure(e)) {
                    failed++;
                    System.out.println("[poly_mixin_audit] FAILED " + target.getKey() + " (configurations "
                            + new TreeSet<>(target.getValue()) + "): " + rootMessage(e));
                } else {
                    // A target whose own superclass or interface is absent (an optional integration) never loads in
                    // the game either.
                    absent++;
                }
            }
        }
        System.out.println("[poly_mixin_audit] audited " + targets.size() + " target class(es) of " + configs
                + " Mixin configuration(s): " + failed + " failed, " + absent + " not present");
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }

    /**
     * The configurations Mixin's transformer has prepared ({@code MixinProcessor.configs}; {@code Mixins.getConfigs()}
     * holds only those not yet handed to it). Mixin internals, read reflectively: this is a diagnosis harness.
     */
    private static List<IMixinConfig> preparedConfigs() {
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            Field processorField = transformer.getClass().getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);
            Field configsField = processor.getClass().getDeclaredField("configs");
            configsField.setAccessible(true);
            List<IMixinConfig> configs = new ArrayList<>();
            for (Object config : (List<?>) configsField.get(processor)) {
                configs.add((IMixinConfig) config);
            }
            return configs;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This Mixin build has no MixinTransformer.processor.configs", e);
        }
    }

    private static boolean isMixinFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.startsWith("org.spongepowered.") || name.endsWith("BridgeTransformException")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
