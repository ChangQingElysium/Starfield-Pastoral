package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.core.ModDimensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 星露谷物语玩家状态HUD
 * 在星露谷维度替代MC原版的生命和饱食度显示
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class StardewPlayerHud {
    
    // 材质资源
    private static final ResourceLocation BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/stardew_bars.png");
    private static final ResourceLocation BAR_CONTENT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/bar_content.png");
    private static final ResourceLocation HEALTH_ICON = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/health_icon.png");
    private static final ResourceLocation ENERGY_ICON = ResourceLocation.fromNamespaceAndPath(
        StardewCraft.MODID, "textures/gui/energy_icon.png");
    
    // 条形图尺寸（原始大小，和原版MC生命值差不多）
    private static final int BAR_WIDTH = 108;    // 原始宽度
    private static final int BAR_HEIGHT = 12;    // 原始高度
    
    // 填充区域（相对于条形图左上角的偏移）
    private static final int FILL_OFFSET_X = 3;  // 原始偏移
    private static final int FILL_OFFSET_Y = 3;  // 原始偏移
    private static final int FILL_WIDTH = 102;   // 原始填充宽度
    private static final int FILL_HEIGHT = 6;    // 原始填充高度
    
    // 图标尺寸（略小于条高）
    private static final int ICON_SIZE = 12;
    
    // 抖动计时器（毫秒）
    private static int healthShakeTimer = 0;
    private static int energyShakeTimer = 0;
    
    // 上一帧的数值，用于检测变化
    private static int lastHealth = -1;
    private static float lastEnergy = -1;
    private static long lastUpdateTime = 0;
    
    /**
     * 拦截原版生命值和饱食度渲染
     */
    @SubscribeEvent
    public static void onRenderHealthBar(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // 只在星露谷维度禁用原版HUD
        if (!shouldRenderCustomHUD(mc.player)) {
            return;
        }
        
        // 禁用原版的生命、饱食度、护甲、氧气条
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) ||
            event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) ||
            event.getName().equals(VanillaGuiLayers.ARMOR_LEVEL) ||
            event.getName().equals(VanillaGuiLayers.AIR_LEVEL)) {
            event.setCanceled(true);
        }
    }
    
    /**
     * 渲染自定义星露谷HUD
     */
    @SubscribeEvent
    public static void onRenderCustomHUD(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof StardewHudLayoutEditorScreen) return;
        
        // 只在星露谷维度渲染自定义HUD
        if (!shouldRenderCustomHUD(mc.player)) {
            return;
        }
        
        // 在HOTBAR层之后渲染（HOTBAR层总是会渲染）
        if (event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            renderStardewBars(event.getGuiGraphics(), mc.player);
        }
    }
    
    /**
     * 检查是否应该渲染自定义HUD
     */
    public static boolean shouldRenderCustomHUD(Player player) {
        // F1 隐藏 HUD
        if (Minecraft.getInstance().options.hideGui) {
            return false;
        }
        // 创造模式和旁观模式不显示
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        
        // 检查是否在星露谷维度或矿井维度
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY 
            && player.level().dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            return false;
        }
        return true;
    }
    
    /**
     * 渲染星露谷风格的能量和生命条
     */
    private static void renderStardewBars(GuiGraphics graphics, Player player) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.PLAYER_BARS, screenWidth, screenHeight);
        
        // 直接从客户端缓存获取数据（和金币HUD一样）
        float currentEnergy = ClientPlayerDataCache.getEnergy();
        float maxEnergy = ClientPlayerDataCache.getMaxEnergy();
        int currentHealth = ClientPlayerDataCache.getHealth();
        int maxHealth = ClientPlayerDataCache.getMaxHealth();
        
        // 检测数值变化并触发抖动
        if (lastHealth >= 0 && currentHealth < lastHealth) {
            float healthPercent = (float) currentHealth / maxHealth;
            if (healthPercent < 0.2f) { 
                healthShakeTimer = 300;
            }
        }
        
        if (lastEnergy >= 0 && currentEnergy < lastEnergy - 1) {
            if (currentEnergy <= 15) { 
                energyShakeTimer = 300;
            }
        }
        
        lastHealth = currentHealth;
        lastEnergy = currentEnergy;
        
        // 创造模式下保持满值
        if (player.isCreative()) {
            currentEnergy = maxEnergy;
            currentHealth = maxHealth;
        }
        
        // 是否疲惫
        boolean exhausted = ClientPlayerDataCache.isExhausted();
        
        graphics.pose().pushPose();
        graphics.pose().translate(placement.x(), placement.y(), 0.0F);
        graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
        renderBarsAtCurrentPose(graphics, currentEnergy, (int) maxEnergy, exhausted, currentHealth, maxHealth);
        graphics.pose().popPose();
        
        // 更新抖动计时器
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime > 0) {
            int deltaMs = (int)(currentTime - lastUpdateTime);
            if (healthShakeTimer > 0) {
                healthShakeTimer -= deltaMs;
                if (healthShakeTimer < 0) healthShakeTimer = 0;
            }
            if (energyShakeTimer > 0) {
                energyShakeTimer -= deltaMs;
                if (energyShakeTimer < 0) energyShakeTimer = 0;
            }
        }
        lastUpdateTime = currentTime;
    }

    public static void renderPreview(GuiGraphics graphics, int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderBarsAtCurrentPose(graphics, 270.0F, 270, false, 90, 100);
        graphics.pose().popPose();
    }

    private static void renderBarsAtCurrentPose(GuiGraphics graphics, float energy, int maxEnergy,
                                                boolean exhausted, int health, int maxHealth) {
        renderEnergyBar(graphics, 0, 16, 3, energy, maxEnergy, exhausted);
        renderHealthBar(graphics, 154, 266, 3, health, maxHealth);
    }
    
    /**
     * 渲染能量条
     */
    @SuppressWarnings("null")
    private static void renderEnergyBar(GuiGraphics graphics, int iconX, int barX, int y, 
                                        float current, int max, boolean exhausted) {
        int shakeX = 0, shakeY = 0;
        
        // 抖动效果（和原版一样：random.Next(-3, 4) 即 -3 到 +3）
        if (energyShakeTimer > 0) {
            shakeX = (int)(Math.random() * 7) - 3;  // 0-6 => -3 to +3
            shakeY = (int)(Math.random() * 7) - 3;
        }
        
        // 计算填充宽度
        float fillRatio = Math.min(1.0f, Math.max(0.0f, current / max));
        int fillWidth = (int)(FILL_WIDTH * fillRatio);
        
        // 计算颜色（根据百分比从红到绿渐变）
        int color = getColorForPercentage(fillRatio);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // 1. 渲染图标 (16x16)
        graphics.blit(ENERGY_ICON, iconX + shakeX, y + shakeY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        
        // 2. 渲染条形图背景 (108x12)
        graphics.blit(BAR_TEXTURE, barX + shakeX, y + shakeY, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        
        // 3. 渲染填充（纯色，从左往右填充）
        if (fillWidth > 0) {
            // 设置颜色（绿色基调）
            graphics.setColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1.0f
            );
            
            // 在填充区域内渲染 (从3,3开始，6*102区域)
            // 使用blit进行UV拉伸实现平滑填充
            graphics.blit(
                BAR_CONTENT_TEXTURE,
                barX + FILL_OFFSET_X + shakeX, 
                y + FILL_OFFSET_Y + shakeY,
                0, 0,
                fillWidth, FILL_HEIGHT,
                FILL_WIDTH, FILL_HEIGHT
            );
            
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        // 4. 渲染疲惫效果（可选：让条变灰或显示图标）
        if (exhausted) {
            // 在条上覆盖半透明灰色
            graphics.fill(barX + FILL_OFFSET_X + shakeX, y + FILL_OFFSET_Y + shakeY, 
                         barX + FILL_OFFSET_X + FILL_WIDTH + shakeX, y + FILL_OFFSET_Y + FILL_HEIGHT + shakeY, 
                         0x80808080);
        }
        
        // 5. 渲染数值文本（在条的中间）
        String text = String.format("%.0f/%d", current, max);
        @SuppressWarnings("null")
        int textWidth = Minecraft.getInstance().font.width(text);
        int textX = barX + BAR_WIDTH / 2 - textWidth / 2;  // 居中
        int textY = y + BAR_HEIGHT / 2 - 4;                // 垂直居中
        
        // 描边效果
        graphics.drawString(Minecraft.getInstance().font, text, textX + 1, textY, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX - 1, textY, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY + 1, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY - 1, 0x000000, false);
        
        // 主文本
        int textColor = exhausted ? 0xAAAAAA : 0xFFFFFF;
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY, textColor, false);
        
        RenderSystem.disableBlend();
    }
    
    /**
     * 渲染生命条
     */
    @SuppressWarnings("null")
    private static void renderHealthBar(GuiGraphics graphics, int barX, int iconX, int y, 
                                        int current, int max) {
        int shakeX = 0, shakeY = 0;
        
        // 抖动效果（和原版一样：random.Next(-3, 4) 即 -3 到 +3）
        if (healthShakeTimer > 0) {
            shakeX = (int)(Math.random() * 7) - 3;  // 0-6 => -3 to +3
            shakeY = (int)(Math.random() * 7) - 3;
            shakeY = (int)(Math.random() * 7) - 3;
        }
        
        // 计算填充宽度
        float fillRatio = Math.min(1.0f, Math.max(0.0f, (float)current / max));
        int fillWidth = (int)(FILL_WIDTH * fillRatio);
        
        // 计算颜色
        int color = getColorForPercentage(fillRatio);
        
        // 低血量闪烁效果
        float alpha = 1.0f;
        if (fillRatio < 0.2f) {
            alpha = 0.5f + 0.5f * (float)Math.sin(System.currentTimeMillis() / 200.0);
        }
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // 1. 渲染条形图背景 (108x12)
        graphics.blit(BAR_TEXTURE, barX + shakeX, y + shakeY, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        
        // 2. 渲染填充（带颜色和透明度，从左往右填充）
        if (fillWidth > 0) {
            // 设置颜色和透明度（红色基调）
            graphics.setColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                alpha
            );
            
            // 在填充区域内渲染 (从3,3开始，6*102区域)
            // 使用blit进行UV拉伸实现平滑填充
            graphics.blit(
                BAR_CONTENT_TEXTURE,
                barX + FILL_OFFSET_X + shakeX, 
                y + FILL_OFFSET_Y + shakeY,
                0, 0,
                fillWidth, FILL_HEIGHT,
                FILL_WIDTH, FILL_HEIGHT
            );
            
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        // 3. 渲染图标 (16x16，在条的右边)
        graphics.blit(HEALTH_ICON, iconX + shakeX, y + shakeY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        
        // 4. 渲染数值文本（在条的中间）
        String text = String.format("%d/%d", current, max);
        @SuppressWarnings("null")
        int textWidth = Minecraft.getInstance().font.width(text);
        int textX = barX + BAR_WIDTH / 2 - textWidth / 2;  // 居中
        int textY = y + BAR_HEIGHT / 2 - 4;                // 垂直居中
        
        // 描边效果
        graphics.drawString(Minecraft.getInstance().font, text, textX + 1, textY, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX - 1, textY, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY + 1, 0x000000, false);
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY - 1, 0x000000, false);
        
        // 主文本（低血量时红色）
        int textColor = fillRatio < 0.3f ? 0xFF5555 : 0xFFFFFF;
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY, textColor, false);
        
        RenderSystem.disableBlend();
    }
    
    /**
     * 根据百分比计算颜色
     * 100%-50%: 绿色
     * 49%-20%: 黄色
     * 19%-0%: 红色
     */
    private static int getColorForPercentage(float percentage) {
        percentage = Math.max(0, Math.min(1, percentage));
        
        if (percentage >= 0.5f) {
            // 50%-100%: 纯绿色
            return 0x00FF00;  // RGB(0, 255, 0)
        } else if (percentage >= 0.2f) {
            // 20%-49%: 纯黄色
            return 0xFFFF00;  // RGB(255, 255, 0)
        } else {
            // 0%-19%: 纯红色
            return 0xFF0000;  // RGB(255, 0, 0)
        }
    }
    
    /**
     * 触发生命值抖动效果
     */
    public static void triggerHealthShake() {
        healthShakeTimer = 10;  // 抖动10帧
    }
    
    /**
     * 触发能量抖动效果
     */
    public static void triggerEnergyShake() {
        energyShakeTimer = 10;  // 抖动10帧
    }
}
