package com.stardew.craft.core;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 维度注册
 */
public class ModDimensions {
    
    // 星露谷维度Key
    @SuppressWarnings("null")
    public static final ResourceKey<Level> STARDEW_VALLEY = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(StardewCraft.MODID, "stardew_valley")
    );
    
    // 星露谷维度类型Key
    @SuppressWarnings("null")
    public static final ResourceKey<DimensionType> STARDEW_VALLEY_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(StardewCraft.MODID, "stardew_valley")
    );
    
    public static void register() {
        StardewCraft.LOGGER.info("Registering Stardew Valley dimension");
    }
}
