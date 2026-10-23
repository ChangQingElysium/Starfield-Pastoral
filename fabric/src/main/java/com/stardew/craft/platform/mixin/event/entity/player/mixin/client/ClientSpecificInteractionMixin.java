package com.stardew.craft.platform.mixin.event.entity.player.mixin.client;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.player.EntityInteractionHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
abstract class ClientSpecificInteractionMixin {
    @WrapOperation(method = "interactAt", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;interactAt(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult stardewcraft$interact(Entity target, Player player, Vec3 localPos,
            InteractionHand hand, Operation<InteractionResult> original) {
        return EntityInteractionHooks.interactAt(target, player, localPos, hand,
                () -> original.call(target, player, localPos, hand));
    }
}
