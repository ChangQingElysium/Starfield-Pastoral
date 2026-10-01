package com.stardew.craft.client.pet;

import com.stardew.craft.port.PortVertex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.pet.PetEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetRenderer extends EntityRenderer<PetEntity> {
    private static final class Playback {
        final PetPose pose;
        double lastX, lastZ, distance;
        boolean initialized;
        String clip = "";
        Playback(PetNativeAssets.Asset asset) { pose = new PetPose(asset); }
    }
    private final Map<PetEntity, Playback> playback = new WeakHashMap<>();
    public PetRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .35f; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(ModEntities.PET.get(), PetRenderer::new); }
    @Override public ResourceLocation getTextureLocation(PetEntity pet) { return pet.variant().breed().texture(); }
    @Override protected boolean shouldShowName(PetEntity pet) { return pet.isDiscrete() || entityRenderDispatcher.crosshairPickEntity == pet; }
    @Override public void render(PetEntity pet, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        var asset = PetNativeAssets.get(pet.variant()); if (asset == null || pet.isInvisible()) return;
        var motion = playback.get(pet);
        if (motion == null || motion.pose.asset != asset) { motion = new Playback(asset); playback.put(pet, motion); }
        double x = Mth.lerp(partial, pet.xOld, pet.getX()), z = Mth.lerp(partial, pet.zOld, pet.getZ());
        if (!motion.clip.equals(pet.clip())) { motion.distance = 0; motion.clip = pet.clip(); }
        if (motion.initialized && pet.movingClip()) {
            double distance = Math.hypot(x - motion.lastX, z - motion.lastZ);
            if (distance < 1) motion.distance += distance;
        }
        motion.initialized = true; motion.lastX = x; motion.lastZ = z;
        double now = (pet.level().getGameTime() + partial) / 20.;
        double time = pet.movingClip() ? motion.distance * 16 / pet.variant().stride(pet.clip().equals("sprint")) * asset.clips().get(pet.clip()).length()
                : Math.max(0, now - pet.clipStart() / 20.);
        motion.pose.sample(pet.clip(), time, pet.clipStart(), now);
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, pet.yRotO, pet.getYRot())));
        stack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        motion.pose.render(stack, buffers.getBuffer(RenderType.entityCutout(getTextureLocation(pet))), light);
        if (!pet.hat().isEmpty() && asset.hat() != null) {
            var hat = asset.hat(); stack.pushPose(); PortVertex.mulPose(stack, motion.pose.pose.world[hat.bone()]);
            stack.translate(hat.position()[0], hat.position()[1], hat.position()[2]);
            com.stardew.craft.client.render.PetHatRenderer.render(pet.hat(), stack, buffers, light, hat.scale());
            stack.popPose();
        }
        stack.popPose(); super.render(pet, yaw, partial, stack, buffers, light);
    }
}
