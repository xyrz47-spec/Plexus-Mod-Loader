package dev.plexusmodloader.runtime.classloading;

import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.GameClassPath;
import dev.plexusmodloader.compat.bridge.GameModuleLayers;
import dev.plexusmodloader.internal.transform.engine.ClassTransformExecutor;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Loads the game jar and mod jars, running every class through the {@link ClassTransformExecutor}
 * before it is defined (ADR-0003).
 *
 * <p>Delegation: classes found in this loader's jars are defined here (child-first) so they can be
 * transformed. Poly Mod Loader's own packages and Java platform packages always come from the parent, so mods
 * and Poly Mod Loader share one copy of the Poly Mod Loader API. Libraries (LWJGL, log4j, gson, …) are not in this loader's
 * jars and therefore also come from the parent; they are never transformed. The one exception to
 * the parent-only Poly Mod Loader packages is the version game layer ({@value #GAME_LAYER_PREFIX}): its jar is
 * one of this loader's jars so it can link against game classes (ADR-0010). Libraries a bridge shares with its
 * mods (Mixin, MixinExtras) also always come from the parent, even when a mod jar carries a copy.
 *
 * <p>Bridge transformers ({@link BridgeClassTransformer}, such as Fabric's Mixin, ADR-0020) run after Poly Mod Loader's
 * transformer on every class this loader defines, in installation order, and may create classes no jar holds.
 *
 * <p>Modules: a loader that needs JPMS modules (Forge, ADR-0021) has this loader define them with {@link
 * #define}. Classes of those modules' packages are read from the module's content and go through the
 * same transformation; they land in their named module. Everything else stays in this loader's unnamed module.
 */
public final class GameClassLoader extends URLClassLoader implements GameClassBytes, GameModuleLayers, GameClassPath {

    static {
        registerAsParallelCapable();
    }

    private static final List<String> PARENT_ONLY_PREFIXES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "dev.plexusmodloader.api.",
            "dev.plexusmodloader.bootstrap.",
            "dev.plexusmodloader.game.",
            "dev.plexusmodloader.internal.",
            "dev.plexusmodloader.runtime.");

    /** Package prefix of version game layers, which this loader defines itself. */
    public static final String GAME_LAYER_PREFIX = "dev.plexusmodloader.game.layer.";

    private final ClassTransformExecutor transformer;
    private final List<OwnedJar> jars;
    /** The resource jars, read for {@link #classBytes} only. */
    private final List<OwnedJar> resourceJars;
    /** Jars whose class bytes {@link #classBytes} answers until a loader defines them as modules. */
    private final List<OwnedJar> classBytesJars;

    private final List<String> sharedLibraryPrefixes;
    private volatile List<String> hiddenFromParent = List.of();
    private final List<BridgeClassTransformer> bridgeTransformers = new CopyOnWriteArrayList<>();
    private final java.util.function.Consumer<String> skippedRedirects;
    /** Modules this loader defines ({@link #define}), by module name and by package. */
    private final Map<String, OwnedModule> modulesByName = new ConcurrentHashMap<>();

    private final Map<String, OwnedModule> modulesByPackage = new ConcurrentHashMap<>();

    /** Asked last for classes and resources ({@link #addFallback}). */
    private final List<ClassLoader> fallbacks = new CopyOnWriteArrayList<>();

    /** Set while a fallback runs a lookup on this thread; its delegation back to this loader finds nothing. */
    private final ThreadLocal<Boolean> inFallback = ThreadLocal.withInitial(() -> false);

    /** A jar this loader reads class files from directly, so it can also close it (Windows file locks). */
    private record OwnedJar(Path path, JarFile file, ProtectionDomain protectionDomain) {}

    /**
     * A module this loader defines; its classes and resources are read through {@code reader}. Classes of a module that
     * is not {@code transformed} (a loader's own code) are defined as they are.
     */
    private record OwnedModule(
            String name, ModuleReader reader, ProtectionDomain protectionDomain, boolean transformed) {}

    /**
     * @param jars game jar first, then mod jars; the first jar containing a class wins
     * @param parent loader that holds Poly Mod Loader and the game libraries
     * @param transformer validated transformation plan executor
     */
    public GameClassLoader(List<Path> jars, ClassLoader parent, ClassTransformExecutor transformer) {
        this(jars, parent, transformer, List.of(), message -> {});
    }

    /**
     * @param jars game jar first, then mod jars; the first jar containing a class wins
     * @param parent loader that holds Poly Mod Loader and the game libraries
     * @param transformer validated transformation plan executor
     * @param sharedLibraryPrefixes package prefixes ({@code com.llamalad7.mixinextras.}) always taken from {@code
     *     parent}, from {@link dev.plexusmodloader.compat.bridge.ModLoaderBridge#sharedLibraryPackages()}
     * @param skippedRedirects receives a warning for each call redirect a bridge transformer made impossible in a class
     */
    public GameClassLoader(
            List<Path> jars,
            ClassLoader parent,
            ClassTransformExecutor transformer,
            List<String> sharedLibraryPrefixes,
            java.util.function.Consumer<String> skippedRedirects) {
        this(jars, List.of(), parent, transformer, sharedLibraryPrefixes, skippedRedirects);
    }

    /**
     * @param jars game jar first, then mod jars; the first jar containing a class wins
     * @param resourceJars jars whose resources this loader finds but whose classes it never defines from them, such as
     *     the jars a loader scans before it defines them as modules (Forge's mod files, ADR-0021)
     * @param parent loader that holds Poly Mod Loader and the game libraries
     * @param transformer validated transformation plan executor
     * @param sharedLibraryPrefixes package prefixes always taken from {@code parent}
     * @param skippedRedirects receives a warning for each call redirect a bridge transformer made impossible in a class
     */
    public GameClassLoader(
            List<Path> jars,
            List<Path> resourceJars,
            ClassLoader parent,
            ClassTransformExecutor transformer,
            List<String> sharedLibraryPrefixes,
            java.util.function.Consumer<String> skippedRedirects) {
        this(jars, resourceJars, List.of(), parent, transformer, sharedLibraryPrefixes, skippedRedirects);
    }

    /**
     * @param jars game jar first, then mod jars; the first jar containing a class wins
     * @param resourceJars jars whose resources this loader finds but whose classes it never defines from them, such as
     *     the jars a loader scans before it defines them as modules (Forge's mod files, ADR-0021)
     * @param classBytesJars jars that are neither resources nor defined from, whose class bytes {@link #classBytes}
     *     answers until a loader defines their classes as modules (NeoForge's patched game and universal jar, which FML
     *     finds in its own library folder, ADR-0026): what a bridge's Mixin reads of the game before the host starts
     * @param parent loader that holds Poly Mod Loader and the game libraries
     * @param transformer validated transformation plan executor
     * @param sharedLibraryPrefixes package prefixes always taken from {@code parent}
     * @param skippedRedirects receives a warning for each call redirect a bridge transformer made impossible in a class
     */
    public GameClassLoader(
            List<Path> jars,
            List<Path> resourceJars,
            List<Path> classBytesJars,
            ClassLoader parent,
            ClassTransformExecutor transformer,
            List<String> sharedLibraryPrefixes,
            java.util.function.Consumer<String> skippedRedirects) {
        super("poly-game", toUrls(concat(jars, resourceJars)), parent);
        this.transformer = transformer;
        this.sharedLibraryPrefixes = List.copyOf(sharedLibraryPrefixes);
        this.skippedRedirects = skippedRedirects;
        List<OwnedJar> owned = new ArrayList<>();
        List<OwnedJar> resources = new ArrayList<>();
        List<OwnedJar> bytesOnly = new ArrayList<>();
        try {
            for (Path jar : jars) {
                owned.add(open(jar));
            }
            for (Path jar : resourceJars) {
                resources.add(open(jar));
            }
            for (Path jar : classBytesJars) {
                bytesOnly.add(open(jar));
            }
        } catch (IOException e) {
            owned.forEach(GameClassLoader::closeQuietly);
            resources.forEach(GameClassLoader::closeQuietly);
            bytesOnly.forEach(GameClassLoader::closeQuietly);
            throw new UncheckedIOException("Failed to open game or mod jar", e);
        }
        this.jars = new java.util.concurrent.CopyOnWriteArrayList<>(owned);
        this.resourceJars = List.copyOf(resources);
        this.classBytesJars = List.copyOf(bytesOnly);
    }

    /**
     * Adds a bridge's transformation stage. It applies to classes defined from now on, so bridges install theirs
     * before any game or mod class is loaded.
     */
    public void installBridgeTransformer(BridgeClassTransformer bridgeTransformer) {
        bridgeTransformers.add(bridgeTransformer);
    }

    /**
     * Defines a module layer whose modules all belong to this loader (ADR-0021). Their classes are transformed like
     * any other class this loader defines and land in their named module, so {@code Class.forName(module, name)},
     * {@code ServiceLoader.load(layer, …)} and module resources work as on a JPMS layer of the JDK's own loaders.
     *
     * <p>Call it before any class of these modules' packages is loaded.
     *
     * @param configuration the resolved modules; their packages must not be packages this loader already defined
     *     classes in
     * @param parents the parent layers, normally the boot layer
     * @param transformed whether the modules' classes go through the transformers (game and mod classes) or are defined
     *     as they are (a loader's own code)
     * @return the layer's controller
     * @throws java.lang.LayerInstantiationException when the JDK refuses the layer (a package of a module already has
     *     classes in this loader, or a module name is taken)
     * @throws UncheckedIOException when a module's content cannot be opened
     */
    @Override
    public ModuleLayer.Controller define(Configuration configuration, List<ModuleLayer> parents, boolean transformed) {
        List<OwnedModule> opened = new ArrayList<>();
        try {
            for (ResolvedModule module : configuration.modules()) {
                ModuleReference reference = module.reference();
                URL location = reference.location().map(GameClassLoader::toUrl).orElse(null);
                opened.add(new OwnedModule(
                        module.name(),
                        reference.open(),
                        new ProtectionDomain(new CodeSource(location, (CodeSigner[]) null), null, this, null),
                        transformed));
            }
        } catch (IOException e) {
            opened.forEach(GameClassLoader::closeQuietly);
            throw new UncheckedIOException("Failed to open a module of the game layer", e);
        }
        ModuleLayer.Controller controller;
        try {
            controller = ModuleLayer.defineModules(configuration, parents, name -> this);
        } catch (RuntimeException e) {
            opened.forEach(GameClassLoader::closeQuietly);
            throw e;
        }
        ModuleLayer layer = controller.layer();
        for (OwnedModule module : opened) {
            modulesByName.put(module.name(), module);
            for (String packageName : layer.findModule(module.name())
                    .orElseThrow()
                    .getDescriptor()
                    .packages()) {
                modulesByPackage.put(packageName, module);
            }
        }
        return controller;
    }

    /**
     * This loader's own classes are defined under its lock for the class name; the parent and the fallbacks are asked
     * after that lock is released. A fallback whose parent is this loader (FML's plugin loader) takes its own lock for a
     * class and then asks this loader for it, so asking it under this loader's lock could deadlock with that (two NeoForge
     * mods constructed in parallel did). Classes found there are the parent's or the fallback's, not this loader's.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loaded;
        synchronized (getClassLoadingLock(name)) {
            loaded = findLoadedClass(name);
            if (loaded == null) {
                OwnedModule module = owningModule(name);
                if (module != null) {
                    loaded = defineModuleClass(name, module);
                } else if (!isParentOnly(name) && owningJar(classFilePath(name)) != null) {
                    loaded = findClass(name);
                } else {
                    loaded = generated(name);
                }
            }
        }
        if (loaded == null) {
            loaded = parentOrFallback(name);
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        OwnedModule module = owningModule(name);
        if (module != null) {
            return defineModuleClass(name, module);
        }
        String path = classFilePath(name);
        OwnedJar jar = isParentOnly(name) ? null : owningJar(path);
        if (jar == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes = transformed(name, read(jar, path));
        // A bridge transformer may have loaded this very class while reading it (Mixin initialisation).
        Class<?> already = findLoadedClass(name);
        if (already != null) {
            return already;
        }
        definePackageIfAbsent(name);
        return defineClass(name, bytes, 0, bytes.length, jar.protectionDomain());
    }

    /** {@code Class.forName(module, name)} for a module of this loader's layer; other modules are not found here. */
    @Override
    protected Class<?> findClass(String moduleName, String name) {
        if (moduleName == null) {
            return super.findClass(null, name);
        }
        if (!modulesByName.containsKey(moduleName)) {
            return null;
        }
        try {
            Class<?> loaded = loadClass(name, false);
            return moduleName.equals(loaded.getModule().getName()) ? loaded : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        OwnedModule module = moduleName == null ? null : modulesByName.get(moduleName);
        if (module == null) {
            return super.findResource(moduleName, name);
        }
        Optional<URI> found = module.reader().find(name);
        return found.isPresent() ? toUrl(found.get()) : null;
    }

    @Override
    public URL findResource(String name) {
        URL found = super.findResource(name);
        if (found != null || modulesByName.isEmpty()) {
            return found;
        }
        for (OwnedModule module : modulesByName.values()) {
            try {
                Optional<URI> uri = module.reader().find(name);
                if (uri.isPresent()) {
                    return toUrl(uri.get());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + name + " from module " + module.name(), e);
            }
        }
        return null;
    }

    /**
     * Child-first, as for classes: a resource this loader's jars or modules hold comes from them, not from a copy on the
     * JVM class path. The Minecraft Launcher and the server install put the vanilla game jar on the class path, while
     * this loader may hold another (Forge's patched game jar, which FML finds through {@code getResource} of a game
     * class). Poly Mod Loader's own and the shared libraries' resources still come from the parent.
     */
    @Override
    public URL getResource(String name) {
        if (!isParentOnly(name.replace('/', '.'))) {
            URL own = findResource(name);
            if (own != null) {
                return own;
            }
        }
        URL found = hiddenInParent(name) ? findResource(name) : super.getResource(name);
        return found != null || inFallback.get() ? found : fallbackResource(name);
    }

    /**
     * Hides the parent's classes and resources under {@code internalPrefixes} (internal form, {@code net/fabricmc/})
     * from game and mod code: a loader API on Poly Mod Loader's class path whose loader runs no mods in this launch, as
     * that loader is absent under the loader that does run. Multi-loader libraries tell the loader by such classes
     * (Yumi checks for {@code net.fabricmc.loader.api.FabricLoader}). This loader's own jars and modules are not
     * affected. Call it before game classes load, from the launching thread.
     */
    public void hideFromParent(List<String> internalPrefixes) {
        hiddenFromParent = List.copyOf(internalPrefixes);
    }

    private boolean hiddenInParent(String resourceName) {
        for (String prefix : hiddenFromParent) {
            if (resourceName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addFallback(ClassLoader fallback) {
        fallbacks.add(Objects.requireNonNull(fallback, "fallback"));
    }

    /** The parent's class, or else the first fallback's that has it ({@link #addFallback}). */
    private Class<?> parentOrFallback(String name) throws ClassNotFoundException {
        if (hiddenInParent(classFilePath(name))) {
            throw new ClassNotFoundException(name + " (its loader runs no mods in this launch)");
        }
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException e) {
            if (inFallback.get()) {
                throw e;
            }
            for (ClassLoader fallback : fallbacks) {
                inFallback.set(true);
                try {
                    return fallback.loadClass(name);
                } catch (ClassNotFoundException notThere) {
                    e.addSuppressed(notThere);
                } finally {
                    inFallback.set(false);
                }
            }
            throw e;
        }
    }

    private URL fallbackResource(String name) {
        for (ClassLoader fallback : fallbacks) {
            inFallback.set(true);
            try {
                URL found = fallback.getResource(name);
                if (found != null) {
                    return found;
                }
            } finally {
                inFallback.set(false);
            }
        }
        return null;
    }

    /** This loader's and its parent's resources, then the fallbacks' ({@link #addFallback}). */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> own = hiddenInParent(name) ? findResources(name) : super.getResources(name);
        if (fallbacks.isEmpty() || inFallback.get()) {
            return own;
        }
        List<URL> found = Collections.list(own);
        for (ClassLoader fallback : fallbacks) {
            inFallback.set(true);
            try {
                for (URL url : Collections.list(fallback.getResources(name))) {
                    if (!found.contains(url)) {
                        found.add(url);
                    }
                }
            } finally {
                inFallback.set(false);
            }
        }
        return Collections.enumeration(found);
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        if (modulesByName.isEmpty()) {
            return super.findResources(name);
        }
        List<URL> found = Collections.list(super.findResources(name));
        for (OwnedModule module : modulesByName.values()) {
            Optional<URI> uri = module.reader().find(name);
            if (uri.isPresent()) {
                found.add(toUrl(uri.get()));
            }
        }
        return Collections.enumeration(found);
    }

    /** The class file after Poly Mod Loader's transformer and the bridges' stages. */
    private byte[] transformed(String name, byte[] originalBytes) {
        byte[] original = LegacyPackageRemapper.remap(originalBytes);
        String internalName = name.replace('.', '/');
        if (bridgeTransformers.isEmpty()) {
            return transformer.transform(internalName, original);
        }
        // Poly Mod Loader's call redirects come after the bridge's stage, so that its injections at those calls find
        // them (ADR-0020).
        return transformer.transformLate(
                internalName,
                bridgeTransform(name, transformer.transformEarly(internalName, original)),
                skippedRedirects);
    }

    /** Defines a class of one of this loader's modules; the JDK puts it into that named module. */
    private Class<?> defineModuleClass(String name, OwnedModule module) throws ClassNotFoundException {
        Class<?> already = findLoadedClass(name);
        if (already != null) {
            return already;
        }
        byte[] original;
        try {
            original = readModule(module, classFilePath(name));
        } catch (ClassNotFoundException e) {
            // A class a bridge's transformer generates into a package a module holds: MixinExtras' generated local
            // reference classes, when Forge's MixinExtras module holds their package (ADR-0021 G8).
            byte[] generated = generatedBytes(name);
            if (generated == null) {
                throw e;
            }
            defineModulePackageIfAbsent(name, module);
            return defineClass(name, generated, 0, generated.length, module.protectionDomain());
        }
        byte[] bytes = module.transformed() ? transformed(name, original) : original;
        already = findLoadedClass(name);
        if (already != null) {
            return already;
        }
        defineModulePackageIfAbsent(name, module);
        return defineClass(name, bytes, 0, bytes.length, module.protectionDomain());
    }

    /**
     * Gives a module package the specification and implementation attributes of its module's manifest, as the JDK's
     * own loaders do for jars; loader code reads its version from them (FML's {@code LauncherVersion}). The {@link
     * Package} names the package only; the class still lands in its named module.
     */
    private void defineModulePackageIfAbsent(String className, OwnedModule module) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }
        String packageName = className.substring(0, lastDot);
        if (getDefinedPackage(packageName) != null) {
            return;
        }
        Manifest manifest = manifest(module);
        Attributes main = manifest == null ? null : manifest.getMainAttributes();
        Attributes section = manifest == null ? null : manifest.getAttributes(packageName.replace('.', '/') + "/");
        try {
            definePackage(
                    packageName,
                    attribute(section, main, Attributes.Name.SPECIFICATION_TITLE),
                    attribute(section, main, Attributes.Name.SPECIFICATION_VERSION),
                    attribute(section, main, Attributes.Name.SPECIFICATION_VENDOR),
                    attribute(section, main, Attributes.Name.IMPLEMENTATION_TITLE),
                    attribute(section, main, Attributes.Name.IMPLEMENTATION_VERSION),
                    attribute(section, main, Attributes.Name.IMPLEMENTATION_VENDOR),
                    null);
        } catch (IllegalArgumentException alreadyDefinedConcurrently) {
            // Another thread defined it between the check and the call; that is fine.
        }
    }

    private final Map<String, Optional<Manifest>> moduleManifests = new ConcurrentHashMap<>();

    private Manifest manifest(OwnedModule module) {
        return moduleManifests
                .computeIfAbsent(module.name(), name -> {
                    try {
                        Optional<InputStream> input = module.reader().open(JarFile.MANIFEST_NAME);
                        if (input.isEmpty()) {
                            return Optional.empty();
                        }
                        try (InputStream stream = input.get()) {
                            return Optional.of(new Manifest(stream));
                        }
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    private static String attribute(Attributes section, Attributes main, Attributes.Name name) {
        String value = section == null ? null : section.getValue(name);
        return value != null || main == null ? value : main.getValue(name);
    }

    private OwnedModule owningModule(String className) {
        if (modulesByPackage.isEmpty()) {
            return null;
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? null : modulesByPackage.get(className.substring(0, lastDot));
    }

    private static byte[] readModule(OwnedModule module, String path) throws ClassNotFoundException {
        try {
            Optional<InputStream> input = module.reader().open(path);
            if (input.isEmpty()) {
                throw new ClassNotFoundException(path + " is not in module " + module.name());
            }
            try (InputStream stream = input.get()) {
                return stream.readAllBytes();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to read " + path + " from module " + module.name(), e);
        }
    }

    /** A class a bridge transformer creates for a name no jar holds, defined here; {@code null} if none does. */
    private Class<?> generated(String name) {
        byte[] bytes = generatedBytes(name);
        if (bytes == null) {
            return null;
        }
        definePackageIfAbsent(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    /** The bytes a bridge's transformer generates for {@code name}, or {@code null}. */
    private byte[] generatedBytes(String name) {
        if (bridgeTransformers.isEmpty() || isPlatformOrPolyModLoader(name)) {
            return null;
        }
        for (BridgeClassTransformer bridgeTransformer : bridgeTransformers) {
            byte[] bytes = bridgeTransformer.generate(name);
            if (bytes != null) {
                return bytes;
            }
        }
        return null;
    }

    private byte[] bridgeTransform(String name, byte[] bytes) {
        byte[] result = bytes;
        for (BridgeClassTransformer bridgeTransformer : bridgeTransformers) {
            try {
                result = bridgeTransformer.transform(name, result);
            } catch (VirtualMachineError e) {
                throw e;
            } catch (RuntimeException | Error e) {
                // Mixin reports failures as Error (MixinTransformerError); Fabric's Knot wraps every one the same way.
                throw new BridgeTransformException(
                        "PLEXUS-COMPAT-133 " + bridgeTransformer.name() + " could not transform " + name + ": "
                                + e.getMessage(),
                        e);
            }
        }
        return result;
    }

    @Override
    public Optional<byte[]> classBytes(String className) {
        String name = className.replace('/', '.');
        String path = classFilePath(name);
        OwnedModule module = owningModule(name);
        if (module != null) {
            try {
                return Optional.of(transformer.inspect(name.replace('.', '/'), readModule(module, path)));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        }
        OwnedJar jar = isParentOnly(name) ? null : owningJar(path);
        if (jar == null && !isParentOnly(name)) {
            // A class of a jar a loader defines as a module later (the host's patched game, its mods): its bytes are
            // those the class will have, not those of a copy on Poly Mod Loader's class path (the vanilla game).
            jar = java.util.stream.Stream.concat(resourceJars.stream(), classBytesJars.stream())
                    .filter(resource -> resource.file().getEntry(path) != null)
                    .findFirst()
                    .orElse(null);
        }
        if (jar != null) {
            try {
                return Optional.of(transformer.inspect(name.replace('.', '/'), read(jar, path)));
            } catch (ClassNotFoundException e) {
                throw new UncheckedIOException(new IOException(e.getMessage(), e.getCause()));
            }
        }
        try (InputStream input = getParent().getResourceAsStream(path)) {
            return input == null ? Optional.empty() : Optional.of(input.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path + " from Poly Mod Loader's class path", e);
        }
    }

    @Override
    public synchronized void addJar(Path jar) throws IOException {
        Path normalized = jar.toAbsolutePath().normalize();
        for (OwnedJar owned : jars) {
            if (owned.path().toAbsolutePath().normalize().equals(normalized)) {
                return;
            }
        }
        CodeSource codeSource = new CodeSource(normalized.toUri().toURL(), (CodeSigner[]) null);
        JarFile file = new JarFile(normalized.toFile());
        jars.add(new OwnedJar(normalized, file, new ProtectionDomain(codeSource, null, this, null)));
        addURL(normalized.toUri().toURL());
    }

    @Override
    public List<Path> jars() {
        return jars.stream().map(OwnedJar::path).toList();
    }

    @Override
    public boolean isDefined(String className) {
        return findLoadedClass(className.replace('/', '.')) != null;
    }

    @Override
    public void close() throws IOException {
        jars.forEach(GameClassLoader::closeQuietly);
        resourceJars.forEach(GameClassLoader::closeQuietly);
        classBytesJars.forEach(GameClassLoader::closeQuietly);
        modulesByName.values().forEach(GameClassLoader::closeQuietly);
        super.close();
    }

    private OwnedJar open(Path jar) throws IOException {
        CodeSource codeSource = new CodeSource(jar.toUri().toURL(), (CodeSigner[]) null);
        return new OwnedJar(jar, new JarFile(jar.toFile()), new ProtectionDomain(codeSource, null, this, null));
    }

    private static byte[] read(OwnedJar jar, String path) throws ClassNotFoundException {
        try (InputStream input = jar.file().getInputStream(jar.file().getEntry(path))) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(
                    "Failed to read " + path + " from " + jar.file().getName(), e);
        }
    }

    private OwnedJar owningJar(String path) {
        for (OwnedJar jar : jars) {
            if (jar.file().getEntry(path) != null) {
                return jar;
            }
        }
        return null;
    }

    private void definePackageIfAbsent(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }
        String packageName = className.substring(0, lastDot);
        if (getDefinedPackage(packageName) == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException alreadyDefinedConcurrently) {
                // Another thread defined it between the check and the call; that is fine.
            }
        }
    }

    private static void closeQuietly(OwnedJar jar) {
        try {
            jar.file().close();
        } catch (IOException ignored) {
            // Closing is best effort; the loader is being discarded either way.
        }
    }

    private static void closeQuietly(OwnedModule module) {
        try {
            module.reader().close();
        } catch (IOException ignored) {
            // Closing is best effort; the loader is being discarded either way.
        }
    }

    private static URL toUrl(URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isParentOnly(String className) {
        if (isPlatformOrPolyModLoader(className)) {
            return true;
        }
        for (String prefix : sharedLibraryPrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlatformOrPolyModLoader(String className) {
        if (className.startsWith(GAME_LAYER_PREFIX)) {
            return false;
        }
        for (String prefix : PARENT_ONLY_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String classFilePath(String className) {
        return className.replace('.', '/') + ".class";
    }

    private static List<Path> concat(List<Path> first, List<Path> second) {
        List<Path> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static URL[] toUrls(List<Path> jars) {
        return jars.stream()
                .map(jar -> {
                    try {
                        return jar.toUri().toURL();
                    } catch (MalformedURLException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toArray(URL[]::new);
    }

    /**
     * A bridge transformer failed on a class ({@code PLEXUS-COMPAT-133}). The message names the transformer and the
     * class; the cause is the transformer's own error, which for Mixin names the mixin and its mod.
     */
    public static final class BridgeTransformException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BridgeTransformException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
