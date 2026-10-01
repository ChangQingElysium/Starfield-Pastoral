package com.stardew.craft.client.particle;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 金星粒子效果 - 完全按原版实现
 */
public class GoldStarParticle {
    
    @SuppressWarnings("null")
    private static final ResourceLocation STAR_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/item/particle/gold_star.png");
    
    private static final int FRAME_SIZE = 5; // 每帧5x5像素
    private static final int TOTAL_FRAMES = 5; // 总共5帧
    private static final float FRAME_DURATION = 100f; // 每帧持续时间（毫秒）
    
    private final float x;
    private final float y;
    private float animationTimer;
    private boolean finished;
    
    public GoldStarParticle(float x, float y) {
        this.x = x;
        this.y = y;
        this.animationTimer = 0;
        this.finished = false;
    }
    
    /**
     * 更新粒子状态
     * @return 是否应该移除此粒子
     */
    public boolean update(float deltaTime) {
        animationTimer += deltaTime;
        
        // 总持续时间：5帧 * 100ms = 500ms
        if (animationTimer >= TOTAL_FRAMES * FRAME_DURATION) {
            finished = true;
            return true;
        }
        
        return false;
    }
    
    /**
     * 渲染粒子
     */
    @SuppressWarnings("null")
    public void render(GuiGraphics graphics) {
        if (finished) return;
        
        // 计算当前帧索引
        int currentFrame = (int)(animationTimer / FRAME_DURATION);
        currentFrame = Math.min(currentFrame, TOTAL_FRAMES - 1);
        
        // 计算纹理Y坐标（竖向排列）
        int textureY = currentFrame * FRAME_SIZE;
        
        // 渲染金星（使用金色Color.Gold）
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 0.843f, 0.0f, 1.0f); // Gold = #FFD700
        graphics.blit(STAR_TEXTURE, (int)x, (int)y, 0, textureY, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE * TOTAL_FRAMES);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    public boolean isFinished() {
        return finished;
    }
}
