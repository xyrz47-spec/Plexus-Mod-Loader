package dev.polymodloader.earlywindow;

import java.util.List;

/**
 * What the early window shows: the loader's name, what it is doing, the lines under the progress bar and the bar's
 * progress.
 *
 * @param name bottom right, beside the cube
 * @param status bottom left; empty shows nothing
 * @param underBar lines under the progress bar, from the top
 * @param progress the bar's filled share, {@code 0} to {@code 1}
 */
record LoadingScreenState(String name, String status, List<String> underBar, float progress) {

    static final String NAME = "Poly Mod Loader";

    LoadingScreenState {
        underBar = List.copyOf(underBar);
    }

    static LoadingScreenState initial() {
        return new LoadingScreenState(NAME, "", List.of(), 0.0f);
    }

    LoadingScreenState withStatus(String value) {
        return new LoadingScreenState(name, value, underBar, progress);
    }

    LoadingScreenState withUnderBar(List<String> value) {
        return new LoadingScreenState(name, status, value, progress);
    }

    LoadingScreenState withProgress(float value) {
        return new LoadingScreenState(
                name, status, underBar, Math.max(progress, Math.max(0.0f, Math.min(1.0f, value))));
    }
}
