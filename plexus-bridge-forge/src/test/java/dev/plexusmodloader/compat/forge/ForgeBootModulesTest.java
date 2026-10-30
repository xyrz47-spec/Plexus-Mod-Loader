package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForgeBootModulesTest {

    private static final String MOD_LOCATOR = "net.minecraftforge.forgespi.locating.IModLocator";
    private static final String MODS_FOLDER = "net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator";
    private static final String CLASS_PATH = "net.minecraftforge.fml.loading.moddiscovery.ClasspathLocator";

    @TempDir
    Path directory;

    /** FML would stop at every Fabric and Poly mod in the mods folder; Poly Mod Loader hands FML the Forge mods instead. */
    @Test
    void fmlDoesNotScanTheModsFolder() throws IOException {
        ModuleReference fmlLoader =
                automaticModule("fmlloader.jar", Map.of(MOD_LOCATOR, List.of(CLASS_PATH, MODS_FOLDER)));

        ModuleReference booted = ForgeBootModules.withRequires(fmlLoader, Set.of());

        assertTrue(booted.descriptor().isAutomatic());
        assertEquals(Map.of(MOD_LOCATOR, List.of(CLASS_PATH)), provides(booted.descriptor()));
        assertEquals(fmlLoader.descriptor().packages(), booted.descriptor().packages());
        try (ModuleReader reader = booted.open()) {
            assertTrue(reader.find(MODS_FOLDER.replace('.', '/') + ".class").isPresent());
        }
    }

    @Test
    void otherAutomaticModulesAreKeptAsTheyAre() throws IOException {
        ModuleReference library = automaticModule("library.jar", Map.of(MOD_LOCATOR, List.of(CLASS_PATH)));

        assertSame(library, ForgeBootModules.withRequires(library, Set.of()));
    }

    private ModuleReference automaticModule(String name, Map<String, List<String>> services) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, List<String>> service : services.entrySet()) {
                out.putNextEntry(new JarEntry("META-INF/services/" + service.getKey()));
                out.write(String.join("\n", service.getValue()).getBytes(StandardCharsets.UTF_8));
                for (String provider : service.getValue()) {
                    out.putNextEntry(new JarEntry(provider.replace('.', '/') + ".class"));
                }
            }
        }
        return ModuleFinder.of(jar).findAll().iterator().next();
    }

    private static Map<String, List<String>> provides(ModuleDescriptor descriptor) {
        return descriptor.provides().stream()
                .collect(Collectors.toMap(ModuleDescriptor.Provides::service, ModuleDescriptor.Provides::providers));
    }
}
