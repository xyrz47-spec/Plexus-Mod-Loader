package dev.polymodloader.compat.forge;

import dev.polymodloader.compat.bridge.GameModuleLayers;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * FML's BOOT layer on Poly Mod Loader (ADR-0021): Forge's loader libraries as modules of the game class loader, the way
 * Forge's {@code ForgeBootstrap} makes them modules, with these differences:
 *
 * <ul>
 *   <li>Libraries Poly Mod Loader's class path already has (Minecraft's libraries, ASM) are not duplicated; modules that
 *       require them read Poly Mod Loader's class path instead, and what they export only to such a module is exported to
 *       the class path. Forge's Mixin is the exception: it stays a module, ahead of the Fabric fork on the class path.
 *       As in Forge's own BOOT layer (securemodules), a jar's {@code module-info} becomes an open module.
 *   <li>{@code cpw.mods.modlauncher} keeps ModLauncher's API packages but takes {@code Launcher}, {@code Environment}
 *       and the launch itself ({@code dev.polymodloader.compat.forge.modlauncher}) from Poly Mod Loader's {@code
 *       forge-host.jar}; ModLauncher's own implementation classes are left out.
 *   <li>Mod files (a {@code META-INF/mods.toml}, or an {@code FMLModType} in the manifest) are not modules here; FML
 *       finds them as resources and puts them into the PLUGIN and GAME layers itself.
 *   <li>FML does not scan the mods folder ({@code ModsFolderLocator} is not provided). Poly Mod Loader found the Forge mods
 *       there already and hands FML those as resources; FML would otherwise stop at every Fabric and Poly mod in the
 *       folder as "not a Forge mod".
 *   <li>night-config's {@code ConfigFormat} closes the file it opens for an empty config ({@link
 *       NightConfigEmptyFilePatch}); left open, Windows refuses to replace that file on the next save.
 * </ul>
 */
final class ForgeBootModules {

    static final String MODLAUNCHER = "cpw.mods.modlauncher";
    static final String HOST_PACKAGE = "dev.polymodloader.compat.forge.modlauncher";
    private static final String FMLLOADER = "net.minecraftforge.fmlloader";
    /**
     * Further BOOT modules Poly Mod Loader's part of {@code cpw.mods.modlauncher} reads when they are modules (a library
     * on Poly Mod Loader's class path is read through it anyway): forgespi and maven-artifact (ADR-0025).
     */
    private static final List<String> HOST_READS = List.of("net.minecraftforge.forgespi", "maven.artifact");

    private static final String IMPLEMENTATION_PREFIX = "cpw/mods/modlauncher/";
    private static final String MIXIN_BOOTSTRAP = "org/spongepowered/asm/launch/MixinBootstrap.class";
    /** Forge's MixinExtras library ({@code mixinextras-forge}), found by its configuration plugin. */
    private static final String MIXINEXTRAS_FORGE =
            "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class";
    /** Service implementations the BOOT layer leaves out; see the class comment. */
    private static final Set<String> LEFT_OUT_PROVIDERS =
            Set.of("net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator");

    /**
     * Forge's libraries sorted by where they go.
     *
     * @param modules jars that become BOOT layer modules
     * @param modLauncher ModLauncher's jar, whose API packages {@code cpw.mods.modlauncher} keeps
     * @param modFiles jars FML finds itself (Forge's {@code universal.jar}, {@code fmlcore}, language providers)
     * @param onClassPath jars left out because Poly Mod Loader's class path has those classes (with a shared Mixin, Forge's
     *     Mixin too)
     */
    record Libraries(List<Path> modules, Path modLauncher, List<Path> modFiles, List<Path> onClassPath) {

