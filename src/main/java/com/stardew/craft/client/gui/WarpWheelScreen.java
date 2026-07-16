package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.warp.WarpWandClientState;
import com.stardew.craft.network.payload.WarpWandTeleportPayload;
import com.stardew.craft.network.payload.WarpWandUnlockPayload;
import com.stardew.craft.warp.WarpDestination;
import com.stardew.craft.warp.WarpDestinations;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 传送轮盘 UI — 类似 Emote Wheel 的径向菜单，用于选择传送目的地。
 * <p>
 * 特性：
 * - 鼠标点击确认（而非松开按键）
 * - 扇区内显示目的地名称文字
 * - 中心区域显示详细预览（名称+描述 或 锁定图标+价格）
 * - 支持已解锁/未解锁两种视觉状态
 * - 扇区数量动态计算，不硬编码
 */
@SuppressWarnings("null")
public class WarpWheelScreen extends Screen implements StardewRealtimeScreen {

    // ── 布局常量 ──
    private static final int OPEN_ANIM_MS = 200;
    private static final double START_ANGLE = -Math.PI / 2.0;
    private static final int DEADZONE = 24;

    // ── 扇区渲染常量 ──
    private static final float SECTOR_INNER_RADIUS = 36.0f;
    private static final float SECTOR_OUTER_RADIUS = 90.0f;
    private static final int ARC_SEGMENTS = 24;
    private static final double SECTOR_GAP = 0.04;

    // ── 中心预览区 ──
    private static final float CENTER_PREVIEW_RADIUS = 32.0f;

    // ── 锁定图标 ──
    private static final ResourceLocation LOCKED_ICON =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/locked.png");

    // ── 状态 ──
    private int selectedIndex = -1;
    private long openedAtMs;
    private final float[] hoverProgress;
    private final List<WarpDestination> destinations;

    public WarpWheelScreen() {
        super(Component.empty());
        this.destinations = WarpDestinations.getAll();
        this.hoverProgress = new float[Math.max(destinations.size(), 1)];
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new WarpWheelScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        openedAtMs = Util.getMillis();
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int cx = screenW / 2;
        int cy = screenH / 2;

        float openProgress = getOpenProgress();
        float eased = easeInOutSmoother(openProgress);

        int count = destinations.size();
        if (count == 0) return;

        // 半透明背景遮罩
        int fadeAlpha = Mth.floor(Mth.lerp(eased, 20.0f, 140.0f));
        g.fill(0, 0, screenW, screenH, fadeAlpha << 24);

        // 计算选中的扇区
        selectedIndex = computeSelectedIndex(mouseX, mouseY, cx, cy, count);

        // 渲染扇区背景
        renderSectorBackgrounds(g, cx, cy, count, eased);

        // 渲染扇区文字标签
        renderSectorLabels(g, cx, cy, count, eased);

        // 渲染中心预览区
        renderCenterPreview(g, cx, cy, eased);
    }

    // ── 扇区背景（三角扇形弧形渲染）──

    private void renderSectorBackgrounds(GuiGraphics g, int cx, int cy, int count, float eased) {
        double step = (Math.PI * 2.0) / count;
        float animatedInner = Mth.lerp(eased, 8, SECTOR_INNER_RADIUS);
        float animatedOuter = Mth.lerp(eased, 12, SECTOR_OUTER_RADIUS);

        for (int i = 0; i < count; i++) {
            WarpDestination dest = destinations.get(i);
            boolean unlocked = WarpWandClientState.isUnlocked(dest.id());
            boolean available = isDestinationAvailable(dest);
            boolean active = unlocked && available;
            boolean isHovered = (i == selectedIndex);

            // 更新 hover 动画进度
            hoverProgress[i] = Mth.clamp(hoverProgress[i] + (isHovered ? 0.15f : -0.1f), 0.0f, 1.0f);

            // 扇区角度范围（带间隙）
            double sectorCenter = START_ANGLE + step * i;
            double halfArc = (step - SECTOR_GAP) * 0.5;

            // 动画：从全部折叠到展开
            double animCenter = Mth.lerp(eased, START_ANGLE, sectorCenter);
            double animHalf = halfArc * eased;

            double angleStart = animCenter - animHalf;
            double angleEnd = animCenter + animHalf;

            // hover 时外径微微扩大
            float hoverExpand = hoverProgress[i] * 8.0f;

            // 颜色
            float r, green, b, a;
            if (isHovered && active) {
                r = 0.35f; green = 0.61f; b = 0.84f; a = 0.55f;
            } else if (isHovered) {
                r = 0.50f; green = 0.50f; b = 0.50f; a = 0.40f;
            } else if (active) {
                r = 0.20f; green = 0.42f; b = 0.75f; a = 0.30f;
            } else if (unlocked) {
                r = 0.20f; green = 0.20f; b = 0.20f; a = 0.24f;
            } else {
                r = 0.30f; green = 0.30f; b = 0.30f; a = 0.22f;
            }

            fillArcTriangles(g, cx, cy, animatedInner, animatedOuter + hoverExpand,
                    angleStart, angleEnd, r, green, b, a);

            // hover 时画外边缘高亮线
            if (hoverProgress[i] > 0.01f) {
                float lineAlpha = hoverProgress[i] * (active ? 0.7f : 0.35f);
                float lr = active ? 0.95f : 0.6f;
                float lg = active ? 0.85f : 0.6f;
                float lb = active ? 0.3f  : 0.6f;
                fillArcTriangles(g, cx, cy,
                        animatedOuter + hoverExpand - 2, animatedOuter + hoverExpand,
                        angleStart, angleEnd, lr, lg, lb, lineAlpha);
            }
        }
    }

