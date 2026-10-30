package dev.plexusmodloader.runtime.metadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line validator for {@code poly.mod.json}, used by the {@code dev.polymodloader.mod}
 * Gradle plugin so mod authors see the same problems at build time that Poly Mod Loader reports at launch.
 * Exit code 0 means no errors (warnings are printed), 1 means at least one error, 2 means usage.
 */
public final class ModMetadataValidatorMain {

    private ModMetadataValidatorMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: ModMetadataValidatorMain <path to poly.mod.json>");
            System.exit(2);
        }
        Path file = Path.of(args[0]);
        ModMetadataReader.Result result = new ModMetadataReader().read(Files.readString(file, StandardCharsets.UTF_8));
        for (MetadataProblem problem : result.problems()) {
            (problem.isError() ? System.err : System.out).println(file.getFileName() + " " + problem);
        }
        if (result.metadata().isEmpty()) {
            System.exit(1);
        }
        ModMetadata metadata = result.metadata().get();
        System.out.println(file.getFileName() + " OK: " + metadata.id() + " " + metadata.version() + " for Minecraft "
                + metadata.minecraftVersions());
    }
}
