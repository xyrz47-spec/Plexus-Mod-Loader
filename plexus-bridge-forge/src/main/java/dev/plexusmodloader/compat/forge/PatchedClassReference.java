package dev.plexusmodloader.compat.forge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * A module as it is, except one class file, which its reader serves through a patch. For Forge's own modules that Poly
 * Mod Loader defines without transforming their classes (FML's BOOT and PLUGIN layers, as in ModLauncher), where a
 * checked patch of one class is needed ({@link NightConfigEmptyFilePatch}, {@link ForgeModListPatch}).
 */
final class PatchedClassReference {

    private PatchedClassReference() {}

    /**
     * {@code reference} reading {@code classFile} (such as {@code net/minecraftforge/fml/ModList.class}) through {@code
     * patch}, which returns its argument when it changes nothing.
     */
    static ModuleReference of(ModuleReference reference, String classFile, UnaryOperator<byte[]> patch) {
        return new ModuleReference(reference.descriptor(), reference.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return new PatchingReader(reference.open(), classFile, patch);
            }
        };
    }

    private static final class PatchingReader implements ModuleReader {

        private final ModuleReader reader;
        private final String classFile;
        private final UnaryOperator<byte[]> patch;

        PatchingReader(ModuleReader reader, String classFile, UnaryOperator<byte[]> patch) {
            this.reader = reader;
            this.classFile = classFile;
            this.patch = patch;
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            return reader.find(name);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            Optional<InputStream> input = reader.open(name);
            if (!name.equals(classFile) || input.isEmpty()) {
                return input;
            }
            try (InputStream stream = input.get()) {
                return Optional.of(new ByteArrayInputStream(patch.apply(stream.readAllBytes())));
            }
        }

        @Override
        public Stream<String> list() throws IOException {
            return reader.list();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
