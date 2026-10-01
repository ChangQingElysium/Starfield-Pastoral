package com.stardew.craft.mixin;

import com.stardew.craft.item.tool.WateringCanItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerRendererWateringCanPoseMixin {

    private static final String TAG_ACTION = "StardewAction";
    private static final int ACTION_REFILL = 2;

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$wateringCanArmPose(
            AbstractClientPlayer player,
            InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> cir
    ) {
        if (!player.isUsingItem() || player.getUsedItemHand() != hand) {
            return;
        }

        @SuppressWarnings("null")
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof WateringCanItem)) {
            return;
        }

        // 汲水蓄力时不要改姿势（避免看起来像在“格挡”水源）
        @SuppressWarnings("null")
        int action = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getInt(TAG_ACTION);
        if (action == ACTION_REFILL) {
            return;
        }

            // 洒水蓄力：第三人称用双手“抱持工具”的姿势，避免拉弓/举盾观感
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
}
