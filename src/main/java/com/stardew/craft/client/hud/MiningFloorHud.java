package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.Config;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 矿井层数显示 HUD - 显示在左上角
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class MiningFloorHud {
    
    // empty_slot 背景图（16x16）
    private static final ResourceLocation EMPTY_SLOT = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/jei/empty_slot.png"
    );
    private static final int SLOT_SIZE = 32;
    
    // 客户端缓存的当前层数（由网络包同步）
    private static int currentFloor = 0;
    
    /**
     * 更新客户端的层数显示
     */
    public static void setCurrentFloor(int floor) {
        currentFloor = floor;
    }

    public static int getCurrentFloor() {
        return currentFloor;
    }
    
    /**
     * 渲染层数 HUD
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen instanceof StardewHudLayoutEditorScreen) {
            return;
        }
        if (mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        
        // 只在矿井维度显示
        if (mc.level.dimension() != ModMiningDimensions.STARDEW_MINING) {
            return;
        }

        // Dynamically calculate floor based on player Z position!
        currentFloor = (int) Math.max(0, Math.round(mc.player.getZ() / MiningCoordinates.FLOOR_SPACING));

        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;
        
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.MINING_FLOOR, guiGraphics.guiWidth(), guiGraphics.guiHeight());
        int x = 0;
        int y = 0;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(placement.x(), placement.y(), 0.0F);
        guiGraphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        
        // 绘制 empty_slot 背景，放大2倍（32x32）
        // 使用正确的blit方法：指定屏幕坐标、UV坐标、宽高、纹理总大小
        RenderSystem.enableBlend();
        guiGraphics.blit(EMPTY_SLOT, 
            x, y,           // 屏幕坐标
            0, 0,           // UV起点
            SLOT_SIZE, SLOT_SIZE,
            SLOT_SIZE, SLOT_SIZE);
        
        // 层数文本（骷髅矿从 121 开始显示为 0，下一层显示 1...）
        int displayFloor = currentFloor >= 121 ? (currentFloor - 121) : currentFloor;
        String floorText = String.valueOf(displayFloor);
        
        // 栗色（Maroon） RGB(128, 0, 0) = 0x800000
        int color = 0xFF800000;
        
        // 计算文字居中位置
        // 纹理empty_slot.png是16x16，可显示区域原始是 7,6 到 22,21（宽15 高15）
        // 放大2倍后：14,12 到 44,42（宽30 高30）
        // 中心点应该在：x + 14 + 30/2 = x + 29, y + 12 + 30/2 = y + 27
        @SuppressWarnings("null")
        int textWidth = font.width(floorText);
        int textHeight = font.lineHeight;
        
        int textX = x + SLOT_SIZE / 2 - textWidth / 2;
        int textY = y + SLOT_SIZE / 2 - textHeight / 2;
        
        // 绘制文字（启用阴影=加粗效果）
        guiGraphics.drawString(font, floorText, textX, textY, color, false);
        guiGraphics.pose().popPose();
        RenderSystem.disableBlend();
    }
}
