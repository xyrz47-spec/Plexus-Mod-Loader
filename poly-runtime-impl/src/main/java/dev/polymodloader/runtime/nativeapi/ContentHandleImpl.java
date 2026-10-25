package dev.polymodloader.runtime.nativeapi;

import dev.polymodloader.api.content.SoundHandle;
import dev.polymodloader.api.content.block.BlockHandle;
import dev.polymodloader.api.content.effect.EffectHandle;
import dev.polymodloader.api.content.entity.EntityHandle;
import dev.polymodloader.api.content.item.ItemHandle;
import dev.polymodloader.api.resource.ResourceId;

/** Handle for declared content; the game layer marks it registered. */
final class ContentHandleImpl implements BlockHandle, ItemHandle, EntityHandle, EffectHandle, SoundHandle {

    private final ResourceId id;
    private volatile boolean registered;

    ContentHandleImpl(ResourceId id) {
        this.id = id;
    }

    void markRegistered() {
        registered = true;
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public String toString() {
        return id + (registered ? " (registered)" : " (declared)");
    }
}
