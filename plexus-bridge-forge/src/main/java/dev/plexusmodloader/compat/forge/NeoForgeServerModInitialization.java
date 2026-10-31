package dev.plexusmodloader.compat.forge;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.internal.transform.InvokeHookAtCall;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plexus Mod Loader's mod initialization on NeoForge's dedicated and game test servers, after NeoForge's registration
 * (ADR-0026 31). Minecraft's mod initialization point, where Fabric Loader initialises mods, is right before {@code
 * Util.startTimerHackThread()}; NeoForge's servers register their mods' content after it, in {@code
 * ServerModLoader.load(boolean)}, whose {@code begin(Runnable, boolean)} gathers NeoForge's mods and posts its {@code
 * RegisterEvent}s, and whose {@code load(Runnable)} then posts the setup events. Other loaders' mods initialised at
 * Minecraft's point would see NeoForge's registry entries unbound: building a mob's default attributes ({@code
 * LivingEntity.createLivingAttributes()}, which NeoForge's patch extends with {@code neoforge:swim_speed}) fails there.
 * On a client NeoForge registers first ({@code ClientModLoader.begin} runs before {@code Minecraft}'s constructor, where
 * Fabric mods initialise) and sets its mods up after. The rule {@link #RULE_ID} runs the initialization right after
 * {@code begin} on a server too, so both sides have the same order. Checked with {@code javap} on NeoForge 26.2.0.93's
 * and 26.3.0.98's universal jars: {@code ServerModLoader.load(Z)V} calls {@code ServerModLoader.begin(Runnable, Z)V}
 * once.
 *
 * <p>The rule stops the launch when it does not apply ({@link TransformFailurePolicy#ABORT_STARTUP}): without it, other
 * loaders' mods would not initialise at all.
 *
 * <p>Thread: the game's main thread.
 */
public final class NeoForgeServerModInitialization {

    /** The rule that runs the deferred initialization after NeoForge's registration. */
    public static final String RULE_ID = "plexus:neoforge_server_mod_initialization";

    static final String SERVER_MOD_LOADER = "net/neoforged/neoforge/server/loading/ServerModLoader";

    private static final AtomicReference<Runnable> PENDING = new AtomicReference<>();

    private NeoForgeServerModInitialization() {}

    /** The rule for NeoForge's servers of {@code gameVersion}: the hook after {@code ServerModLoader.begin}. */
    static TransformRuleDefinition rule(String gameVersion) {
        return new TransformRuleDefinition(
                RULE_ID,
                "plexus",
                gameVersion,
                Set.of(GameSide.DEDICATED_SERVER),
                new MethodTarget(SERVER_MOD_LOADER, "load", "(Z)V"),
                new InvokeHookAtCall(
                        new MethodTarget(SERVER_MOD_LOADER, "begin", "(Ljava/lang/Runnable;Z)V"),
                        0,
                        InvokeHookAtCall.Timing.AFTER,
                        NeoForgeServerModInitialization.class.getName().replace('.', '/'),
                        "afterRegistration"),
                TransformFailurePolicy.ABORT_STARTUP);
    }

    /** Keeps {@code modInitialization} for {@link #afterRegistration}. */
    static void defer(Runnable modInitialization) {
        Objects.requireNonNull(modInitialization, "modInitialization");
        if (!PENDING.compareAndSet(null, modInitialization)) {
            throw new IllegalStateException("Plexus Mod Loader's mod initialization was already deferred");
        }
    }

    /**
     * Called right after {@code ServerModLoader.begin(periodicTask, ...)}: runs the deferred mod initialization, once.
     *
     * @param periodicTask NeoForge's task between loading steps, unused
     * @param flag {@code begin}'s second argument, unused
     */
    public static void afterRegistration(Runnable periodicTask, boolean flag) {
        Runnable modInitialization = PENDING.getAndSet(null);
        if (modInitialization != null) {
            modInitialization.run();
        }
    }
}
