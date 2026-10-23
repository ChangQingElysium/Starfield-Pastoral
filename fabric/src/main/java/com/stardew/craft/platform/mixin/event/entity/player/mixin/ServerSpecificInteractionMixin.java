package com.stardew.craft.platform.mixin.event.entity.player.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.player.EntityInteractionHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wrap only the accepted interaction action, after native packet/permission guards. */
@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl$1")
abstract class ServerSpecificInteractionMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;interactAt(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), require = 1)
    private static InteractionResult stardewcraft$interact(Entity target, Player player, Vec3 localPos,
            InteractionHand hand, Operation<InteractionResult> original) {
        return EntityInteractionHooks.interactAt(target, player, localPos, hand,
                () -> original.call(target, player, localPos, hand));
    }
}
