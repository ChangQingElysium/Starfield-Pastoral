package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.menu.MineExitMenu;
import com.stardew.craft.network.payload.MineExitActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 矿井出口传送菜单GUI（客户端界面）
 */
public class MineExitScreen extends AbstractContainerScreen<MineExitMenu> {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 25;
	private int lastKnownFloor = Integer.MIN_VALUE;
	
	public MineExitScreen(MineExitMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166; // 增加高度以容纳标题+3个按钮
	}
	
	@SuppressWarnings("null")
	@Override
	protected void init() {
		super.init();
		
		int centerX = this.width / 2;
		int startY = this.height / 2 - 30; // 从中心稍微往上，为标题留空间
		
		int currentFloor = this.menu.getCurrentFloor();
		
		// 按钮1：返回上一层（只有在floor > 0时显示）
		if (currentFloor > 0) {
			this.addRenderableWidget(Button.builder(
					Component.translatable("gui.stardew_craft.mine_exit.go_up"),
					button -> {
						PacketDistributor.sendToServer(new MineExitActionPayload(MineExitActionPayload.Action.GO_UP_FLOOR));
						this.onClose();
					})
					.bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
			startY += BUTTON_SPACING;
		}
		
		// 按钮2：返回第0层（始终显示）
		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.stardew_craft.mine_exit.go_to_0"),
				button -> {
					PacketDistributor.sendToServer(new MineExitActionPayload(MineExitActionPayload.Action.GO_TO_FLOOR_0));
					this.onClose();
				})
				.bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		startY += BUTTON_SPACING;
		
		// 按钮3：关闭菜单
		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.stardew_craft.mine_exit.cancel"),
				button -> this.onClose())
				.bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}
	
	@Override
	protected void renderBg(@SuppressWarnings("null") GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		// 绘制半透明背景
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xC0101010);
	}
	
	@SuppressWarnings("null")
	@Override
	public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		int currentFloor = this.menu.getCurrentFloor();
		if (currentFloor != lastKnownFloor) {
			lastKnownFloor = currentFloor;
			this.clearWidgets();
			this.init();
		}
	}
	
	@SuppressWarnings("null")
	@Override
	protected void renderLabels(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY) {
		// 绘制标题
		Component title = StardewFonts.title(
				Component.translatable("container.stardew_craft.mine_exit"));
		@SuppressWarnings("null")
		int titleWidth = this.font.width(title);
		graphics.drawString(this.font, title, (this.imageWidth - titleWidth) / 2, 10, 0xFFD700, false);
		
		// 显示当前楼层
		Component floorText = Component.translatable("gui.stardew_craft.mine_exit.current_floor", this.menu.getCurrentFloor());
		@SuppressWarnings("null")
		int floorWidth = this.font.width(floorText);
		graphics.drawString(this.font, floorText, (this.imageWidth - floorWidth) / 2, 25, 0xFFFFFF, false);
	}
}
