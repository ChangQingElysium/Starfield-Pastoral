package com.stardew.craft.client.monsternative;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.GreenSlimeEntity;
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

/** Generic cuboids, native keyframe sampling, per-part tint, and culled inverted hulls. */
@SuppressWarnings({"null", "removal"})
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeSlimeRenderer extends EntityRenderer<GreenSlimeEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StardewCraft.MODID, "monster_native/green_slime.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/entity/monster_native/green_slime.png");
    private static volatile NativeNpcModel loaded;
    private NativeNpcModel posedModel;
    private final java.util.Map<GreenSlimeEntity, NativeSlimeMotion> motions = new java.util.WeakHashMap<>();
    private final Vector3f vertex = new Vector3f(), normal = new Vector3f();
    private final Matrix3f normalMatrix = new Matrix3f();

    public NativeSlimeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = .35F;
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GREEN_SLIME.get(), NativeSlimeRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_JELLY.get(), NativeSlimeRenderer::new);
        event.registerEntityRenderer(ModEntities.SLUDGE.get(), NativeSlimeRenderer::new);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) NativeSlimeRenderer::load);
    }
    private static void load(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(MODEL).openAsReader()) {
            NativeNpcModel model = new Gson().fromJson(reader, NativeNpcModel.class);
            if (model == null || model.version() != 1 || model.bones() == null || model.bones().isEmpty()
                    || model.quads() == null || model.quads().isEmpty() || !TEXTURE.toString().equals(model.texture())) {
                throw new IllegalArgumentException("Invalid native slime model header");
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
            for (String suffix : new String[]{"idle", "idle_hop", "charge", "airborne", "land"}) {
                var clip = model.clips().get("animation.slime." + suffix);
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
            resources.getResourceOrThrow(TEXTURE);
            loaded = model;
        } catch (Exception ex) {
            // A broken resource pack must be diagnosable, not a silently invisible monster.
            throw new IllegalStateException("Cannot load " + MODEL, ex);
        }
    }
    private static void finite(float[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Invalid vector");
        for (float v : values) if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite coordinate");
    }
    @Override protected boolean shouldShowName(GreenSlimeEntity entity) { return false; }
    @Override public ResourceLocation getTextureLocation(GreenSlimeEntity entity) { return TEXTURE; }
    @Override public void render(GreenSlimeEntity entity, float yaw, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        var model = loaded;
        if (model == null || entity.isInvisible()) return;
        if (posedModel != model) { posedModel = model; motions.clear(); }
        var motion = motions.computeIfAbsent(entity, key -> new NativeSlimeMotion(model));
        motion.sample(entity.phase(), entity.actionSequence(), entity.animationTime(partialTick),
                (entity.tickCount + partialTick) / 20.0,
                Mth.lerp(partialTick, entity.xOld, entity.getX()), Mth.lerp(partialTick, entity.zOld, entity.getZ()),
                entity.growth(), entity.deathTime > 0);
        var matrices = motion.pose().matrices();
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        // SDV yOffset is visual elevation: keep the one ground AABB stable while the gel hops.
        double lift = motion.lift();
        float growthScale = entity.growth() / 16F;
        float scale = growthScale * entity.getScale();
        var death = NativeMonsterPresentation.deathTransform(entity.deathTime > 0 ? entity.deathTime + partialTick : 0, 4);
        stack.translate(0, (lift + NativeMonsterPresentation.groundLift(model, matrices, death, growthScale, entity.antenna())) * entity.getScale(), 0);
        stack.scale(scale, scale, scale);
        stack.mulPose(death);
        int bodyColor = NativeMonsterPresentation.slimeTint(entity.color(), false);
        int outlineColor = NativeMonsterPresentation.slimeTint(entity.color(), true);
        if (entity.frostRush()) {
            float glow = entity.frostGlow(partialTick);
            bodyColor = Math.round((bodyColor >> 16 & 255) * (1 - glow)) << 16
                    | Math.round(Mth.lerp(glow, bodyColor >> 8 & 255, 255)) << 8
                    | Math.round(Mth.lerp(glow, bodyColor & 255, 255));
        }
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
        for (int pass = 0; pass < 2; pass++) {
            boolean translucent = pass == 1;
            var consumer = buffers.getBuffer(translucent ? RenderType.entityTranslucentCull(TEXTURE) : RenderType.entityCutout(TEXTURE));
            for (var quad : model.quads()) {
                if (quad.translucent() != translucent) continue;
                String part = quad.sourcePart();
                if (!NativeMonsterPresentation.slimePartVisible(part, entity.antenna())) continue;
                boolean tint = part.startsWith("gel_") || part.startsWith("male_")
                        || part.startsWith("antenna_") && entity.antenna() == 1;
                int c = part.endsWith("_outline") ? outlineColor : bodyColor;
                normal.set(quad.normal());
                // The visible inner hull normal already faces the viewer. Negating it lights
                // the hidden outward side and makes the rim darker than the matching body face.
                int partLight = part.endsWith("_outline")
                        ? net.minecraft.client.renderer.LightTexture.pack(Math.max(10,
                            net.minecraft.client.renderer.LightTexture.block(light)),
                            net.minecraft.client.renderer.LightTexture.sky(light)) : light;
                matrices[quad.bone()].normal(normalMatrix).transform(normal).normalize();
                for (var v : quad.vertices()) {
                    matrices[quad.bone()].transformPosition(vertex.set(v[0], v[1], v[2]));
                    consumer.addVertex(stack.last().pose(), vertex.x, vertex.y, vertex.z)
                            .setColor(tint ? c >> 16 & 255 : 255, tint ? c >> 8 & 255 : 255, tint ? c & 255 : 255, 255)
                            .setUv(v[3], v[4]).setOverlay(overlay).setLight(partLight)
                            .setNormal(stack.last(), normal.x, normal.y, normal.z);
                }
            }
        }
        stack.popPose();
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }
}
