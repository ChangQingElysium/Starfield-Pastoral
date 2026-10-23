package com.stardew.craft.platform.mixin.event.entity.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntityJoinMixin {
    @WrapMethod(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z")
    private boolean stardewcraft$join(EntityAccess access, boolean loadedFromDisk, Operation<Boolean> original) {
        if (!(access instanceof Entity entity)) return original.call(access, loadedFromDisk);
        return EntityDispatch.join(entity, entity.level(), loadedFromDisk, () -> original.call(access, loadedFromDisk));
    }
}
