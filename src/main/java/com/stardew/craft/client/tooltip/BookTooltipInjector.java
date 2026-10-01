package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.StardewBookItem;
import com.stardew.craft.tooltip.BookTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class BookTooltipInjector {
    private BookTooltipInjector() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof StardewBookItem bookItem)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        Either<FormattedText, TooltipComponent> title = elements.isEmpty()
                ? Either.left(stack.getHoverName())
                : elements.get(0);
        elements.clear();
        elements.add(title);
        elements.add(Either.right(new BookTooltipComponent(stack, bookItem.getDefinition())));
    }
}