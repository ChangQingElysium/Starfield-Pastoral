package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import java.util.Set;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpreadPlayersCommand.class)
public abstract class SpreadPlayersMixin {
    @WrapOperation(method = "setPlayerPositions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z"))
    private static boolean stardewcraft$spread(Entity entity, ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relative, float yaw, float pitch, Operation<Boolean> original) {
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.SpreadPlayersCommand(entity, x, y, z));
        return !event.isCanceled() && original.call(entity, level, event.getTargetX(), event.getTargetY(), event.getTargetZ(), relative, yaw, pitch);
    }
}
