package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.WizardBuildingCatalogBlockEntity;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.world.WizardBuildingCatalogService;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class WizardBuildingCatalogGeoModel extends GeoModel<WizardBuildingCatalogBlockEntity> {
    private static final ResourceLocation INACTIVE_MODEL = resource(
            "geo/block/decor/wizard_building_catalog_inactive.geo.json");
    private static final ResourceLocation ACTIVE_MODEL = resource(
            "geo/block/decor/wizard_building_catalog_active.geo.json");
    private static final ResourceLocation INACTIVE_TEXTURE = resource(
            "textures/block/decor/wizard_building_catalog_inactive.png");
    private static final ResourceLocation ACTIVE_TEXTURE = resource(
            "textures/block/decor/wizard_building_catalog_active.png");

    @Override
    public ResourceLocation getModelResource(WizardBuildingCatalogBlockEntity animatable) {
        return isUnlocked() ? ACTIVE_MODEL : INACTIVE_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WizardBuildingCatalogBlockEntity animatable) {
        return isUnlocked() ? ACTIVE_TEXTURE : INACTIVE_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WizardBuildingCatalogBlockEntity animatable) {
        return null;
    }

    private static boolean isUnlocked() {
        return ClientPlayerDataCache.hasMailFlag(WizardBuildingCatalogService.UNLOCK_FLAG);
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}
