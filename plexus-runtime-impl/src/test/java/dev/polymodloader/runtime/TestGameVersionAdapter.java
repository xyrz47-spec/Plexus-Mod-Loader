package dev.polymodloader.runtime;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.api.system.SystemLookup;
import dev.polymodloader.game.version.GameLaunchTarget;
import dev.polymodloader.game.version.GameVersionAdapter;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.ReplaceConstantReturnWithStaticCall;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.util.EnumSet;
import java.util.List;

/** Adapter for the synthetic game built by {@link PolyRuntimeTest}; registered via META-INF/services. */
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
                new ReplaceConstantReturnWithStaticCall("vanilla", "dev/polymodloader/runtime/TestGameHooks", "brand"),
                TransformFailurePolicy.DISABLE_RULE));
    }
}
