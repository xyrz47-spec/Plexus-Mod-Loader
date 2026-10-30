package dev.polymodloader.game.layer.v1_21_11.content;

import com.mojang.serialization.Codec;
import dev.polymodloader.api.content.item.ComponentType;
import dev.polymodloader.api.content.item.ItemComponentKey;
import dev.polymodloader.api.data.DataTree;
import dev.polymodloader.api.inventory.ItemStackRef;
import dev.polymodloader.api.inventory.ItemStackView;
import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.game.layer.v1_21_11.GameTags;
import dev.polymodloader.game.layer.v1_21_11.Views;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

/** Registers mod item data components and gives mods live access to item stacks. */
public final class ItemComponents {

    private static final Map<ResourceId, DataComponentType<?>> TYPES = new ConcurrentHashMap<>();

    private ItemComponents() {}

    /** Registers the declared components; called before the registries freeze. */
    public static void install(List<ItemComponentKey<?>> keys) {
        for (ItemComponentKey<?> key : keys) {
            DataComponentType<?> type = build(key.type());
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Views.id(key.id()), type);
            TYPES.put(key.id(), type);
        }
    }

    private static DataComponentType<?> build(ComponentType<?> type) {
        if (type == ComponentType.INTEGER) {
            return DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build();
        }
        if (type == ComponentType.BOOLEAN) {
            return DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build();
        }
        if (type == ComponentType.STRING) {
            return DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build();
        }
        return DataComponentType.<DataTree>builder()
                .persistent(CompoundTag.CODEC.xmap(Nbt::fromTag, Nbt::toTag))
                .networkSynchronized(ByteBufCodecs.COMPOUND_TAG.map(Nbt::fromTag, Nbt::toTag))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> type(ItemComponentKey<T> key) {
        DataComponentType<?> type = TYPES.get(key.id());
        if (type == null) {
            throw new IllegalArgumentException("Item component " + key.id() + " is not registered");
        }
        return (DataComponentType<T>) type;
    }

    /** A live reference to {@code stack}; {@code changed} runs after every change. */
    public static ItemStackRef ref(ItemStack stack, Runnable changed) {
        return new StackRef(stack, changed);
    }

    /** A snapshot of {@code stack}. */
    public static ItemStackView snapshot(ItemStack stack) {
        return stack.isEmpty()
                ? ItemStackView.EMPTY
                : new ItemStackView(Views.id(BuiltInRegistries.ITEM.getKey(stack.getItem())), stack.getCount());
    }

    @SuppressWarnings("unchecked")
    private static <T> T copy(T value) {
        return value instanceof DataTree tree ? (T) tree.copy() : value;
    }

    private record StackRef(ItemStack stack, Runnable changed) implements ItemStackRef {

        @Override
        public ResourceId item() {
            return snapshot().item();
        }

        @Override
        public int count() {
            return stack.isEmpty() ? 0 : stack.getCount();
        }

        @Override
        public boolean isIn(dev.polymodloader.api.tag.TagKey tag) {
            boolean in = GameTags.isIn(stack.getItem(), tag);
            return in && !stack.isEmpty();
        }

        @Override
        public void setCount(int count) {
            if (count < 0 || count > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("count must be 0-" + stack.getMaxStackSize() + ": " + count);
            }
            stack.setCount(count);
            changed.run();
        }

        @Override
        public <T> Optional<T> get(ItemComponentKey<T> key) {
            return Optional.ofNullable(stack.get(type(key))).map(ItemComponents::copy);
        }

        @Override
        public <T> void set(ItemComponentKey<T> key, T value) {
            DataComponentType<T> type = type(key);
            requireStack();
            stack.set(type, copy(key.type().valueClass().cast(value)));
            changed.run();
        }

        @Override
        public void remove(ItemComponentKey<?> key) {
            stack.remove(type(key));
            changed.run();
        }

        @Override
        public Optional<String> customName() {
            return Optional.ofNullable(stack.get(DataComponents.CUSTOM_NAME)).map(Component::getString);
        }

        @Override
        public void setCustomName(String name) {
            if (name == null) {
                stack.remove(DataComponents.CUSTOM_NAME);
            } else {
                requireStack();
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
            changed.run();
        }

        @Override
        public int damage() {
            return stack.getDamageValue();
        }

        @Override
        public int maxDamage() {
            return stack.getMaxDamage();
        }

        @Override
        public void setDamage(int damage) {
            stack.setDamageValue(Math.max(0, Math.min(damage, stack.getMaxDamage())));
            changed.run();
        }

        @Override
        public ItemStackView snapshot() {
            return ItemComponents.snapshot(stack);
        }

        private void requireStack() {
            if (stack.isEmpty()) {
                throw new IllegalStateException("The stack is empty");
            }
        }
    }
}
