package com.stardew.craft.client.render;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.quest.network.ClientQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * SDV parity Town.cs:1086-1090 — 公告栏头顶的 "!" 提示：
 * <ul>
 *   <li>本地玩家有未接的每日任务 → 浮起一个黄色感叹号</li>
 *   <li>正弦上下漂浮 + 顶点时轻微放大（节奏同原版 sin(ms/250) + 脉动）</li>
 *   <li>纯客户端实现，多人下每个玩家看到的是自己 ClientQuestData 的状态</li>
 * </ul>
 *
 * 另外也处理 "首次未查看公告栏" 的 "?"（SDV Town.cs:1080-1085）：
 * 用 ccBulletin（公告栏献祭）尚未完成但已解锁 public 区域作为对标条件。
 * 这里简化为：本地玩家没有 mailFlag "checkedBulletinOnce" 时显示（等于 SDV playerCheckedBoard）。
 */
@SuppressWarnings("unused")
public final class BillboardQuestIndicatorRenderer {

    /** 最远渲染距离（世界格）。 */
    private static final double RENDER_RANGE = 16.0;
    private static final double RENDER_RANGE_SQ = RENDER_RANGE * RENDER_RANGE;

    /** "!" 的黄色 — 接近 SDV mouseCursors 上的那一抹橙黄。 */
    private static final int EXCLAM_R = 0xFF;
    private static final int EXCLAM_G = 0xE6;
    private static final int EXCLAM_B = 0x55;

    /** "?" 的灰白（首次提示）。 */
    private static final int QUESTION_R = 0xE0;
    private static final int QUESTION_G = 0xE0;
    private static final int QUESTION_B = 0xE0;

    private BillboardQuestIndicatorRenderer() {}

    // ======================== 主入口 ========================

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // 判断是否需要画 "!"（有未接每日任务）和 "?"（首次未查看）
        boolean showExclam = ClientQuestData.hasUnclaimedDailyQuest();
        boolean showQuestion = !ClientPlayerDataCache.hasMailFlag("checkedBulletinOnce");

        if (!showExclam && !showQuestion) return;

        // 扫描附近 BULLETIN_BOARD 方块并按 cluster 合并（公告栏是 2 格拼成的，
        // 不 cluster 会浮两个符号；用 cluster center 做为单个指示器的位置）
        List<Vec3> billboardCenters = findNearbyBillboardCenters(player);
        if (billboardCenters.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        long nowMs = System.currentTimeMillis();
        // 正弦漂浮：4 像素 * sin(ms/250) / 16 = ~0.25 格，周期 ~1.57s（SDV 同节奏）
        float yOffset = 0.25F * (float) Math.sin(nowMs / 250.0);
        // 顶点时多大一点（SDV: scale = 4 + max(0, 0.25 - yOffset/16)）
        float scalePulse = 1.0F + Math.max(0.0F, (0.25F - yOffset) * 0.25F);

        for (Vec3 center : billboardCenters) {
            // 气泡位置：cluster 中心顶上 + 漂浮偏移
            double bx = center.x;
            double by = center.y + 1.3 + yOffset;
            double bz = center.z;

            ps.pushPose();
            ps.translate(bx - cam.x, by - cam.y, bz - cam.z);
            // Billboard：始终朝摄像机
            ps.mulPose(event.getCamera().rotation());
            // 缩放到世界大小（和 DamageNumberClient 同手法：scale 负 Y 翻转字形）
            float baseScale = 0.025F * scalePulse;
            ps.scale(baseScale, -baseScale, baseScale);

            // "!" 优先于 "?"。 SDV 是两者都画（"?" 在下方，"!" 在右上），但
            // 实际场景里"从没看过公告栏"几乎立刻就会被点一次，平时只会看到 "!"。
            // 我们先只画优先级最高的那一个。
            if (showExclam) {
                drawGlyph(StardewFonts.spriteTextColored(), ps, buf, "!", EXCLAM_R, EXCLAM_G, EXCLAM_B);
            } else {
                drawGlyph(StardewFonts.spriteTextColored(), ps, buf, "?", QUESTION_R, QUESTION_G, QUESTION_B);
            }

            ps.popPose();
        }

        buf.endBatch();
    }

    // ======================== Helpers ========================

    /**
     * 扫描附近所有 BULLETIN_BOARD 方块，把相邻的（同 Y + X 差 1 或 Z 差 1）归到一簇，
     * 为每个簇返回一个中心点（方块中心的几何平均）。这样 2 格宽的公告栏只会出现一个指示器。
     */
    @SuppressWarnings("null")
    private static List<Vec3> findNearbyBillboardCenters(Player player) {
        Level level = player.level();
        BlockPos pcenter = player.blockPosition();
        int r = (int) RENDER_RANGE;

        // 先收集所有方块
        List<BlockPos> blocks = new ArrayList<>();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = pcenter.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(ModBlocks.BULLETIN_BOARD.get())) continue;
                    blocks.add(pos.immutable());
                }
            }
        }
        if (blocks.isEmpty()) return java.util.Collections.emptyList();

        // Union-Find: 按"同 Y + 6-邻接（水平相邻）" 合并相邻方块。
        int n = blocks.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                BlockPos a = blocks.get(i);
                BlockPos b = blocks.get(j);
                if (a.getY() != b.getY()) continue;
                int dx = a.getX() - b.getX();
                int dz = a.getZ() - b.getZ();
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    union(parent, i, j);
                }
            }
        }

        // 按 root 归类 → 算每簇的几何中心
        java.util.Map<Integer, double[]> clusters = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            BlockPos bp = blocks.get(i);
            double[] acc = clusters.computeIfAbsent(root, k -> new double[]{0, 0, 0, 0}); // sumX, sumY, sumZ, count
            acc[0] += bp.getX() + 0.5;
            acc[1] += bp.getY();
            acc[2] += bp.getZ() + 0.5;
            acc[3] += 1;
        }

        List<Vec3> result = new ArrayList<>();
        for (double[] acc : clusters.values()) {
            double cx = acc[0] / acc[3];
            double cy = acc[1] / acc[3];
            double cz = acc[2] / acc[3];
            // 距离裁剪（以玩家视角）
            double ex = cx - player.getX();
            double ey = cy + 1.0 - player.getY();
            double ez = cz - player.getZ();
            if (ex * ex + ey * ey + ez * ez > RENDER_RANGE_SQ) continue;
            result.add(new Vec3(cx, cy, cz));
        }
        return result;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /**
     * 以 font 双通道（黑描边 + 彩色填充）画一个字符，画布中心为原点。
     */
    @SuppressWarnings("null")
    private static void drawGlyph(Font font, PoseStack ps, MultiBufferSource buf,
                                   String glyph, int r, int g, int b) {
        Component c = Component.literal(glyph);
        int width = font.width(c);
        // 居中
        float x = -width / 2.0F;
        float y = -font.lineHeight / 2.0F;

        int textColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
        // drawInBatch with drop-shadow=true 会自动画描边
        font.drawInBatch(c, x, y, textColor, false,
                ps.last().pose(),
                buf,
                Font.DisplayMode.NORMAL,
                0, 0xF000F0);
    }
}
