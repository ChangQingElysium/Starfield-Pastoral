package com.stardew.craft.platform.fabric.extension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ItemAttackProvider {
    /** True consumes the melee attempt, as for a slingshot. */
    boolean onLeftClickEntity(ItemStack stack, Player player, Entity target);
}
