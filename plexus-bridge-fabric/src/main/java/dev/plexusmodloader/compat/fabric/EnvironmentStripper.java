/*
 * Adapted from Fabric Loader 0.19.5: net.fabricmc.loader.impl.transformer.EnvironmentStrippingData, ClassStripper and
 * FabricTransformer, and the class split of net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider
 * .getBuiltinTransforms. Copyright 2016 FabricMC. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). See THIRD_PARTY_NOTICES.md.
 *
 * The Quilt part (QuiltScope, QuiltStrippingData, LambdaStripCalculator and applyQuilt) is adapted from Quilt Loader
 * 0.31.0-beta.4: org.quiltmc.loader.impl.transformer.AbstractStripData, ClassStrippingData, PackageStrippingData,
 * LambdaStripCalculator and QuiltTransformer, and the package check of
 * org.quiltmc.loader.impl.launch.knot.KnotClassDelegate. Copyright 2022, 2023 QuiltMC. Licensed under the Apache
 * License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0). Changes: applied to the classes of Quilt mod jars
 * only (Quilt Loader applies it to every mod), a class or package for the other side fails as it loads instead of being
 * hidden by a transform cache, and @Requires checks the IDs of every loaded mod of every loader, their provided IDs and
 * the built-in ones. See THIRD_PARTY_NOTICES.md.
 */
package dev.plexusmodloader.compat.fabric;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.fabric.loader.FabricFamilyMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.LambdaMetafactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;

/**
 * Fabric Loader's environment stripping (ADR-0020): in a mod or library class (anything but Minecraft's own), members
 * annotated {@code @Environment(EnvType.CLIENT)} are removed on a dedicated server, and {@code SERVER} ones on a
 * client; {@code @EnvironmentInterface} removes an interface the same way. A class annotated for the other side cannot
 * be loaded at all. Writes to removed fields in constructors and static initialisers are removed with them. Mods rely
 * on this to keep client-only methods in common classes.
 *
 * <p>Classes of the mods a host loader runs (Forge's, ADR-0021) are left alone: their loader ignores Fabric's
 * annotations, which a mod built from code shared with its Fabric build still carries.
 *
 * <p>Classes of Quilt mod jars ({@link QuiltScope}) are stripped as Quilt Loader strips them (ADR-0027 decision 9): on
 * top of Fabric's annotations, {@code @ClientOnly} and {@code @DedicatedServerOnly} remove a member, an interface (as a
 * type annotation on the {@code implements} clause) or, on a class or its package's {@code package-info}, refuse the
 * whole class or package for the other side; {@code @Requires(mod IDs)} does the same when a named mod is not loaded.
 * With {@code stripLambdas} (the default) a removed method's lambda bodies go with it, unless another method uses them.
 * Fabric mods' classes keep Fabric Loader's behaviour: only {@code @Environment} counts.
 *
 * <p>Thread: immutable; safe from any thread.
 */
final class EnvironmentStripper {

