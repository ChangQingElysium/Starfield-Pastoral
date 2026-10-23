package com.stardew.craft.platform.mixin.extension.mixin;


import com.stardew.craft.platform.fabric.extension.MobEffectAttributeFunctions;
import java.util.function.IntToDoubleFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.MobEffect$AttributeTemplate")
abstract class MobEffectAttributeTemplateMixin implements MobEffectAttributeFunctions.Template {
    @Shadow @Final private ResourceLocation id;
    @Shadow @Final private AttributeModifier.Operation operation;
    @Unique private IntToDoubleFunction stardewcraft$amount;

    @Override public void stardewcraft$amountFunction(IntToDoubleFunction function) { stardewcraft$amount = function; }

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$create(int amplifier, CallbackInfoReturnable<AttributeModifier> cir) {
        if (stardewcraft$amount != null) {
            cir.setReturnValue(MobEffectAttributeFunctions.create(id, operation, stardewcraft$amount, amplifier));
        }
    }
}
