package dev.plexusmodloader.runtime.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.game.version.GameLaunchTarget;
import dev.plexusmodloader.game.version.GameVersionAdapter;
import dev.plexusmodloader.internal.transform.InjectStaticCallAtHead;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdapterTransformRulesTest {

    private static TransformRuleDefinition rule(String id, String gameVersion) {
        return new TransformRuleDefinition(
                id,
                "poly",
                gameVersion,
                EnumSet.allOf(GameSide.class),
                new MethodTarget("game/Server", "tick", "()V"),
                new InjectStaticCallAtHead("hooks/Hooks", "onTick"),
                TransformFailurePolicy.ABORT_STARTUP,
                List.of("poly:other"));
    }

    /** Covers 26.1 and its hotfixes; one rule names a version the adapter does not cover. */
    private static final GameVersionAdapter HOTFIX_ADAPTER = new GameVersionAdapter() {
        @Override
        public String adapterId() {
            return "minecraft-26.1";
        }

        @Override
        public String gameVersion() {
            return "26.1";
        }

        @Override
        public Set<String> gameVersions() {
            return Set.of("26.1", "26.1.1", "26.1.2");
        }

        @Override
        public String mainClass(GameLaunchTarget target) {
            return "game.Main";
        }

        @Override
        public List<TransformRuleDefinition> builtInTransformRules() {
            return List.of(rule("poly:tick", "26.1"), rule("poly:stale", "26.0"));
        }
    };

    @Test
    void plansTheAdaptersRulesForTheHotfixThatRuns() {
        List<TransformRuleDefinition> rules = AdapterTransformRules.forGameVersion(HOTFIX_ADAPTER, "26.1.2");

        TransformRuleDefinition tick = rules.get(0);
        assertEquals("26.1.2", tick.gameVersion());
        TransformRuleDefinition original =
                HOTFIX_ADAPTER.builtInTransformRules().get(0);
        assertEquals(original.ruleId(), tick.ruleId());
        assertEquals(original.ownerModId(), tick.ownerModId());
        assertEquals(original.sides(), tick.sides());
        assertEquals(original.target(), tick.target());
        assertEquals(original.action(), tick.action());
        assertEquals(original.failurePolicy(), tick.failurePolicy());
        assertEquals(original.after(), tick.after());
    }

    @Test
    void leavesRulesForOtherVersionsForThePlannerToReject() {
        List<TransformRuleDefinition> rules = AdapterTransformRules.forGameVersion(HOTFIX_ADAPTER, "26.1.1");

        assertEquals("26.0", rules.get(1).gameVersion());
    }

    @Test
    void keepsRulesThatAlreadyNameTheRunningVersion() {
        List<TransformRuleDefinition> rules = AdapterTransformRules.forGameVersion(HOTFIX_ADAPTER, "26.1");

        assertEquals(HOTFIX_ADAPTER.builtInTransformRules(), rules);
        assertSame(TransformFailurePolicy.ABORT_STARTUP, rules.get(0).failurePolicy());
    }

    @Test
    void rejectsAVersionTheAdapterDoesNotCover() {
        assertThrows(
                IllegalArgumentException.class, () -> AdapterTransformRules.forGameVersion(HOTFIX_ADAPTER, "26.2"));
    }
}
