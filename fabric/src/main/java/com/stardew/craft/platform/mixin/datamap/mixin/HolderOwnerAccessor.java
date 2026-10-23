package com.stardew.craft.platform.mixin.datamap.mixin;


import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Holder.Reference.class)
public interface HolderOwnerAccessor {
    @Accessor("owner")
    HolderOwner<?> stardewcraft$dataMapOwner();
}
