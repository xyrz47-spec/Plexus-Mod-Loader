package dev.plexusmodloader.runtime;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemLookup;
import dev.plexusmodloader.game.version.GameLaunchTarget;
import dev.plexusmodloader.game.version.GameVersionAdapter;
import dev.plexusmodloader.internal.transform.MethodTarget;
import dev.plexusmodloader.internal.transform.ReplaceConstantReturnWithStaticCall;
import dev.plexusmodloader.internal.transform.TransformFailurePolicy;
import dev.plexusmodloader.internal.transform.TransformRuleDefinition;
import java.util.EnumSet;
import java.util.List;

/** Adapter for the synthetic game built by {@link PlexusRuntimeTest}; registered via META-INF/services. */
public final class TestGameVersionAdapter implements GameVersionAdapter {

    static final String GAME_VERSION = "test-1";

    @Override
    public String adapterId() {
        return "test-game";
    }

    @Override
    public String gameVersion() {
        return GAME_VERSION;
    }

    @Override
    public String mainClass(GameLaunchTarget target) {
        return "fakegame.Main";
    }

    @Override
    public void bindSystems(SystemLookup systems) {
        TestGameHooks.bind(systems);
    }

    @Override
    public List<TransformRuleDefinition> builtInTransformRules() {
        return List.of(new TransformRuleDefinition(
                "test:brand",
                "poly",
                GAME_VERSION,
                EnumSet.allOf(GameSide.class),
                new MethodTarget("fakegame/Brand", "brand", "()Ljava/lang/String;"),
                new ReplaceConstantReturnWithStaticCall(
                        "vanilla", "dev/plexusmodloader/runtime/TestGameHooks", "brand"),
                TransformFailurePolicy.DISABLE_RULE));
    }
}
