package com.stardew.craft.client.gingerisland;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.model.nativebb.BlockbenchPlayback;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPoseRenderer;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.gingerisland.GiantTurtleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

/** Approved v4 cuboids and atlas, through the existing native geometry/animation pipeline. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GiantTurtleRenderer extends EntityRenderer<GiantTurtleEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID,
            "island_actor_native/giant_turtle.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID,
            "textures/entity/island_actor_native/giant_turtle.png");
    private static volatile NativeNpcModel loaded;
    private final NativeNpcPoseRenderer geometry = new NativeNpcPoseRenderer();
    private final java.util.Map<GiantTurtleEntity, BlockbenchPlayback> motions = new java.util.WeakHashMap<>();

    public GiantTurtleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.2F;
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GINGER_GIANT_TURTLE.get(), GiantTurtleRenderer::new);
    }

    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GiantTurtleRenderer::load);
    }

    private static void load(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(MODEL).openAsReader()) {
            NativeNpcModel model = new Gson().fromJson(reader, NativeNpcModel.class);
            validate(model);
            resources.getResourceOrThrow(TEXTURE);
            loaded = model;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load native giant turtle " + MODEL, error);
        }
    }

    private static void validate(NativeNpcModel model) {
        if (model == null || model.version() != 1 || !TEXTURE.toString().equals(model.texture())
                || model.bones() == null || model.bones().isEmpty() || model.quads() == null || model.quads().isEmpty()
                || model.clips() == null) throw new IllegalArgumentException("Invalid giant turtle model");
        for (int i = 0; i < model.bones().size(); i++) {
            var bone = model.bones().get(i);
            if (bone.parent() < -1 || bone.parent() >= i) throw new IllegalArgumentException("Bone hierarchy");
            finite(bone.origin(), 3); finite(bone.rotation(), 3);
        }
        for (var quad : model.quads()) {
            if (quad.bone() < 0 || quad.bone() >= model.bones().size() || quad.vertices().length != 4)
                throw new IllegalArgumentException("Invalid quad");
            finite(quad.normal(), 3);
            for (var vertex : quad.vertices()) finite(vertex, 5);
        }
        for (String name : new String[]{"idle", "walk"}) {
            var clip = model.clips().get(name);
            if (clip == null || !clip.loop() || !Double.isFinite(clip.length()) || clip.length() <= 0
                    || clip.tracks().isEmpty()) throw new IllegalArgumentException("Invalid " + name + " clip");
            for (var track : clip.tracks()) {
                if (track.bone() < 0 || track.bone() >= model.bones().size() || track.keys().isEmpty()
                        || !java.util.Set.of("position", "rotation", "scale").contains(track.channel()))
                    throw new IllegalArgumentException("Invalid animation track");
                double previous = -1;
                for (var key : track.keys()) {
                    if (!Double.isFinite(key.time()) || key.time() < 0 || key.time() <= previous
                            || key.time() > clip.length()) throw new IllegalArgumentException("Key time");
                    finite(key.before(), 3); finite(key.after(), 3);
                    if (track.channel().equals("scale")) for (int axis = 0; axis < 3; axis++)
                        if (key.before()[axis] <= 0 || key.after()[axis] <= 0)
                            throw new IllegalArgumentException("Singular scale");
                    previous = key.time();
                }
            }
        }
    }

    private static void finite(float[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Vector size");
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite vector");
    }

    @Override protected boolean shouldShowName(GiantTurtleEntity entity) { return false; }
    @Override public ResourceLocation getTextureLocation(GiantTurtleEntity entity) { return TEXTURE; }

    @Override public void render(GiantTurtleEntity entity, float yaw, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        var model = loaded;
        if (model == null || entity.isInvisible()) return;
        var motion = motions.compute(entity, (actor, old) -> old != null && old.model() == model
                ? old : new BlockbenchPlayback(model));
        var pose = motion.sample(entity.modelAnimation(false, partialTick),
                (entity.level().getGameTime() + (double) partialTick) / 20, entity.modelTransitionTicks());
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        stack.scale(1F / 16, 1F / 16, 1F / 16);
        geometry.renderGeometry(stack, buffers, light, model, pose);
        stack.popPose();
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }
}
