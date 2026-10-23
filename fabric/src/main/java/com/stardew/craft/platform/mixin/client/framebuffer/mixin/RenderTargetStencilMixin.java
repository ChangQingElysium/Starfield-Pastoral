package com.stardew.craft.platform.mixin.client.framebuffer.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.stardew.craft.platform.fabric.client.framebuffer.StencilTarget;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderTarget.class)
public abstract class RenderTargetStencilMixin implements StencilTarget {
    @Shadow public int viewWidth;
    @Shadow public int viewHeight;
    @Shadow public abstract void resize(int width, int height, boolean clearError);
    @Unique private boolean stardewcraft$stencilEnabled;

    @Override public void stardewcraft$enableStencil() {
        if (stardewcraft$stencilEnabled) return;
        stardewcraft$stencilEnabled = true;
        resize(viewWidth, viewHeight, Minecraft.ON_OSX);
    }
    @Override public boolean stardewcraft$isStencilEnabled() { return stardewcraft$stencilEnabled; }

    @WrapOperation(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", ordinal = 0))
    private void stardewcraft$depthStorage(int target, int level, int internalFormat, int width, int height, int border,
            int format, int type, IntBuffer pixels, Operation<Void> original) {
        if (stardewcraft$stencilEnabled) {
            original.call(target, level, GL30.GL_DEPTH32F_STENCIL8, width, height, border, GL30.GL_DEPTH_STENCIL,
                    GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, pixels);
        } else original.call(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @WrapOperation(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V", ordinal = 1))
    private void stardewcraft$depthAndStencilAttachments(int target, int attachment, int textureTarget, int texture, int level, Operation<Void> original) {
        original.call(target, attachment, textureTarget, texture, level);
        // Neo's default useCombinedDepthStencilAttachment=false: the same packed image backs both attachments.
        if (stardewcraft$stencilEnabled) original.call(target, GL30.GL_STENCIL_ATTACHMENT, textureTarget, texture, level);
    }
}
