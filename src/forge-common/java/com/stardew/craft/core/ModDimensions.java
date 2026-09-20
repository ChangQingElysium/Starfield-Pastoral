package com.stardew.craft.core;

import com.mojang.logging.LogUtils;
import com.stardew.craft.forge.ForgeBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;

/**
 * 维度注册
 */
public class ModDimensions {

    // 星露谷维度Key
    @SuppressWarnings("null")
    public static final ResourceKey<Level> STARDEW_VALLEY = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(ForgeBootstrap.MOD_ID, "stardew_valley")
    );

    // 星露谷维度类型Key
    @SuppressWarnings("null")
    public static final ResourceKey<DimensionType> STARDEW_VALLEY_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(ForgeBootstrap.MOD_ID, "stardew_valley")
    );

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register() {
        LOGGER.info("Registering Stardew Valley dimension");
    }
}
