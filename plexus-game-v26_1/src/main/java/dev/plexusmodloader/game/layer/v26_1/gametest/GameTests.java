package dev.plexusmodloader.game.layer.v26_1.gametest;

import dev.plexusmodloader.game.layer.v26_1.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.GameTestDeclaration;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;

/** Registers mod game tests as the game's test functions. */
public final class GameTests {

    private GameTests() {}

    public static void install(List<GameTestDeclaration> tests) {
        for (GameTestDeclaration test : tests) {
            ResourceKey<Consumer<GameTestHelper>> key =
                    ResourceKey.create(Registries.TEST_FUNCTION, Views.id(test.id()));
            Consumer<GameTestHelper> function = helper -> test.test().run(new GameTestContextView(helper));
            Registry.register(BuiltInRegistries.TEST_FUNCTION, key, function);
        }
    }
}
