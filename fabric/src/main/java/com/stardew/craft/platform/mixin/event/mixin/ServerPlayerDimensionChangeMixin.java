package com.stardew.craft.platform.mixin.event.mixin;


import com.stardew.craft.platform.fabric.event.FabricPlayerEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Publishes after the complete native cross-world transfer, without shared reentrancy state. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionChangeMixin {
    // Native 1.21.1 has three returns: removed/null, same-world teleport, and
    // successful cross-world transfer. Only the last writes lastSentFood.
    // This point follows packets, postDimensionTransition, and all three sync
    // sentinel resets. The captured key belongs to this invocation, including
    // when a transition callback recursively changes dimensions again.
    @Inject(
            method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;lastSentFood:I",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1)
    private void stardewcraft$afterCompletedDimensionTransfer(DimensionTransition transition,
            CallbackInfoReturnable<Entity> callback, ServerLevel destination, ServerLevel originalLevel,
            ResourceKey<Level> originalDimension, LevelData levelData, PlayerList playerList) {
        FabricPlayerEvents.postDimensionChanged((ServerPlayer) (Object) this,
                originalDimension, transition.newLevel().dimension());
    }
}
