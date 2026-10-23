// Upstream-SHA256: ac11387ca27d9c7e13639c4b178066912856c8b8b81f8e600d2257c74e6f9de9
package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.combat.CollapseArmorModel;
import com.stardew.craft.client.combat.CollapsePlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class ArmorLayerCombatCollapseMixin {
    @Unique private CollapseArmorModel stardewcraft$inner;
    @Unique private CollapseArmorModel stardewcraft$outer;

    @Inject(method = "renderArmorPiece", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;usesInnerModel(Lnet/minecraft/world/entity/EquipmentSlot;)Z"))
    private void stardewcraft$articulatedArmor(PoseStack pose, MultiBufferSource buffers, LivingEntity entity,
            EquipmentSlot slot, int light, HumanoidModel<?> original, CallbackInfo callback,
            @Local(argsOnly = true) LocalRef<HumanoidModel<?>> model) {
        // FAPI's custom ArmorRenderer cancels at HEAD and retains its own rendering contract.
        // This native point follows copyPropertiesTo and setPartVisibility, matching Neo's getArmorModelHook.
        var parent = ((HumanoidArmorLayer<?, ?, ?>) (Object) this).getParentModel();
        if (!(parent instanceof CollapsePlayerModel<?> player)) return;
        if (stardewcraft$inner == null) {
            stardewcraft$inner = new CollapseArmorModel(true);
            stardewcraft$outer = new CollapseArmorModel(false);
        }
        var armor = slot == EquipmentSlot.LEGS ? stardewcraft$inner : stardewcraft$outer;
        armor.follow(player, original);
        model.set(armor);
    }
}
