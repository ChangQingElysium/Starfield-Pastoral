package com.stardew.craft.mixin;

import com.stardew.craft.port.PortEnchantments;
import java.util.function.Predicate;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantRandomlyFunction.class)
public abstract class PortRandomEnchantmentPoolMixin {
    @ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;",
            ordinal = 0, remap = false), index = 0)
    private Predicate<Enchantment> stardewcraft$randomLootPool(Predicate<Enchantment> original) {
        return enchantment -> PortEnchantments.inRandomLoot(enchantment, original.test(enchantment));
    }
}
