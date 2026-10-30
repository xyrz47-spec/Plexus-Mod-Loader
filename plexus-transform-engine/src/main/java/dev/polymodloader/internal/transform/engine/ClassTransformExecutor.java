package dev.polymodloader.internal.transform.engine;

import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.MethodTransformAction;
import dev.polymodloader.internal.transform.RedirectCall;
import dev.polymodloader.internal.transform.TransformProblem;
import dev.polymodloader.internal.transform.TransformProblemCode;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Applies a {@link TransformPlan} to class bytes as classes are loaded. The input array is never
 * modified; a transformed class is returned as a new array.
 *
 * <p>The writer copies the original constant pool and does not recompute frames or max stack.
 * Recomputing frames would require loading other classes from inside class loading, which is a
 * recursion risk (design doc §12.4); the current actions never change stack shape, so it is not
 * needed. Every transformed class is checked structurally with ASM's {@link CheckClassAdapter}
 * before it is handed back.
 */
public final class ClassTransformExecutor {

    private final TransformPlan plan;
    private final Optional<Path> dumpDirectory;
    private final Consumer<TransformReportEntry> transformListener;
    private final Optional<TransformCache> cache;
    private final String engineFingerprint;
    private final List<TransformReportEntry> report = new CopyOnWriteArrayList<>();

    /** Creates an executor without a cache. */
    public ClassTransformExecutor(
            TransformPlan plan, Optional<Path> dumpDirectory, Consumer<TransformReportEntry> transformListener) {
        this(plan, dumpDirectory, transformListener, Optional.empty(), fingerprint("unversioned"));
    }

    /**
     * @param plan the validated plan
     * @param dumpDirectory when present, before/after class files are written there for debugging
     * @param transformListener called once per transformed class, e.g. to log the transformation report
     * @param cache when present, transformed classes are stored and reused across launches
     * @param engineFingerprint identifies the engine build; see {@link #fingerprint(String)}
     */
    public ClassTransformExecutor(
            TransformPlan plan,
            Optional<Path> dumpDirectory,
            Consumer<TransformReportEntry> transformListener,
            Optional<TransformCache> cache,
            String engineFingerprint) {
        this.plan = plan;
        this.dumpDirectory = dumpDirectory;
        this.transformListener = transformListener;
        this.cache = cache;
        this.engineFingerprint = engineFingerprint;
    }

    /**
     * Fingerprint of everything in the engine that can change transformation output: the Poly Mod Loader
     * version, the engine revision, and the ASM API level and version.
     */
    public static String fingerprint(String polyVersion) {
        String asmVersion = Optional.ofNullable(ClassReader.class.getPackage().getImplementationVersion())
                .orElse("unknown");
        // engine=2: rules on one method are combined in declared order; access widening.
        // engine=3: extendable constructors; interface fields stay final when made mutable.
        // engine=4: call-site observers (InvokeHookAtCall).
        return "poly=" + polyVersion + ";engine=4;asm-api=" + Opcodes.ASM9 + ";asm=" + asmVersion;
    }

    /** Whether the plan has rules or access widenings for {@code internalName}. */
    public boolean hasRulesFor(String internalName) {
        return plan.rulesByClass().containsKey(internalName)
                || plan.wideningsByClass().containsKey(internalName);
    }

    /**
     * Returns the transformed class file, or {@code original} itself when no rule or access widening
     * targets the class.
     *
     * @throws dev.polymodloader.internal.transform.TransformException when a planned rule no longer
     *     matches or the result fails verification
     */
    public byte[] transform(String internalName, byte[] original) {
        List<TransformRuleDefinition> rules = plan.rulesByClass().getOrDefault(internalName, List.of());
        List<AccessWidening> widenings = plan.wideningsByClass().getOrDefault(internalName, List.of());
        if (rules.isEmpty() && widenings.isEmpty()) {
            return original;
        }
        TransformCacheKey key = TransformCacheKey.of(internalName, original, rules, widenings, engineFingerprint);
        Optional<byte[]> cached = cache.flatMap(store -> store.get(key));
        if (cached.isPresent()) {
            record(internalName, rules, widenings, original, cached.get(), true);
            return cached.get().clone();
        }
        byte[] transformed = transformUncached(internalName, original, rules, widenings);
        cache.ifPresent(store -> store.put(key, transformed));
        record(internalName, rules, widenings, original, transformed, false);
        return transformed;
    }

