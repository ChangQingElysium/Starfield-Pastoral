package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.animal.DuckEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DuckGeoModel extends GeoModel<DuckEntity> {
	private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "geo/entity/animal/duck.geo.json");
	private static final ResourceLocation ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/entity/animal/duck.animation.json");
	private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/entity/animal/duck.png");

	@Override
	public ResourceLocation getModelResource(DuckEntity animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(DuckEntity animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(DuckEntity animatable) {
		return ANIMATION;
	}
}
