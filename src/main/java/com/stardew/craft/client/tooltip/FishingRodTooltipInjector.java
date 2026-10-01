package com.stardew.craft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.TooltipConstants;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.tooltip.FishingRodSlotRowTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingRodTooltipInjector {
	private FishingRodTooltipInjector() {
	}

	@SubscribeEvent
	public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof FishingRodItem rod)) {
			return;
		}

		// Pre-compute shared label width and attachments once
		FishingRodItem.Attachments att = rod.getAttachmentsForTooltip(stack);
		var font = Minecraft.getInstance().font;
		@SuppressWarnings("null")
		int labelWidth = Math.max(
				font.width(Component.translatable("tooltip.stardewcraft.fishing.bait")),
				font.width(Component.translatable("tooltip.stardewcraft.fishing.tackle"))
		);
		int tackleSlots = rod.getTackleSlots();

		List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

		for (int i = 0; i < elements.size(); i++) {
			Either<FormattedText, TooltipComponent> el = elements.get(i);
			if (el.left().isPresent()) {
				String text = el.left().get().getString();

				if (TooltipConstants.MARKER_FISHING_ROD_BAIT.equals(text)) {
					elements.set(i, Either.right(new FishingRodSlotRowTooltipComponent(
							FishingRodSlotRowTooltipComponent.RowType.BAIT,
							labelWidth,
							1,
							att.bait(),
							ItemStack.EMPTY
					)));
					continue;
				}

				if (TooltipConstants.MARKER_FISHING_ROD_TACKLE.equals(text)) {
					elements.set(i, Either.right(new FishingRodSlotRowTooltipComponent(
							FishingRodSlotRowTooltipComponent.RowType.TACKLE,
							labelWidth,
							tackleSlots,
							att.tackle1(),
							tackleSlots >= 2 ? att.tackle2() : ItemStack.EMPTY
					)));
				}
			}
		}
	}
}
