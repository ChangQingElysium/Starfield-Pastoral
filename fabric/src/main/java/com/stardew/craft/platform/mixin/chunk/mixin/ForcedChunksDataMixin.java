package com.stardew.craft.platform.mixin.chunk.mixin;


import com.stardew.craft.platform.fabric.chunk.ChunkTicketData;
import com.stardew.craft.platform.fabric.chunk.ForcedChunkDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ForcedChunksSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForcedChunksSavedData.class)
abstract class ForcedChunksDataMixin implements ForcedChunkDataAccess {
    @Unique private ChunkTicketData stardewcraft$controllerTickets;
    @Override public ChunkTicketData stardewcraft$controllerTickets() {
        if (stardewcraft$controllerTickets == null) stardewcraft$controllerTickets = new ChunkTicketData(new CompoundTag());
        return stardewcraft$controllerTickets;
    }
    @Override public void stardewcraft$controllerTickets(ChunkTicketData data) { stardewcraft$controllerTickets = data; }
    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private static void stardewcraft$retainControllerNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<ForcedChunksSavedData> result) {
        ((ForcedChunkDataAccess) result.getReturnValue()).stardewcraft$controllerTickets(new ChunkTicketData(tag));
    }
    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void stardewcraft$preserveOpaqueControllers(CompoundTag tag, HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> result) {
        stardewcraft$controllerTickets().saveInto(result.getReturnValue());
    }
}
