package com.stardew.craft.platform.fabric.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public abstract class RenderPlayerEvent extends PlayerEvent {
    private final PlayerRenderer renderer;
    private final float partialTick;
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final int light;
    protected RenderPlayerEvent(Player player, PlayerRenderer renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, boolean cancelable) {
        super(player, cancelable); this.renderer = renderer; this.partialTick = partialTick; this.pose = pose; this.buffers = buffers; this.light = light;
    }
    public PlayerRenderer getRenderer() { return renderer; }
    public float getPartialTick() { return partialTick; }
    public PoseStack getPoseStack() { return pose; }
    public MultiBufferSource getMultiBufferSource() { return buffers; }
    public int getPackedLight() { return light; }
    public static final class Pre extends RenderPlayerEvent {
        public Pre(Player player, PlayerRenderer renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) { super(player, renderer, partialTick, pose, buffers, light, true); }
    }
    public static final class Post extends RenderPlayerEvent {
        public Post(Player player, PlayerRenderer renderer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) { super(player, renderer, partialTick, pose, buffers, light, false); }
    }
}
