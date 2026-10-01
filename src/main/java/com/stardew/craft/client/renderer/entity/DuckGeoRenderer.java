package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.DuckGeoModel;
import com.stardew.craft.entity.animal.DuckEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

public class DuckGeoRenderer extends BlockbenchEntityRenderer<DuckEntity> {
	public DuckGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new DuckGeoModel());
		this.shadowRadius = 0.35F;
	}
}
