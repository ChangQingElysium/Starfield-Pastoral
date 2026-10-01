package com.stardew.craft.item.equipment;

import com.stardew.craft.combat.equipment.EquipmentStats;
import com.stardew.craft.item.IStardewItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@SuppressWarnings("null")
public class CombinedRingItem extends Item implements IStardewItem {
    public CombinedRingItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.ring";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int total = 0;
        for (ItemStack ringStack : CombinedRingData.split(stack)) {
            if (ringStack.getItem() instanceof StardewRingItem ring) {
                total += ring.getSellPrice(ringStack);
            }
        }
        return total / 2;
    }

    public EquipmentStats getEquipmentStats(ItemStack stack) {
        EquipmentStats.Builder builder = EquipmentStats.builder();
        for (ItemStack ringStack : CombinedRingData.split(stack)) {
            if (ringStack.getItem() instanceof StardewRingItem ring) {
                builder.merge(ring.getEquipmentStats());
            }
        }
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        List<ItemStack> rings = CombinedRingData.split(stack);
        if (rings.isEmpty()) {
            tooltipComponents.add(Component.translatable("stardewcraft.ring.combined.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (ItemStack ringStack : rings) {
            tooltipComponents.add(Component.literal(" ").append(ringStack.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
