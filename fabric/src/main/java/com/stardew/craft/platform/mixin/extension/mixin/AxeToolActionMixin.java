package com.stardew.craft.platform.mixin.extension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.extension.BlockActions;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AxeItem.class)
public abstract class AxeToolActionMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/AxeItem;evaluateNewBlockState(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;"), require = 1)
    private Optional<BlockState> stardewcraft$resolveAxe(AxeItem axe, Level level, BlockPos position,
            Player player, BlockState state, Operation<Optional<BlockState>> original,
            @Local(argsOnly = true) UseOnContext context) {
        return BlockActions.evaluateAxe(context, state);
    }
}
