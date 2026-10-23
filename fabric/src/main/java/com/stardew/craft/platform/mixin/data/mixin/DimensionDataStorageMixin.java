package com.stardew.craft.platform.mixin.data.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import com.stardew.craft.platform.fabric.data.SavedDataFixes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DimensionDataStorage.class)
public abstract class DimensionDataStorageMixin {
    // Vanilla unconditionally dereferences DataFixTypes. NeoForge's two-argument
    // Factory defaults to null and explicitly skips DFU for mod-owned records.
    @WrapOperation(
            method = "readTagFromDisk(Ljava/lang/String;Lnet/minecraft/util/datafix/DataFixTypes;I)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/DataFixTypes;update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;"),
            require = 1)
    private CompoundTag stardewcraft$readModOwnedSavedData(DataFixTypes type, DataFixer fixer,
            CompoundTag data, int from, int to, Operation<CompoundTag> original) {
        return type == null ? SavedDataFixes.update(null, fixer, data, from, to)
                : original.call(type, fixer, data, from, to);
    }
}
