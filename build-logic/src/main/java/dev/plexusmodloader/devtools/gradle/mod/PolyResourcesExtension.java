package dev.plexusmodloader.devtools.gradle.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;

/**
 * The {@code polyResources} block of a mod's build script. Each entry names content the mod's code
 * registers and gets its standard resource files generated ({@link ModResourceGenerator}):
 *
 * <pre>{@code
 * polyResources {
 *     item("ruby")
 *     block("ruby_block") { minedWith = "pickaxe" }
 *     block("ruby_lamp") { displayName = "Ruby Lamp"; texture = "minecraft:block/redstone_lamp_on" }
 * }
 * }</pre>
 */
public abstract class PolyResourcesExtension {

    private final List<ContentResourceDefinition> definitions = new ArrayList<>();

    /** An item with a flat model of {@code textures/item/<name>.png}. */
    public void item(String name) {
        item(name, spec -> {});
    }

    /** An item, with its settings. */
    public void item(String name, Action<? super ItemResourceSpec> configure) {
        ItemResourceSpec spec = new ItemResourceSpec();
        configure.execute(spec);
        add(() -> new ContentResourceDefinition(
                ContentResourceDefinition.Kind.ITEM, name, spec.getDisplayName(), spec.getTexture(), null, false));
    }

    /** A full cube block with {@code textures/block/<name>.png} on every face that drops itself. */
    public void block(String name) {
        block(name, spec -> {});
    }

    /** A block, with its settings. */
    public void block(String name, Action<? super BlockResourceSpec> configure) {
        BlockResourceSpec spec = new BlockResourceSpec();
        configure.execute(spec);
        add(() -> new ContentResourceDefinition(
                ContentResourceDefinition.Kind.BLOCK,
                name,
                spec.getDisplayName(),
                spec.getTexture(),
                spec.getMinedWith(),
                spec.getDropsSelf()));
    }

    /** The entries in declaration order. */
    public List<ContentResourceDefinition> getDefinitions() {
        return List.copyOf(definitions);
    }

    /** Adds a definition; a value Minecraft would reject fails the build script where it is declared. */
    private void add(Supplier<ContentResourceDefinition> definition) {
        try {
            definitions.add(definition.get());
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException(e.getMessage(), e);
        }
    }
}
