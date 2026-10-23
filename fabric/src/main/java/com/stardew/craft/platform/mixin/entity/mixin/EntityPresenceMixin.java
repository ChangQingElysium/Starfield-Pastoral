package com.stardew.craft.platform.mixin.entity.mixin;


import com.stardew.craft.platform.fabric.entity.EntityPresence;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityPresenceMixin implements EntityPresence.State {
    @Unique private boolean stardewcraft$addedToLevel;
    @Override public boolean stardewcraft$isAddedToLevel() { return stardewcraft$addedToLevel; }
    @Override public void stardewcraft$setAddedToLevel(boolean added) { stardewcraft$addedToLevel = added; }
}
