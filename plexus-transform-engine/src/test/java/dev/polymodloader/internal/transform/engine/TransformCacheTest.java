package dev.polymodloader.internal.transform.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.internal.transform.MethodTarget;
import dev.polymodloader.internal.transform.ReplaceReturnedStringConstant;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class TransformCacheTest {

    private static final String OWNER = "test/CachedBrand";
    private static final MethodTarget TARGET = new MethodTarget(OWNER, "brand", "()Ljava/lang/String;");
    private static final byte[] ORIGINAL = brandClass("vanilla");

    @TempDir
    Path cacheDirectory;

    private final List<String> warnings = new ArrayList<>();

    @Test
    void secondRunReusesTheCachedResult() {
        List<TransformReportEntry> first = run(rule("poly"), "fingerprint");
        List<TransformReportEntry> second = run(rule("poly"), "fingerprint");

        assertFalse(first.getFirst().fromCache());
        assertTrue(second.getFirst().fromCache());
        assertEquals(first.getFirst().sha256After(), second.getFirst().sha256After());
    }

    @Test
    void changingTheRuleInvalidatesTheEntry() {
        run(rule("poly"), "fingerprint");

        List<TransformReportEntry> changed = run(rule("something-else"), "fingerprint");

        assertFalse(changed.getFirst().fromCache());
    }

    @Test
    void changingTheEngineInvalidatesTheEntry() {
        run(rule("poly"), "engine-1");

        assertFalse(run(rule("poly"), "engine-2").getFirst().fromCache());
    }

    @Test
    void changingTheOriginalClassChangesTheKey() {
        TransformCacheKey vanilla = TransformCacheKey.of(OWNER, ORIGINAL, List.of(rule("poly")), "f");
        TransformCacheKey other = TransformCacheKey.of(OWNER, brandClass("vanilla "), List.of(rule("poly")), "f");

        assertNotEquals(vanilla, other);
    }

    @Test
    void keyDoesNotDependOnSetIterationOrder() {
        TransformRuleDefinition enumSet = new TransformRuleDefinition(
                "test:brand",
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                TARGET,
                new ReplaceReturnedStringConstant("vanilla", "poly"),
                TransformFailurePolicy.DISABLE_RULE);
        TransformRuleDefinition reversed = new TransformRuleDefinition(
                "test:brand",
                "test",
                "26.2",
                Set.of(GameSide.DEDICATED_SERVER, GameSide.CLIENT),
                TARGET,
                new ReplaceReturnedStringConstant("vanilla", "poly"),
                TransformFailurePolicy.DISABLE_RULE);

        assertEquals(
                TransformCacheKey.of(OWNER, ORIGINAL, List.of(enumSet), "f"),
                TransformCacheKey.of(OWNER, ORIGINAL, List.of(reversed), "f"));
    }

    @Test
    void damagedEntriesAreDiscardedAndRecomputed() throws Exception {
        List<TransformReportEntry> first = run(rule("poly"), "fingerprint");
        DirectoryTransformCache cache = new DirectoryTransformCache(cacheDirectory, warnings::add);
        Path entry = cache.entryPath(TransformCacheKey.of(OWNER, ORIGINAL, List.of(rule("poly")), "fingerprint"));
        byte[] bytes = Files.readAllBytes(entry);
        bytes[bytes.length - 1] ^= 0x5A;
        Files.write(entry, bytes);

        List<TransformReportEntry> afterDamage = run(rule("poly"), "fingerprint");

        assertFalse(afterDamage.getFirst().fromCache());
        assertEquals(first.getFirst().sha256After(), afterDamage.getFirst().sha256After());
        assertTrue(
                warnings.getFirst().startsWith("PLEXUS-TRANSFORM-008 damaged cache entry for " + OWNER),
                warnings::toString);
    }

    @Test
    void cachedBytesAreACopy() {
        run(rule("poly"), "fingerprint");
        TransformPlan plan = plan(rule("poly"));
        ClassTransformExecutor executor = executor(plan, "fingerprint", new ArrayList<>());

        byte[] first = executor.transform(OWNER, ORIGINAL);
        first[0] = 0;
        byte[] second = executor.transform(OWNER, ORIGINAL);

        assertEquals((byte) 0xCA, second[0], "mutating a returned array must not corrupt later results");
    }

    @Test
    void cacheOverItsLimitDropsTheLeastRecentlyUsedEntries() throws Exception {
        // Each entry is 32 + 100 bytes; the limit holds two of them.
        DirectoryTransformCache cache = new DirectoryTransformCache(cacheDirectory, warnings::add, 300);
        TransformCacheKey first = new TransformCacheKey("test/A", "a".repeat(64));
        TransformCacheKey second = new TransformCacheKey("test/B", "b".repeat(64));
        TransformCacheKey third = new TransformCacheKey("test/C", "c".repeat(64));
        cache.put(first, new byte[100]);
        cache.put(second, new byte[100]);
        Files.setLastModifiedTime(cache.entryPath(first), java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(cache.entryPath(second), java.nio.file.attribute.FileTime.fromMillis(2_000));
        // A hit marks the first entry as used, so the second is now the oldest.
        assertTrue(cache.get(first).isPresent());

        cache.put(third, new byte[100]);

        assertTrue(Files.exists(cache.entryPath(first)), "recently used entry is kept");
        assertFalse(Files.exists(cache.entryPath(second)), "least recently used entry is removed");
        assertTrue(Files.exists(cache.entryPath(third)), "the entry just written is kept");
        assertEquals(List.of(), warnings);
    }

    @Test
    void entryLargerThanTheLimitIsKeptAndReported() {
        DirectoryTransformCache cache = new DirectoryTransformCache(cacheDirectory, warnings::add, 50);
        TransformCacheKey big = new TransformCacheKey("test/Big", "d".repeat(64));

        cache.put(big, new byte[100]);

        assertTrue(cache.get(big).isPresent());
        assertTrue(
                warnings.getFirst().startsWith("PLEXUS-TRANSFORM-009 the transform cache is still"),
                warnings::toString);
    }

    @Test
    void limitMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new DirectoryTransformCache(cacheDirectory, warnings::add, 0));
    }

    private List<TransformReportEntry> run(TransformRuleDefinition rule, String fingerprint) {
        List<TransformReportEntry> report = new ArrayList<>();
        executor(plan(rule), fingerprint, report).transform(OWNER, ORIGINAL);
        return report;
    }

    private ClassTransformExecutor executor(TransformPlan plan, String fingerprint, List<TransformReportEntry> report) {
        return new ClassTransformExecutor(
                plan,
                Optional.empty(),
                report::add,
                Optional.of(new DirectoryTransformCache(cacheDirectory, warnings::add)),
                fingerprint);
    }

    private static TransformPlan plan(TransformRuleDefinition rule) {
        return new ClassTransformPlanner()
                .plan(List.of(rule), "26.2", GameSide.CLIENT, name -> Optional.of(ORIGINAL.clone()));
    }

    private static TransformRuleDefinition rule(String replacement) {
        return new TransformRuleDefinition(
                "test:brand",
                "test",
                "26.2",
                EnumSet.allOf(GameSide.class),
                TARGET,
                new ReplaceReturnedStringConstant("vanilla", replacement),
                TransformFailurePolicy.DISABLE_RULE);
    }

    private static byte[] brandClass(String brand) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, OWNER, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "brand", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn(brand);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
