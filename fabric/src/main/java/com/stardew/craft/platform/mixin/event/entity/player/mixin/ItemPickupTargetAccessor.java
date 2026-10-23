package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemPickupTargetAccessor {
    @Accessor("target") UUID stardewcraft$pickupTarget();
}
