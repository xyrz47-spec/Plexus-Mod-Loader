package dev.polymodloader.internal.transform.engine;

import java.util.List;

/**
 * One transformed class in the transformation report (design doc §5.10, §12.3).
 *
 * @param className JVM internal name of the class
 * @param appliedRuleIds rules applied, in application order
 * @param sha256Before SHA-256 of the class file before transformation
 * @param sha256After SHA-256 of the class file after transformation
 * @param fromCache whether the result was reused from the transformation cache
 */
public record TransformReportEntry(
        String className, List<String> appliedRuleIds, String sha256Before, String sha256After, boolean fromCache) {

    public TransformReportEntry {
        appliedRuleIds = List.copyOf(appliedRuleIds);
    }
}
