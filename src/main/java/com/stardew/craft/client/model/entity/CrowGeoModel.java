package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.passive.CrowEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CrowGeoModel extends GeoModel<CrowEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(StardewCraft.MODID, "geo/entity/crow.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(StardewCraft.MODID, "textures/entity/crow.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(StardewCraft.MODID, "animations/entity/crow.animation.json");

    @Override public ResourceLocation getModelResource(CrowEntity a) { return MODEL; }
    @Override public ResourceLocation getTextureResource(CrowEntity a) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(CrowEntity a) { return ANIMATION; }
}
