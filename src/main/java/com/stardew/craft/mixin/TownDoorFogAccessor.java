package com.stardew.craft.mixin;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FogRenderer.class)
public interface TownDoorFogAccessor {
    @Accessor("targetBiomeFog") static int stardewcraft$getTarget() { throw new AssertionError(); }
    @Accessor("targetBiomeFog") static void stardewcraft$setTarget(int value) { throw new AssertionError(); }
    @Accessor("previousBiomeFog") static int stardewcraft$getPrevious() { throw new AssertionError(); }
    @Accessor("previousBiomeFog") static void stardewcraft$setPrevious(int value) { throw new AssertionError(); }
    @Accessor("biomeChangedTime") static long stardewcraft$getChangedAt() { throw new AssertionError(); }
    @Accessor("biomeChangedTime") static void stardewcraft$setChangedAt(long value) { throw new AssertionError(); }
}
