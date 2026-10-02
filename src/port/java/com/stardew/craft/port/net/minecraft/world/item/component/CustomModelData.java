package com.stardew.craft.port.net.minecraft.world.item.component;

import com.mojang.serialization.Codec;

/** 1.21 {@code minecraft:custom_model_data}; stored as the vanilla 1.20.1 {@code CustomModelData} int tag. */
public record CustomModelData(int value) {
    public static final CustomModelData DEFAULT = new CustomModelData(0);
    public static final Codec<CustomModelData> CODEC = Codec.INT.xmap(CustomModelData::new, CustomModelData::value);
}
