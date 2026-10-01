package com.stardew.craft.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.FishingRodSlotRowTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class FishingRodSlotRowClientTooltipComponent implements ClientTooltipComponent {
	private static final ResourceLocation BAIT_SLOT_TEX = new ResourceLocation(
			StardewCraft.MODID, "textures/gui/bait_slot.png"
	);
	private static final ResourceLocation TACKLE_SLOT_TEX = new ResourceLocation(
			StardewCraft.MODID, "textures/gui/tackle_slot.png"
	);
	private static final ResourceLocation SLOT_EMPTY_TEX = new ResourceLocation(
			StardewCraft.MODID, "textures/gui/fishing/slot_empty.png"
	);

	private static final int SLOT_SIZE = 18;
	private static final int SLOT_GAP = 3;
	private static final int LABEL_GAP = 6;
	private static final int ROW_BOTTOM_PADDING = 4; // 行底部间距，避免多行挤在一起

	private static final Component BAIT_LABEL = Component.translatable("tooltip.stardewcraft.fishing.bait");
	private static final Component TACKLE_LABEL = Component.translatable("tooltip.stardewcraft.fishing.tackle");

	private final FishingRodSlotRowTooltipComponent.RowType type;
	private final int sharedLabelWidth;
	private final int slotCount;
	private final ItemStack slot1;
	private final ItemStack slot2;

	public FishingRodSlotRowClientTooltipComponent(FishingRodSlotRowTooltipComponent component) {
		this.type = component.type();
		this.sharedLabelWidth = Math.max(0, component.sharedLabelWidth());
		this.slotCount = Mth.clamp(component.slotCount(), 0, 2);
		this.slot1 = component.slot1();
		this.slot2 = component.slot2();
	}

	@Override
	public int getHeight() {
		// 返回槽位高度 + 底部间距，避免连续多行组件挤在一起
		return SLOT_SIZE + ROW_BOTTOM_PADDING;
	}

	@Override
	public int getWidth(@SuppressWarnings("null") Font font) {
		if (slotCount <= 0) {
			return 0;
		}
		int slotsWidth = slotCount * SLOT_SIZE + (slotCount - 1) * SLOT_GAP;
		return sharedLabelWidth + LABEL_GAP + slotsWidth;
	}

	@SuppressWarnings("null")
	@Override
	public void renderImage(@SuppressWarnings("null") Font font, int x, int y, @SuppressWarnings("null") GuiGraphics graphics) {
		if (slotCount <= 0) {
			return;
		}

		Component label = type == FishingRodSlotRowTooltipComponent.RowType.TACKLE ? TACKLE_LABEL : BAIT_LABEL;
		ResourceLocation baseTex = type == FishingRodSlotRowTooltipComponent.RowType.TACKLE ? TACKLE_SLOT_TEX : BAIT_SLOT_TEX;

		int slotsX = x + sharedLabelWidth + LABEL_GAP;
		int labelY = y + (SLOT_SIZE - font.lineHeight) / 2;
		graphics.drawString(font, label, x + (sharedLabelWidth - font.width(label)), labelY, 0xFFFFFF, false);

		Minecraft mc = Minecraft.getInstance();
		@SuppressWarnings("null")
		boolean hasBaseTex = mc.getResourceManager().getResource(baseTex).isPresent();
		@SuppressWarnings("null")
		boolean hasFilledTex = mc.getResourceManager().getResource(SLOT_EMPTY_TEX).isPresent();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		for (int i = 0; i < slotCount; i++) {
			ItemStack stack = i == 0 ? slot1 : slot2;
			int sx = slotsX + i * (SLOT_SIZE + SLOT_GAP);
			boolean occupied = stack != null && !stack.isEmpty();
			ResourceLocation tex = occupied ? SLOT_EMPTY_TEX : baseTex;
			boolean hasTex = occupied ? hasFilledTex : hasBaseTex;
			if (hasTex) {
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.blit(tex, sx, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
			} else {
				int outer = 0xFFB0B0B0;
				int inner = 0xFF3A3A3A;
				graphics.fill(sx, y, sx + SLOT_SIZE, y + SLOT_SIZE, outer);
				graphics.fill(sx + 1, y + 1, sx + SLOT_SIZE - 1, y + SLOT_SIZE - 1, inner);
			}

			if (occupied) {
				int ix = sx + 1;
				int iy = y + 1;
				graphics.renderItem(stack, ix, iy);
				graphics.renderItemDecorations(font, stack, ix, iy);
			}
		}

		RenderSystem.disableBlend();
	}
}
