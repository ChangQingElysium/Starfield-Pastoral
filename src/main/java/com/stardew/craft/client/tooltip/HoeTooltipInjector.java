package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.TooltipConstants;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.tooltip.MaxChargeRangeTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class HoeTooltipInjector {

    private HoeTooltipInjector() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof HoeItem hoe)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> el = elements.get(i);
            if (el.left().isPresent()) {
                String text = el.left().get().getString();
                if (TooltipConstants.MARKER_MAX_CHARGE_RANGE.equals(text)) {
                    int maxCharge = hoe.getEffectiveMaxChargeLevel(stack);

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
