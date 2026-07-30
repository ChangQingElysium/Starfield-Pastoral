package com.stardew.craft.item.weapon;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface IStardewWeapon {
    String getWeaponId();
    WeaponData getWeaponData();

    default Optional<WeaponSkillData> getSkillData(boolean majorSkill) {
        WeaponData weaponData = getWeaponData();
        return Optional.ofNullable(weaponData == null ? null : weaponData.getSkill(majorSkill));
    }

    default Optional<ResourceLocation> getSkillId(boolean majorSkill) {
        return getSkillData(majorSkill).map(WeaponSkillData::getResourceId);
    }

    /**
     * Legacy implementation fallback. New handlers are selected once by
     * {@code WeaponSkillDispatcher} before this method is called.
     */
    InteractionResultHolder<ItemStack> useSkill(Level level, Player player, InteractionHand hand, boolean majorSkill);
}
