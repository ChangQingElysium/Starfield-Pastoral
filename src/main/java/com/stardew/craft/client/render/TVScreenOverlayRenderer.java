package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.tv.TVChannelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;

/**
 * Renders animated channel sprites on the TV screen in the 3D world.
 * Mirrors original TV.cs TemporaryAnimatedSprite rendering:
 * - Each channel has an opening sprite and (for weather/fortune) a content sprite
 * - Weather and fortune channels have animated overlays on top
 * - Sprites are pre-sliced from Stardew UI atlases into standalone TV textures
 */
@SuppressWarnings("null")
public class TVScreenOverlayRenderer {

    // ==================== Active TV State ====================

    private static BlockPos activePos;
    private static int activeChannel;
    private static boolean activeContentPhase; // false=opening, true=content
    private static boolean isBigTV;
    private static long channelStartTime;

    // Weather/fortune overlay data (from payload)
    private static String weatherId;
    private static double dailyLuck;

    // ==================== Public API (called by TVScreen) ====================

    public static void setActiveTV(BlockPos pos, boolean bigTV) {
        activePos = pos;
        isBigTV = bigTV;
        activeChannel = 0;
        activeContentPhase = false;
    }

    public static void setChannel(int channel, String weather, double luck) {
        activeChannel = channel;
        activeContentPhase = false;
        weatherId = weather;
        dailyLuck = luck;
        channelStartTime = System.currentTimeMillis();
    }

    public static void setContentPhase() {
        activeContentPhase = true;
        channelStartTime = System.currentTimeMillis();
    }

    public static void clearActiveTV() {
        activePos = null;
        activeChannel = 0;
        activeContentPhase = false;
    }

    // ==================== Screen Area Definitions ====================

    // tv_1 (floor TV): screen from (1,1,1) to (15,12,1) in model coords
    private static final float TV1_X0 = 1f / 16f, TV1_X1 = 15f / 16f;
    private static final float TV1_Y0 = 1f / 16f, TV1_Y1 = 12f / 16f;
    private static final float TV1_Z = 1f / 16f - 0.001f; // north face of element at z=1

    // tv_2 (plasma TV): screen from (-15,4,12) to (15,20,12) in model coords
    private static final float TV2_X0 = -15f / 16f, TV2_X1 = 15f / 16f;
    private static final float TV2_Y0 = 4f / 16f, TV2_Y1 = 20f / 16f;
    private static final float TV2_Z = 12f / 16f - 0.001f;

    // Extra offset for overlay on top of screen sprite
    private static final float OVERLAY_Z_OFFSET = -0.0005f;

    // ==================== Rendering ====================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (activePos == null || activeChannel == 0) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        // Validate block still exists
        BlockState state = level.getBlockState(activePos);
        if (!(state.getBlock() instanceof com.stardew.craft.block.tv.TVBlock)) {
            clearActiveTV();
            return;
        }

        Direction facing = state.getValue(MapDecorStaticBlock.FACING);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        poseStack.pushPose();
        poseStack.translate(
                activePos.getX() - cam.x,
                activePos.getY() - cam.y,
                activePos.getZ() - cam.z
        );

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int packedLight = LevelRenderer.getLightColor(level, activePos.above());

        long now = System.currentTimeMillis();
        long elapsed = now - channelStartTime;

        // Select screen area based on TV type
        float x0, y0, x1, y1, z;
        if (isBigTV) {
            x0 = TV2_X0; y0 = TV2_Y0; x1 = TV2_X1; y1 = TV2_Y1; z = TV2_Z;
        } else {
            x0 = TV1_X0; y0 = TV1_Y0; x1 = TV1_X1; y1 = TV1_Y1; z = TV1_Z;
        }

        // Draw main screen sprite
        SpriteData screenSprite = getScreenSprite();
        if (screenSprite != null) {
            int frame = (int) ((elapsed / screenSprite.frameTimeMs) % screenSprite.frameCount);
            RenderType rt = RenderType.entityTranslucent(screenSprite.atlas);
            VertexConsumer vc = buffers.getBuffer(rt);
            drawScreenQuad(poseStack, vc, packedLight, facing, x0, y0, x1, y1, z, screenSprite, frame);
            buffers.endBatch(rt);
        }

