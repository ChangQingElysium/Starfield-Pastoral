package com.stardew.craft.platform.mixin.event.enchant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantItemMixin {
    @Shadow @Final private Container enchantSlots;

    @WrapOperation(method = "method_17410", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;getEnchantmentList(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/ItemStack;II)Ljava/util/List;"), require = 1)
    private List<EnchantmentInstance> stardewcraft$rememberEnchantments(EnchantmentMenu menu, RegistryAccess registries,
            ItemStack stack, int button, int cost, Operation<List<EnchantmentInstance>> original,
            @Share("stardewcraft$appliedEnchantments") LocalRef<List<EnchantmentInstance>> applied) {
        var result = original.call(menu, registries, stack, button, cost);
        applied.set(result);
        return result;
    }

    // The slot holds the fully enchanted result, including BOOK transmutation. Shared invocation state
    // avoids raw local-variable indices that other Fabric mixins can move or invalidate.
    @WrapOperation(method = "method_17410", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"), require = 1)
    private void stardewcraft$enchanted(ItemStack lapis, int cost, LivingEntity player, Operation<Void> original,
            @Share("stardewcraft$appliedEnchantments") LocalRef<List<EnchantmentInstance>> applied) {
        FabricEnchantingEvents.enchanted((Player) player, enchantSlots.getItem(0),
                java.util.Objects.requireNonNull(applied.get(), "Applied enchantment list unavailable"));
        original.call(lapis, cost, player);
    }
}
