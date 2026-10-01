package com.stardew.craft.client.fishing;


import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.fishing.TreasureChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 钓鱼宝箱UI - 类似原版箱子界面，可以看到背包和拿取物品
 */
public class TreasureChestScreen extends AbstractContainerScreen<TreasureChestMenu> implements com.stardew.craft.port.PortContainerScreen {
	// 使用原版的54格箱子贴图（通用箱子GUI）
	private static final ResourceLocation CHEST_TEXTURE = 
			new ResourceLocation("textures/gui/container/generic_54.png");
	
	private static final int CHEST_ROWS = 4;
	
	public TreasureChestScreen(TreasureChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		// 36格箱子高度: 114 + 玩家背包 + 标题栏
		// 每行18像素，4行 = 72像素，加上边距和标题
		this.imageHeight = CHEST_ROWS * 18 + 103 + 17; // 箱子部分 + 玩家背包部分 + 顶部标题
		this.inventoryLabelY = this.imageHeight - 94; // 调整"背包"文字位置
	}

	@Override
	protected void init() {
		super.init();
		// A vanilla menu-open packet may already be in flight when fishing is interrupted.
		if (!FishingCatchVisuals.expectsTreasureMenu() && minecraft != null && minecraft.player != null) {
			minecraft.player.closeContainer();
		}
	}

	@SuppressWarnings("null")
	@Override
	protected void renderBg(@SuppressWarnings("null") GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;
		
		// 绘制箱子背景
		// 顶部
		graphics.blit(CHEST_TEXTURE, x, y, 0, 0, this.imageWidth, CHEST_ROWS * 18 + 17);
		
		// 玩家背包部分
		graphics.blit(CHEST_TEXTURE, x, y + CHEST_ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
	}
	
	@SuppressWarnings("null")
	@Override
	public void render(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}
	
	@SuppressWarnings("null")
	@Override
	protected void renderLabels(@SuppressWarnings("null") GuiGraphics graphics, int mouseX, int mouseY) {
		// 绘制标题
		Component title = this.menu.isGolden() 
				? Component.translatable("stardewcraft.treasure.golden.title")
				: Component.translatable("stardewcraft.treasure.title");
		int titleColor = this.menu.isGolden() ? 0xFFD700 : 0x404040;
		int titleMaxWidth = this.imageWidth - this.titleLabelX - 8;
		graphics.drawString(this.font, GuiText.ellipsize(this.font, title, titleMaxWidth),
				this.titleLabelX, this.titleLabelY, titleColor, false);
		
		// 绘制"背包"文字
		int inventoryMaxWidth = this.imageWidth - this.inventoryLabelX - 8;
		graphics.drawString(this.font, GuiText.ellipsize(this.font, this.playerInventoryTitle, inventoryMaxWidth),
				this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
	}
}
