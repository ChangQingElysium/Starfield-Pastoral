package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BlackHolePostEffectClient {

    private static final ResourceLocation SHADER_ID = new ResourceLocation(
        StardewCraft.MODID,
        "black_hole"
    );

    private static ShaderInstance shader;
    private static boolean shaderLoadFailed;

    private static final List<Effect> EFFECTS = new ArrayList<>();

    private BlackHolePostEffectClient() {}

    public static void add(float x, float y, float z, float radiusNorm, float strength, int durationTicks) {
        if (!Config.ENABLE_WEAPON_POST_EFFECTS.get()) {
            return;
        }
        if (durationTicks <= 0 || strength <= 0.0f) {
            return;
        }
        EFFECTS.add(new Effect(new Vec3(x, y, z), radiusNorm, strength, durationTicks));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (EFFECTS.isEmpty()) {
            return;
        }
        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect effect = it.next();
            effect.age++;
            if (effect.age > effect.durationTicks) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!Config.ENABLE_WEAPON_POST_EFFECTS.get()) {
            return;
        }
        if (EFFECTS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ShaderInstance shaderInstance = getShader(mc);
        if (shaderInstance == null) {
            return;
        }

        var effect = EFFECTS.get(EFFECTS.size() - 1);
        float t = effect.durationTicks <= 0 ? 1.0f : (effect.age / (float) effect.durationTicks);
        t = Math.max(0.0f, Math.min(1.0f, t));
        float strength = (float) (Math.sin(Math.PI * t) * effect.strength);
        if (strength <= 0.001f) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        Matrix4f view = event.getPoseStack().last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        Vector4f screen = worldToScreen(effect.pos, camPos, view, projection);
        if (screen == null) {
            return;
        }

        shaderInstance.safeGetUniform("Center").set(screen.x, screen.y);
        shaderInstance.safeGetUniform("Strength").set(strength);
        shaderInstance.safeGetUniform("Radius").set(effect.radiusNorm);
        shaderInstance.safeGetUniform("Time").set(mc.level.getGameTime() + event.getPartialTick());

        RenderSystem.setShader(() -> shaderInstance);
        RenderSystem.setShaderTexture(0, mc.getMainRenderTarget().getColorTextureId());
        RenderSystem.setShaderTexture(1, mc.getMainRenderTarget().getDepthTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 1.0f);
        buffer.addVertex(1.0f, -1.0f, 0.0f).setUv(1.0f, 1.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f).setUv(1.0f, 0.0f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f).setUv(0.0f, 0.0f);
        BufferUploader.drawWithShader(buffer.build());

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @SuppressWarnings("null")
    private static ShaderInstance getShader(Minecraft mc) {
        if (shader != null) {
            return shader;
        }
        if (shaderLoadFailed) {
            return null;
        }
        ResourceManager manager = mc.getResourceManager();
        try {
            ResourceProvider provider = manager;
            ResourceLocation shaderId = SHADER_ID;
            VertexFormat format = DefaultVertexFormat.POSITION_TEX;
            shader = new ShaderInstance(provider, shaderId, format);
        } catch (IOException e) {
            StardewCraft.LOGGER.error("Failed to load black hole shader", e);
            shaderLoadFailed = true;
            shader = null;
        }
        return shader;
    }

    private static Vector4f worldToScreen(Vec3 worldPos, Vec3 camPos, Matrix4f view, Matrix4f projection) {
        Vector4f pos = new Vector4f(
            (float) (worldPos.x - camPos.x),
            (float) (worldPos.y - camPos.y),
            (float) (worldPos.z - camPos.z),
            1.0f
        );
        pos.mul(view);
        pos.mul(projection);
        if (pos.w <= 0.0f) {
            return null;
        }
        float invW = 1.0f / pos.w;
        float ndcX = pos.x * invW;
        float ndcY = pos.y * invW;
        float u = ndcX * 0.5f + 0.5f;
        float v = 1.0f - (ndcY * 0.5f + 0.5f);
        return new Vector4f(u, v, 0.0f, 1.0f);
    }

    private static final class Effect {
        private final Vec3 pos;
        private final float radiusNorm;
        private final float strength;
        private final int durationTicks;
        private int age = 0;

        private Effect(Vec3 pos, float radiusNorm, float strength, int durationTicks) {
            this.pos = pos;
            this.radiusNorm = radiusNorm;
            this.strength = strength;
            this.durationTicks = durationTicks;
        }
    }
}
