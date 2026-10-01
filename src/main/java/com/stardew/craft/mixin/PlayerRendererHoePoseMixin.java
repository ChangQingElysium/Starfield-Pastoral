package com.stardew.craft.mixin;

import com.stardew.craft.item.tool.HoeItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerRendererHoePoseMixin {

    @SuppressWarnings("null")
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$hoeArmPose(
            AbstractClientPlayer player,
            InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> cir
    ) {
        if (!player.isUsingItem() || player.getUsedItemHand() != hand) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof HoeItem)) {
            return;
        }

        // 点按不显示“蓄力抱持”姿势，避免单格锄地出现先蓄一下的割裂。
        int activeTicks = stack.getUseDuration() - player.getUseItemRemainingTicks();
        if (activeTicks < HoeItem.TAP_THRESHOLD_TICKS) {
            return;
        }

        // 蓄力时第三人称用“抱持工具”的姿势（与喷壶对齐）
        cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
}