    // ── 扇区文字标签 ──

    private void renderSectorLabels(GuiGraphics g, int cx, int cy, int count, float eased) {
        if (minecraft == null || eased < 0.3f) return;

        double step = (Math.PI * 2.0) / count;
        float textRadius = (SECTOR_INNER_RADIUS + SECTOR_OUTER_RADIUS) * 0.5f;
        float animatedTextRadius = Mth.lerp(eased, 10, textRadius);

        // 文字淡入
        float textAlpha = Mth.clamp((eased - 0.3f) / 0.4f, 0.0f, 1.0f);

        for (int i = 0; i < count; i++) {
            WarpDestination dest = destinations.get(i);
            boolean unlocked = WarpWandClientState.isUnlocked(dest.id());
            boolean available = isDestinationAvailable(dest);
            boolean active = unlocked && available;
            boolean isHovered = (i == selectedIndex);

            double sectorCenter = START_ANGLE + step * i;
            double animAngle = Mth.lerp(eased, START_ANGLE, sectorCenter);

            float labelX = cx + (float) Math.cos(animAngle) * animatedTextRadius;
            float labelY = cy + (float) Math.sin(animAngle) * animatedTextRadius;

            Component name = Component.translatable(dest.nameKey());
            int nameW = minecraft.font.width(name);

            // hover 时放大
            float scale = 1.0f + hoverProgress[i] * 0.2f;

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(labelX, labelY, 0);
            pose.scale(scale, scale, 1.0f);

            int alpha;
            int color;
            if (active) {
                alpha = (int) (textAlpha * (isHovered ? 255 : 200));
                color = isHovered ? 0xFFF4EED0 : 0xFFDDCCAA;
            } else {
                alpha = (int) (textAlpha * (isHovered ? 180 : 120));
                color = 0xFF999999;
            }
            // 将 alpha 应用到颜色
            color = (alpha << 24) | (color & 0x00FFFFFF);

            g.drawString(minecraft.font, name,
                    -nameW / 2, -4, color, false);

            // 未解锁时在名称下方小字显示价格
            if (!unlocked) {
                String costStr = String.format("%,dg", dest.cost());
                Component costComp = Component.literal(costStr);
                int costW = minecraft.font.width(costComp);
                int costAlpha = (int) (textAlpha * (isHovered ? 200 : 100));
                g.drawString(minecraft.font, costComp,
                        -costW / 2, 7, (costAlpha << 24) | 0xFFAA00, false);
            }

            pose.popPose();
        }
    }

