package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.model.nativebb.BlockbenchFrame;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class JunimoTintLayer {
    private static final ResourceLocation TEXTURE=new ResourceLocation("stardewcraft","textures/entity/junimo/junimo_tint.png");
    private JunimoTintLayer() {}
    public static void render(JunimoEntity entity,BlockbenchFrame frame,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        float[] rgb=entity.getColorComponents();
        int color=(Math.round(entity.getAlpha()*255)<<24)|((int)(rgb[0]*255)<<16)|((int)(rgb[1]*255)<<8)|(int)(rgb[2]*255);
        frame.render(pose,buffers,new BlockbenchFrame.Material() {
            @Override public RenderType type(int bone) {return RenderType.entityTranslucent(TEXTURE);}
            @Override public int light(int bone) {return light;}
        },overlay,color);
    }
}
