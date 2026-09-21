package com.stardew.craft.deco;

import net.minecraft.resources.ResourceLocation;

public record DecorationStyle(
    DecorationType type,
    String id,
    ResourceLocation texture,
    int texWidth,
    int texHeight,
    int sourceX,
    int sourceY,
    int sourceWidth,
    int sourceHeight,
    String unlockHintKey,
    int sortOrder
) {
}
