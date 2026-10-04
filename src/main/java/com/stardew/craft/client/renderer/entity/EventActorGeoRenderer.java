package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.EventActorGeoModel;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.client.npcnative.NativeNpcAssets;
import com.stardew.craft.client.npcnative.NativeSamRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

/**
 * Renderer for {@link EventActorEntity}.
 * Uses the same resource-driven model selection as real NPCs. Scripted animation
 * requests must never swap an available native character back to its legacy model.
 */
public class EventActorGeoRenderer extends EntityRenderer<EventActorEntity> {
    private final BlockbenchEntityRenderer<EventActorEntity> legacy;
    private final EntityRendererProvider.Context context;
    private final java.util.Map<String,NativeSamRenderer<EventActorEntity>> nativeRenderers;

    public EventActorGeoRenderer(EntityRendererProvider.Context context) {
        super(context);
        legacy=new BlockbenchEntityRenderer<>(context,new EventActorGeoModel()) {
            @Override protected String npcAnimationId(EventActorEntity entity) {
                return NativeNpcAssets.legacyId(entity.getNpcId());
            }
        };
        this.context = context;
        nativeRenderers = new java.util.HashMap<>();
        this.shadowRadius = 0.35F;
    }

    private boolean useNative(EventActorEntity entity) {
        String id = NativeNpcAssets.renderId(entity.getNpcId());
        if (NativeNpcAssets.model(id) == null) return false;
        nativeRenderers.computeIfAbsent(id, key -> new NativeSamRenderer<>(context, key));
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(EventActorEntity entity) {
        return useNative(entity)?nativeRenderers.get(NativeNpcAssets.renderId(entity.getNpcId())).getTextureLocation(entity):legacy.getTextureLocation(entity);
    }

    @Override
    public boolean shouldRender(EventActorEntity entity,Frustum frustum,double x,double y,double z) {
        return useNative(entity) ? super.shouldRender(entity,frustum,x,y,z) : legacy.shouldRender(entity,frustum,x,y,z);
    }

    @Override
    public void render(EventActorEntity entity,float yaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light) {
        if(useNative(entity))nativeRenderers.get(NativeNpcAssets.renderId(entity.getNpcId())).render(entity,yaw,partialTick,stack,buffers,light);
        else legacy.render(entity,yaw,partialTick,stack,buffers,light);
    }
}
