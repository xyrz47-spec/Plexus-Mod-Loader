package dev.plexusmodloader.compat.fabric.mixin;

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
 * Mixin's error handler in a launch where a host loader patched the game (ADR-0021 G8, ADR-0030). {@link
 * HostPatchAdjustments} keeps the Fabric mods whose mixins cannot work on the host's patched classes out of the launch
 * before any mod code runs ({@code PLEXUS-COMPAT-224}); a failure it could not foresee (injection points found by
 * expressions or slices) keeps Mixin's action, as under Fabric Loader: a required injection that finds nothing stops
 * the game. Such a failure is reported as Poly Mod Loader's ({@code PLEXUS-COMPAT-225}), so that the player reports it to
 * Poly Mod Loader and not to the mod's author, whose mixin is correct for the game it was written for. Poly Mod Loader
 * does not skip the mixin and run the rest of the mod. Errors of the host's own mods keep the host Mixin's handling.
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
        return handle(config, mixin, null, error, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return handle(mixin.getConfig(), mixin, targetClassName, error, action);
    }

    /**
     * Reports {@code error}, which Mixin threw while it changed {@code className} without asking this handler (an
     * injection that found fewer targets than it requires throws {@code InjectionError} directly), as Poly Mod Loader's
     * when its message names a Fabric or Quilt mod's configuration ({@code PLEXUS-COMPAT-225}). The caller rethrows it.
     */
    static void reportUnforeseen(String className, Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            for (String config : FABRIC_CONFIGS) {
                if (message.contains(" " + config + ":")) {
                    LOGGER.error(
                            "[Poly Mod Loader] PLEXUS-COMPAT-225 a Fabric mod's mixin from {} does not apply to {} as {}"
                                    + " patches it: {}. Poly Mod Loader's check before the launch did not foresee"
                                    + " this. This is a limitation of Poly Mod Loader running Fabric mods with {}, not"
                                    + " a bug in the mod: report it to Poly Mod Loader, not to the mod's author. The"
                                    + " game stops, as it would under Fabric Loader; remove that mod (or the {} mods)"
                                    + " to play",
                            config,
                            className,
                            hostName,
                            message,
                            hostName,
                            hostName);
                    return;
                }
            }
        }
    }

    private static boolean isCountCheck(Throwable error) {
        return error instanceof InvalidInjectionException
                && error.getMessage() != null
                && error.getMessage().startsWith("Injection validation failed");
    }

    private static ErrorAction handle(
            IMixinConfig config, IMixinInfo mixin, String target, Throwable error, ErrorAction action) {
        if (config == null || !FABRIC_CONFIGS.contains(config.getName())) {
            if (config != null && target == null && error instanceof MixinTargetAlreadyLoadedException) {
                // A host mod's mixin whose target loaded before its configuration was selected: another mod's Mixin
                // plugin loaded it while Mixin selected the configurations (Iris for NeoForge loads BlockGetter, which
                // Lithium's mixin targets). The host's own Mixin service does not notice such a class (NeoForge's
                // tracks only the classes its Mixin processor handled), so there the mixin silently does not apply.
                Object modId = config.getDecoration(FabricUtil.KEY_MOD_ID);
                LOGGER.warn(
                        "[Poly Mod Loader] PLEXUS-COMPAT-220 [limited] {} mod {} ({}, {}): {}; as under {}'s own Mixin, this"
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
        if (action != ErrorAction.ERROR) {
            // The mod's own optional injection: Mixin only warns, as under Fabric Loader.
            return action;
        }
        Object modId = config.getDecoration(FabricUtil.KEY_MOD_ID);
        String mod = modId == null ? "?" : modId.toString();
        LOGGER.error(
                "[Poly Mod Loader] PLEXUS-COMPAT-225 Fabric mod {} ({}, {}){} does not apply to the game as {} patches it:"
                        + " {}. Poly Mod Loader's check before the launch did not foresee this. This is a limitation of"
                        + " Poly Mod Loader running Fabric mods with {}, not a bug in mod {}: report it to Poly Mod"
                        + " Loader, not to the mod's author. The game stops, as it would under Fabric Loader; remove"
                        + " mod {} (or the {} mods) to play",
                mod,
                config.getName(),
                mixin == null ? "?" : mixin.getName(),
                target == null ? "" : " into " + target,
                hostName,
                error.getMessage(),
                hostName,
                mod,
                mod,
                hostName);
        return action;
    }
}
