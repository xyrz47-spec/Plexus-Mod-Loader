package cpw.mods.modlauncher;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.TypesafeMap;
import java.util.Optional;

/**
 * Poly Mod Loader's stand-in for ModLauncher's launcher (ADR-0021). FML and the access transformer library read only
 * {@link #INSTANCE}, {@link #environment()} and {@link #blackboard()} (checked with {@code javap} on Forge
 * 26.2-65.1.5's jars), so that is all this class has, with the private field {@code argumentHandler} that mods read
 * through reflection ({@link ArgumentHandler}); Poly Mod Loader's {@code PolyModLauncher} does the launching. Not
 * ModLauncher's code.
 */
public final class Launcher {

    /** The running launcher, as ModLauncher sets it; written once by {@code PolyModLauncher}. */
    public static Launcher INSTANCE;

    private final Environment environment;
    private final TypesafeMap blackboard = new TypesafeMap();
    private final ArgumentHandler argumentHandler;

    private Launcher(Environment environment, String[] arguments) {
        this.environment = environment;
        this.argumentHandler = new ArgumentHandler(arguments);
    }

    /** Creates the launcher with ModLauncher's launch {@code arguments} and makes it {@link #INSTANCE}. */
    public static Launcher install(Environment environment, String[] arguments) {
        Launcher launcher = new Launcher(environment, arguments);
        INSTANCE = launcher;
        return launcher;
    }

    public Environment environment() {
        return environment;
    }

    public TypesafeMap blackboard() {
        return blackboard;
    }

    public Optional<IModuleLayerManager> findLayerManager() {
        return environment.findModuleLayerManager();
    }
}
