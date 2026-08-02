package com.stardew.craft.combat;

import com.stardew.craft.item.ModItems;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Combat behavior supplied by Stardew weapon forge data. */
final class WeaponForgeCombatRules {
    private WeaponForgeCombatRules() {
    }

    static boolean hasDragonToothBonus(ItemStack weapon, String kind) {
        return WeaponForgeData.dragonToothBonuses(
                WeaponForgeData.read(weapon).dragonToothEnchantment()
        ).stream().anyMatch(bonus -> Objects.equals(kind, bonus.kind()));
    }

    static boolean isSlimeSlayerTarget(LivingEntity target) {
        return target.getTags().contains("sd_mob_slime");
    }

    static boolean isSlimeGathererTarget(LivingEntity target) {
        var tags = target.getTags();
        return tags.contains("sd_mob_slime")
                || tags.contains("sd_mob_bigslime_skull");
    }

    static void dropSlimeGathererReward(
            LivingEntity target,
            ServerPlayer player
    ) {
        int bound = Math.max(
                1,
                (int) Math.ceil(Math.sqrt(target.getMaxHealth()) / 3.0)
        );
        int count = 1 + player.getRandom().nextInt(bound);
        ItemEntity item = new ItemEntity(
                target.level(),
                target.getX(),
                target.getY(),
                target.getZ(),
                new ItemStack(ModItems.SLIME_ITEM.get(), count)
        );
        item.setDefaultPickUpDelay();
        target.level().addFreshEntity(item);
    }
}
