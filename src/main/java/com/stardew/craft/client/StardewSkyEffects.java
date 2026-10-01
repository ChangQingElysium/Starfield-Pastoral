package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * 星露谷维度天空效果
 * 使用NORMAL天空类型，让天空颜色跟随MC的dayTime自然变化
 */
public class StardewSkyEffects extends DimensionSpecialEffects {
    
    public StardewSkyEffects() {
        // 使用NORMAL天空，让它跟随原版逻辑
        super(192.0f, true, SkyType.NORMAL, false, false);
    }
    
    @Override
    public @javax.annotation.Nonnull Vec3 getBrightnessDependentFogColor(@javax.annotation.Nonnull Vec3 color, float brightness) {
        // 强制返回明亮的天空颜色
        // brightness范围：0（夜晚）到1（白天）
        
        if (brightness > 0.5f) {
            // 白天：明亮的蓝天
            float factor = (brightness - 0.5f) * 2.0f; // 0到1
            return new Vec3(
                0.6 * (0.7f + factor * 0.3f),
                0.8 * (0.7f + factor * 0.3f),
                1.0 * (0.7f + factor * 0.3f)
            );
        } else if (brightness > 0.2f) {
            // 日出/日落：橙红色过渡
            float factor = (brightness - 0.2f) / 0.3f;
            return new Vec3(0.9 * factor, 0.6 * factor, 0.4 * factor);
        } else {
            // 夜晚：深蓝色而不是黑色
            return new Vec3(0.05, 0.05, 0.2);
        }
    }
    
    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }
    
    /**
     * 注册维度效果
     */
    @EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        @SuppressWarnings("null")
        public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
            // 不注册自定义效果，直接使用minecraft:overworld效果
            // event.register(
            //     new ResourceLocation(StardewCraft.MODID + ":stardew_valley"),
            //     new StardewSkyEffects()
            // );
            StardewCraft.LOGGER.info("Using vanilla overworld sky effects for Stardew Valley dimension");
        }
    }
}
