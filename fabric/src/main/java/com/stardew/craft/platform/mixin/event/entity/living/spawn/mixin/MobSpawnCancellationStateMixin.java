package com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin;


import com.stardew.craft.platform.fabric.event.entity.living.spawn.SpawnCancellation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public abstract class MobSpawnCancellationStateMixin implements SpawnCancellation.State {
    @Unique private boolean stardewcraft$spawnCancelled;
    @Override public boolean stardewcraft$spawnCancelled() { return stardewcraft$spawnCancelled; }
    @Override public void stardewcraft$spawnCancelled(boolean value) { stardewcraft$spawnCancelled = value; }
}
