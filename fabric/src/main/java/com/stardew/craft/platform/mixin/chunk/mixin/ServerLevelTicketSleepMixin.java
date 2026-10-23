package com.stardew.craft.platform.mixin.chunk.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.chunk.TicketControllers;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class ServerLevelTicketSleepMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;isEmpty()Z"), require = 1)
    private boolean stardewcraft$ownedTicketsKeepTheWorldAwake(LongSet vanillaForced, Operation<Boolean> original) {
        return original.call(vanillaForced) && !TicketControllers.hasForcedChunks((ServerLevel) (Object) this);
    }
}
