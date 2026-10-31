package cpw.mods.modlauncher;

/**
 * Plexus Mod Loader's stand-in for ModLauncher's argument handler (ADR-0021): only the launch arguments, in the private field
 * {@code args} that ModLauncher's own class has. FML does not read it; mods that are ModLauncher services do, through
 * reflection on {@code Launcher.argumentHandler} (Crash Assistant reads the launch target, CustomSkinLoader the loader
 * version; checked with {@code javap}). Not ModLauncher's code.
 */
public final class ArgumentHandler {

    private final String[] args;

    ArgumentHandler(String[] args) {
        this.args = args.clone();
    }

    /** The launch arguments, as ModLauncher passes them on. */
    public String[] buildArgumentList() {
        return args.clone();
    }
}
