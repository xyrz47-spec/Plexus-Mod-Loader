package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.internal.transform.InvokeHookAtCall;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** ADR-0026 31: other loaders' mods initialise after NeoForge's registration on a server too. */
class NeoForgeServerModInitializationTest {

    @Test
    void theRuleRunsTheHookAfterServerModLoaderBeginOnServersAndStopsTheLaunchWhenItDoesNotApply() {
        TransformRuleDefinition rule = NeoForgeServerModInitialization.rule("26.2");

        assertEquals(Set.of(GameSide.DEDICATED_SERVER), rule.sides());
        assertEquals(
                new MethodTarget("net/neoforged/neoforge/server/loading/ServerModLoader", "load", "(Z)V"),
                rule.target());
        assertEquals(TransformFailurePolicy.ABORT_STARTUP, rule.failurePolicy());
        InvokeHookAtCall hook = (InvokeHookAtCall) rule.action();
        assertEquals(
                new MethodTarget(
                        "net/neoforged/neoforge/server/loading/ServerModLoader", "begin", "(Ljava/lang/Runnable;Z)V"),
                hook.invoked());
        assertEquals(InvokeHookAtCall.Timing.AFTER, hook.timing());
        assertEquals("dev/plexusmodloader/compat/forge/NeoForgeServerModInitialization", hook.hookOwnerInternalName());
        assertEquals("afterRegistration", hook.hookMethodName());
    }

    @Test
    void theDeferredInitializationRunsOnceAfterRegistration() {
        AtomicInteger runs = new AtomicInteger();
        NeoForgeServerModInitialization.defer(runs::incrementAndGet);

        NeoForgeServerModInitialization.afterRegistration(() -> {}, false);
        NeoForgeServerModInitialization.afterRegistration(() -> {}, false);

        assertEquals(1, runs.get());
    }

    @Test
    void onlyOneInitializationIsDeferred() {
        NeoForgeServerModInitialization.defer(() -> {});
        try {
            assertThrows(IllegalStateException.class, () -> NeoForgeServerModInitialization.defer(() -> {}));
        } finally {
            NeoForgeServerModInitialization.afterRegistration(() -> {}, false);
        }
    }
}
