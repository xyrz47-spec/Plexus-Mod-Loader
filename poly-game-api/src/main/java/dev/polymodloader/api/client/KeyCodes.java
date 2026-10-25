package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/** Keyboard key codes for default key bindings (the GLFW codes the game uses). */
@ApiStability(StabilityLevel.PREVIEW)
public final class KeyCodes {

    /** No default key; the player must bind one. */
    public static final int UNBOUND = -1;

    public static final int A = 65;
    public static final int B = 66;
    public static final int G = 71;
    public static final int H = 72;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int R = 82;
    public static final int U = 85;
    public static final int V = 86;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;

    private KeyCodes() {}
}
