package dev.polymodloader.internal.transform.engine;

import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.TransformRuleDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Cache key for one transformed class. It hashes every input that can change the output (design doc
 * §12.4): the original class bytes (which also pin the Minecraft version and mappings), each
 * applied rule's full definition (ID, owner mod, game version, target, action, policy, order), the
 * access widenings of the class, and the
 * engine fingerprint (Poly Mod Loader and ASM versions). Changing any of them yields a different key, so
 * invalidation is automatic.
 *
 * @param className JVM internal name, kept readable for diagnostics
 * @param digest hex SHA-256 over all inputs
 */
public record TransformCacheKey(String className, String digest) {

    /** Computes the key for transforming {@code original} with {@code rules}. */
    public static TransformCacheKey of(
            String className, byte[] original, List<TransformRuleDefinition> rules, String engineFingerprint) {
        return of(className, original, rules, List.of(), engineFingerprint);
    }

    /** Computes the key for transforming {@code original} with {@code rules} and {@code widenings}. */
    public static TransformCacheKey of(
            String className,
            byte[] original,
            List<TransformRuleDefinition> rules,
            List<AccessWidening> widenings,
            String engineFingerprint) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            update(sha256, "engine", engineFingerprint);
            update(sha256, "class", className);
            sha256.update(original);
            for (TransformRuleDefinition rule : rules) {
                update(sha256, "rule", canonical(rule));
            }
            // Widenings combine in any order, so their sorted text forms are hashed.
            for (String widening : widenings.stream()
                    .map(TransformCacheKey::canonical)
                    .sorted()
                    .toList()) {
                update(sha256, "access", widening);
            }
            return new TransformCacheKey(className, HexFormat.of().formatHex(sha256.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Stable text form of a rule. {@code sides} is sorted because {@code Set.copyOf} iteration order
     * changes between JVM runs, which would otherwise make every launch miss the cache.
     */
    static String canonical(TransformRuleDefinition rule) {
        return String.join(
                "|",
                rule.ruleId(),
                rule.ownerModId(),
                rule.gameVersion(),
                rule.sides().stream().map(Enum::name).sorted().toList().toString(),
                rule.target().toString(),
                rule.action().toString(),
                rule.failurePolicy().name(),
                rule.after().toString());
    }

    /** Stable text form of an access widening; sets are sorted for the same reason as above. */
    static String canonical(AccessWidening widening) {
        return String.join(
                "|",
                widening.ownerModId(),
                widening.gameVersion(),
                widening.sides().stream().map(Enum::name).sorted().toList().toString(),
                widening.ownerInternalName(),
                widening.kind().name(),
                widening.name(),
                widening.descriptor(),
                widening.access().stream().map(Enum::name).sorted().toList().toString(),
                widening.failurePolicy().name());
    }

    private static void update(MessageDigest digest, String label, String value) {
        byte[] bytes = (label + "=" + value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