        /**
         * These libraries with Forge's MixinExtras ({@code mixinextras-forge}) replaced by {@link MixinExtrasStub}'s copy
         * in {@code directory}, for a shared Mixin.
         */
        Libraries withSharedMixinExtras(Path directory) throws IOException {
            List<Path> files = new ArrayList<>();
            for (Path file : modFiles) {
                boolean mixinExtras;
                try (JarFile jar = new JarFile(file.toFile())) {
                    mixinExtras = contains(jar, MIXINEXTRAS_FORGE);
                }
                files.add(mixinExtras ? MixinExtrasStub.write(file, directory) : file);
            }
            return new Libraries(modules, modLauncher, List.copyOf(files), onClassPath);
        }
    }

    private ForgeBootModules() {}

    /**
     * Sorts {@code libraries}; {@code classPath} is Poly Mod Loader's own class loader. With {@code ownMixin} false (Fabric
     * mods run in the same launch, ADR-0021 G8), Forge's Mixin is left out: the Mixin on Poly Mod Loader's class path serves
     * Forge mods too, because the game class loader can hold only one (for MixinExtras see {@link
     * Libraries#withSharedMixinExtras}).
     */
    static Libraries sort(List<Path> libraries, ClassLoader classPath, boolean ownMixin) throws IOException {
        List<Path> modules = new ArrayList<>();
        List<Path> modFiles = new ArrayList<>();
        List<Path> onClassPath = new ArrayList<>();
        Path modLauncher = null;
        for (Path library : libraries) {
            try (JarFile jar = new JarFile(library.toFile())) {
                if (!ownMixin && contains(jar, MIXIN_BOOTSTRAP)) {
                    onClassPath.add(library);
                } else if (isModFile(jar)) {
                    modFiles.add(library);
                } else if (contains(jar, "net/minecraftforge/bootstrap/ForgeBootstrap.class")) {
                    // Forge's own entry point, which Poly Mod Loader replaces.
                    continue;
                } else if (contains(jar, IMPLEMENTATION_PREFIX + "Launcher.class")) {
                    modLauncher = library;
                } else if (contains(jar, MIXIN_BOOTSTRAP)) {
                    // Forge's Mixin (the original, with its ModLauncher services) stays a module although
                    // Poly Mod Loader's class path has Fabric's fork of the same packages: the game class loader
                    // resolves
                    // a module's packages before its parent, so FML and Forge mods get Forge's Mixin.
                    modules.add(library);
                } else if (onClassPath(jar, classPath)) {
                    onClassPath.add(library);
                } else {
                    modules.add(library);
                }
            }
        }
        if (modLauncher == null) {
            throw new IOException("Forge's libraries have no ModLauncher jar");
        }
        return new Libraries(List.copyOf(modules), modLauncher, List.copyOf(modFiles), List.copyOf(onClassPath));
    }