    private static final String ENVIRONMENT = "Lnet/fabricmc/api/Environment;";
    private static final String ENVIRONMENT_INTERFACE = "Lnet/fabricmc/api/EnvironmentInterface;";
    private static final String ENVIRONMENT_INTERFACES = "Lnet/fabricmc/api/EnvironmentInterfaces;";
    private static final String CLIENT_ONLY = "Lorg/quiltmc/loader/api/minecraft/ClientOnly;";
    private static final String DEDICATED_SERVER_ONLY = "Lorg/quiltmc/loader/api/minecraft/DedicatedServerOnly;";
    private static final String REQUIRES = "Lorg/quiltmc/loader/api/Requires;";
    private static final byte[] MARKER = "net/fabricmc/api/Environment".getBytes(StandardCharsets.UTF_8);
    private static final List<byte[]> QUILT_MARKERS = List.of(
            MARKER,
            "org/quiltmc/loader/api/minecraft/ClientOnly".getBytes(StandardCharsets.UTF_8),
            "org/quiltmc/loader/api/minecraft/DedicatedServerOnly".getBytes(StandardCharsets.UTF_8),
            "org/quiltmc/loader/api/Requires".getBytes(StandardCharsets.UTF_8));
    private static final String LAMBDA_FACTORY = Type.getInternalName(LambdaMetafactory.class);
    private static final String LAMBDA_FACTORY_DESCRIPTOR = "(Ljava/lang/invoke/MethodHandles$Lookup;"
            + "Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final List<String> MINECRAFT_PACKAGES = List.of(
            "net.minecraft.",
            "com.mojang.minecraft.",
            "com.mojang.rubydung.",
            "com.mojang.blaze3d.",
            "com.mojang.renderpearl.",
            "com.mojang.math.",
            "com.mojang.realmsclient.");
    /** IDs that are always loaded: the game, Java, and the loaders Poly Mod Loader stands in for. */
    private static final Set<String> BUILT_IN_IDS =
            Set.of("minecraft", "java", "quilt_loader", "fabricloader", "mixinextras");

    /**
     * The classes Quilt's annotations apply to: those of the loaded Quilt mods' jars.
     *
     * @param classes binary names of the classes in the Quilt mods' jars
     * @param packageInfos the {@code package-info} class of each package (dotted name) in those jars
     * @param modIds what {@code @Requires} finds: the IDs and provided IDs of every loaded mod of every loader and the
     *     built-in IDs
     */
    record QuiltScope(Set<String> classes, Map<String, byte[]> packageInfos, Set<String> modIds) {

        /** No Quilt mods. */
        static final QuiltScope NONE = new QuiltScope(Set.of(), Map.of(), Set.of());

        QuiltScope {
            classes = Set.copyOf(classes);
            packageInfos = Map.copyOf(packageInfos);
            modIds = Set.copyOf(modIds);
        }

        /** The scope of the Quilt mods {@code context} loads ({@link FabricFamilyMod#QUILT}). */
        static QuiltScope of(BridgeLaunchContext context) {
            List<Path> jars = context.mods().stream()
                    .filter(mod -> mod.loader().equals(FabricFamilyMod.QUILT) && !mod.runByHost())
                    .map(BridgeLaunchContext.LoadedMod::jar)
                    .distinct()
                    .toList();
            if (jars.isEmpty()) {
                return NONE;
            }
            Set<String> ids = new HashSet<>(BUILT_IN_IDS);
            for (BridgeLaunchContext.LoadedMod mod : context.mods()) {
                ids.add(mod.modId());
                ids.addAll(mod.providedIds());
            }
            Map<String, byte[]> packageInfos = new LinkedHashMap<>();
            for (Path jar : jars) {
                try (JarFile file = new JarFile(jar.toFile())) {
                    Enumeration<JarEntry> entries = file.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.endsWith("/package-info.class") || name.startsWith("META-INF/")) {
                            continue;
                        }
                        String packageName = name.substring(0, name.length() - "/package-info.class".length())
                                .replace('/', '.');
                        if (!packageInfos.containsKey(packageName)) {
                            try (InputStream input = file.getInputStream(entry)) {
                                packageInfos.put(packageName, input.readAllBytes());
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot read the package-info classes of " + jar.getFileName(), e);
                }
            }
            return new QuiltScope(classesIn(jars), packageInfos, ids);
        }
    }

    private final String envType;
    private final Set<String> hostClasses;
    private final QuiltScope quilt;
    /** Why each package of a Quilt mod cannot load on this side, from its {@code package-info}. */
    private final Map<String, String> deniedPackages;

    /** @param hostClasses binary names of the classes in the jars of the mods a host loader runs */
    EnvironmentStripper(GameSide side, Set<String> hostClasses) {
        this(side, hostClasses, QuiltScope.NONE);
    }

    /**
     * @param hostClasses binary names of the classes in the jars of the mods a host loader runs
     * @param quilt the classes Quilt's annotations apply to
     */
    EnvironmentStripper(GameSide side, Set<String> hostClasses, QuiltScope quilt) {
        this.envType = side == GameSide.CLIENT ? "CLIENT" : "SERVER";
        this.hostClasses = Set.copyOf(hostClasses);
        this.quilt = quilt;
        Map<String, String> denied = new HashMap<>();
        quilt.packageInfos().forEach((packageName, bytes) -> {
            QuiltStrippingData data = new QuiltStrippingData();
            new ClassReader(bytes).accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            if (!data.denyReasons.isEmpty()) {
                denied.put(packageName, data.summarizeDenyReasons());
            }
        });
        this.deniedPackages = Map.copyOf(denied);
    }

    /** Binary names of the classes in {@code jars}. */
    static Set<String> classesIn(Collection<Path> jars) {
        Set<String> classes = new HashSet<>();
        for (Path jar : jars) {
            try (JarFile file = new JarFile(jar.toFile())) {
                file.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class") && !name.startsWith("META-INF/"))
                        .forEach(name -> classes.add(name.substring(0, name.length() - ".class".length())
                                .replace('/', '.')));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list the classes of " + jar.getFileName(), e);
            }
        }
        return classes;
    }

    /** Whether Fabric Loader strips {@code className} (binary name): every class but Minecraft's own. */
    static boolean applies(String className) {
        String name = className.replace('/', '.');
        return name.indexOf('.') >= 0 && MINECRAFT_PACKAGES.stream().noneMatch(name::startsWith);
    }

    /**
     * The class without its members for the other side.
     *
     * @return {@code bytes} itself when nothing is removed
     * @throws IllegalStateException when the whole class (or a Quilt mod's package) is for the other side, with Fabric
     *     Loader's or Quilt Loader's message
     */
    byte[] apply(String className, byte[] bytes) {
        String dotted = className.replace('/', '.');
        if (quilt.classes().contains(dotted)) {
            return applyQuilt(dotted, bytes);
        }
        if (!applies(className) || !contains(bytes, MARKER) || hostClasses.contains(dotted)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        StrippingData data = new StrippingData();
        reader.accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        if (data.stripEntireClass) {
            throw new IllegalStateException("Cannot load class " + dotted + " in environment type " + envType);
        }
        if (data.isEmpty()) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new Stripper(writer, data.stripInterfaces, data.stripFields, data.stripMethods), 0);
        return writer.toByteArray();
    }

    /** Quilt Loader's {@code QuiltTransformer} stripping step, with Knot's package check before it. */
    private byte[] applyQuilt(String className, byte[] bytes) {
        int dot = className.lastIndexOf('.');
        if (dot > 0) {
            String packageName = className.substring(0, dot);
            String reason = deniedPackages.get(packageName);
            if (reason != null) {
                throw new IllegalStateException("Cannot load package " + packageName + " " + reason);
            }
        }
        if (!applies(className) || QUILT_MARKERS.stream().noneMatch(marker -> contains(bytes, marker))) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        QuiltStrippingData data = new QuiltStrippingData();
        reader.accept(data, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        if (!data.denyReasons.isEmpty()) {
            throw new IllegalStateException("Cannot load " + className + " " + data.summarizeDenyReasons());
        }
        if (data.isEmpty()) {
            return bytes;
        }
        Collection<String> methods = data.stripMethods;
        boolean lambdasStripped = false;
        if (!data.stripMethodLambdas.isEmpty()) {
            LambdaStripCalculator lambdas = new LambdaStripCalculator(data.stripMethodLambdas);
            reader.accept(lambdas, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Set<String> additional = lambdas.additionalMethodsToStrip();
            if (!additional.isEmpty()) {
                methods = new HashSet<>(methods);
                methods.addAll(additional);
                lambdasStripped = true;
            }
        }
        // Without the removed lambdas' bootstrap methods the constant pool cannot be copied as it is.
        ClassWriter writer = lambdasStripped ? new ClassWriter(0) : new ClassWriter(reader, 0);
        reader.accept(new Stripper(writer, data.stripInterfaces, data.stripFields, methods), 0);
        return writer.toByteArray();
    }

    private static boolean contains(byte[] bytes, byte[] marker) {
        outer:
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (bytes[i + j] != marker[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** Finds what to strip: Fabric Loader's {@code EnvironmentStrippingData}. */
    private final class StrippingData extends ClassVisitor {

        boolean stripEntireClass;
        final Collection<String> stripInterfaces = new HashSet<>();
        final Collection<String> stripFields = new HashSet<>();
        final Collection<String> stripMethods = new HashSet<>();

        StrippingData() {
            super(Opcodes.ASM9);
        }

        boolean isEmpty() {
            return stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ENVIRONMENT.equals(descriptor)) {
                return new EnvironmentValue(() -> stripEntireClass = true);
            } else if (ENVIRONMENT_INTERFACE.equals(descriptor)) {
                return new InterfaceValue(stripInterfaces);
            } else if (ENVIRONMENT_INTERFACES.equals(descriptor)) {
                return new InterfacesValue(stripInterfaces);
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return ENVIRONMENT.equals(annotation)
                            ? new EnvironmentValue(() -> stripFields.add(name + descriptor))
                            : null;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return ENVIRONMENT.equals(annotation)
                            ? new EnvironmentValue(() -> stripMethods.add(name + descriptor))
                            : null;
                }
            };
        }
    }

    /** {@code @Environment(value)}: runs {@code onMismatch} when the value is the other side. */
    private final class EnvironmentValue extends AnnotationVisitor {
        private final Runnable onMismatch;

        EnvironmentValue(Runnable onMismatch) {
            super(Opcodes.ASM9);
            this.onMismatch = onMismatch;
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("value".equals(name) && !envType.equals(value)) {
                onMismatch.run();
            }
        }
    }

    /** {@code @EnvironmentInterface(value, itf)}: strips {@code itf} when the value is the other side. */
    private final class InterfaceValue extends AnnotationVisitor {
        private final Collection<String> stripInterfaces;
        private boolean mismatch;
        private Type itf;

        InterfaceValue(Collection<String> stripInterfaces) {
            super(Opcodes.ASM9);
            this.stripInterfaces = stripInterfaces;
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("value".equals(name) && !envType.equals(value)) {
                mismatch = true;
            }
        }

        @Override
        public void visit(String name, Object value) {
            if ("itf".equals(name)) {
                itf = (Type) value;
            }
        }

        @Override
        public void visitEnd() {
            if (mismatch && itf != null) {
                stripInterfaces.add(itf.getInternalName());
            }
        }
    }

    /** {@code @EnvironmentInterfaces({...})}: each one as {@link InterfaceValue}. */
    private final class InterfacesValue extends AnnotationVisitor {
        private final Collection<String> stripInterfaces;

        InterfacesValue(Collection<String> stripInterfaces) {
            super(Opcodes.ASM9);
            this.stripInterfaces = stripInterfaces;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if (!"value".equals(name)) {
                return null;
            }
            return new AnnotationVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                    return new InterfaceValue(stripInterfaces);
                }
            };
        }
    }

    /**
     * Finds what to strip in a Quilt mod's class: Quilt Loader's {@code ClassStrippingData}, which reads Fabric's
     * annotations and Quilt's ({@code @ClientOnly}, {@code @DedicatedServerOnly}, {@code @Requires}). On a {@code
     * package-info} class its class-level reasons are the package's ({@code PackageStrippingData}).
     */
    private final class QuiltStrippingData extends ClassVisitor {

        final List<String> denyReasons = new ArrayList<>();
        final Collection<String> stripInterfaces = new HashSet<>();
        final Collection<String> stripFields = new HashSet<>();
        final Collection<String> stripMethods = new HashSet<>();
        /** The removed methods whose lambda bodies go with them; each is also in {@link #stripMethods}. */
        final Collection<String> stripMethodLambdas = new HashSet<>();

        private String type = "class";
        private String[] interfaces = new String[0];

        QuiltStrippingData() {
            super(Opcodes.ASM9);
        }

        boolean isEmpty() {
            return stripInterfaces.isEmpty() && stripFields.isEmpty() && stripMethods.isEmpty();
        }

        String summarizeDenyReasons() {
            if (denyReasons.size() == 1) {
                return "because " + denyReasons.getFirst();
            }
            StringBuilder text = new StringBuilder("because:");
            denyReasons.forEach(reason -> text.append("\n- ").append(reason));
            return text.toString();
        }

        private boolean onServer() {
            return envType.equals("SERVER");
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.interfaces = interfaces == null ? new String[0] : interfaces;
            if (name.endsWith("/package-info")) {
                type = "package";
            } else if ((access & Opcodes.ACC_ENUM) != 0) {
                type = "enum";
            } else if ((access & Opcodes.ACC_RECORD) != 0) {
                type = "record";
            } else if ((access & Opcodes.ACC_INTERFACE) != 0) {
                type = "interface";
            } else if ((access & Opcodes.ACC_ANNOTATION) != 0) {
                type = "annotation";
            } else {
                type = "class";
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            switch (descriptor) {
                case CLIENT_ONLY -> {
                    if (onServer()) {
                        denyReasons.add("the " + type + " is annotated with @ClientOnly but we're on the dedicated"
                                + " server");
                    }
                    return null;
                }
                case DEDICATED_SERVER_ONLY -> {
                    if (!onServer()) {
                        denyReasons.add(
                                "the " + type + " is annotated with @DedicatedServerOnly but we're on the client");
                    }
                    return null;
                }
                case REQUIRES -> {
                    return new RequiresValue((required, missing) -> denyReasons.add("the " + type
                            + " is annotated with @Requires(" + required + ") but the mods " + missing
                            + " are not loaded!"));
                }
                case ENVIRONMENT -> {
                    return new EnvironmentValue(() -> denyReasons.add("the " + type + " is annotated with"
                            + " @Environment for the other side (" + (onServer() ? "CLIENT" : "SERVER") + ")"));
                }
                case ENVIRONMENT_INTERFACE -> {
                    return new InterfaceValue(stripInterfaces);
                }
                case ENVIRONMENT_INTERFACES -> {
                    return new InterfacesValue(stripInterfaces);
                }
                default -> {
                    return null;
                }
            }
        }

        /** An annotation on an {@code implements} clause removes that interface. */
        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef, TypePath typePath, String descriptor, boolean visible) {
            TypeReference reference = new TypeReference(typeRef);
            if (reference.getSort() != TypeReference.CLASS_EXTENDS) {
                return null;
            }
            int index = reference.getSuperTypeIndex();
            if (index < 0 || index >= interfaces.length) {
                // On the superclass: Quilt Loader ignores it.
                return null;
            }
            String itf = interfaces[index];
            return switch (descriptor) {
                case REQUIRES -> new RequiresValue((required, missing) -> stripInterfaces.add(itf));
                case CLIENT_ONLY -> {
                    if (onServer()) {
                        stripInterfaces.add(itf);
                    }
                    yield null;
                }
                case DEDICATED_SERVER_ONLY -> {
                    if (!onServer()) {
                        stripInterfaces.add(itf);
                    }
                    yield null;
                }
                default -> null;
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return memberAnnotation(annotation, () -> stripFields.add(name + descriptor), null);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            String method = name + descriptor;
            return new MethodVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return memberAnnotation(
                            annotation, () -> stripMethods.add(method), () -> stripMethodLambdas.add(method));
                }
            };
        }

        /**
         * @param onLambdas runs too when the member's lambdas go with it ({@code stripLambdas}); {@code null} for a
         *     field
         */
        private AnnotationVisitor memberAnnotation(String descriptor, Runnable onMismatch, Runnable onLambdas) {
            return switch (descriptor) {
                case ENVIRONMENT -> new EnvironmentValue(onMismatch);
                case CLIENT_ONLY -> onServer() ? new SideValue(onMismatch, onLambdas) : null;
                case DEDICATED_SERVER_ONLY -> onServer() ? null : new SideValue(onMismatch, onLambdas);
                case REQUIRES ->
                    new RequiresValue(
                            (required, missing) -> {
                                onMismatch.run();
                                return true;
                            },
                            onLambdas);
                default -> null;
            };
        }
    }

    /** {@code @ClientOnly}/{@code @DedicatedServerOnly} for the other side on a member: removes it. */
    private static final class SideValue extends AnnotationVisitor {
        private final Runnable onMismatch;
        private final Runnable onLambdas;
        private boolean stripLambdas = true;

        SideValue(Runnable onMismatch, Runnable onLambdas) {
            super(Opcodes.ASM9);
            this.onMismatch = onMismatch;
            this.onLambdas = onLambdas;
        }

        @Override
        public void visit(String name, Object value) {
            if ("stripLambdas".equals(name) && Boolean.FALSE.equals(value)) {
                stripLambdas = false;
            }
        }

        @Override
        public void visitEnd() {
            onMismatch.run();
            if (stripLambdas && onLambdas != null) {
                onLambdas.run();
            }
        }
    }

    /** What to do when a {@code @Requires} names mods that are not loaded. */
    @FunctionalInterface
    private interface OnModsMissing {
        /**
         * @return whether the annotated member's lambdas may go with it (a member's; ignored for classes)
         */
        boolean missing(List<String> required, List<String> missing);
    }

    /** {@code @Requires(value, stripLambdas)}: acts when a named mod is not loaded. */
    private final class RequiresValue extends AnnotationVisitor {
        private final OnModsMissing onMissing;
        private final Runnable onLambdas;
        private final List<String> required = new ArrayList<>();
        private final List<String> missing = new ArrayList<>();
        private boolean stripLambdas = true;

        RequiresValue(java.util.function.BiConsumer<List<String>, List<String>> onMissing) {
            this(
                    (required, missing) -> {
                        onMissing.accept(required, missing);
                        return false;
                    },
                    null);
        }

        RequiresValue(OnModsMissing onMissing, Runnable onLambdas) {
            super(Opcodes.ASM9);
            this.onMissing = onMissing;
            this.onLambdas = onLambdas;
        }

        @Override
        public void visit(String name, Object value) {
            if ("stripLambdas".equals(name) && Boolean.FALSE.equals(value)) {
                stripLambdas = false;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if (!"value".equals(name)) {
                return null;
            }
            return new AnnotationVisitor(api) {
                @Override
                public void visit(String unused, Object value) {
                    String mod = String.valueOf(value);
                    required.add(mod);
                    if (!quilt.modIds().contains(mod)) {
                        missing.add(mod);
                    }
                }
            };
        }

        @Override
        public void visitEnd() {
            if (missing.isEmpty()) {
                return;
            }
            boolean lambdas = onMissing.missing(List.copyOf(required), List.copyOf(missing));
            if (lambdas && stripLambdas && onLambdas != null) {
                onLambdas.run();
            }
        }
    }

    /**
     * Quilt Loader's {@code LambdaStripCalculator}: the lambda bodies ({@code LambdaMetafactory.metafactory} targets in
     * this class) that only removed methods use.
     */
    private static final class LambdaStripCalculator extends ClassVisitor {

        private final Collection<String> strippedMethods;
        private final Set<String> usedByStripped = new HashSet<>();
        private final Set<String> usedByKept = new HashSet<>();
        private String className = "";

        LambdaStripCalculator(Collection<String> strippedMethods) {
            super(Opcodes.ASM9);
            this.strippedMethods = strippedMethods;
        }

        Set<String> additionalMethodsToStrip() {
            Set<String> strip = new HashSet<>(usedByStripped);
            strip.removeAll(usedByKept);
            return strip;
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            Set<String> into = strippedMethods.contains(name + descriptor) ? usedByStripped : usedByKept;
            return new MethodVisitor(api) {
                @Override
                public void visitInvokeDynamicInsn(
                        String indyName, String indyDescriptor, Handle bootstrap, Object... arguments) {
                    if (bootstrap.getTag() == Opcodes.H_INVOKESTATIC
                            && bootstrap.getName().equals("metafactory")
                            && bootstrap.getOwner().equals(LAMBDA_FACTORY)
                            && bootstrap.getDesc().equals(LAMBDA_FACTORY_DESCRIPTOR)
                            && arguments.length == 3
                            && arguments[1] instanceof Handle target
                            && target.getOwner().equals(className)) {
                        into.add(target.getName() + target.getDesc());
                    }
                }
            };
        }
    }

    /** Removes what the stripping data found: Fabric Loader's and Quilt Loader's {@code ClassStripper}. */
    private static final class Stripper extends ClassVisitor {

        private final Collection<String> stripInterfaces;
        private final Collection<String> stripFields;
        private final Collection<String> stripMethods;
        private String className;

        Stripper(
                ClassVisitor next,
                Collection<String> stripInterfaces,
                Collection<String> stripFields,
                Collection<String> stripMethods) {
            super(Opcodes.ASM9, next);
            this.stripInterfaces = stripInterfaces;
            this.stripFields = stripFields;
            this.stripMethods = stripMethods;
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            String[] kept = interfaces;
            if (!stripInterfaces.isEmpty() && interfaces != null) {
                List<String> list = new ArrayList<>();
                for (String itf : interfaces) {
                    if (!stripInterfaces.contains(itf)) {
                        list.add(itf);
                    }
                }
                kept = list.toArray(String[]::new);
            }
            super.visit(version, access, name, signature, superName, kept);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return stripFields.contains(name + descriptor)
                    ? null
                    : super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            if (stripMethods.contains(name + descriptor)) {
                return null;
            }
            MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (stripFields.isEmpty()) {
                return method;
            }
            int opcodeToStrip;
            switch (name) {
                case "<clinit>" -> opcodeToStrip = Opcodes.PUTSTATIC;
                case "<init>" -> opcodeToStrip = Opcodes.PUTFIELD;
                default -> {
                    return method;
                }
            }
            // Writes to removed fields in constructors and static initialisers become pops of their values.
            return new MethodVisitor(api, method) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                    if (opcode != opcodeToStrip
                            || !owner.equals(className)
                            || !stripFields.contains(field + fieldDescriptor)) {
                        super.visitFieldInsn(opcode, owner, field, fieldDescriptor);
                    } else {
                        super.visitInsn(Type.getType(fieldDescriptor).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
                        if (opcode == Opcodes.PUTFIELD) {
                            super.visitInsn(Opcodes.POP);
                        }
                    }
                }
            };
        }
    }
}
