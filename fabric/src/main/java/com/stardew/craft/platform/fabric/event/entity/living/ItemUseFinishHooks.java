package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ItemUseFinishHooks {
    private ItemUseFinishHooks() {}
    public static ItemStack finish(LivingEntity entity, ItemStack current, int remaining, Supplier<ItemStack> original) {
        ItemStack before = current.copy();
        ItemStack result = original.get();
        return FabricEvents.GAME.post(new LivingEntityUseItemEvent.Finish(entity, before, remaining, result)).getResultStack();
    }
}
