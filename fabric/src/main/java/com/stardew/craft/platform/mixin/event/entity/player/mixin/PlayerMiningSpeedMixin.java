package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.stardew.craft.platform.fabric.event.entity.player.MiningSpeedHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerMiningSpeedMixin {
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float stardewcraft$breakSpeed(float original, BlockState state) {
        return MiningSpeedHooks.result((Player) (Object) this, state, original);
    }
}
