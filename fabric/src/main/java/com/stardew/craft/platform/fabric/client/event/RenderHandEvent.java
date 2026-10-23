package com.stardew.craft.platform.fabric.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class RenderHandEvent extends Event {
    private final InteractionHand hand;
    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final int light;
    private final float partialTick;
    private final float pitch;
    private final float swing;
    private final float equip;
    private final ItemStack stack;
    public RenderHandEvent(InteractionHand hand, PoseStack pose, MultiBufferSource buffers, int light,
                           float partialTick, float pitch, float swing, float equip, ItemStack stack) {
        super(true);
        this.hand = hand; this.pose = pose; this.buffers = buffers; this.light = light;
        this.partialTick = partialTick; this.pitch = pitch; this.swing = swing; this.equip = equip; this.stack = stack;
    }
    public InteractionHand getHand() { return hand; }
    public PoseStack getPoseStack() { return pose; }
    public MultiBufferSource getMultiBufferSource() { return buffers; }
    public int getPackedLight() { return light; }
    public float getPartialTick() { return partialTick; }
    public float getInterpolatedPitch() { return pitch; }
    public float getSwingProgress() { return swing; }
    public float getEquipProgress() { return equip; }
    public ItemStack getItemStack() { return stack; }
}
