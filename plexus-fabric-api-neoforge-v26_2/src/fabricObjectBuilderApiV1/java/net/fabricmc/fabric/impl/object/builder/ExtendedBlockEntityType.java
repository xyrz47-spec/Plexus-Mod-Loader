/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Source: Sinytra Forgified Fabric API, branch 26.2.x, commit 40f8ce0,
// fabric-object-builder-api-v1/src/main/java/net/fabricmc/fabric/impl/object/builder/ExtendedBlockEntityType.java
// (Fabric API's
// file as Forgified Fabric API has it).
// Plexus Mod Loader (ADR-0031): reformatted; no other change.
package net.fabricmc.fabric.impl.object.builder;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

public class ExtendedBlockEntityType<T extends BlockEntity> extends BlockEntityType<T> {
    @Nullable
    private final Boolean canPotentiallyExecuteCommands;

    public ExtendedBlockEntityType(
            BlockEntitySupplier<? extends T> factory,
            Set<Block> blocks,
            @Nullable Boolean canPotentiallyExecuteCommands) {
        super(factory, blocks);
        this.canPotentiallyExecuteCommands = canPotentiallyExecuteCommands;
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        if (canPotentiallyExecuteCommands != null) {
            return canPotentiallyExecuteCommands;
        }

        return super.onlyOpCanSetNbt();
    }
}
