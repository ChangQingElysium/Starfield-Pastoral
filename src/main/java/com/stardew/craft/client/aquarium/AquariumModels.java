package com.stardew.craft.client.aquarium;

import com.stardew.craft.port.PortRenderStateShards;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.Map;

import com.stardew.craft.port.PortVertex;
@EventBusSubscriber(modid=StardewCraft.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class AquariumModels {
    private record Model(NativeNpcModel data, NativeNpcPose pose, RenderType solid, RenderType glass) {}
    private static volatile Map<String, Model> models = Map.of();
    private AquariumModels() {}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            var next = new java.util.HashMap<String, Model>();
            resources.listResources("aquarium", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
                try (var reader = resource.openAsReader()) {
                    var data = new Gson().fromJson(reader, NativeNpcModel.class);
                    var texture = new ResourceLocation(data.texture());
                    resources.getResourceOrThrow(texture);
                    if (data.version() != 1 || data.quads().isEmpty()) throw new IllegalStateException("Invalid aquarium geometry");
                    String name = id.getPath().substring(9, id.getPath().length() - 5);
                    next.put(name, new Model(data, new NativeNpcPose(data), RenderType.entityCutout(texture), glass(texture)));
                } catch (Exception e) { throw new IllegalStateException("Cannot load aquarium " + id, e); }
            });
            models = Map.copyOf(next);
        });
    }
    private static RenderType glass(ResourceLocation texture) {
        return RenderType.create("aquarium_glass_" + texture.getPath(), DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 4096, false, true, RenderType.CompositeState.builder()
                        .setShaderState(PortRenderStateShards.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(PortRenderStateShards.TRANSLUCENT_TRANSPARENCY).setCullState(PortRenderStateShards.CULL)
                        .setLightmapState(PortRenderStateShards.LIGHTMAP).setOverlayState(PortRenderStateShards.OVERLAY)
                        .setWriteMaskState(PortRenderStateShards.COLOR_WRITE).setOutputState(PortRenderStateShards.ITEM_ENTITY_TARGET)
                        .createCompositeState(false));
    }
    public static void render(String name, boolean transparent, PoseStack pose, MultiBufferSource buffers, int light) {
        Model model = models.get(name); if (model == null) return;
        var matrices = model.pose().matrices();
        var vertex = new Vector3f(); var normal = new Vector3f(); var normalMatrix = new Matrix3f();
        var consumer = buffers.getBuffer(transparent ? model.glass() : model.solid());
        for (var quad : model.data().quads()) {
            if (quad.translucent() != transparent) continue;
            normal.set(quad.normal());
            if (quad.bone() >= 0) matrices[quad.bone()].normal(normalMatrix).transform(normal).normalize();
            for (var v : quad.vertices()) {
                vertex.set(v[0], v[1], v[2]);
                if (quad.bone() >= 0) matrices[quad.bone()].transformPosition(vertex);
                PortVertex.of(consumer).addVertex(pose.last().pose(), vertex.x, vertex.y, vertex.z).setColor(255,255,255,255)
                        .setUv(v[3],v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose.last(), normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
}
