package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): NeoForge 1.21.1 {@code Inventory#tick} calls {@code stack.inventoryTick(level, player, slot,
 * selected == slot)} with the global slot index (armor 36-39, offhand 40) and only marks hotbar slots selected.
 * Forge 1.20.1 goes through {@code IForgeItem#onInventoryTick}, which passes the per-compartment index (armor 0-3,
 * offhand 0), reports an armor/offhand stack as selected when the hotbar selection equals that small index, and
 * calls {@code onArmorTick} for armor. StardewCraft items (none override {@code onInventoryTick}/{@code onArmorTick})
 * get the 1.21.1 call; other items keep the Forge path.
 */
@Mixin(Inventory.class)
public abstract class PortInventoryTickMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/item/ItemStack;onInventoryTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;II)V"))
    private void stardewcraft$inventoryTick121(ItemStack stack, Level level, Player player, int slot, int selected,
                                              Operation<Void> original) {
        if (StardewCraft.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            stack.inventoryTick(level, player, slot, selected == slot);
        } else {
            original.call(stack, level, player, slot, selected);
        }
    }
}