        // Draw overlay sprite (weather or fortune) — only in content phase
        SpriteData overlaySprite = getOverlaySprite();
        if (overlaySprite != null && activeContentPhase) {
            int oFrame = (int) ((elapsed / overlaySprite.frameTimeMs) % overlaySprite.frameCount);
            RenderType ort = RenderType.entityTranslucent(overlaySprite.atlas);
            VertexConsumer ovc = buffers.getBuffer(ort);

            float screenW = x1 - x0;
            float screenH = y1 - y0;

            // Overlay position within screen area (fractional, from original TV.cs)
            // Weather: screenPos + (3,3)*sizeModifier → (3/42, 3/28) of sprite space
            // Fortune: screenPos + (15,1)*sizeModifier → (15/42, 1/28) of sprite space
            float offX, offY;
            if (activeChannel == TVChannelData.CHANNEL_FORTUNE) {
                offX = 15f / 42f;
                offY = 1f / 28f;
            } else {
                offX = 3f / 42f;
                offY = 3f / 28f;
            }
            float overlayW = (13f / 42f) * screenW;
            float overlayH = (13f / 28f) * screenH;

            // Y offset is from the TOP of the screen (in Stardew, Y increases downward)
            float oy1Top = y1 - offY * screenH;
            float oy0Bot = oy1Top - overlayH;
            float ox0 = x0 + offX * screenW;

            drawScreenQuad(poseStack, ovc, packedLight, facing,
                    ox0, oy0Bot, ox0 + overlayW, oy1Top, z + OVERLAY_Z_OFFSET,
                    overlaySprite, oFrame);
            buffers.endBatch(ort);
        }

        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ==================== Sprite Selection ====================

    @Nullable
    private static SpriteData getScreenSprite() {
        return switch (activeChannel) {
            case TVChannelData.CHANNEL_WEATHER -> activeContentPhase
                    ? sprite("weather_content", 42, 28, 1, 9999)
                    : sprite("weather_intro", 42, 28, 2, 150);
            case TVChannelData.CHANNEL_FORTUNE -> {
                if (activeContentPhase) {
                    if (dailyLuck >= 0.1)
                        yield sprite("fortune_good_content", 42, 28, 1, 9999);
                    else if (dailyLuck <= -0.1)
                        yield sprite("fortune_bad_content", 42, 28, 1, 9999);
                    else
                        yield sprite("fortune_neutral_content", 42, 28, 1, 9999);
                } else {
                    yield sprite("fortune_intro", 42, 28, 2, 150);
                }
            }
            case TVChannelData.CHANNEL_TIPS ->
                    sprite("tips", 42, 28, 2, 150);
            case TVChannelData.CHANNEL_COOKING ->
                    sprite("cooking", 42, 28, 2, 150);
            case TVChannelData.CHANNEL_FISHING ->
                    sprite("fishing", 42, 28, 2, 150);
            default -> null;
        };
    }

    @Nullable
    private static SpriteData getOverlaySprite() {
        if (activeChannel == TVChannelData.CHANNEL_WEATHER) {
            return getWeatherOverlay();
        } else if (activeChannel == TVChannelData.CHANNEL_FORTUNE) {
            return getFortuneOverlay();
        }
        return null;
    }

    @Nullable
    private static SpriteData getWeatherOverlay() {
        if (weatherId == null) return null;
        return switch (weatherId) {
            case "Snow"       -> sprite("weather_snow", 13, 13, 4, 100);
            case "Rain"       -> sprite("weather_rain", 13, 13, 4, 70);
            case "Storm"      -> sprite("weather_storm", 13, 13, 4, 120);
            case "WindSpring" -> sprite("weather_wind_spring", 13, 13, 4, 70);
            case "WindFall"   -> sprite("weather_wind_fall", 13, 13, 4, 70);
            case "Festival"   -> sprite("weather_festival", 13, 13, 4, 120);
            default           -> sprite("weather_sun", 13, 13, 4, 100);
        };
    }

    @Nullable
    private static SpriteData getFortuneOverlay() {
        if (dailyLuck < -0.07) {
            return sprite("fortune_very_bad", 13, 13, 4, 100);
        } else if (dailyLuck < -0.02) {
            return sprite("fortune_bad", 13, 13, 4, 100);
        } else if (dailyLuck > 0.07) {
            return sprite("fortune_very_good", 13, 13, 4, 100);
        } else if (dailyLuck > 0.02) {
            return sprite("fortune_good", 13, 13, 4, 100);
        } else {
            return sprite("fortune_neutral", 13, 13, 4, 100);
        }
    }

    private static SpriteData sprite(String name, int frameWidth, int frameHeight, int frameCount, int frameTimeMs) {
        return new SpriteData(tv(name), frameWidth * frameCount, frameHeight, 0, 0, frameWidth, frameHeight, frameCount, frameTimeMs);
    }

    private static ResourceLocation tv(String name) {
        return new ResourceLocation(StardewCraft.MODID, "textures/gui/tv/" + name + ".png");
    }

    // ==================== Quad Drawing ====================

