package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillContext;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Server-authoritative entry point for public and legacy weapon skills. */
public final class WeaponSkillDispatcher {
    private WeaponSkillDispatcher() {
    }

    public static Optional<ResourceLocation> publicSkillId(ItemStack stack, boolean majorSkill) {
        StardewEquipmentData data = StardewEquipmentDataApi.get(stack);
        if (data == null || data.weapon().isEmpty()) return Optional.empty();
        StardewEquipmentData.Weapon weapon = data.weapon().get();
        return majorSkill ? weapon.secondarySkill() : weapon.primarySkill();
    }

    public static boolean hasPublicSkill(ItemStack stack, boolean majorSkill) {
        return publicSkillId(stack, majorSkill).isPresent();
    }

    public static InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand, boolean majorSkill) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<ResourceLocation> publicSkill = publicSkillId(stack, majorSkill);
        if (publicSkill.isPresent()) {
            var handler = StardewWeaponSkillHandlers.get(publicSkill.get()).orElse(null);
            if (handler != null) {
                try {
                    InteractionResultHolder<ItemStack> result = handler.use(new StardewWeaponSkillContext(
                            level, player, hand, stack, publicSkill.get(), majorSkill));
                    if (result != null && !result.getResult().equals(net.minecraft.world.InteractionResult.PASS)) {
                        return result;
                    }
                } catch (RuntimeException exception) {
                    StardewCraft.LOGGER.error("Weapon skill handler {} failed for item {}",
                            publicSkill.get(), stack.getItem().builtInRegistryHolder().key().location(), exception);
                }
            }
        }
        if (stack.getItem() instanceof IStardewWeapon weapon) {
            return weapon.useSkill(level, player, hand, majorSkill);
        }
        return InteractionResultHolder.pass(stack);
    }
}
