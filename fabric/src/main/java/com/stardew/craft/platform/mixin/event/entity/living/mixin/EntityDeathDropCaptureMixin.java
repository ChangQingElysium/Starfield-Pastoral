package com.stardew.craft.platform.mixin.event.entity.living.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDropHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityDeathDropCaptureMixin {
    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean stardewcraft$captureSpawn(Level level, Entity item, Operation<Boolean> original) {
        return item instanceof ItemEntity drop && LivingDropHooks.capture((Entity) (Object) this, drop) || original.call(level, item);
    }
}
