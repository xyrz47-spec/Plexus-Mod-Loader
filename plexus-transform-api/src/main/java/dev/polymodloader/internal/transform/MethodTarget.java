package dev.polymodloader.internal.transform;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.regex.Pattern;

/**
 * Exactly one method in one class. Matching always uses the full JVM descriptor, never the name
 * alone, so overloads and signature changes between Minecraft versions cannot silently select a
 * different method (design doc §5.5).
 *
 * @param ownerInternalName JVM internal name of the declaring class, e.g. {@code net/minecraft/client/ClientBrandRetriever}
 * @param name method name
 * @param descriptor JVM method descriptor, e.g. {@code ()Ljava/lang/String;}
 */
@ApiStability(StabilityLevel.INTERNAL)
public record MethodTarget(String ownerInternalName, String name, String descriptor) {

    private static final Pattern INTERNAL_NAME = Pattern.compile("[^./;\\[]+(/[^./;\\[]+)*");
    private static final Pattern METHOD_DESCRIPTOR = Pattern.compile("\\(.*\\).+");

    public MethodTarget {
        if (!INTERNAL_NAME.matcher(ownerInternalName).matches()) {
            throw new IllegalArgumentException(
                    "Owner must be a JVM internal name with '/' separators: " + ownerInternalName);
        }
        if (name.isEmpty() || name.contains(".") || name.contains("/")) {
            throw new IllegalArgumentException("Invalid method name: " + name);
        }
        if (!METHOD_DESCRIPTOR.matcher(descriptor).matches()) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
    }

    @Override
    public String toString() {
        return ownerInternalName + "." + name + descriptor;
    }
}
