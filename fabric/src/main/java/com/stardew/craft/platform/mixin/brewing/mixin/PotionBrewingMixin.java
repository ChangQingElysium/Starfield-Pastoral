package com.stardew.craft.platform.mixin.brewing.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.brewing.BrewingBootstrap;
import com.stardew.craft.platform.fabric.brewing.BrewingRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin implements BrewingRecipes.Access {
    @Unique private BrewingRecipes stardewcraft$custom = BrewingRecipes.EMPTY;

    @Override public void stardewcraft$recipes(BrewingRecipes recipes) { stardewcraft$custom = recipes; }

    @WrapOperation(method = "bootstrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/alchemy/PotionBrewing$Builder;build()Lnet/minecraft/world/item/alchemy/PotionBrewing;"))
    private static PotionBrewing stardewcraft$register(PotionBrewing.Builder builder, Operation<PotionBrewing> original) {
        // Neo dispatches after all vanilla mixes, immediately before this build.
        BrewingRecipes recipes = BrewingBootstrap.collect();
        PotionBrewing result = original.call(builder);
        ((BrewingRecipes.Access) result).stardewcraft$recipes(recipes);
        return result;
    }

    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$ingredient(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stardewcraft$custom.isIngredient(stack)) callback.setReturnValue(true);
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$hasMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> callback) {
        if (!stardewcraft$custom.output(input, ingredient).isEmpty()) callback.setReturnValue(true);
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$mix(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> callback) {
        // Native mix has the opposite argument order to hasMix and IBrewingRecipe.
        ItemStack result = stardewcraft$custom.output(input, ingredient);
        if (!result.isEmpty()) callback.setReturnValue(result);
    }
}
