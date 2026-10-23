package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomSequence;

/** Direct native SavedData state, without calling its mutating get(sequence). */
public interface RandomSequencesState {
    long stardewcraft$worldSeed();
    int stardewcraft$salt();
    boolean stardewcraft$includeWorldSeed();
    boolean stardewcraft$includeSequenceId();
    Map<ResourceLocation, RandomSequence> stardewcraft$sequences();
}
