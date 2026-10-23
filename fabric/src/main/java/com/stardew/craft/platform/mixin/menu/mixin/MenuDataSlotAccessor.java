package com.stardew.craft.platform.mixin.menu.mixin;


import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only to bound the incoming data index against the current menu's actual slot list. */
@Mixin(AbstractContainerMenu.class)
public interface MenuDataSlotAccessor {
    @Accessor("dataSlots")
    List<DataSlot> stardewcraft$getDataSlots();
}
