package dev.plexusmodloader.compat.forge.modlauncher;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.plexusmodloader.compat.bridge.BridgeClassTransformer;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ModLauncher's class transformation, as Plexus Mod Loader's game class loader stage (ADR-0021). For each class, in the
 * order ModLauncher documents for its services:
 *
 * <ol>
 *   <li>launch plugins that handle the class in the {@code BEFORE} phase ({@link ILaunchPluginService#handlesClass});
 *   <li>the transformation services' {@link ITransformer}s, by vote: pre-class transformers, then each field's and
 *       each method's, then class transformers;
 *   <li>launch plugins in the {@code AFTER} phase.
 * </ol>
 *
 * <p>A class nothing touches keeps its bytes. Written for Plexus Mod Loader from ModLauncher's public API; not ModLauncher's
 * code.
 *
 * <p>Thread: the game class loader calls it from any thread; the transformer registry is fixed before the first call.
 */
final class ForgeClassTransformer implements BridgeClassTransformer {

    private static final String FIELD_TO_METHOD_TRANSFORMER =
            "net.minecraftforge.forge.transformers.FieldToMethodTransformer";

    private final Collection<ILaunchPluginService> plugins;
    private final GameClassBytes classes;
    private final Map<String, List<ITransformer<?>>> transformersByTarget = new HashMap<>();
    private final Set<String> classesWithTransformers = new HashSet<>();
    /** How deep this thread is in {@link #transform(String, byte[], boolean)}. */
    private final ThreadLocal<Integer> processing = ThreadLocal.withInitial(() -> 0);

    ForgeClassTransformer(Collection<ILaunchPluginService> plugins, GameClassBytes classes) {
        this.plugins = plugins;
        this.classes = classes;
    }

    /** Registers a service's transformers under each of their targets; called before the first class is loaded. */
    void addTransformers(ITransformationService service) {
        for (ITransformer<?> transformer : service.transformers()) {
            for (ITransformer.Target target : transformer.targets()) {
                String internalName = target.className().replace('.', '/');
                classesWithTransformers.add(internalName);
                transformersByTarget
                        .computeIfAbsent(
                                key(
                                        internalName,
                                        target.targetType(),
                                        target.elementName(),
                                        target.elementDescriptor()),
                                k -> new ArrayList<>())
                        .add(transformer);
            }
        }
    }

    @Override
    public String name() {
        return "ModLauncher services (Plexus Mod Loader)";
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        byte[] result = transform(className, bytes, false);
        return result == null ? bytes : result;
    }

    /**
     * A class no jar holds, created by a launch plugin that handles empty classes (Mixin), or {@code null}. Not while a
     * plugin is processing a class on this thread: a missing class it asks for comes from its own code, which ModLauncher
     * loads in the BOOT layer, where nothing is generated (Mixin probes for classes while it creates its transformer).
     */
    @Override
    public byte[] generate(String className) {
        if (processing.get() > 0) {
            return null;
        }
        return transform(className, new byte[0], true);
    }

    /** The transformed class, or {@code null} when nothing changed it. */
    private byte[] transform(String className, byte[] input, boolean empty) {
        processing.set(processing.get() + 1);
        try {
            return transformOnce(className, input, empty);
        } finally {
            processing.set(processing.get() - 1);
        }
    }

    private byte[] transformOnce(String className, byte[] input, boolean empty) {
        String internalName = className.replace('.', '/');
        Type type = Type.getObjectType(internalName);
        String reason = ITransformerActivity.CLASSLOADING_REASON;
        EnumMap<ILaunchPluginService.Phase, List<ILaunchPluginService>> phases =
                new EnumMap<>(ILaunchPluginService.Phase.class);
        for (ILaunchPluginService plugin : plugins) {
            for (ILaunchPluginService.Phase phase : plugin.handlesClass(type, empty, reason)) {
                phases.computeIfAbsent(phase, p -> new ArrayList<>()).add(plugin);
            }
        }
        boolean hasTransformers = !empty && classesWithTransformers.contains(internalName);
        if (phases.isEmpty() && !hasTransformers) {
            return null;
        }
        ClassNode node = new ClassNode(Opcodes.ASM9);
        if (empty) {
            node.name = internalName;
            node.version = Opcodes.V1_8;
            node.superName = "java/lang/Object";
        } else {
            new ClassReader(input).accept(node, 0);
        }
        int before = offer(ILaunchPluginService.Phase.BEFORE, phases, node, type, reason);
        List<ILaunchPluginService> after = phases.getOrDefault(ILaunchPluginService.Phase.AFTER, List.of());
        if (before == ILaunchPluginService.ComputeFlags.NO_REWRITE && !hasTransformers && after.isEmpty()) {
            return null;
        }
        if (hasTransformers) {
            node = vote(internalName, input, node);
        }
        int afterFlags = offer(ILaunchPluginService.Phase.AFTER, phases, node, type, reason);
        if (before == ILaunchPluginService.ComputeFlags.NO_REWRITE
                && afterFlags == ILaunchPluginService.ComputeFlags.NO_REWRITE
                && !hasTransformers) {
            return null;
        }
        int flags = hasTransformers ? ClassWriter.COMPUTE_FRAMES : (before | afterFlags);
        ClassWriter writer = new HierarchyClassWriter(flags & (ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int offer(
            ILaunchPluginService.Phase phase,
            Map<ILaunchPluginService.Phase, List<ILaunchPluginService>> phases,
            ClassNode node,
            Type type,
            String reason) {
        int flags = ILaunchPluginService.ComputeFlags.NO_REWRITE;
        for (ILaunchPluginService plugin : phases.getOrDefault(phase, List.of())) {
            flags |= plugin.processClassWithFlags(phase, node, type, reason);
        }
        return flags;
    }

    private ClassNode vote(String internalName, byte[] input, ClassNode node) {
        VotingContext context = new VotingContext(internalName.replace('/', '.'), input);
        ClassNode result =
                vote(transformersFor(internalName, ITransformer.TargetType.PRE_CLASS, "", ""), node, context);
        List<FieldNode> fields = new ArrayList<>(result.fields.size());
        for (FieldNode field : result.fields) {
            fields.add(
                    vote(transformersFor(internalName, ITransformer.TargetType.FIELD, field.name, ""), field, context));
        }
        List<MethodNode> methods = new ArrayList<>(result.methods.size());
        for (MethodNode method : result.methods) {
            methods.add(vote(
                    transformersFor(internalName, ITransformer.TargetType.METHOD, method.name, method.desc),
                    method,
                    context));
        }
        result.fields = fields;
        result.methods = methods;
        return vote(transformersFor(internalName, ITransformer.TargetType.CLASS, "", ""), result, context);
    }

    @SuppressWarnings("unchecked")
    private <T> List<ITransformer<T>> transformersFor(
            String internalName, ITransformer.TargetType targetType, String name, String descriptor) {
        List<ITransformer<T>> found = new ArrayList<>();
        for (ITransformer<?> transformer :
                transformersByTarget.getOrDefault(key(internalName, targetType, name, descriptor), List.of())) {
            found.add((ITransformer<T>) transformer);
        }
        return found;
    }

    /**
     * ModLauncher's vote: every remaining transformer votes; a {@code REJECT} stops the class, {@code NO} drops the
     * transformer, the first {@code YES} transforms and leaves; {@code DEFER} with nothing else left is a deadlock.
     */
    private static <T> T vote(List<ITransformer<T>> transformers, T node, VotingContext context) {
        T current = node;
        context.node = current;
        while (!transformers.isEmpty()) {
            EnumMap<TransformerVoteResult, List<ITransformer<T>>> votes = new EnumMap<>(TransformerVoteResult.class);
            for (ITransformer<T> transformer : transformers) {
                votes.computeIfAbsent(transformer.castVote(context), v -> new ArrayList<>())
                        .add(transformer);
            }
            if (votes.containsKey(TransformerVoteResult.REJECT)) {
                throw new IllegalStateException("A ModLauncher transformer rejected " + context.getClassName() + ": "
                        + votes.get(TransformerVoteResult.REJECT));
            }
            transformers.removeAll(votes.getOrDefault(TransformerVoteResult.NO, List.of()));
            List<ITransformer<T>> yes = votes.get(TransformerVoteResult.YES);
            if (yes != null) {
                ITransformer<T> transformer = yes.get(0);
                current = transformWithPrivateFields(transformer, current, context);
                context.node = current;
                transformers.remove(transformer);
                continue;
            }
            if (votes.containsKey(TransformerVoteResult.DEFER)) {
                throw new IllegalStateException("ModLauncher transformers deadlocked on " + context.getClassName()
                        + ": " + votes.get(TransformerVoteResult.DEFER));
            }
        }
        return current;
    }

    /**
     * Runs {@code transformer}; Forge's {@code FieldToMethodTransformer} sees the class's instance fields private, as in
     * the game Forge patched. That transformer redirects reads of a private field to a getter (Forge's biome modifiers:
     * {@code Biome.climateSettings}) and refuses a field that is not private, but another loader's mod may have widened
     * it (Fabric API's biome class tweaker, ADR-0021 G8). The redirect applies as on the unwidened class, and the field
     * keeps the access the widening gave it afterwards.
     */
    private static <T> T transformWithPrivateFields(ITransformer<T> transformer, T node, VotingContext context) {
        if (!(node instanceof ClassNode classNode)
                || !transformer.getClass().getName().equals(FIELD_TO_METHOD_TRANSFORMER)) {
            return transformer.transform(node, context);
        }
        Map<FieldNode, Integer> widened = new HashMap<>();
        for (FieldNode field : classNode.fields) {
            if ((field.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) {
                widened.put(field, field.access);
                field.access = (field.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;
            }
        }
        try {
            return transformer.transform(node, context);
        } finally {
            widened.forEach((field, access) -> field.access = access);
        }
    }

    private static String key(String internalName, ITransformer.TargetType type, String name, String descriptor) {
        return type + " " + internalName + " " + name + " " + descriptor;
    }

    /** Computes frames from class files the game class loader can read, without loading those classes. */
    private final class HierarchyClassWriter extends ClassWriter {

        HierarchyClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String first, String second) {
            if (isInterface(first) || isInterface(second)) {
                return "java/lang/Object";
            }
            Set<String> ancestors = new HashSet<>();
            for (String type = first; type != null; type = superName(type)) {
                ancestors.add(type);
            }
            for (String type = second; type != null; type = superName(type)) {
                if (ancestors.contains(type)) {
                    return type;
                }
            }
            return "java/lang/Object";
        }

        private boolean isInterface(String internalName) {
            return header(internalName)
                    .map(reader -> (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0)
                    .orElse(false);
        }

        private String superName(String internalName) {
            return header(internalName).map(ClassReader::getSuperName).orElse(null);
        }

        private Optional<ClassReader> header(String internalName) {
            return classes.classBytes(internalName).map(ClassReader::new);
        }
    }

    /** What a transformer sees while voting and transforming. */
    private static final class VotingContext implements ITransformerVotingContext {

        private final String className;
        private final byte[] input;
        private Object node;

        VotingContext(String className, byte[] input) {
            this.className = className;
            this.input = input;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean doesClassExist() {
            return input.length > 0;
        }

        @Override
        public byte[] getInitialClassSha256() {
            try {
                return MessageDigest.getInstance("SHA-256").digest(input);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Every Java runtime has SHA-256", e);
            }
        }

        @Override
        public List<ITransformerActivity> getAuditActivities() {
            return List.of();
        }

        @Override
        public String getReason() {
            return ITransformerActivity.CLASSLOADING_REASON;
        }

        @Override
        public boolean applyFieldPredicate(FieldPredicate predicate) {
            return node instanceof FieldNode field
                    && predicate.test(field.access, field.name, field.desc, field.signature, field.value);
        }

        @Override
        public boolean applyMethodPredicate(MethodPredicate predicate) {
            return node instanceof MethodNode method
                    && predicate.test(
                            method.access,
                            method.name,
                            method.desc,
                            method.signature,
                            method.exceptions.toArray(String[]::new));
        }

        @Override
        public boolean applyClassPredicate(ClassPredicate predicate) {
            return node instanceof ClassNode clazz
                    && predicate.test(
                            clazz.version,
                            clazz.access,
                            clazz.name,
                            clazz.signature,
                            clazz.superName,
                            clazz.interfaces.toArray(String[]::new));
        }

        @Override
        public boolean applyInstructionPredicate(InsnPredicate predicate) {
            if (!(node instanceof MethodNode method)) {
                return false;
            }
            int count = 0;
            for (var instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction.getOpcode() >= 0 && predicate.test(count++, instruction.getOpcode())) {
                    return true;
                }
            }
            return false;
        }
    }
}
