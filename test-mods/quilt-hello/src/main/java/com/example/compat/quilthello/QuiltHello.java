package com.example.compat.quilthello;

import com.example.compat.quiltnestedlib.QuiltNestedLib;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Quilt's {@code main}: calls the parts of Quilt Loader's API that mods use and logs what it gets. */
public final class QuiltHello implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("quilt_hello");

    @Override
    public void onInitialize() {
        Stages.ran("main");
        LOGGER.info("[quilt_hello] entrypoint order: {}", Stages.order());
        LOGGER.info(
                "[quilt_hello] MinecraftQuiltLoader.getEnvironmentType() = {}, QuiltLoader.getRawGameVersion() = {}",
                MinecraftQuiltLoader.getEnvironmentType(),
                QuiltLoader.getRawGameVersion());
        for (String id : List.of("fabric_lib", "fabric_nested_lib", "minimal_native_mod", "native_lib", "minecraft")) {
            LOGGER.info("[quilt_hello] sees {}", describe(id));
        }
        LOGGER.info(
                "[quilt_hello] fabricloader is provided by {}; quilt_nested_unused is loaded: {}",
                QuiltLoader.getModContainer("fabricloader")
                        .map(mod -> mod.metadata().id())
                        .orElse("nothing"),
                QuiltLoader.isModLoaded("quilt_nested_unused"));

        ModMetadata own =
                QuiltLoader.getModContainer("quilt_hello").orElseThrow().metadata();
        LOGGER.info(
                "[quilt_hello] own metadata: version {}, group {}, name {}, licenses {}, contributors {},"
                        + " homepage {}, settings answer = {}",
                own.version().raw(),
                own.group(),
                own.name(),
                own.licenses().stream().map(ModLicense::id).toList(),
                own.contributors().stream()
                        .map(person -> person.name() + " " + person.roles())
                        .toList(),
                own.getContactInfo("homepage"),
                own.value("quilt_hello:settings")
                        .asObject()
                        .get("answer")
                        .asNumber()
                        .intValue());
        LOGGER.info(
                "[quilt_hello] provides {}; isModLoaded(\"quilt_hello_api\") = {}, quilt_hello_versioned is from {}",
                own.provides().stream()
                        .map(provided ->
                                provided.id() + " " + provided.version().raw())
                        .toList(),
                QuiltLoader.isModLoaded("quilt_hello_api"),
                QuiltLoader.getModContainer("quilt_hello_versioned")
                        .map(mod -> mod.metadata().id())
                        .orElse("nothing"));

        List<EntrypointContainer<Greeting>> custom =
                QuiltLoader.getEntrypointContainers("quilt_hello:custom", Greeting.class);
        List<Greeting> fabricCustom = FabricLoader.getInstance().getEntrypoints("quilt_hello:custom", Greeting.class);
        LOGGER.info(
                "[quilt_hello] {} custom entrypoint(s) from {}: {}; FabricLoader.getEntrypoints gives the same instance: {}",
                custom.size(),
                custom.stream()
                        .map(container -> container.getProvider().metadata().id())
                        .collect(Collectors.joining(", ")),
                custom.getFirst().getEntrypoint().greet(),
                fabricCustom.size() == 1
                        && fabricCustom.getFirst() == custom.getFirst().getEntrypoint());
        LOGGER.info(
                "[quilt_hello] the main entrypoint QuiltLoader returns is this instance: {}",
                QuiltLoader.getEntrypoints("main", ModInitializer.class).contains(this));
        LOGGER.info(
                "[quilt_hello] FabricLoader sees quilt_hello as type {}, version {}",
                FabricLoader.getInstance()
                        .getModContainer("quilt_hello")
                        .map(mod -> mod.getMetadata().getType())
                        .orElse("none"),
                FabricLoader.getInstance()
                        .getModContainer("quilt_hello")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                        .orElse("none"));
        LOGGER.info(
                "[quilt_hello] getModContainer(QuiltHello.class) = {}",
                QuiltLoader.getModContainer(QuiltHello.class)
                        .map(mod -> mod.metadata().id())
                        .orElse("none"));
        LOGGER.info(
                "[quilt_hello] stripped on {}: @ClientOnly method present = {}, @DedicatedServerOnly method present = {},"
                        + " @Requires(\"absent_mod\") field present = {}, @Requires(\"quilt_loader\") field present = {}",
                MinecraftQuiltLoader.getEnvironmentType(),
                hasMethod("clientOnly"),
                hasMethod("dedicatedServerOnly"),
                hasField("requiresAbsentMod"),
                hasField("requiresQuiltLoader"));
        LOGGER.info(
                "[quilt_hello] access wideners: SharedConstants.prefixDebugFlagName public = {}, Mth.SIN public = {}",
                isPublic("net.minecraft.SharedConstants", "prefixDebugFlagName"),
                isPublic("net.minecraft.util.Mth", "SIN"));
        LOGGER.info("[quilt_hello] nested quilt_nested_lib is {}", QuiltNestedLib.status());
    }

    private static String describe(String id) {
        return QuiltLoader.getModContainer(id)
                .map(mod -> id + ": " + mod.getSourceType() + ", group "
                        + mod.metadata().group() + ", version "
                        + mod.metadata().version().raw())
                .orElse(id + ": not loaded (isModLoaded " + QuiltLoader.isModLoaded(id) + ")");
    }

    private static boolean hasMethod(String name) {
        return Arrays.stream(SidedMembers.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch(name::equals);
    }

    private static boolean hasField(String name) {
        return Arrays.stream(SidedMembers.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name::equals);
    }

    /** Whether the game's member {@code name} became public, which only the mod's access wideners do. */
    private static boolean isPublic(String className, String name) {
        try {
            Class<?> type = Class.forName(className, false, QuiltHello.class.getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return Modifier.isPublic(method.getModifiers());
                }
            }
            return Modifier.isPublic(type.getDeclaredField(name).getModifiers());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(className + "." + name + " is missing", e);
        }
    }
}
