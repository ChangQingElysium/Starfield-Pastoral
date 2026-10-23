package com.stardew.craft.platform.mixin.extension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.stardew.craft.platform.fabric.extension.BlockActions;
import com.stardew.craft.platform.fabric.extension.ItemAbilities;
import java.util.Map;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoeItem.class)
public abstract class HoeToolActionMixin {
    // NeoForge replaces this exact vanilla map lookup with the block action query.
    // Keep native sounds, server-only writes, game event, hand and durability code.
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1)
    private Object stardewcraft$resolveTilling(Map<?, ?> map, Object block, Operation<Object> original,
            @Local(argsOnly = true) UseOnContext context) {
        var state = BlockActions.getModifiedState(context.getLevel().getBlockState(context.getClickedPos()),
                context, ItemAbilities.HOE_TILL, false);
        return state == null ? null : Pair.of((java.util.function.Predicate<UseOnContext>) ignored -> true,
                HoeItem.changeIntoState(state));
    }
}
