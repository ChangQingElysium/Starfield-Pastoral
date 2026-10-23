package com.stardew.craft.platform.mixin.item.transfer.mixin;


import com.stardew.craft.platform.fabric.item.transfer.RandomSequencesState;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomSequences.class)
public interface RandomSequencesStateMixin extends RandomSequencesState {
    @Override @Accessor("worldSeed") long stardewcraft$worldSeed();
    @Override @Accessor("salt") int stardewcraft$salt();
    @Override @Accessor("includeWorldSeed") boolean stardewcraft$includeWorldSeed();
    @Override @Accessor("includeSequenceId") boolean stardewcraft$includeSequenceId();
    @Override @Accessor("sequences") Map<ResourceLocation, RandomSequence> stardewcraft$sequences();
}
