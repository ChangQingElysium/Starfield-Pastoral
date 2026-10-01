package com.stardew.craft.client.render;

import com.stardew.craft.blockentity.StorageChestBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class StorageChestBlockEntityRenderer extends StardewGeoBlockRenderer<StorageChestBlockEntity> {
    public StorageChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new GeoModel<>() {
            private ResourceLocation resource(StorageChestBlockEntity chest, String directory, String suffix) {
                return new ResourceLocation("stardewcraft", directory + chest.variant().id + suffix);
            }
            @Override public ResourceLocation getModelResource(StorageChestBlockEntity chest) {
                return resource(chest, "geo/block/utility/", ".geo.json");
            }
            @Override public ResourceLocation getTextureResource(StorageChestBlockEntity chest) {
                int color = chest.getColorSelection();
                return resource(chest, "textures/block/utility/", chest.variant().dyeable && color >= 0
                        ? String.format(java.util.Locale.ROOT, "_color_%02d.png", color) : ".png");
            }
            @Override public ResourceLocation getAnimationResource(StorageChestBlockEntity chest) {
                return resource(chest, "animations/block/utility/", ".animation.json");
            }
        });
    }
}
