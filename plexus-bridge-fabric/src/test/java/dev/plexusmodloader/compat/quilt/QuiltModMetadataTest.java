package dev.plexusmodloader.compat.quilt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.fabric.loader.FabricLoaderRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.ModMetadataToBeMovedToPlugins;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

/** The Quilt API's view of every mod, and the Fabric API's view of Quilt mods (ADR-0027 decision 4). */
@SuppressWarnings("deprecation") // ModMetadataToBeMovedToPlugins is deprecated in Quilt Loader, and still what it shows
class QuiltModMetadataTest {

    @TempDir
    Path directory;

    @BeforeEach
    void attach() throws IOException {
        QuiltTestLaunch launch = new QuiltTestLaunch(directory, GameSide.CLIENT);
        FabricLoaderRuntime fabric =
                FabricLoaderRuntime.create(launch.context, "0.19.5", Map.of("mixinextras", "0.5.5"));
        FabricLoaderImpl.INSTANCE.attachPlexusModLoaderRuntime(fabric);
        QuiltLoaderImpl.INSTANCE.attachPlexusModLoaderRuntime(
                QuiltLoaderRuntime.create(launch.context, fabric, "0.31.0-beta.4"));
    }

    private static ModMetadata metadata(String id) {
        return QuiltLoader.getModContainer(id).orElseThrow().metadata();
    }

    @Test
    void aQuiltModIsItsQuiltModJson() {
        ModMetadata metadata = metadata("quilt_hello");

        assertEquals("quilt_hello", metadata.id());
        assertEquals("1.0.0", metadata.version().raw());
        assertTrue(metadata.version().isSemantic());
        assertEquals("Quilt Hello", metadata.name());
        assertEquals("Says hello", metadata.description());
        assertEquals(
                List.of("MIT"), metadata.licenses().stream().map(ModLicense::id).toList());
        assertEquals(
                List.of("Ana:[Owner]", "Bo:[Artist, Tester]"),
                metadata.contributors().stream()
                        .map(person -> person.name() + ":" + person.roles())
                        .toList());
        assertEquals("Owner", metadata.contributors().iterator().next().role());
        assertEquals("https://example.com", metadata.getContactInfo("homepage"));
        assertEquals(Map.of("homepage", "https://example.com"), metadata.contactInfo());
        assertEquals("assets/quilt_hello/small.png", metadata.icon(8));
        assertEquals("assets/quilt_hello/big.png", metadata.icon(32));
        assertEquals("assets/quilt_hello/big.png", metadata.icon(512));
        assertEquals(
                List.of("quilt_hello_api 1.0.0 dev.example", "quilt_hello_versioned 3.0.0 dev.example"),
                metadata.provides().stream()
                        .map(provided ->
                                provided.id() + " " + provided.version().raw() + " " + provided.group())
                        .toList());
    }

    @Test
    void customValuesAreTheWholeQuiltModJson() {
        ModMetadata metadata = metadata("quilt_hello");

        assertTrue(metadata.containsValue("quilt_loader"));
        LoaderValue settings = metadata.value("quilt_hello:settings");
        assertEquals(LoaderValue.LType.OBJECT, settings.type());
        assertEquals(42, settings.asObject().get("answer").asNumber().intValue());
        assertEquals("b", settings.asObject().get("tags").asArray().get(1).asString());
        assertTrue(settings.asObject().get("on").asBoolean());
        assertEquals(LoaderValue.LType.NULL, settings.asObject().get("none").type());
        assertEquals(
                "quilt.mod.json:/quilt_hello:settings/tags/1",
                settings.asObject().get("tags").asArray().get(1).location());
        assertThrows(ClassCastException.class, settings::asString);
        assertEquals(settings, metadata.values().get("quilt_hello:settings"));
        assertNull(metadata.value("absent"));
    }

    @Test
    void dependenciesKeepQuiltsTree() {
        ModMetadata metadata = metadata("quilt_hello");
        List<ModDependency> depends = List.copyOf(metadata.depends());

        assertEquals(3, depends.size());
        ModDependency.Only minecraft = assertInstanceOf(ModDependency.Only.class, depends.getFirst());
        assertEquals("minecraft", minecraft.id().id());
        assertTrue(minecraft.matches(org.quiltmc.loader.api.Version.of("26.2")));
        assertFalse(minecraft.matches(org.quiltmc.loader.api.Version.of("26.0")));
        ModDependency.Any any = assertInstanceOf(ModDependency.Any.class, depends.get(1));
        assertEquals(2, any.size());
        assertTrue(assertInstanceOf(ModDependency.Only.class, depends.get(2)).optional());
        ModDependency.Only breaks = assertInstanceOf(
                ModDependency.Only.class, metadata.breaks().iterator().next());
        assertEquals("crashes", breaks.reason());
    }

