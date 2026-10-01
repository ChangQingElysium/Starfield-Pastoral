package com.stardew.craft.mixin;

import com.stardew.craft.port.event.PortEventHooks;
import net.minecraft.advancements.critereon.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): MinecraftForge fires {@code PlayerEnchantItemEvent} inside the enchanting-table lambda; Forge has no hook
 * there and the lambda name is not stable across mappings, so the event is fired from the only caller of
 * {@code EnchantedItemTrigger#trigger} (the same lambda, after the enchantments were applied).
 */
@Mixin(EnchantedItemTrigger.class)
public abstract class PortEnchantedItemTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void stardewcraft$playerEnchantItemEvent(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        PortEventHooks.firePlayerEnchantItem(player, stack);
    }
}
