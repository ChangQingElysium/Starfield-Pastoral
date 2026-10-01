package com.stardew.craft.client.animal;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.runtime.LivestockEntity;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LivestockRenderer extends GeoEntityRenderer<LivestockEntity> {
    public LivestockRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public ResourceLocation getModelResource(LivestockEntity e) { return new ResourceLocation("stardewcraft", e.asset().modelPath(e.isBaby())); }
            @Override public ResourceLocation getTextureResource(LivestockEntity e) { return new ResourceLocation("stardewcraft", e.asset().texturePath(e.isBaby())); }
            @Override public ResourceLocation getAnimationResource(LivestockEntity e) { return new ResourceLocation("stardewcraft", e.asset().animationPath(e.isBaby())); }
        }); shadowRadius = .25f;
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LIVESTOCK_ANIMAL.get(), LivestockRenderer::new);
        event.registerEntityRenderer(ModEntities.LIVESTOCK_PRODUCT.get(), LivestockProductRenderer::new);
    }
}
