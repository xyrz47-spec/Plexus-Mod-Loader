plugins {
    id("poly.java-conventions")
}

description = "Poly Mod Loader's early loading window: drawn in its own process while the game starts."

// The window's process runs only this module's classes: no dependencies besides the JDK's java.desktop.
