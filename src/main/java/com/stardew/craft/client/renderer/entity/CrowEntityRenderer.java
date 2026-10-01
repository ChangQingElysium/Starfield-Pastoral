package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.CrowGeoModel;
import com.stardew.craft.entity.passive.CrowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

public class CrowEntityRenderer extends BlockbenchEntityRenderer<CrowEntity> {
    public CrowEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CrowGeoModel());
        this.shadowRadius = 0.2F;
    }
}
