package com.stardew.craft.platform.fabric.event.entity.living.spawn;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface OwnedSpawner {
    Either<BlockEntity, Entity> stardewcraft$spawnerOwner();
    interface Bindable extends OwnedSpawner {
        void stardewcraft$spawnerOwner(Either<BlockEntity, Entity> owner);
    }
}
