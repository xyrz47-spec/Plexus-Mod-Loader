package dev.plexusmodloader.game.layer.v26_3.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import dev.plexusmodloader.api.client.KeyCodes;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Plexus Mod Loader key codes (GLFW key tokens, ADR-0029) against the SDL scancodes of 26.3's own constants. */
class GameKeyCodesTest {

    @Test
    void publicKeyCodesGetTheSameKey() {
        assertEquals(OptionalInt.of(InputConstants.KEY_A), GameKeyCodes.toGame(KeyCodes.A));
        assertEquals(OptionalInt.of(InputConstants.KEY_G), GameKeyCodes.toGame(KeyCodes.G));
        assertEquals(OptionalInt.of(InputConstants.KEY_K), GameKeyCodes.toGame(KeyCodes.K));
        assertEquals(OptionalInt.of(InputConstants.KEY_Z), GameKeyCodes.toGame(KeyCodes.Z));
        assertEquals(OptionalInt.of(InputConstants.KEY_F6), GameKeyCodes.toGame(KeyCodes.F6));
        assertEquals(OptionalInt.of(InputConstants.KEY_F10), GameKeyCodes.toGame(KeyCodes.F10));
    }

    @Test
    void otherGlfwTokensGetTheSameKey() {
        assertEquals(OptionalInt.of(InputConstants.KEY_ESCAPE), GameKeyCodes.toGame(256));
        assertEquals(OptionalInt.of(InputConstants.KEY_SPACE), GameKeyCodes.toGame(32));
        assertEquals(OptionalInt.of(InputConstants.KEY_0), GameKeyCodes.toGame(48));
        assertEquals(OptionalInt.of(InputConstants.KEY_LSHIFT), GameKeyCodes.toGame(340));
        assertEquals(OptionalInt.of(InputConstants.KEY_LGUI), GameKeyCodes.toGame(343));
        assertEquals(OptionalInt.of(InputConstants.KEY_NUMPAD0), GameKeyCodes.toGame(320));
    }

    @Test
    void keypadDecimalIsThePhysicalKeypadPeriod() {
        assertEquals(
                OptionalInt.of(
                        InputConstants.getKey("key.keyboard.keypad.period").getValue()),
                GameKeyCodes.toGame(330));
    }

    @Test
    void unboundIsTheGamesUnknownKey() {
        assertEquals(OptionalInt.of(InputConstants.UNKNOWN.getValue()), GameKeyCodes.toGame(KeyCodes.UNBOUND));
    }

    @Test
    void keysTheGameDoesNotHaveAreEmpty() {
        assertTrue(GameKeyCodes.toGame(314).isEmpty(), "F25");
        assertTrue(GameKeyCodes.toGame(65_000).isEmpty());
    }
}
