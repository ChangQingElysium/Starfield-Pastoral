package com.stardew.craft.api.v1.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record StardewWeaponSkillContext(
        Level level,
        Player player,
        InteractionHand hand,
        ItemStack weapon,
        ResourceLocation skillId,
        boolean majorSkill
) {
}
