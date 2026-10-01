package com.stardew.craft.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.building.runtime.RobinConstructionEntity;
import com.stardew.craft.client.npcnative.NativeNpcAssets;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.client.npcnative.NativeNpcPoseRenderer;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RobinConstructionRenderer extends EntityRenderer<RobinConstructionEntity> {
    private final NativeNpcPoseRenderer renderer = new NativeNpcPoseRenderer();
    private NativeNpcModel model;
    private NativeNpcPose pose;

    public RobinConstructionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35f;
    }

    @Override public ResourceLocation getTextureLocation(RobinConstructionEntity entity) {
        var asset = NativeNpcAssets.robinConstruction();
        return asset == null ? new ResourceLocation("textures/misc/white.png")
                : new ResourceLocation(asset.texture());
    }

    @Override public void render(RobinConstructionEntity entity, float yaw, float partialTick,
                                 PoseStack stack, MultiBufferSource buffers, int light) {
        var asset = NativeNpcAssets.robinConstruction();
        if (asset == null) return;
        if (model != asset) { model = asset; pose = new NativeNpcPose(asset); }
        pose.reset();
        pose.apply(entity.high() ? "animation.robin.construction" : "animation.robin.construction_low",
                entity.animationSeconds(partialTick));
        // This actor is not a LivingEntity: use its real synchronized yaw, never a default body yaw.
        renderer.render(entity, stack, buffers, light, model, pose, entity.getYRot());
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROBIN_CONSTRUCTION.get(), RobinConstructionRenderer::new);
    }
}
