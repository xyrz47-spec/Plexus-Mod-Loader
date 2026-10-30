package dev.plexusmodloader.game.layer.v26_3.content;

import dev.plexusmodloader.api.data.DataTree;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;

/**
 * Converts {@link DataTree}s to the game's NBT and back. Booleans are stored as bytes; a byte tag
 * therefore reads back as a boolean (0 = false). Tags of other types are skipped on read.
 */
public final class Nbt {

    private Nbt() {}

    public static CompoundTag toTag(DataTree tree) {
        CompoundTag tag = new CompoundTag();
        for (String key : tree.keys()) {
            Object value = tree.raw(key).orElseThrow();
            tag.put(
                    key,
                    switch (value) {
                        case Integer integer -> IntTag.valueOf(integer);
                        case Long number -> LongTag.valueOf(number);
                        case Double number -> DoubleTag.valueOf(number);
                        case Boolean bool -> ByteTag.valueOf(bool);
                        case String text -> StringTag.valueOf(text);
                        case byte[] bytes -> new ByteArrayTag(bytes.clone());
                        case DataTree child -> toTag(child);
                        default -> throw new IllegalStateException("Unsupported data value " + value.getClass());
                    });
        }
        return tag;
    }

    public static DataTree fromTag(CompoundTag tag) {
        DataTree tree = DataTree.create();
        fill(tag, tree);
        return tree;
    }

    private static void fill(CompoundTag tag, DataTree tree) {
        for (String key : tag.keySet()) {
            switch (tag.get(key)) {
                case IntTag integer -> tree.putInt(key, integer.value());
                case LongTag number -> tree.putLong(key, number.value());
                case DoubleTag number -> tree.putDouble(key, number.value());
                case ByteTag bool -> tree.putBoolean(key, bool.value() != 0);
                case StringTag text -> tree.putString(key, text.value());
                case ByteArrayTag bytes -> tree.putBytes(key, bytes.getAsByteArray());
                case CompoundTag child -> fill(child, tree.child(key));
                case null, default -> {}
            }
        }
    }
}