    @Test
    void mixinsAccessWidenersAndEnvironment() {
        ModMetadataToBeMovedToPlugins metadata = (ModMetadataToBeMovedToPlugins) metadata("quilt_hello");

        assertEquals(
                List.of("quilt_hello.mixins.json", "quilt_hello.client.mixins.json"),
                List.copyOf(metadata.mixins(EnvType.CLIENT)));
        assertEquals(List.of("quilt_hello.mixins.json"), List.copyOf(metadata.mixins(EnvType.SERVER)));
        assertEquals(
                List.of("quilt_hello.accesswidener", "quilt_hello_extra.accesswidener"),
                List.copyOf(metadata.accessWideners()));
        assertEquals(ModEnvironment.UNIVERSAL, metadata.environment());
    }

    /** As Quilt Loader's {@code FabricModMetadataWrapper}. */
    @Test
    void aFabricModSeenThroughTheQuiltApi() {
        ModMetadata metadata = metadata("fabric_lib");

        assertEquals("loader.fabric", metadata.group());
        assertEquals("Fabric Lib", metadata.name());
        assertEquals(
                List.of("Cy:[Author]", "Di:[Contributor]"),
                metadata.contributors().stream()
                        .map(ModContributor::name)
                        .map(name -> name + ":"
                                + metadata.contributors().stream()
                                        .filter(person -> person.name().equals(name))
                                        .findFirst()
                                        .orElseThrow()
                                        .roles())
                        .toList());
        assertEquals(
                List.of("Apache-2.0"),
                metadata.licenses().stream().map(ModLicense::id).toList());
        ModDependency.Only minecraft = assertInstanceOf(
                ModDependency.Only.class, metadata.depends().iterator().next());
        assertTrue(minecraft.matches(org.quiltmc.loader.api.Version.of("26.2.1")));
        assertFalse(minecraft.matches(org.quiltmc.loader.api.Version.of("26.3")));
        assertEquals("on", metadata.value("fabric_lib:flag").asString());
        assertEquals(List.of(), List.copyOf(((ModMetadataToBeMovedToPlugins) metadata).mixins(EnvType.CLIENT)));
    }

    /** As Quilt Loader's {@code GeneralExt2FabricMetadata}: {@code getType()} is {@code quilt}. */
    @Test
    void aQuiltModSeenThroughTheFabricApi() throws VersionParsingException {
        net.fabricmc.loader.api.metadata.ModMetadata metadata = FabricLoader.getInstance()
                .getModContainer("quilt_hello")
                .orElseThrow()
                .getMetadata();

        assertEquals("quilt", metadata.getType());
        assertEquals("1.0.0", metadata.getVersion().getFriendlyString());
        assertEquals("Quilt Hello", metadata.getName());
        assertEquals(
                List.of("Ana"),
                metadata.getAuthors().stream().map(person -> person.getName()).toList());
        assertEquals(
                List.of("Bo"),
                metadata.getContributors().stream()
                        .map(person -> person.getName())
                        .toList());
        assertEquals(List.of("MIT"), List.copyOf(metadata.getLicense()));
        assertEquals(
                "https://example.com", metadata.getContact().get("homepage").orElseThrow());
        assertEquals(List.of("quilt_hello_api", "quilt_hello_versioned"), List.copyOf(metadata.getProvides()));
        assertEquals("assets/quilt_hello/big.png", metadata.getIconPath(32).orElseThrow());
        assertEquals(ModEnvironment.UNIVERSAL, metadata.getEnvironment());
        assertEquals(
                42,
                metadata.getCustomValue("quilt_hello:settings")
                        .getAsObject()
                        .get("answer")
                        .getAsNumber()
                        .intValue());
        // Only single dependencies are moved: the any-of array is left out.
        List<net.fabricmc.loader.api.metadata.ModDependency> dependencies = List.copyOf(metadata.getDependencies());
        assertEquals(
                List.of("DEPENDS minecraft", "DEPENDS optional_mod", "BREAKS bad_mod"),
                dependencies.stream()
                        .map(dependency -> dependency.getKind() + " " + dependency.getModId())
                        .toList());
        assertTrue(dependencies.getFirst().matches(Version.parse("26.2")));
        assertFalse(dependencies.getFirst().matches(Version.parse("26.0")));
        assertTrue(dependencies.get(2).matches(Version.parse("1.5.0")));
        assertFalse(dependencies.get(2).matches(Version.parse("2.0.0")));
        assertTrue(FabricLoader.getInstance().isModLoaded("quilt_hello_api"));
    }
}
