package com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin;


import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.entity.living.spawn.OwnedSpawner;
import net.minecraft.world.entity.vehicle.MinecartSpawner;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SpawnerBlockEntity.class, MinecartSpawner.class})
public abstract class NativeSpawnerOwnersMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void stardewcraft$bindOwner(CallbackInfo callback) {
        Object owner = this;
        if (owner instanceof SpawnerBlockEntity block) {
            ((OwnedSpawner.Bindable) block.getSpawner()).stardewcraft$spawnerOwner(Either.left(block));
        } else if (owner instanceof MinecartSpawner minecart) {
            ((OwnedSpawner.Bindable) minecart.getSpawner()).stardewcraft$spawnerOwner(Either.right(minecart));
        } else throw new IllegalStateException("Unsupported native spawner owner " + owner.getClass().getName());
    }
}
