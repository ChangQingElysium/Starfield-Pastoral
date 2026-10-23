package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Shulker.class)
public abstract class ShulkerTeleportMixin {
    @ModifyExpressionValue(method = "teleportSomewhere", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/Shulker;findAttachableSurface(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"))
    private Direction stardewcraft$shulker(Direction direction, @Local(ordinal = 1) LocalRef<BlockPos> position) {
        if (direction == null) return null;
        BlockPos pos = position.get();
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.EnderEntity((Shulker) (Object) this, pos.getX(), pos.getY(), pos.getZ()));
        position.set(BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ()));
        return event.isCanceled() ? null : direction;
    }
}