    /**
     * Defines the BOOT layer in the game class loader. Every module reads Poly Mod Loader's class path ({@code classPath}'s
     * unnamed module), where the libraries it shares with Minecraft are.
     */
    static ModuleLayer define(Libraries libraries, Path hostJar, GameModuleLayers layers, ClassLoader classPath) {
        Map<String, ModuleReference> references = new HashMap<>();
        for (ModuleReference reference :
                ModuleFinder.of(libraries.modules().toArray(Path[]::new)).findAll()) {
            references.put(reference.descriptor().name(), reference);
        }
        references.computeIfPresent(
                NightConfigEmptyFilePatch.MODULE, (name, reference) -> NightConfigEmptyFilePatch.patching(reference));
        ModuleReference modLauncher = modLauncher(libraries.modLauncher(), hostJar, references.keySet());
        references.put(MODLAUNCHER, modLauncher);
        Set<String> available = new HashSet<>(references.keySet());
        ModuleFinder.ofSystem()
                .findAll()
                .forEach(system -> available.add(system.descriptor().name()));
        Map<String, ModuleReference> rewritten = new HashMap<>();
        for (Map.Entry<String, ModuleReference> entry : references.entrySet()) {
            rewritten.put(entry.getKey(), withRequires(entry.getValue(), available));
        }
        ModuleFinder finder = new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(rewritten.get(name));
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.copyOf(rewritten.values());
            }
        };
        Configuration configuration =
                ModuleLayer.boot().configuration().resolveAndBind(finder, ModuleFinder.of(), rewritten.keySet());
        ModuleLayer.Controller controller = layers.define(configuration, List.of(ModuleLayer.boot()), false);
        Module classPathModule = classPath.getUnnamedModule();
        for (Module module : controller.layer().modules()) {
            // With mods of another loader, the game class loader defines the class path's libraries itself (so that
            // Mixin can change them); its own unnamed module stands in for the class path then.
            List<Module> standIns =
                    List.of(classPathModule, module.getClassLoader().getUnnamedModule());
            for (Module standIn : standIns) {
                controller.addReads(module, standIn);
                // A package exported only to modules that Poly Mod Loader's class path stands in for is exported to the
                // class path instead.
                for (ModuleDescriptor.Exports exports : module.getDescriptor().exports()) {
                    if (exports.isQualified() && standsIn(exports.targets(), controller.layer())) {
                        controller.addExports(module, exports.source(), standIn);
                    }
                }
            }
        }
        return controller.layer();
    }

    /**
     * A module of the BOOT layer without the requires Poly Mod Loader's class path answers instead, and without the
     * {@linkplain #LEFT_OUT_PROVIDERS left-out} service implementations.
     */
    static ModuleReference withRequires(ModuleReference reference, Set<String> available) {
        ModuleDescriptor descriptor = reference.descriptor();
        if (descriptor.isAutomatic()) {
            return withoutLeftOutProviders(reference);
        }
        // An open module, as securemodules reads a module-info for Forge's own BOOT layer: MixinExtras, a GAME layer
        // module, reflects into Mixin's internals.
        ModuleDescriptor.Builder builder = ModuleDescriptor.newOpenModule(descriptor.name());
        descriptor.version().ifPresent(builder::version);
        for (ModuleDescriptor.Requires requires : descriptor.requires()) {
            if (available.contains(requires.name())) {
                builder.requires(requires);
            }
        }
        descriptor.exports().forEach(builder::exports);
        descriptor.uses().forEach(builder::uses);
        provideKept(descriptor, builder);
        builder.packages(descriptor.packages());
        descriptor.mainClass().ifPresent(builder::mainClass);
        return delegate(builder.build(), reference);
    }

    /** An automatic module (FML's loader) as it is, but without the left-out service implementations. */
    private static ModuleReference withoutLeftOutProviders(ModuleReference reference) {
        ModuleDescriptor descriptor = reference.descriptor();
        if (descriptor.provides().stream()
                .flatMap(provides -> provides.providers().stream())
                .noneMatch(LEFT_OUT_PROVIDERS::contains)) {
            return reference;
        }
        ModuleDescriptor.Builder builder = ModuleDescriptor.newAutomaticModule(descriptor.name());
        descriptor.version().ifPresent(builder::version);
        provideKept(descriptor, builder);
        builder.packages(descriptor.packages());
        descriptor.mainClass().ifPresent(builder::mainClass);
        return delegate(builder.build(), reference);
    }

    private static void provideKept(ModuleDescriptor descriptor, ModuleDescriptor.Builder builder) {
        for (ModuleDescriptor.Provides provides : descriptor.provides()) {
            List<String> kept = provides.providers().stream()
                    .filter(provider -> !LEFT_OUT_PROVIDERS.contains(provider))
                    .toList();
            if (!kept.isEmpty()) {
                builder.provides(provides.service(), kept);
            }
        }
    }

    /**
     * {@code cpw.mods.modlauncher}: ModLauncher's API from its jar, and from {@code hostJar} Poly Mod Loader's {@code
     * Launcher}, {@code Environment} and launch. ModLauncher's own implementation classes stay out, and so do the
     * services it provides with them.
     */
    private static ModuleReference modLauncher(Path modLauncherJar, Path hostJar, Set<String> available) {
        ModuleReference real =
                ModuleFinder.of(modLauncherJar).findAll().iterator().next();
        ModuleReference host = ModuleFinder.of(hostJar).findAll().iterator().next();
        ModuleDescriptor descriptor = real.descriptor();
        ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(MODLAUNCHER, descriptor.modifiers());
        descriptor.version().ifPresent(builder::version);
        descriptor.requires().forEach(builder::requires);
        descriptor.exports().forEach(builder::exports);
        builder.exports(HOST_PACKAGE);
        descriptor.opens().forEach(builder::opens);
        descriptor.uses().forEach(builder::uses);
        // Poly Mod Loader's game test launch handler extends FML's CommonLaunchHandler.
        builder.requires(FMLLOADER);
        // ForeignModList shows other mods as forgespi mod files, with Maven versions (ADR-0025).
        for (String module : HOST_READS) {
            if (available.contains(module)
                    && descriptor.requires().stream().noneMatch(r -> r.name().equals(module))) {
                builder.requires(module);
            }
        }
        builder.provides(
                "cpw.mods.modlauncher.api.ILaunchHandlerService", List.of(HOST_PACKAGE + ".GameTestLaunchHandler"));
        Set<String> packages = new HashSet<>(descriptor.packages());
        packages.add(HOST_PACKAGE);
        builder.packages(packages);
        ModuleDescriptor overlay = builder.build();
        return new ModuleReference(overlay, real.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return new OverlayReader(host.open(), real.open());
            }
        };
    }

    private static ModuleReference delegate(ModuleDescriptor descriptor, ModuleReference original) {
        return new ModuleReference(descriptor, original.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return original.open();
            }
        };
    }

    /**
     * Poly Mod Loader's classes first, except {@code META-INF/} (ModLauncher's manifest carries its API packages' versions,
     * which FML checks); ModLauncher's implementation classes ({@code cpw/mods/modlauncher/*.class}) never.
     */
    private static final class OverlayReader implements ModuleReader {

        private final ModuleReader host;
        private final ModuleReader real;

        OverlayReader(ModuleReader host, ModuleReader real) {
            this.host = host;
            this.real = real;
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            if (name.startsWith("META-INF/")) {
                return real.find(name);
            }
            Optional<URI> own = host.find(name);
            return own.isPresent() || isImplementation(name) ? own : real.find(name);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            if (name.startsWith("META-INF/")) {
                return real.open(name);
            }
            Optional<InputStream> own = host.open(name);
            return own.isPresent() || isImplementation(name) ? own : real.open(name);
        }

        @Override
        public Stream<String> list() throws IOException {
            return Stream.concat(host.list(), real.list().filter(name -> !isImplementation(name)))
                    .distinct();
        }

        @Override
        public void close() throws IOException {
            try {
                host.close();
            } finally {
                real.close();
            }
        }

        private static boolean isImplementation(String name) {
            return name.startsWith(IMPLEMENTATION_PREFIX)
                    && name.indexOf('/', IMPLEMENTATION_PREFIX.length()) < 0
                    && name.endsWith(".class");
        }
    }

    private static boolean isModFile(JarFile jar) throws IOException {
        if (jar.getEntry("META-INF/mods.toml") != null) {
            return true;
        }
        var manifest = jar.getManifest();
        return manifest != null && manifest.getMainAttributes().getValue(new Attributes.Name("FMLModType")) != null;
    }

    /** Whether none of {@code targets} is a module of {@code layer} or its parents, so the class path answers them. */
    private static boolean standsIn(Set<String> targets, ModuleLayer layer) {
        return targets.stream().noneMatch(target -> layer.findModule(target).isPresent());
    }

    private static boolean contains(JarFile jar, String entry) {
        return jar.getEntry(entry) != null;
    }

    /** Whether Poly Mod Loader's class path already has this jar's classes (checked with its first class). */
    private static boolean onClassPath(JarFile jar, ClassLoader classPath) {
        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                return classPath.getResource(name) != null;
            }
        }
        return false;
    }
}
