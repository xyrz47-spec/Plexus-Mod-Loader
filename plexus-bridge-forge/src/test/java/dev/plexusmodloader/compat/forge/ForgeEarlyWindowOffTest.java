package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForgeEarlyWindowOffTest {

    // As fmlearlydisplay: an automatic module providing FML's early window.
    private static final ModuleDescriptor EARLY_DISPLAY = ModuleDescriptor.newAutomaticModule(
                    "net.minecraftforge.earlydisplay")
            .version("1.0")
            .packages(Set.of("net.minecraftforge.fml.earlydisplay"))
            .provides(
                    ForgeEarlyWindowOff.PROVIDER_SERVICE, List.of("net.minecraftforge.fml.earlydisplay.DisplayWindow"))
            .provides("java.lang.Runnable", List.of("net.minecraftforge.fml.earlydisplay.Other"))
            .build();

    @Test
    void theEarlyWindowsProviderIsLeftOutAndTheRestKept() {
        List<String> log = new ArrayList<>();
        ModuleReference off = ForgeEarlyWindowOff.of(reference(EARLY_DISPLAY), log::add);
        ModuleDescriptor descriptor = off.descriptor();
        assertTrue(descriptor.isAutomatic());
        assertEquals(EARLY_DISPLAY.name(), descriptor.name());
        assertEquals(EARLY_DISPLAY.version(), descriptor.version());
        assertEquals(EARLY_DISPLAY.packages(), descriptor.packages());
        assertEquals(
                Set.of("java.lang.Runnable"),
                descriptor.provides().stream()
                        .map(ModuleDescriptor.Provides::service)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, log.size());
    }

    @Test
    void otherModulesStayAsTheyAre() {
        ModuleReference other = reference(ModuleDescriptor.newAutomaticModule("net.minecraftforge.fmlloader")
                .packages(Set.of("net.minecraftforge.fml.loading"))
                .build());
        List<String> log = new ArrayList<>();
        assertSame(other, ForgeEarlyWindowOff.of(other, log::add));
        assertTrue(log.isEmpty());
    }

    private static ModuleReference reference(ModuleDescriptor descriptor) {
        return new ModuleReference(descriptor, null) {
            @Override
            public ModuleReader open() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
