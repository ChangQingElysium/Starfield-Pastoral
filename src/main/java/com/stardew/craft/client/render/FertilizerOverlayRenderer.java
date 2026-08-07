package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.client.ClientFertilizerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Renders fertilizer textures as depth-tested, non-depth-writing farmland decals. */
public final class FertilizerOverlayRenderer {
    private static final float FARMLAND_TOP = 15.0F / 16.0F;
    private static final float GARDEN_POT_SOIL_TOP = 11.0F / 16.0F;
    private static final float GARDEN_POT_SOIL_MIN = 1.5F / 16.0F;
    private static final float GARDEN_POT_SOIL_MAX = 14.5F / 16.0F;
    private static final float SURFACE_OFFSET = 1.0F / 256.0F;
    private static final int ALPHA = 220;
    private static final Map<FertilizerType, RenderType> RENDER_TYPES = createRenderTypes();

    private FertilizerOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        ChunkPos cameraChunk = new ChunkPos(BlockPos.containing(cameraPos));
        int chunkRadius = Math.max(2, minecraft.options.getEffectiveRenderDistance()) + 1;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        EnumSet<FertilizerType> usedTypes = EnumSet.noneOf(FertilizerType.class);

        poseStack.pushPose();
        ClientFertilizerCache.forEachInChunkRange(
                level.dimension(),
                cameraChunk,
                chunkRadius,
                (chunkX, chunkZ) -> isChunkVisible(event, level, chunkX, chunkZ),
                (pos, type) -> {
                    if (!level.hasChunkAt(pos)) {
                        return;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof FarmBlock)
                            || state.getBlock() instanceof GardenPotBlock) {
                        return;
                    }

                    usedTypes.add(type);
                    renderFertilizerOverlay(
                            poseStack,
                            buffers.getBuffer(RENDER_TYPES.get(type)),
                            level,
                            pos,
                            cameraPos);
                });
        poseStack.popPose();

        // Deterministic, targeted flushes avoid disturbing unrelated render buffers.
        for (FertilizerType type : FertilizerType.values()) {
            if (usedTypes.contains(type)) {
                buffers.endBatch(RENDER_TYPES.get(type));
            }
        }
    }

    /** Garden pots render as block entities, so their fertilizer decal must share that pass. */
    public static void renderGardenPotOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            FertilizerType type,
            int packedLight
    ) {
        if (type == null) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPES.get(type));
        PoseStack.Pose pose = poseStack.last();
        float min = GARDEN_POT_SOIL_MIN;
        float max = GARDEN_POT_SOIL_MAX;
        float y = GARDEN_POT_SOIL_TOP + SURFACE_OFFSET;

        addVertex(consumer, pose, min, y, min, 0.0F, 0.0F, packedLight);
        addVertex(consumer, pose, min, y, max, 0.0F, 1.0F, packedLight);
        addVertex(consumer, pose, max, y, max, 1.0F, 1.0F, packedLight);
        addVertex(consumer, pose, max, y, min, 1.0F, 0.0F, packedLight);
    }

    private static boolean isChunkVisible(
            RenderLevelStageEvent event,
            Level level,
            int chunkX,
            int chunkZ
    ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return event.getFrustum().isVisible(new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                minX + 16,
                level.getMaxBuildHeight(),
                minZ + 16));
    }

    private static void renderFertilizerOverlay(
            PoseStack poseStack,
            VertexConsumer consumer,
            Level level,
            BlockPos pos,
            Vec3 cameraPos
    ) {
        float x = (float) (pos.getX() - cameraPos.x);
        float y = (float) (pos.getY() + FARMLAND_TOP + SURFACE_OFFSET - cameraPos.y);
        float z = (float) (pos.getZ() - cameraPos.z);
        int packedLight = LevelRenderer.getLightColor(level, pos.above());
        PoseStack.Pose pose = poseStack.last();

        addVertex(consumer, pose, x, y, z, 0.0F, 0.0F, packedLight);
        addVertex(consumer, pose, x, y, z + 1.0F, 0.0F, 1.0F, packedLight);
        addVertex(consumer, pose, x + 1.0F, y, z + 1.0F, 1.0F, 1.0F, packedLight);
        addVertex(consumer, pose, x + 1.0F, y, z, 1.0F, 0.0F, packedLight);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static Map<FertilizerType, RenderType> createRenderTypes() {
        EnumMap<FertilizerType, RenderType> renderTypes = new EnumMap<>(FertilizerType.class);
        for (FertilizerType type : FertilizerType.values()) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "textures/block/fertilizer/" + type.getSerializedName() + ".png");
            renderTypes.put(type, createRenderType(type, texture));
        }
        return Map.copyOf(renderTypes);
    }

    private static RenderType createRenderType(
            FertilizerType type,
            ResourceLocation texture
    ) {
        return RenderType.create(
                "stardewcraft_fertilizer_" + type.getSerializedName(),
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderType.ShaderStateShard(
                                GameRenderer::getRendertypeEntityTranslucentShader))
                        .setTextureState(new RenderType.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(false));
    }
}
