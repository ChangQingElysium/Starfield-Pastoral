package com.stardew.craft.client.interior;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import java.io.IOException;

import com.stardew.craft.port.PortVertex;
/** Samples the already resolved child image using screen coordinates, with the aperture's real depth. */
final class TownDoorComposite {
    private static ShaderInstance shader;

    private TownDoorComposite() {}

    static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Runnable release = () -> {
                if (shader != null) shader.close();
                shader = null;
                TownDoorClient.shaderReload();
            };
            if (RenderSystem.isOnRenderThread()) release.run();
            else RenderSystem.recordRenderCall(release::run);
        });
    }

    private static void loadShader() {
        if (shader != null) return;
        var provider = Minecraft.getInstance().getResourceManager();
        var shaderId = new ResourceLocation("stardewcraft", "town_door_composite");
        var format = DefaultVertexFormat.POSITION;
        try {
            shader = new ShaderInstance(provider, shaderId, format);
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Could not load doorway composite shader", error);
        }
    }

    static void draw(RenderTarget child, int opaqueDepth, Vec3 center, double width, Matrix4f view, Matrix4f projection) {
        loadShader();
        TownDoorClipping.disable();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        // Foreground transparent terrain is already shaded in the child. Test the aperture
        // against the outer opaque depth in the shader, not against water/glass in depthtex0.
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glEnable(GL32.GL_DEPTH_CLAMP);
        try {
            shader.setSampler("Sampler0", child.getColorTextureId());
            shader.setSampler("Sampler1", opaqueDepth);
            shader.setSampler("Sampler2", child.getDepthTextureId());
            shader.MODEL_VIEW_MATRIX.set(view);
            shader.PROJECTION_MATRIX.set(projection);
            shader.safeGetUniform("ScreenSize").set((float) child.viewWidth, (float) child.viewHeight);
            shader.apply();
            try {
                double half = width * .5;
                BufferBuilder buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
                vertex(buffer, center, -half, 1); vertex(buffer, center, -half, -1); vertex(buffer, center, half, -1);
                vertex(buffer, center, half, -1); vertex(buffer, center, half, 1); vertex(buffer, center, -half, 1);
                BufferUploader.draw(buffer.end());
            } finally {
                shader.clear();
            }
        } finally {
            GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            RenderSystem.enableCull();
        }
    }

    private static void vertex(BufferBuilder buffer, Vec3 center, double x, double y) {
        PortVertex.of(buffer).addVertex((float) (center.x + x), (float) (center.y + y), (float) center.z).endVertex();
    }
}
