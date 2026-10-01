package com.stardew.craft.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntitySwingAccessor {
    @Accessor("swingTime")
    void stardewcraft$setSwingTime(int swingTime);

    @Accessor("swinging")
    void stardewcraft$setSwinging(boolean swinging);

    /** PORT(1.20.1): LivingEntity#getCurrentSwingDuration is private before 1.20.5. */
    @Invoker("getCurrentSwingDuration")
    int stardewcraft$getCurrentSwingDuration();
}
