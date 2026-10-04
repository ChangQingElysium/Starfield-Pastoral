package com.stardew.craft.client.animal;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.runtime.LivestockEntity;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LivestockRenderer extends BlockbenchEntityRenderer<LivestockEntity> {
    public LivestockRenderer(EntityRendererProvider.Context context) {
        super(context, new BlockbenchModel<>() {
            @Override public ResourceLocation getModelResource(LivestockEntity e) { return ResourceLocation.fromNamespaceAndPath("stardewcraft", e.asset().modelPath(e.isBaby())); }
            @Override public ResourceLocation getTextureResource(LivestockEntity e) { return ResourceLocation.fromNamespaceAndPath("stardewcraft", e.asset().texturePath(e.isBaby())); }
            @Override public ResourceLocation getAnimationResource(LivestockEntity e) { return ResourceLocation.fromNamespaceAndPath("stardewcraft", e.asset().animationPath(e.isBaby())); }
        }); shadowRadius = .25f;
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LIVESTOCK_ANIMAL.get(), LivestockRenderer::new);
        event.registerEntityRenderer(ModEntities.LIVESTOCK_PRODUCT.get(), LivestockProductRenderer::new);
    }
}