    /**
     * Draws a screen quad at the correct world position for the given facing direction.
     * Screen coordinates (scrX0..scrY1, scrZ) are defined in the model's default NORTH orientation.
     * The method transforms them to match the actual facing without UV mirroring.
     *
     * Facing transforms (blockstate y-rotation applied to screen coords):
     *   NORTH:  (x, y, z)  → screen on -Z face
     *   SOUTH:  (1-x, y, 1-z) → screen on +Z face
     *   EAST:   (1-z, y, x)  → screen on +X face
     *   WEST:   (z, y, 1-x)  → screen on -X face
     */
    private static void drawScreenQuad(PoseStack poseStack, VertexConsumer vc, int packedLight,
                                        Direction facing,
                                        float scrX0, float scrY0, float scrX1, float scrY1, float scrZ,
                                        SpriteData sprite, int frame) {
        float u0 = (float) (sprite.u + frame * sprite.frameWidth) / sprite.atlasWidth;
        float u1 = (float) (sprite.u + (frame + 1) * sprite.frameWidth) / sprite.atlasWidth;
        float v0 = (float) sprite.v / sprite.atlasHeight;
        float v1 = (float) (sprite.v + sprite.frameHeight) / sprite.atlasHeight;

        int lightU = packedLight & 0xFFFF;
        int lightV = (packedLight >> 16) & 0xFFFF;
        PoseStack.Pose last = poseStack.last();

        // Compute 4 vertex positions (BL, BR, TR, TL) and normal for each facing.
        // UV is always u0→u1 left-to-right from the viewer's perspective (non-mirrored).
        float bx0, by0, bz0; // BL
        float bx1, by1, bz1; // BR
        float bx2, by2, bz2; // TR
        float bx3, by3, bz3; // TL
        float nx, ny, nz;

        switch (facing) {
            case SOUTH: // +Z face, viewer at +Z looking toward -Z, RIGHT = +X
                bx0 = 1 - scrX1; by0 = scrY0; bz0 = 1 - scrZ;
                bx1 = 1 - scrX0; by1 = scrY0; bz1 = 1 - scrZ;
                bx2 = 1 - scrX0; by2 = scrY1; bz2 = 1 - scrZ;
                bx3 = 1 - scrX1; by3 = scrY1; bz3 = 1 - scrZ;
                nx = 0; ny = 0; nz = 1;
                break;
            case EAST: // +X face, viewer at +X looking toward -X, RIGHT = +Z
                bx0 = 1 - scrZ; by0 = scrY0; bz0 = scrX0;
                bx1 = 1 - scrZ; by1 = scrY0; bz1 = scrX1;
                bx2 = 1 - scrZ; by2 = scrY1; bz2 = scrX1;
                bx3 = 1 - scrZ; by3 = scrY1; bz3 = scrX0;
                nx = 1; ny = 0; nz = 0;
                break;
            case WEST: // -X face, viewer at -X looking toward +X, RIGHT = -Z
                bx0 = scrZ; by0 = scrY0; bz0 = 1 - scrX0;
                bx1 = scrZ; by1 = scrY0; bz1 = 1 - scrX1;
                bx2 = scrZ; by2 = scrY1; bz2 = 1 - scrX1;
                bx3 = scrZ; by3 = scrY1; bz3 = 1 - scrX0;
                nx = -1; ny = 0; nz = 0;
                break;
            default: // NORTH: -Z face, viewer at -Z looking toward +Z, RIGHT = +X
                bx0 = scrX0; by0 = scrY0; bz0 = scrZ;
                bx1 = scrX1; by1 = scrY0; bz1 = scrZ;
                bx2 = scrX1; by2 = scrY1; bz2 = scrZ;
                bx3 = scrX0; by3 = scrY1; bz3 = scrZ;
                nx = 0; ny = 0; nz = -1;
                break;
        }

        vc.addVertex(last, bx0, by0, bz0).setUv(u0, v1).setColor(255, 255, 255, 255)
                .setUv1(0, 10).setUv2(lightU, lightV).setNormal(nx, ny, nz);
        vc.addVertex(last, bx1, by1, bz1).setUv(u1, v1).setColor(255, 255, 255, 255)
                .setUv1(0, 10).setUv2(lightU, lightV).setNormal(nx, ny, nz);
        vc.addVertex(last, bx2, by2, bz2).setUv(u1, v0).setColor(255, 255, 255, 255)
                .setUv1(0, 10).setUv2(lightU, lightV).setNormal(nx, ny, nz);
        vc.addVertex(last, bx3, by3, bz3).setUv(u0, v0).setColor(255, 255, 255, 255)
                .setUv1(0, 10).setUv2(lightU, lightV).setNormal(nx, ny, nz);
    }

    // ==================== Sprite Data ====================

    private record SpriteData(
            ResourceLocation atlas, int atlasWidth, int atlasHeight,
            int u, int v, int frameWidth, int frameHeight,
            int frameCount, int frameTimeMs
    ) {}
}
