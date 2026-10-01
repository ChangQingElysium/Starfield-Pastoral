package com.stardew.craft.client.monsternative;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.monster.MineRockGolemEntity;
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

import com.stardew.craft.port.PortVertex;
/** Source-shaped rock heap and stone creature, rendered with the shared native cuboid format. */
@SuppressWarnings({"null", "removal"})
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeRockGolemRenderer extends EntityRenderer<MineRockGolemEntity> {
    private static final String[] VARIANTS = {"rock_golem", "wilderness_golem", "iridium_golem"};
    private static volatile java.util.Map<String, NativeNpcModel> loaded = java.util.Map.of();
    private NativeNpcModel posedModel;
    private final java.util.Map<MineRockGolemEntity,NativeRockGolemPlayback> motions=new java.util.WeakHashMap<>();
    private final Vector3f vertex = new Vector3f(), normal = new Vector3f();
    private final Matrix3f normalMatrix = new Matrix3f();
    public NativeRockGolemRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .28F; }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROCK_GOLEM.get(), NativeRockGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.WILDERNESS_GOLEM.get(), NativeRockGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.IRIDIUM_GOLEM.get(), NativeRockGolemRenderer::new);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) NativeRockGolemRenderer::load);
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
                throw new IllegalArgumentException("Invalid native rock golem model header");
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
            for (String suffix : new String[]{"disguise","emerge","idle","walk","hit","death"}) {
                var clip = model.clips().get("animation.rock_golem." + suffix);
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
    @Override protected boolean shouldShowName(MineRockGolemEntity entity) { return false; }
    private static ResourceLocation texture(String variant) {
        return new ResourceLocation(StardewCraft.MODID, "textures/entity/monster_native/" + variant + ".png");
    }
    @Override public ResourceLocation getTextureLocation(MineRockGolemEntity entity) { return texture(entity.visualVariant()); }
    @Override public void render(MineRockGolemEntity entity, float yaw, float partialTick, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        var model = loaded.get(entity.visualVariant());
        if (model == null || entity.isInvisible() || entity.deathTime+partialTick>=16) return;
        if (posedModel != model) { posedModel=model;motions.clear(); }
        var motion=motions.computeIfAbsent(entity,e->new NativeRockGolemPlayback(model,entity.isFarmGolem()));
        motion.sample((entity.level().getGameTime()+(double)partialTick)/20.,entity.moving(),entity.phase(),entity.riseProgress(partialTick),
                entity.hitTime(partialTick),entity.deathTime>0?(entity.deathTime+partialTick)/20.:0);
        shadowRadius=entity.phase()==2?.28F:0;
        var matrices=motion.pose().matrices();
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partialTick,entity.yRotO,entity.getYRot())));
        float scale=entity.getScale()/16;
        stack.scale(scale,scale,scale);
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
        var consumer=buffers.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
        for(var quad:model.quads()){
            if(quad.sourcePart().startsWith("eyes_")&&(entity.phase()==0||entity.riseProgress(partialTick)<.25))continue;
            if(entity.isFarmGolem() && entity.phase()!=2 && quad.sourcePart().endsWith("_outline"))continue;
            normal.set(quad.normal());matrices[quad.bone()].normal(normalMatrix).transform(normal).normalize();
            for(var v:quad.vertices()){
                matrices[quad.bone()].transformPosition(vertex.set(v[0],v[1],v[2]));
                PortVertex.of(consumer).addVertex(stack.last().pose(),vertex.x,vertex.y,vertex.z).setColor(255,255,255,255).setUv(v[3],v[4])
                        .setOverlay(overlay).setLight(light).setNormal(stack.last(),normal.x,normal.y,normal.z).endVertex();
            }
        }
        stack.popPose();
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }
}
