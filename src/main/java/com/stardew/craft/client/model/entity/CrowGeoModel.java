package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.passive.CrowEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class CrowGeoModel extends BlockbenchModel<CrowEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/entity/crow.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/entity/crow.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/entity/crow.animation.json");

    @Override public ResourceLocation getModelResource(CrowEntity a) { return MODEL; }
    @Override public ResourceLocation getTextureResource(CrowEntity a) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(CrowEntity a) { return ANIMATION; }
}
