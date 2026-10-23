package com.stardew.craft.platform.mixin.item.transfer.mixin;


import com.stardew.craft.platform.fabric.item.transfer.LegacyRandomState;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacyRandomSource.class)
public interface LegacyRandomStateMixin extends LegacyRandomState {
    @Override @Accessor("seed") AtomicLong stardewcraft$randomSeed();
}
