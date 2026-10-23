package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {
    @Inject(method = "performTeleport", at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$command(CallbackInfo callback, @Local(argsOnly = true) Entity entity,
            @Local(argsOnly = true, ordinal = 0) LocalDoubleRef x, @Local(argsOnly = true, ordinal = 1) LocalDoubleRef y,
            @Local(argsOnly = true, ordinal = 2) LocalDoubleRef z) {
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.TeleportCommand(entity, x.get(), y.get(), z.get()));
        if (event.isCanceled()) callback.cancel();
        else { x.set(event.getTargetX()); y.set(event.getTargetY()); z.set(event.getTargetZ()); }
    }
}
