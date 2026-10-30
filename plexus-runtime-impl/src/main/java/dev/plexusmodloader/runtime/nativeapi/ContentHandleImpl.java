package dev.plexusmodloader.runtime.nativeapi;

import dev.plexusmodloader.api.content.SoundHandle;
import dev.plexusmodloader.api.content.block.BlockHandle;
import dev.plexusmodloader.api.content.effect.EffectHandle;
import dev.plexusmodloader.api.content.entity.EntityHandle;
import dev.plexusmodloader.api.content.item.ItemHandle;
import dev.plexusmodloader.api.resource.ResourceId;

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
