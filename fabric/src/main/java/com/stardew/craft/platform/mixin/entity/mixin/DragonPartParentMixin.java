package com.stardew.craft.platform.mixin.entity.mixin;


import com.stardew.craft.platform.fabric.entity.PartEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnderDragonPart.class)
public abstract class DragonPartParentMixin implements PartEntity<EnderDragon> {
    @Shadow @Final public EnderDragon parentMob;
    @Override public EnderDragon getParent() { return parentMob; }
}
