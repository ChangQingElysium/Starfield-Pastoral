package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** The exact optional named sequence selected by vanilla LootContext.Builder. */
public interface LootTableRandomSequence {
    Optional<ResourceLocation> stardewcraft$randomSequence();
}
