package dev.plexusmodloader.game.layer.v26_3.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Converts Plexus Mod Loader key codes to the key codes of Minecraft 26.3 (ADR-0029). Plexus Mod Loader key codes are
 * the GLFW key tokens that 26.1 and 26.2 use; 26.3 reads the keyboard through SDL and names keys by SDL scancode.
 * The table pairs the codes the two versions give the same key name ({@code key.keyboard.*} in
 * {@code InputConstants.Type}), so a key keeps its physical place. The keypad decimal key goes to the keypad
 * period, the scancode of that physical key; SDL has no F25.
 */
final class GameKeyCodes {

    /** Plexus Mod Loader (GLFW) key code to SDL scancode. */
    private static final Map<Integer, Integer> SCANCODES = Map.ofEntries(
            Map.entry(32, 44), // space
            Map.entry(39, 52), // apostrophe
            Map.entry(44, 54), // comma
            Map.entry(45, 45), // minus
            Map.entry(46, 55), // period
            Map.entry(47, 56), // slash
            Map.entry(48, 39), // 0
            Map.entry(49, 30), // 1
            Map.entry(50, 31), // 2
            Map.entry(51, 32), // 3
            Map.entry(52, 33), // 4
            Map.entry(53, 34), // 5
            Map.entry(54, 35), // 6
            Map.entry(55, 36), // 7
            Map.entry(56, 37), // 8
            Map.entry(57, 38), // 9
            Map.entry(59, 51), // semicolon
            Map.entry(61, 46), // equal
            Map.entry(65, 4), // a
            Map.entry(66, 5), // b
            Map.entry(67, 6), // c
            Map.entry(68, 7), // d
            Map.entry(69, 8), // e
            Map.entry(70, 9), // f
            Map.entry(71, 10), // g
            Map.entry(72, 11), // h
            Map.entry(73, 12), // i
            Map.entry(74, 13), // j
            Map.entry(75, 14), // k
            Map.entry(76, 15), // l
            Map.entry(77, 16), // m
            Map.entry(78, 17), // n
            Map.entry(79, 18), // o
            Map.entry(80, 19), // p
            Map.entry(81, 20), // q
            Map.entry(82, 21), // r
            Map.entry(83, 22), // s
            Map.entry(84, 23), // t
            Map.entry(85, 24), // u
            Map.entry(86, 25), // v
            Map.entry(87, 26), // w
            Map.entry(88, 27), // x
            Map.entry(89, 28), // y
            Map.entry(90, 29), // z
            Map.entry(91, 47), // left.bracket
            Map.entry(92, 49), // backslash
            Map.entry(93, 48), // right.bracket
            Map.entry(96, 53), // grave.accent
            Map.entry(161, 100), // world.1
            Map.entry(162, 50), // world.2
            Map.entry(256, 41), // escape
            Map.entry(257, 40), // enter
            Map.entry(258, 43), // tab
            Map.entry(259, 42), // backspace
            Map.entry(260, 73), // insert
            Map.entry(261, 76), // delete
            Map.entry(262, 79), // right
            Map.entry(263, 80), // left
            Map.entry(264, 81), // down
            Map.entry(265, 82), // up
            Map.entry(266, 75), // page.up
            Map.entry(267, 78), // page.down
            Map.entry(268, 74), // home
            Map.entry(269, 77), // end
            Map.entry(280, 57), // caps.lock
            Map.entry(281, 71), // scroll.lock
            Map.entry(282, 83), // num.lock
            Map.entry(283, 70), // print.screen
            Map.entry(284, 72), // pause
            Map.entry(290, 58), // f1
            Map.entry(291, 59), // f2
            Map.entry(292, 60), // f3
            Map.entry(293, 61), // f4
            Map.entry(294, 62), // f5
            Map.entry(295, 63), // f6
            Map.entry(296, 64), // f7
            Map.entry(297, 65), // f8
            Map.entry(298, 66), // f9
            Map.entry(299, 67), // f10
            Map.entry(300, 68), // f11
            Map.entry(301, 69), // f12
            Map.entry(302, 104), // f13
            Map.entry(303, 105), // f14
            Map.entry(304, 106), // f15
            Map.entry(305, 107), // f16
            Map.entry(306, 108), // f17
            Map.entry(307, 109), // f18
            Map.entry(308, 110), // f19
            Map.entry(309, 111), // f20
            Map.entry(310, 112), // f21
            Map.entry(311, 113), // f22
            Map.entry(312, 114), // f23
            Map.entry(313, 115), // f24
            Map.entry(320, 98), // keypad.0
            Map.entry(321, 89), // keypad.1
            Map.entry(322, 90), // keypad.2
            Map.entry(323, 91), // keypad.3
            Map.entry(324, 92), // keypad.4
            Map.entry(325, 93), // keypad.5
            Map.entry(326, 94), // keypad.6
            Map.entry(327, 95), // keypad.7
            Map.entry(328, 96), // keypad.8
            Map.entry(329, 97), // keypad.9
            Map.entry(330, 99), // keypad.decimal
            Map.entry(331, 84), // keypad.divide
            Map.entry(332, 85), // keypad.multiply
            Map.entry(333, 86), // keypad.subtract
            Map.entry(334, 87), // keypad.add
            Map.entry(335, 88), // keypad.enter
            Map.entry(336, 103), // keypad.equal
            Map.entry(340, 225), // left.shift
            Map.entry(341, 224), // left.control
            Map.entry(342, 226), // left.alt
            Map.entry(343, 227), // left.win
            Map.entry(344, 229), // right.shift
            Map.entry(345, 228), // right.control
            Map.entry(346, 230), // right.alt
            Map.entry(347, 231), // right.win
            Map.entry(348, 118)); // menu

    private GameKeyCodes() {}

    /**
     * The 26.3 key code for a Plexus Mod Loader key code: {@link InputConstants#UNKNOWN} for
     * {@code KeyCodes.UNBOUND} (-1), empty when 26.3 has no such key.
     */
    static OptionalInt toGame(int plexusKey) {
        if (plexusKey == -1) {
            return OptionalInt.of(InputConstants.UNKNOWN.getValue());
        }
        Integer scancode = SCANCODES.get(plexusKey);
        return scancode == null ? OptionalInt.empty() : OptionalInt.of(scancode);
    }
}
