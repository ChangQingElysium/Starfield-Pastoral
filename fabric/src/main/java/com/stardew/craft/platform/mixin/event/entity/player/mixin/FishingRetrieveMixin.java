package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.ItemFishedEvent;
import java.util.List;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingRetrieveMixin {
    @Inject(method = "retrieve", at = @At(value = "FIELD", target = "Lnet/minecraft/advancements/CriteriaTriggers;FISHING_ROD_HOOKED:Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;", ordinal = 1), cancellable = true)
    private void stardewcraft$fished(CallbackInfoReturnable<Integer> callback, @Local List<ItemStack> drops,
            @Share("stardewcraft$fished") LocalRef<ItemFishedEvent> decision) {
        var hook = (FishingHook) (Object) this;
        var event = FabricEvents.GAME.post(new ItemFishedEvent(drops, hook.onGround() ? 2 : 1, hook));
        decision.set(event);
        if (event.isCanceled()) { hook.discard(); callback.setReturnValue(event.getRodDamage()); }
    }
    @ModifyReturnValue(method = "retrieve", at = @At("RETURN"))
    private int stardewcraft$rodDamage(int nativeDamage, @Share("stardewcraft$fished") LocalRef<ItemFishedEvent> decision) {
        return decision.get() == null ? nativeDamage : decision.get().getRodDamage();
    }
}
