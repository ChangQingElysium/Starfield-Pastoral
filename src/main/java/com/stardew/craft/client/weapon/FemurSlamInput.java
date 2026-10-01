package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ModKeyMappings;
import com.stardew.craft.combat.network.WeaponSkillUsePayload;
import com.stardew.craft.combat.skill.WeaponSkillDispatcher;
import com.stardew.craft.combat.skill.handler.BuiltinWeaponSkillHandlers;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.network.PacketDistributor;

/** Ground-targeted charge input must not depend on vanilla's entity/block interaction result. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FemurSlamInput {
    private FemurSlamInput() {}

    public static boolean isSlam(ItemStack stack) {
        return stack.getItem() instanceof IStardewWeapon
                && WeaponSkillDispatcher.configuredSkillId(stack, false)
                .filter(BuiltinWeaponSkillHandlers.FEMUR_SLAM::equals).isPresent();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || mc.player == null || mc.level == null || mc.screen != null || mc.player.isSpectator()
                || !ModKeyMappings.SKILL_MINOR.isActiveAndMatches(mc.options.keyUse.getKey())
                || !isSlam(mc.player.getMainHandItem())) return;
        // Cutscene and collapse input blocks retain priority over weapon input.
        if (com.stardew.craft.cutscene.runtime.EventPlayer.get().isPlayerFrozen()
                || com.stardew.craft.client.ritual.GalaxySwordRitualClientState.isPlayerFrozen()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        boolean clicked = false;
        while (ModKeyMappings.SKILL_MINOR.consumeClick()) clicked = true;
        if (clicked) {
            predictCharge(mc.player.getMainHandItem());
            PacketDistributor.sendToServer(new WeaponSkillUsePayload(false));
        }
    }

    public static void predictCharge(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isSlam(stack) || mc.player.isUsingItem()) return;
        IStardewWeapon weapon = (IStardewWeapon) stack.getItem();
        if (!WeaponSkillCooldownsClient.isOnCooldown(weapon.getWeaponId(), "femur_slam")) {
            mc.player.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    /** Only substitutes the release check for this skill, without changing the actual use-key state. */
    public static boolean isUseHeld(KeyMapping key) {
        Minecraft mc = Minecraft.getInstance();
        if (key == mc.options.keyUse && mc.player != null && mc.player.isUsingItem()
                && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && isSlam(mc.player.getUseItem())) {
            return ModKeyMappings.SKILL_MINOR.isDown();
        }
        return key.isDown();
    }
}
