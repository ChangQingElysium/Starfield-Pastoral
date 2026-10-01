package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import software.bernie.geckolib.model.GeoModel;

public class CoopAnimalGeoModel<T extends BaseCoopAnimalEntity> extends GeoModel<T> {
	@Override
	public ResourceLocation getModelResource(T animatable) {
		String path = Objects.requireNonNull(animatable.getVariant().modelPath(animatable.isBaby()));
		return new ResourceLocation(StardewCraft.MODID, path);
	}

	@Override
	public ResourceLocation getTextureResource(T animatable) {
		String path = Objects.requireNonNull(animatable.getVariant().texturePath(animatable.isBaby()));
		return new ResourceLocation(StardewCraft.MODID, path);
	}

	@Override
	public ResourceLocation getAnimationResource(T animatable) {
		String path = Objects.requireNonNull(animatable.getVariant().animationPath(animatable.isBaby()));
		return new ResourceLocation(StardewCraft.MODID, path);
	}
}