    /**
     * 用三角扇形正确渲染弧形区域。
     */
    private static void fillArcTriangles(GuiGraphics g, float cx, float cy,
                                         float innerR, float outerR,
                                         double startAngle, double endAngle,
                                         float r, float green, float b, float a) {
        if (endAngle <= startAngle) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        double angleStep = (endAngle - startAngle) / ARC_SEGMENTS;
        for (int s = 0; s <= ARC_SEGMENTS; s++) {
            double angle = startAngle + angleStep * s;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            buf.addVertex(matrix, cx + cosA * outerR, cy + sinA * outerR, 0)
                    .setColor(r, green, b, a);
            buf.addVertex(matrix, cx + cosA * innerR, cy + sinA * innerR, 0)
                    .setColor(r, green, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ── 中心预览区 ──

    private void renderCenterPreview(GuiGraphics g, int cx, int cy, float eased) {
        if (minecraft == null) return;
        if (eased < 0.1f) return;

        // 中心半透明圆底
        float pr = CENTER_PREVIEW_RADIUS * eased;
        fillCircle(g, cx, cy, pr, 0.05f, 0.05f, 0.08f, 0.65f);

        if (selectedIndex >= 0 && selectedIndex < destinations.size()) {
            WarpDestination dest = destinations.get(selectedIndex);
            boolean unlocked = WarpWandClientState.isUnlocked(dest.id());
            boolean available = isDestinationAvailable(dest);

            if (!available) {
                Component disabled = Component.translatable("stardewcraft.warp.farm.unavailable");
                GuiText.drawCenteredClamped(g, minecraft.font, disabled, cx, cy - 4,
                    Math.round(CENTER_PREVIEW_RADIUS * 2.0f), 0xAA777777, true);
            } else if (unlocked) {
                // 已解锁：显示描述
                Component desc = Component.translatable(dest.descKey());
                GuiText.drawWrappedCentered(g, minecraft.font, desc, cx, cy - 9,
                    Math.round(CENTER_PREVIEW_RADIUS * 2.0f), 0xAA999999, true, 2);
            } else {
                // 未解锁：锁图标 + 价格
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.6f);
                g.blit(LOCKED_ICON, cx - 8, cy - 14, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                String costText = String.format("%,dg", dest.cost());
                Component costComp = Component.literal(costText);
                int costW = minecraft.font.width(costComp);
                g.drawString(minecraft.font, costComp,
                        cx - costW / 2, cy + 6,
                        0xFFFFAA00, false);
            }
        } else {
            // 无选中：显示物品名称
            Component hint = Component.translatable("item.stardewcraft.warp_wand");
            int alpha = (int) (eased * 180);
                GuiText.drawCenteredClamped(g, minecraft.font, hint, cx, cy - 4,
                    Math.round(CENTER_PREVIEW_RADIUS * 2.0f), (alpha << 24) | 0xCCCCCC, true);
        }
    }

    /**
     * 用三角扇形渲染实心圆。
     */
    private static void fillCircle(GuiGraphics g, float cx, float cy, float radius,
                                   float r, float green, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        buf.addVertex(matrix, cx, cy, 0).setColor(r, green, b, a);

        int segments = 32;
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0 / segments) * i;
            buf.addVertex(matrix, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0)
                    .setColor(r, green, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ── 输入处理 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedIndex >= 0 && selectedIndex < destinations.size()) {
            WarpDestination dest = destinations.get(selectedIndex);
            if (!isDestinationAvailable(dest)) {
                return true;
            }

            boolean unlocked = WarpWandClientState.isUnlocked(dest.id());

            if (unlocked) {
                // 已解锁 → 传送
                PacketDistributor.sendToServer(new WarpWandTeleportPayload(dest.id()));
                onClose();
            } else {
                // 未解锁 → 请求服务端购买解锁（等待服务端同步确认，不在客户端乐观更新）
                PacketDistributor.sendToServer(new WarpWandUnlockPayload(dest.id()));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isDestinationAvailable(WarpDestination dest) {
        return !dest.requiresPlayerFarm() || ClientPlayerDataCache.hasFarm();
    }

    // ── 扇区检测（复用 Emote Wheel 极坐标算法）──

    private static int computeSelectedIndex(int mouseX, int mouseY, int cx, int cy, int count) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < DEADZONE) return -1;

        double step = (Math.PI * 2.0) / count;
        double normalized = Math.atan2(dy, dx) - START_ANGLE + step * 0.5;
        while (normalized < 0) normalized += Math.PI * 2.0;
        while (normalized >= Math.PI * 2.0) normalized -= Math.PI * 2.0;

        int sector = Mth.floor(normalized / step);
        return Mth.clamp(sector, 0, count - 1);
    }

    // ── 动画工具 ──

    private float getOpenProgress() {
        if (openedAtMs <= 0) return 1.0f;
        long elapsed = Util.getMillis() - openedAtMs;
        return Mth.clamp(elapsed / (float) OPEN_ANIM_MS, 0.0f, 1.0f);
    }

    private static float easeInOutSmoother(float t) {
        float x = Mth.clamp(t, 0.0f, 1.0f);
        return x * x * x * (x * (x * 6.0f - 15.0f) + 10.0f);
    }
}