    /**
     * The first of two passes for a class that another transformer (a loader bridge's Mixin) changes in between: every
     * rule and access widening except call redirects ({@link RedirectCall}), which {@link #transformLate} applies
     * afterwards (ADR-0020). The other transformer then sees each call Poly Mod Loader redirects as the game has it, so its
     * own injections at that call still find it.
     */
    public byte[] transformEarly(String internalName, byte[] original) {
        List<TransformRuleDefinition> rules = earlyRules(internalName);
        List<AccessWidening> widenings = plan.wideningsByClass().getOrDefault(internalName, List.of());
        if (rules.isEmpty() && widenings.isEmpty()) {
            return original;
        }
        TransformCacheKey key = TransformCacheKey.of(internalName, original, rules, widenings, engineFingerprint);
        Optional<byte[]> cached = cache.flatMap(store -> store.get(key));
        if (cached.isPresent()) {
            record(internalName, rules, widenings, original, cached.get(), true);
            return cached.get().clone();
        }
        byte[] transformed = transformUncached(internalName, original, rules, widenings);
        cache.ifPresent(store -> store.put(key, transformed));
        record(internalName, rules, widenings, original, transformed, false);
        return transformed;
    }

    /**
     * The second pass: call redirects, on the class as the other transformer left it. A redirect whose call is no
     * longer there (the other transformer replaced or moved it, as Mixin's {@code @Redirect} and
     * {@code @WrapOperation} do) is left out and reported to {@code skipped}; the class still loads, and only that rule
     * does not apply to it.
     *
     * @param skipped receives one message per rule left out
     * @throws dev.polymodloader.internal.transform.TransformException when the result fails verification
     */
    public byte[] transformLate(String internalName, byte[] bytes, Consumer<String> skipped) {
        List<TransformRuleDefinition> rules = lateRules(internalName);
        if (rules.isEmpty()) {
            return bytes;
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        List<TransformRuleDefinition> applicable = new ArrayList<>();
        for (TransformRuleDefinition rule : rules) {
            Optional<MethodNode> method = ClassTransformPlanner.findMethod(classNode, rule.target());
            Optional<String> problem = method.isEmpty()
                    ? Optional.of("the method is gone")
                    : MethodActionApplier.checkPrecondition(rule.action(), method.get());
            if (problem.isPresent()) {
                skipped.accept(TransformProblemCode.REDIRECT_TAKEN_BY_OTHER_TRANSFORMER.code() + " rule "
                        + rule.ruleId() + " (" + rule.ownerModId() + ") does not apply"
                        + " to " + internalName + " after another loader's transformer changed it: "
                        + problem.get());
            } else {
                applicable.add(rule);
            }
        }
        if (applicable.isEmpty()) {
            return bytes;
        }
        byte[] transformed = transformUncached(internalName, bytes, applicable, List.of());
        record(internalName, applicable, List.of(), bytes, transformed, false);
        return transformed;
    }

    private List<TransformRuleDefinition> earlyRules(String internalName) {
        return plan.rulesByClass().getOrDefault(internalName, List.of()).stream()
                .filter(rule -> !(rule.action() instanceof RedirectCall))
                .toList();
    }

    private List<TransformRuleDefinition> lateRules(String internalName) {
        return plan.rulesByClass().getOrDefault(internalName, List.of()).stream()
                .filter(rule -> rule.action() instanceof RedirectCall)
                .toList();
    }

    /**
     * Returns what {@link #transformEarly} would return, for a class that is only being read, not defined (a loader's
     * own transformer inspecting targets and supertypes). Nothing is reported, logged or dumped; the cache is
     * used as by {@link #transformEarly}.
     */
    public byte[] inspect(String internalName, byte[] original) {
        List<TransformRuleDefinition> rules = earlyRules(internalName);
        List<AccessWidening> widenings = plan.wideningsByClass().getOrDefault(internalName, List.of());
        if (rules.isEmpty() && widenings.isEmpty()) {
            return original;
        }
        TransformCacheKey key = TransformCacheKey.of(internalName, original, rules, widenings, engineFingerprint);
        Optional<byte[]> cached = cache.flatMap(store -> store.get(key));
        if (cached.isPresent()) {
            return cached.get().clone();
        }
        byte[] transformed = transformUncached(internalName, original, rules, widenings);
        cache.ifPresent(store -> store.put(key, transformed));
        return transformed;
    }

    private void record(
            String internalName,
            List<TransformRuleDefinition> rules,
            List<AccessWidening> widenings,
            byte[] original,
            byte[] transformed,
            boolean fromCache) {
        List<String> applied = new ArrayList<>();
        rules.forEach(rule -> applied.add(rule.ruleId()));
        widenings.stream()
                .map(widening -> "access widening by " + widening.ownerModId())
                .distinct()
                .forEach(applied::add);
        TransformReportEntry entry =
                new TransformReportEntry(internalName, applied, sha256(original), sha256(transformed), fromCache);
        report.add(entry);
        transformListener.accept(entry);
        dumpDirectory.ifPresent(directory -> dump(directory, internalName, original, transformed));
    }

    private static byte[] transformUncached(
            String internalName, byte[] original, List<TransformRuleDefinition> rules, List<AccessWidening> widenings) {
        ClassReader reader = new ClassReader(original);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        // Rules are in plan order; those on one method are applied together, after every
        // precondition was checked on the untouched method.
        Map<MethodNode, List<MethodTransformAction>> byMethod = new LinkedHashMap<>();
        for (TransformRuleDefinition rule : rules) {
            MethodNode method = ClassTransformPlanner.findMethod(classNode, rule.target())
                    .orElseThrow(() -> failure(
                            TransformProblemCode.TARGET_METHOD_NOT_FOUND,
                            rule,
                            "method disappeared between planning and class loading"));
            Optional<String> preconditionFailure = MethodActionApplier.checkPrecondition(rule.action(), method);
            if (preconditionFailure.isPresent()) {
                throw failure(TransformProblemCode.PRECONDITION_FAILED, rule, preconditionFailure.get());
            }
            byMethod.computeIfAbsent(method, ignored -> new ArrayList<>()).add(rule.action());
        }
        byMethod.forEach((method, actions) -> MethodActionApplier.applyInOrder(actions, internalName, method));
        if (!widenings.isEmpty()) {
            for (AccessWidening widening : widenings) {
                Optional<String> missing = AccessWideningApplier.checkTarget(widening, classNode);
                if (missing.isPresent()) {
                    throw new dev.polymodloader.internal.transform.TransformException(
                            new dev.polymodloader.internal.transform.AccessWideningProblem(
                                    TransformProblemCode.ACCESS_TARGET_NOT_FOUND,
                                    widening,
                                    missing.get() + " (it disappeared between planning and class loading)"));
                }
            }
            AccessWideningApplier.apply(classNode, widenings);
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        classNode.accept(writer);
        byte[] transformed = writer.toByteArray();
        try {
            new ClassReader(transformed).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
        } catch (RuntimeException verificationError) {
            throw new dev.polymodloader.internal.transform.TransformException(
                    new TransformProblem(
                            TransformProblemCode.VERIFICATION_FAILED,
                            rules.getFirst(),
                            "transformed " + internalName + " failed verification: " + verificationError.getMessage()),
                    verificationError);
        }
        return transformed;
    }

    /** Classes transformed so far, in load order. */
    public List<TransformReportEntry> report() {
        return List.copyOf(report);
    }

    private static dev.polymodloader.internal.transform.TransformException failure(
            TransformProblemCode code, TransformRuleDefinition rule, String message) {
        return new dev.polymodloader.internal.transform.TransformException(new TransformProblem(code, rule, message));
    }

    private static void dump(Path directory, String internalName, byte[] original, byte[] transformed) {
        try {
            Path before = directory.resolve("before").resolve(internalName + ".class");
            Path after = directory.resolve("after").resolve(internalName + ".class");
            Files.createDirectories(before.getParent());
            Files.createDirectories(after.getParent());
            Files.write(before, original);
            Files.write(after, transformed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write transformation dump for " + internalName, e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
