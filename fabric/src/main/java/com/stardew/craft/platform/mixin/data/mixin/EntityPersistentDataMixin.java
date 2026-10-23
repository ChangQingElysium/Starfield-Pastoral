package com.stardew.craft.platform.mixin.data.mixin;


import com.stardew.craft.platform.fabric.data.PersistentEntityDataAccess;
import com.stardew.craft.platform.fabric.data.PersistentEntityDataStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPersistentDataMixin implements PersistentEntityDataAccess {
    @Unique
    private PersistentEntityDataStorage stardewcraft$persistentEntityData;

    @Override
    public PersistentEntityDataStorage stardewcraft$persistentEntityData() {
        if (stardewcraft$persistentEntityData == null) {
            stardewcraft$persistentEntityData = new PersistentEntityDataStorage();
        }
        return stardewcraft$persistentEntityData;
    }

    // Verified against vanilla 1.21.1 bytecode and the NeoForge 21.1.217 patch:
    // write before the subclass save hook, not at method return.
    @Inject(
            method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"),
            require = 1)
    private void stardewcraft$savePersistentData(CompoundTag entityTag, CallbackInfoReturnable<CompoundTag> callback) {
        stardewcraft$persistentEntityData().save(entityTag);
    }

    // Subclasses must see restored data during their own read hook. Entity's
    // abstract readAdditionalSaveData has no body to inject into directly.
    @Inject(
            method = "load(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"),
            require = 1)
    private void stardewcraft$loadPersistentData(CompoundTag entityTag, CallbackInfo callback) {
        stardewcraft$persistentEntityData().load(entityTag);
    }
}
