package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.TooltipConstants;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.tooltip.MaxChargeRangeTooltipComponent;
import com.stardew.craft.tooltip.WaterAmountTooltipComponent;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class WateringCanTooltipInjector {
    private WateringCanTooltipInjector() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WateringCanItem can)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> el = elements.get(i);
            if (el.left().isPresent()) {
                String text = el.left().get().getString();

                if (TooltipConstants.MARKER_WATER_AMOUNT.equals(text)) {
                    int water = can.getWater(stack);
                    int max = can.getTier().getCapacity();
                    boolean bottomless = can.isBottomless(stack);
                    elements.set(i, Either.right(new WaterAmountTooltipComponent(water, max, bottomless)));
                    continue;
                }

                if (TooltipConstants.MARKER_MAX_CHARGE_RANGE.equals(text)) {
                    int maxCharge = can.getEffectiveMaxChargeLevel(stack);
                    int rows = 1;
                    int cols = 1;
                    if (maxCharge == 1) {
                        rows = 1;
                        cols = 3;
                    } else if (maxCharge == 2) {
                        rows = 1;
                        cols = 5;
                    } else if (maxCharge == 3) {
                        rows = 3;
                        cols = 3;
                    } else if (maxCharge == 4) {
                        rows = 3;
                        cols = 6;
                    } else if (maxCharge >= 5) {
                        rows = 5;
                        cols = 5;
                    }

                    elements.set(i, Either.right(new MaxChargeRangeTooltipComponent(rows, cols)));
                }
            }
        }
    }
}
