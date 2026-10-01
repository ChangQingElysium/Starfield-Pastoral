package com.stardew.craft.client.hud;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * SDV Tracker profession (ID 17) parity.
 *
 * <p>Draws small arrows at screen edges pointing toward off-screen forageable items,
 * replicating the behavior from Game1.cs where the game iterates currentLocation.objects
 * and draws directional arrows for spawned objects not visible on screen.
 *
 * <p>SDV arrow sprite: cursors.png (412, 495, 5, 4), rendered at 4x scale.
 * SDV only activates outdoors; we scan nearby ForageBlock instances within render distance.
 */
@SuppressWarnings("null")
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class TrackerProfessionHud {

    /** Search radius in blocks around the player */
    private static final int SCAN_RADIUS = 48;

    /** Minimum distance (screen px) from edge for arrow placement */
    private static final int EDGE_MARGIN = 8;

    /** Rescan interval in milliseconds */
    private static final long RESCAN_INTERVAL_MS = 500;

    /** Cached forage positions */
    private static List<BlockPos> cachedForagePositions = new ArrayList<>();
    private static long lastScanTime = 0;
    private static BlockPos lastScanPos = BlockPos.ZERO;

    private TrackerProfessionHud() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        // Check Tracker profession
        if (!ClientPlayerDataCache.hasProfession(ProfessionType.TRACKER)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.player.isSpectator()) return;

        // SDV: only works outdoors. In MC we skip enclosed dimensions (like mines).
        // We allow it in the overworld and custom stardew dimensions but not the nether/end.
        // Simple heuristic: check if sky is visible (not underground)
        if (!mc.level.dimensionType().hasSkyLight()) return;

        Player player = mc.player;
        Level level = mc.level;
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        BlockPos playerPos = player.blockPosition();

        // Periodic rescan for ForageBlocks (every 500ms or if player moved > 8 blocks)
        long now = System.currentTimeMillis();
        if (now - lastScanTime > RESCAN_INTERVAL_MS
            || playerPos.distManhattan(lastScanPos) > 8) {
            rescanForagePositions(level, playerPos);
            lastScanTime = now;
            lastScanPos = playerPos;
        }

        // Draw arrows for cached positions
        for (BlockPos pos : cachedForagePositions) {
            Vec3 blockCenter = Vec3.atCenterOf(pos);
            Vec3 screenPos = worldToScreen(mc, blockCenter);
            if (screenPos == null) continue; // behind camera

            float sx = (float) screenPos.x;
            float sy = (float) screenPos.y;

            // If on screen, don't draw arrow
            if (sx >= 0 && sx < screenW && sy >= 0 && sy < screenH) continue;

            drawTrackerArrow(graphics, sx, sy, screenW, screenH);
        }
    }

    /**
     * Scan for ForageBlock instances around the player.
     * Uses chunk section palette to skip sections that can't contain ForageBlock,
     * reducing from ~100K getBlockState calls to only checking relevant sections.
     */
    private static void rescanForagePositions(Level level, BlockPos playerPos) {
        cachedForagePositions.clear();

        int minCX = SectionPos.blockToSectionCoord(playerPos.getX() - SCAN_RADIUS);
        int maxCX = SectionPos.blockToSectionCoord(playerPos.getX() + SCAN_RADIUS);
        int minCZ = SectionPos.blockToSectionCoord(playerPos.getZ() - SCAN_RADIUS);
        int maxCZ = SectionPos.blockToSectionCoord(playerPos.getZ() + SCAN_RADIUS);
        int minY = playerPos.getY() - 5;
        int maxY = playerPos.getY() + 5;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);

                // Check each section that overlaps our Y range
                int minSY = SectionPos.blockToSectionCoord(minY);
                int maxSY = SectionPos.blockToSectionCoord(maxY);

                for (int sy = minSY; sy <= maxSY; sy++) {
                    int sectionIdx = chunk.getSectionIndexFromSectionY(sy);
                    if (sectionIdx < 0 || sectionIdx >= chunk.getSectionsCount()) continue;
                    LevelChunkSection section = chunk.getSection(sectionIdx);
                    if (section.hasOnlyAir()) continue;

                    // Check palette: if no ForageBlock in this section, skip entirely
                    boolean hasForage = section.getStates().maybeHas(
                        state -> state.getBlock() instanceof ForageBlock);
                    if (!hasForage) continue;

                    // This section contains at least one ForageBlock — scan it
                    int secBaseX = SectionPos.sectionToBlockCoord(cx);
                    int secBaseY = SectionPos.sectionToBlockCoord(sy);
                    int secBaseZ = SectionPos.sectionToBlockCoord(cz);
                    int yStart = Math.max(0, minY - secBaseY);
                    int yEnd = Math.min(15, maxY - secBaseY);

                    for (int dx = 0; dx < 16; dx++) {
                        int worldX = secBaseX + dx;
                        if (worldX < playerPos.getX() - SCAN_RADIUS || worldX > playerPos.getX() + SCAN_RADIUS)
                            continue;
                        for (int dz = 0; dz < 16; dz++) {
                            int worldZ = secBaseZ + dz;
                            if (worldZ < playerPos.getZ() - SCAN_RADIUS || worldZ > playerPos.getZ() + SCAN_RADIUS)
                                continue;
                            for (int dy = yStart; dy <= yEnd; dy++) {
                                BlockState state = section.getBlockState(dx, dy, dz);
                                if (state.getBlock() instanceof ForageBlock) {
                                    cachedForagePositions.add(new BlockPos(worldX, secBaseY + dy, worldZ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Project a world position to GUI screen coordinates.
     * Returns null if the point is behind the camera.
     */
    private static Vec3 worldToScreen(Minecraft mc, Vec3 worldPos) {
        // Get camera-relative position
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 relative = worldPos.subtract(cameraPos);

        // Transform to camera space using the camera's rotation quaternion
        org.joml.Quaternionf rot = mc.gameRenderer.getMainCamera().rotation();
        // Camera looks along -Z in camera space, so we need inverse rotation
        org.joml.Vector3f camSpace = new org.joml.Vector3f(
            (float) relative.x, (float) relative.y, (float) relative.z);
        // Conjugate = inverse for unit quaternion
        org.joml.Quaternionf invRot = new org.joml.Quaternionf(rot).conjugate();
        invRot.transform(camSpace);

        // In camera space, -Z is forward. If Z > 0, it's behind the camera.
        if (camSpace.z > 0) return null;

        // Simple perspective projection
        float fov = 70.0f; // default MC FOV, good enough for edge detection
        float aspect = (float) mc.getWindow().getGuiScaledWidth() / mc.getWindow().getGuiScaledHeight();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov / 2.0));

        float ndcX = camSpace.x / (-camSpace.z * tanHalfFov * aspect);
        float ndcY = camSpace.y / (-camSpace.z * tanHalfFov);

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        float screenX = (ndcX + 1.0f) / 2.0f * guiW;
        float screenY = (1.0f - ndcY) / 2.0f * guiH;

        return new Vec3(screenX, screenY, 0);
    }

    /**
     * Draw a directional arrow at the screen edge, pointing toward an off-screen target.
     * Replicates SDV Game1.cs tracker arrow rendering logic.
     */
    private static void drawTrackerArrow(GuiGraphics graphics, float targetX, float targetY,
                                          int screenW, int screenH) {
        // Clamp to screen edges with margin
        float arrowX = Math.max(EDGE_MARGIN, Math.min(targetX, screenW - EDGE_MARGIN));
        float arrowY = Math.max(EDGE_MARGIN, Math.min(targetY, screenH - EDGE_MARGIN));

        // Calculate rotation based on direction (SDV style)
        // SDV: up=0, right=π/2, down=π, left=-π/2
        float rotation = 0;

        if (targetX < 0) {
            // Target is to the left
            rotation = -(float) Math.PI / 2f;
            arrowX = EDGE_MARGIN;
        } else if (targetX >= screenW) {
            // Target is to the right
            rotation = (float) Math.PI / 2f;
            arrowX = screenW - EDGE_MARGIN;
        }

        if (targetY < 0) {
            // Target is above
            rotation = 0;
            arrowY = EDGE_MARGIN;
            // SDV corner adjustments
            if (targetX < 0) rotation -= (float) Math.PI / 4f;
            else if (targetX >= screenW) rotation += (float) Math.PI / 4f;
        } else if (targetY >= screenH) {
            // Target is below
            rotation = (float) Math.PI;
            arrowY = screenH - EDGE_MARGIN;
            if (targetX < 0) rotation += (float) Math.PI / 4f;
            else if (targetX >= screenW) rotation -= (float) Math.PI / 4f;
        }

        // Draw the arrow sprite from cursors.png with rotation
        graphics.pose().pushPose();
        graphics.pose().translate(arrowX, arrowY, 500);  // high z to draw over everything
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(rotation));
        float scale = 4.0f / (float) Minecraft.getInstance().getWindow().getGuiScale();
        graphics.pose().scale(scale * 4f, scale * 4f, 1.0f);
        // SDV origin is (2, 2) for a 5x4 sprite — center-ish
        CommonGuiTextures.drawQuestObjectiveArrowAtCurrentPose(graphics, -2, -2);
        graphics.pose().popPose();
    }
}
