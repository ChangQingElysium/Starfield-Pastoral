package com.stardew.craft.platform.mixin.item.transfer.mixin;


import com.stardew.craft.platform.fabric.item.transfer.LootTableRandomSequence;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTable.class)
public interface LootTableRandomSequenceMixin extends LootTableRandomSequence {
    @Override @Accessor("randomSequence") Optional<ResourceLocation> stardewcraft$randomSequence();
}
