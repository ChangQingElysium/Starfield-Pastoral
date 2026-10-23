package com.stardew.craft.platform.fabric.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** Neo's living render event extends Event, independently of RenderPlayerEvent and LivingEvent. */
@Environment(EnvType.CLIENT)
public abstract class RenderLivingEvent<T extends LivingEntity, M extends EntityModel<T>> extends Event {
    private final LivingEntity entity;
    private final LivingEntityRenderer<T, M> renderer;
    private final float partialTick;
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final int light;
    protected RenderLivingEvent(LivingEntity entity, LivingEntityRenderer<T, M> renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, boolean cancelable) {
        super(cancelable); this.entity = entity; this.renderer = renderer; this.partialTick = partialTick; this.pose = pose; this.buffers = buffers; this.light = light;
    }
    public LivingEntity getEntity() { return entity; }
    public LivingEntityRenderer<T, M> getRenderer() { return renderer; }
    public float getPartialTick() { return partialTick; }
    public PoseStack getPoseStack() { return pose; }
    public MultiBufferSource getMultiBufferSource() { return buffers; }
    public int getPackedLight() { return light; }
    public static final class Pre<T extends LivingEntity, M extends EntityModel<T>> extends RenderLivingEvent<T, M> {
        public Pre(LivingEntity entity, LivingEntityRenderer<T, M> renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) { super(entity, renderer, partialTick, pose, buffers, light, true); }
    }
    public static final class Post<T extends LivingEntity, M extends EntityModel<T>> extends RenderLivingEvent<T, M> {
        public Post(LivingEntity entity, LivingEntityRenderer<T, M> renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) { super(entity, renderer, partialTick, pose, buffers, light, false); }
    }
}
