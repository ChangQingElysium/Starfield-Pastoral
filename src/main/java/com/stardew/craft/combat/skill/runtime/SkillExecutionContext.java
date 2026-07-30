package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record SkillExecutionContext(
        ServerPlayer player,
        InteractionHand hand,
        ItemStack weapon,
        ResourceLocation weaponId,
        ResourceLocation skillId,
        WeaponSkillData skillData,
        boolean majorSkill,
        long nowTick
) {
    public SkillExecutionContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(weaponId, "weaponId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(skillData, "skillData");
        weapon = weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy();
    }

    /**
     * Prevent callers from mutating the release-time stack retained by the
     * active execution.
     */
    @Override
    public ItemStack weapon() {
        return weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy();
    }

    public WeaponDamageSnapshot weaponSnapshot() {
        return WeaponDamageSnapshot.capture(weaponId, weapon);
    }

    public SkillExecutionContext withNowTick(long value) {
        return new SkillExecutionContext(
                player,
                hand,
                weapon,
                weaponId,
                skillId,
                skillData,
                majorSkill,
                value
        );
    }
}
