package dev.polymodloader.compat.forge;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.util.function.Consumer;

/**
 * MinecraftForge's FML without its early loading window, when Poly Mod Loader shows its own over the launch. FML's
 * {@code ImmediateWindowHandler.load} looks for the window's provider ({@code ImmediateWindowProvider} named {@code
 * fmlearlywindow}) with {@code ServiceLoader}; the module that provides it, {@code fmlearlydisplay}, then provides none,
 * so FML takes its own path for a disabled early window ({@code DummyProvider}, as with {@code earlyWindowControl =
 * false} in {@code fml.toml}): the game opens its window itself, and its loading screen follows. The module's classes
 * stay, for code that refers to them.
 *
 * <p>Checked against MinecraftForge 26.1-62.0.9, 26.2-65.1.5 and 26.3-66.1.0 ({@code fmlearlydisplay}, an automatic
 * module whose {@code META-INF/services} names {@code net.minecraftforge.fml.earlydisplay.DisplayWindow}).
 *
 * <p>Thread: the launch thread, while the host defines its modules.
 */
final class ForgeEarlyWindowOff {

    static final String PROVIDER_SERVICE = "net.minecraftforge.fml.loading.ImmediateWindowProvider";

    private ForgeEarlyWindowOff() {}

    /** {@code reference} without the early window's provider when it provides one, else {@code reference}. */
    static ModuleReference of(ModuleReference reference, Consumer<String> log) {
        ModuleDescriptor descriptor = reference.descriptor();
        boolean provides = descriptor.provides().stream()
                .anyMatch(provided -> provided.service().equals(PROVIDER_SERVICE));
        if (!provides) {
            return reference;
        }
        ModuleDescriptor without = without(descriptor);
        log.accept(String.format(
                java.util.Locale.ROOT,
                "Early loading window: disabled %s providing %s; Poly Mod Loader shows its own early window instead",
                descriptor.name(),
                PROVIDER_SERVICE));
        return new ModuleReference(without, reference.location().orElse(null)) {
            @Override
            public ModuleReader open() throws IOException {
                return reference.open();
            }
        };
    }

    /** {@code descriptor} without its {@link #PROVIDER_SERVICE} provider; the same module otherwise. */
    static ModuleDescriptor without(ModuleDescriptor descriptor) {
        ModuleDescriptor.Builder builder;
        if (descriptor.isAutomatic()) {
            builder = ModuleDescriptor.newAutomaticModule(descriptor.name());
        } else if (descriptor.isOpen()) {
            builder = ModuleDescriptor.newOpenModule(descriptor.name());
            descriptor.requires().forEach(builder::requires);
        } else {
            builder = ModuleDescriptor.newModule(descriptor.name(), descriptor.modifiers());
            descriptor.requires().forEach(builder::requires);
            descriptor.opens().forEach(builder::opens);
        }
        if (!descriptor.isAutomatic()) {
            descriptor.exports().forEach(builder::exports);
            descriptor.uses().forEach(builder::uses);
        }
        descriptor.version().ifPresent(builder::version);
        descriptor.mainClass().ifPresent(builder::mainClass);
        builder.packages(descriptor.packages());
        descriptor.provides().stream()
                .filter(provided -> !provided.service().equals(PROVIDER_SERVICE))
                .forEach(builder::provides);
        return builder.build();
    }
}
