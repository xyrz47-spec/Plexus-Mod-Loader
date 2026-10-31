/*
 * Adapted from Fabric Loader 0.19.5, net.fabricmc.loader.impl.launch.knot.MixinServiceKnot.
 * Copyright 2016 FabricMC. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changed for Plexus Mod Loader: classes come from Plexus Mod Loader's game class
 * loader (GameClassBytes) instead of Knot; logging goes to SLF4J; see THIRD_PARTY_NOTICES.md.
 */
package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.api.runtime.GameSide;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * Mixin's view of Plexus Mod Loader (ADR-0020): class bytes and classes come from the game class loader, as Fabric Loader's
 * Knot service provides them from Knot. Mixin creates this class itself ({@code mixin.service}); it reads the launch
 * from {@link MixinLaunch}.
 *
 * <p>Class bytes handed to Mixin are those Plexus Mod Loader's own transformer produced (its hooks and access widenings),
 * as Fabric hands Mixin the bytes after its class tweaker: Mixin always runs last.
 */
public final class PlexusMixinService
        implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {

    private final ReEntranceLock lock = new ReEntranceLock(1);

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    /**
     * @param runTransformers ignored: Mixin asks without transformers only for its own mixin classes, which
     *     Plexus Mod Loader's transformer never changes
     */
    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        byte[] bytes =
                MixinLaunch.current().classes.classBytes(name).orElseThrow(() -> new ClassNotFoundException(name));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, readerFlags);
        return node;
    }

    /** Required by Mixin's interface although Mixin 0.8 no longer needs it. */
    @Deprecated
    @Override
    public URL[] getClassPath() {
        // Mixin 0.8 uses this only to find itself; the code source of this jar answers that.
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return MixinLaunch.current().gameClassLoader.loadClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, MixinLaunch.current().gameClassLoader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, PlexusMixinService.class.getClassLoader());
    }

    @Override
    public String getName() {
        return "Plexus Mod Loader/Fabric";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {}

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            MixinLaunch.current().transformer = factory.createTransformer();
        }
    }

    @Override
    public void init() {}

    @Override
    public void beginPhase() {}

    @Override
    public void checkEnv(Object bootSource) {}

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return new IAdviceProvider() {
            @Override
            public String higherCompatibilityNeeded(int requiredCompatibility, String requiredCompatibilityString) {
                return "the mod needs the Mixin of Fabric Loader "
                        + MixinCompatibility.minimumLoaderVersion(requiredCompatibility)
                                .orElse("(unknown)")
                        + " or newer; Plexus Mod Loader provides the Mixin of Fabric Loader "
                        + MixinLaunch.current().fabricLoaderVersion;
            }
        };
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        try {
            return new ContainerHandleURI(PlexusMixinService.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Plexus Mod Loader's Fabric bridge jar has no usable location", e);
        }
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return List.of();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return MixinLaunch.current().gameClassLoader.getResourceAsStream(name);
    }

    @Override
    public void registerInvalidClass(String className) {}

    /**
     * While Mixin selects a host loader's configurations ({@link FabricMixinBootstrap#addShared}), a target that is
     * already loaded is answered as not loaded and recorded (ADR-0026 43). Mixin would refuse such a mixin while it
     * prepares the configuration ({@code MixinTargetAlreadyLoadedException}), and its processor then drops that whole
     * configuration: all of Lithium's {@code lithium.mixins.json} after Iris's plugin had loaded {@code BlockGetter},
     * while other Lithium mixins that rely on it applied. Answered as not loaded, only the mixins into that class do not
     * apply, as under the host's own Mixin, whose service does not notice such a class either.
     */
    private static volatile boolean hostSelection;

    private static final java.util.Set<String> LOADED_BEFORE_HOST_SELECTION =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> LOADED_DURING_HOST_SELECTION =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Records {@code className} when it loads while Mixin selects a host's configurations. */
    static void loading(String className) {
        if (hostSelection) {
            LOADED_DURING_HOST_SELECTION.add(className.replace('/', '.'));
        }
    }

    private static final java.util.Set<String> ASKED_DURING_HOST_SELECTION =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The classes Mixin asked about during the last host selection: the targets of the mixins it prepared. */
    static java.util.Set<String> askedDuringHostSelection() {
        return java.util.Set.copyOf(ASKED_DURING_HOST_SELECTION);
    }

    /** The classes that loaded during the last host selection (Mixin applied no host mixin to them). */
    static java.util.Set<String> loadedDuringHostSelection() {
        return java.util.Set.copyOf(LOADED_DURING_HOST_SELECTION);
    }

    /** Starts or ends the host's selection; the classes recorded since the last start. */
    static java.util.Set<String> hostSelection(boolean running) {
        if (running) {
            LOADED_BEFORE_HOST_SELECTION.clear();
            LOADED_DURING_HOST_SELECTION.clear();
            ASKED_DURING_HOST_SELECTION.clear();
        }
        hostSelection = running;
        return java.util.Set.copyOf(LOADED_BEFORE_HOST_SELECTION);
    }

    @Override
    public boolean isClassLoaded(String className) {
        boolean loaded = MixinLaunch.current().classes.isDefined(className);
        if (hostSelection) {
            ASKED_DURING_HOST_SELECTION.add(className.replace('/', '.'));
        }
        if (loaded && hostSelection) {
            LOADED_BEFORE_HOST_SELECTION.add(className.replace('/', '.'));
            return false;
        }
        return loaded;
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return List.of();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return List.of();
    }

    @Override
    public void addTransformerExclusion(String name) {}

    /** {@code CLIENT} or {@code SERVER}, the names Mixin's side detection and Fabric's {@code EnvType} use. */
    @Override
    public String getSideName() {
        return MixinLaunch.current().side == GameSide.CLIENT ? "CLIENT" : "SERVER";
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    public ILogger getLogger(String name) {
        return MixinLog.get(name);
    }
}
