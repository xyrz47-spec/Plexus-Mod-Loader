package dev.polymodloader.compat.fabric.mixin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.transformer.throwables.MixinTargetAlreadyLoadedException;

/**
 * Mixin's error handler in a launch where a host loader patched the game (ADR-0021 G8): a Fabric mod's mixin that does
 * not apply to the host's patched class is reported ({@code POLY-COMPAT-138}) and skipped, and the game goes on without
 * that part of the mod, instead of stopping. {@link HostPatchAdjustments} catches what it can before Mixin starts; this
 * catches the rest (injection points found by expressions or slices, members a patch removed). Errors of the host's own
 * mods keep Mixin's action, as under the host's own Mixin.
 *
 * <p>Mixin creates this class itself ({@code Mixins.registerErrorHandlerClass}), so the Fabric configurations it
 * handles are set beforehand ({@link #handle}). Thread: Mixin calls it from whichever thread loads a class.
 */
public final class HostedMixinErrors implements IMixinErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final Set<String> FABRIC_CONFIGS = ConcurrentHashMap.newKeySet();
    private static volatile String hostName = "";

    /** Handles errors of the mixins in {@code fabricConfigs} for a game {@code host} patched. */
    static void handle(Set<String> fabricConfigs, String host) {
        FABRIC_CONFIGS.addAll(fabricConfigs);
        hostName = host;
    }

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return skip(config, mixin, null, error, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return skip(mixin.getConfig(), mixin, targetClassName, error, action);
    }

    private static boolean isCountCheck(Throwable error) {
        return error instanceof InvalidInjectionException
                && error.getMessage() != null
                && error.getMessage().startsWith("Injection validation failed");
    }

    private static ErrorAction skip(
            IMixinConfig config, IMixinInfo mixin, String target, Throwable error, ErrorAction action) {
        if (config == null || !FABRIC_CONFIGS.contains(config.getName())) {
            if (config != null && target == null && error instanceof MixinTargetAlreadyLoadedException) {
                // A host mod's mixin whose target loaded before its configuration was selected: another mod's Mixin
                // plugin loaded it while Mixin selected the configurations (Iris for NeoForge loads BlockGetter, which
                // Lithium's mixin targets). The host's own Mixin service does not notice such a class (NeoForge's
                // tracks only the classes its Mixin processor handled), so there the mixin silently does not apply.
                Object modId = config.getDecoration(FabricUtil.KEY_MOD_ID);
                LOGGER.warn(
                        "[Poly Mod Loader] POLY-COMPAT-220 [limited] {} mod {} ({}, {}): {}; as under {}'s own Mixin, this"
                                + " mixin does not apply",
                        hostName,
                        modId == null ? "?" : modId,
                        config.getName(),
                        mixin == null ? "?" : mixin.getName(),
                        error.getMessage(),
                        hostName);
                return ErrorAction.NONE;
            }
            // The injection count check runs for every mixin here; for the host's mods a count below the expected one
            // is no error, as under the host's own Mixin.
            return isCountCheck(error) ? ErrorAction.NONE : action;
        }
        Object modId = config.getDecoration(FabricUtil.KEY_MOD_ID);
        LOGGER.warn(
                "[Poly Mod Loader] POLY-COMPAT-138 [limited] Fabric mod {} ({}, {}){} does not apply to the game as {} patches"
                        + " it: {}; that part of the mod does not run",
                modId == null ? "?" : modId,
                config.getName(),
                mixin == null ? "?" : mixin.getName(),
                target == null ? "" : " into " + target,
                hostName,
                error.getMessage());
        return ErrorAction.NONE;
    }
}
