package com.stardew.craft.item;

import com.stardew.craft.museum.LostBookService;
import com.stardew.craft.time.StardewTimePauseService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * SDV object 102. It remains a real registered item for loot and debug
 * commands, but converts itself into sequential shared-library progress after
 * pickup, once the player has returned from every GUI to normal gameplay.
 */
public final class LostBookItem extends Item implements IStardewItem {
    public LostBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.misc";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide
                || stack.isEmpty()
                || !(entity instanceof ServerPlayer player)
                || !LostBookService.canFindAnother(player)) {
            return;
        }
        StardewTimePauseService.requestGameplayFeedbackState(player);
        if (!StardewTimePauseService.canPlayGameplayFeedback(player)) {
            return;
        }
        LostBookService.receive(player, stack);
        StardewTimePauseService.completeGameplayFeedback(player);
    }
}
