package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.animal.DuckEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class DuckGeoModel extends BlockbenchModel<DuckEntity> {
	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "geo/entity/animal/duck.geo.json");
	private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "animations/entity/animal/duck.animation.json");
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/entity/animal/duck.png");

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
