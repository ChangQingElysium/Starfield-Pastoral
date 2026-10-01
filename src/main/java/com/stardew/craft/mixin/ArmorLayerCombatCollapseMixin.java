package com.stardew.craft.mixin;

import com.stardew.craft.client.combat.CollapseArmorModel;
import com.stardew.craft.client.combat.CollapsePlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public class ArmorLayerCombatCollapseMixin {
    @Unique private CollapseArmorModel stardewcraft$inner;
    @Unique private CollapseArmorModel stardewcraft$outer;
    // PORT(1.20.1): getArmorModelHook is a Forge-added method (not obfuscated), so it is not remapped.
    @Inject(method="getArmorModelHook",at=@At("RETURN"),cancellable=true,remap=false)
    private void stardewcraft$articulatedArmor(LivingEntity entity,ItemStack stack,EquipmentSlot slot,
            HumanoidModel<?> original,CallbackInfoReturnable<Model> cir) {
        // A mod-provided custom armor model retains its own rendering contract.
        if(cir.getReturnValue()!=original)return;
        var parent=((HumanoidArmorLayer<?,?,?>)(Object)this).getParentModel();
        if(!(parent instanceof CollapsePlayerModel<?> player))return;
        if(stardewcraft$inner==null) { stardewcraft$inner=new CollapseArmorModel(true);stardewcraft$outer=new CollapseArmorModel(false); }
        var armor=slot==EquipmentSlot.LEGS?stardewcraft$inner:stardewcraft$outer;
        armor.follow(player,original);cir.setReturnValue(armor);
    }
}
