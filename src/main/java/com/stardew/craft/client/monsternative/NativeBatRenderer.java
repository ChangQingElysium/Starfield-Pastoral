package com.stardew.craft.client.monsternative;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineBatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/** Approved Generic bat, cutout wing membranes and a backface-culled signed head hull. */
@SuppressWarnings({"null", "removal"})
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeBatRenderer extends EntityRenderer<MineBatEntity> {
    private static final String[] VARIANTS = {"bat", "frost_bat", "lava_bat", "iridium_bat"};
    private static volatile java.util.Map<String, NativeNpcModel> loaded = java.util.Map.of();
    private NativeNpcModel posedModel;
    private NativeNpcPose pose;
    private final Vector3f vertex = new Vector3f(), normal = new Vector3f();
    private final Matrix3f normalMatrix = new Matrix3f();
    public NativeBatRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BAT.get(), NativeBatRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_BAT.get(), NativeBatRenderer::new);
        event.registerEntityRenderer(ModEntities.LAVA_BAT.get(), NativeBatRenderer::new);
        event.registerEntityRenderer(ModEntities.IRIDIUM_BAT.get(), NativeBatRenderer::new);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) NativeBatRenderer::load);
    }
    private static void load(ResourceManager resources) {
        var models = new java.util.HashMap<String, NativeNpcModel>();
        for (String variant : VARIANTS) models.put(variant, loadVariant(resources, variant));
        loaded = java.util.Map.copyOf(models);
    }
    private static NativeNpcModel loadVariant(ResourceManager resources, String variant) {
        var modelId = new ResourceLocation(StardewCraft.MODID, "monster_native/" + variant + ".json");
        var texture = texture(variant);
        try (var reader = resources.getResourceOrThrow(modelId).openAsReader()) {
            NativeNpcModel model = new Gson().fromJson(reader, NativeNpcModel.class);
            if (model == null || model.version() != 1 || model.bones() == null || model.bones().isEmpty()
                    || model.quads() == null || model.quads().isEmpty() || !texture.toString().equals(model.texture())) {
                throw new IllegalArgumentException("Invalid native bat model header");
            }
            for (int i = 0; i < model.bones().size(); i++) {
                var bone = model.bones().get(i);
                if (bone.parent() < -1 || bone.parent() >= i) throw new IllegalArgumentException("Invalid bone hierarchy");
                finite(bone.origin(), 3); finite(bone.rotation(), 3);
            }
            for (var quad : model.quads()) {
                if (quad.bone() < 0 || quad.bone() >= model.bones().size()
                        || quad.vertices().length != 4 || quad.sourcePart() == null) throw new IllegalArgumentException("Invalid quad");
                finite(quad.normal(), 3);
                for (var v : quad.vertices()) finite(v, 5);
            }
            for (String suffix : new String[]{"roost", "wake", "fly"}) {
                var clip = model.clips().get("animation.bat." + suffix);
                if (clip == null || !Double.isFinite(clip.length()) || clip.length() <= 0) throw new IllegalArgumentException("Missing/invalid " + suffix);
                for (var track : clip.tracks()) {
                    if (track.bone() < 0 || track.bone() >= model.bones().size() || track.keys().isEmpty()
                            || !java.util.Set.of("position", "rotation", "scale").contains(track.channel())) throw new IllegalArgumentException("Invalid track");
                    double previous = -1;
                    for (var key : track.keys()) {
                        if (!Double.isFinite(key.time()) || key.time() <= previous || key.time() > clip.length()) throw new IllegalArgumentException("Invalid key time");
                        finite(key.before(), 3); finite(key.after(), 3);
                        if (track.channel().equals("scale")) for (int axis=0; axis<3; axis++)
                            if (key.before()[axis] <= 0 || key.after()[axis] <= 0) throw new IllegalArgumentException("Singular scale");
                        previous = key.time();
                    }
                }
            }
            resources.getResourceOrThrow(texture);
            return model;
        } catch (Exception ex) {
            // A broken resource pack must be diagnosable, not a silently invisible monster.
            throw new IllegalStateException("Cannot load " + modelId, ex);
        }
    }
    private static void finite(float[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Invalid vector");
        for (float v : values) if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite coordinate");
    }
    @Override protected boolean shouldShowName(MineBatEntity entity) { return false; }
    private static ResourceLocation texture(String variant) {
        return new ResourceLocation(StardewCraft.MODID, "textures/entity/monster_native/" + variant + ".png");
    }
    @Override public ResourceLocation getTextureLocation(MineBatEntity entity) { return texture(entity.variant()); }
    @Override public void render(MineBatEntity entity, float yaw, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        var model = loaded.get(entity.variant());
        if (model == null || entity.isInvisible()) return;
        if (posedModel != model) { posedModel = model; pose = new NativeNpcPose(model); }
        double time = entity.animationTime(partialTick);
        // Hold the pose at death rather than continuing to flap through the death roll.
        if (entity.deathTime > 0) time = Math.max(0, time - (entity.deathTime + partialTick) / 20.0);
        NativeBatMotion.sample(pose, entity.phase(), time);
        var matrices = pose.matrices();
        var death = NativeMonsterPresentation.deathTransform(entity.deathTime > 0 ? entity.deathTime + partialTick : 0, 8);
        float lift = NativeBatMotion.lift(model, matrices, entity.phase(), time);
        if (entity.deathTime > 0) lift = Math.max(lift, NativeMonsterPresentation.groundLift(model, matrices, death, 1F / 16));
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        stack.translate(0, lift * entity.getScale(), 0);
        float scale = entity.getScale() / 16;
        stack.scale(scale, scale, scale);
        stack.mulPose(death);
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
        var consumer = buffers.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
        for (var quad : model.quads()) {
            normal.set(quad.normal());
            matrices[quad.bone()].normal(normalMatrix).transform(normal).normalize();
            for (var v : quad.vertices()) {
                matrices[quad.bone()].transformPosition(vertex.set(v[0], v[1], v[2]));
                consumer.addVertex(stack.last().pose(), vertex.x, vertex.y, vertex.z)
                        .setColor(255, entity.deepRed() ? 0 : 255, entity.deepRed() ? 0 : 255, 255).setUv(v[3], v[4]).setOverlay(overlay).setLight(light)
                        .setNormal(stack.last(), normal.x, normal.y, normal.z);
            }
        }
        stack.popPose();
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }
}
