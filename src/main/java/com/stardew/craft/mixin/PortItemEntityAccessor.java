package com.stardew.craft.mixin;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): 1.21 {@code ItemEntity#getTarget()} (the field exists in 1.20.1 without a getter). */
@Mixin(ItemEntity.class)
public interface PortItemEntityAccessor {
    @Nullable
    @Accessor("target")
    UUID stardewcraft$getTarget();
}
