package com.stardew.craft.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.tooltip.FishingRodSlotsTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class FishingRodSlotsClientTooltipComponent implements ClientTooltipComponent {
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
	private static final int ROW_GAP = 2;

	private static final Component BAIT_LABEL = Component.translatable("tooltip.stardewcraft.fishing.bait");
	private static final Component TACKLE_LABEL = Component.translatable("tooltip.stardewcraft.fishing.tackle");

	private final boolean showBait;
	private final int tackleSlots;
	private final ItemStack bait;
	private final ItemStack tackle1;
	private final ItemStack tackle2;

	public FishingRodSlotsClientTooltipComponent(FishingRodSlotsTooltipComponent component) {
		this.showBait = component.showBait();
		this.tackleSlots = Mth.clamp(component.tackleSlots(), 0, 2);
		this.bait = component.bait();
		this.tackle1 = component.tackle1();
		this.tackle2 = component.tackle2();
	}

	private int rowCount() {
		int rows = 0;
		if (showBait) rows++;
		if (tackleSlots > 0) rows++;
		return Math.max(0, rows);
	}

	@Override
	public int getHeight() {
		int rows = rowCount();
		if (rows == 0) return 0;
		return rows * SLOT_SIZE + (rows - 1) * ROW_GAP;
	}

	@SuppressWarnings("null")
	@Override
	public int getWidth(@SuppressWarnings("null") Font font) {
		int rows = rowCount();
		if (rows == 0) return 0;

		int labelWidth = 0;
		if (showBait) labelWidth = Math.max(labelWidth, font.width(BAIT_LABEL));
		if (tackleSlots > 0) labelWidth = Math.max(labelWidth, font.width(TACKLE_LABEL));

		int maxSlots = 0;
		if (showBait) maxSlots = Math.max(maxSlots, 1);
		maxSlots = Math.max(maxSlots, tackleSlots);

		int slotsWidth = maxSlots * SLOT_SIZE + (maxSlots - 1) * SLOT_GAP;
		return labelWidth + LABEL_GAP + slotsWidth;
	}

	@SuppressWarnings("null")
	@Override
	public void renderImage(@SuppressWarnings("null") Font font, int x, int y, @SuppressWarnings("null") GuiGraphics graphics) {
		int rows = rowCount();
		if (rows == 0) return;

		int labelWidth = 0;
		if (showBait) labelWidth = Math.max(labelWidth, font.width(BAIT_LABEL));
		if (tackleSlots > 0) labelWidth = Math.max(labelWidth, font.width(TACKLE_LABEL));
		int slotsX = x + labelWidth + LABEL_GAP;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		int row = 0;
		if (showBait) {
			renderRow(font, graphics, x, y + row * (SLOT_SIZE + ROW_GAP), labelWidth, slotsX, BAIT_LABEL,
					new ItemStack[]{bait}, BAIT_SLOT_TEX);
			row++;
		}
		if (tackleSlots > 0) {
			ItemStack[] tackles = tackleSlots == 2 ? new ItemStack[]{tackle1, tackle2} : new ItemStack[]{tackle1};
			renderRow(font, graphics, x, y + row * (SLOT_SIZE + ROW_GAP), labelWidth, slotsX, TACKLE_LABEL, tackles, TACKLE_SLOT_TEX);
		}

		RenderSystem.disableBlend();
	}

	@SuppressWarnings("null")
	private void renderRow(Font font, GuiGraphics g, int x, int y, int labelWidth, int slotsX, Component label, ItemStack[] stacks, ResourceLocation slotTex) {
		int labelY = y + (SLOT_SIZE - font.lineHeight) / 2;
		g.drawString(font, label, x + (labelWidth - font.width(label)), labelY, 0xFFFFFF, false);

		Minecraft mc = Minecraft.getInstance();
		@SuppressWarnings("null")
		boolean hasBaseTex = mc.getResourceManager().getResource(slotTex).isPresent();
		@SuppressWarnings("null")
		boolean hasFilledTex = mc.getResourceManager().getResource(SLOT_EMPTY_TEX).isPresent();

		for (int i = 0; i < stacks.length; i++) {
			int sx = slotsX + i * (SLOT_SIZE + SLOT_GAP);
			ItemStack stack = stacks[i];
			boolean occupied = stack != null && !stack.isEmpty();
			ResourceLocation tex = occupied ? SLOT_EMPTY_TEX : slotTex;
			boolean hasTex = occupied ? hasFilledTex : hasBaseTex;
			if (hasTex) {
				g.setColor(1f, 1f, 1f, 1f);
				g.blit(tex, sx, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
			} else {
				// High-contrast fallback when slot textures are missing.
				int outer = 0xFFB0B0B0;
				int inner = 0xFF3A3A3A;
				g.fill(sx, y, sx + SLOT_SIZE, y + SLOT_SIZE, outer);
				g.fill(sx + 1, y + 1, sx + SLOT_SIZE - 1, y + SLOT_SIZE - 1, inner);
			}

			if (stack != null && !stack.isEmpty()) {
				int ix = sx + 1;
				int iy = y + 1;
				g.renderItem(stack, ix, iy);
				g.renderItemDecorations(font, stack, ix, iy);
			}
		}
	}
}
