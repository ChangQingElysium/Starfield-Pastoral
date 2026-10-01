package com.stardew.craft.client.hotspring;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.hotspring.HotSpringAreaRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ViewportEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Random;

/**
 * 客户端温泉雾感（3D 风格）。
 *
 * 原版 BathHousePool 是 2D 屏幕铺贴的蒸汽 tile；在 3D MC 里照搬不合适。
 * 改造为三层叠加：
 *
 * 1. {@link ViewportEvent.ComputeFogColor} — 把世界雾色拉向暖白，给画面整体罩一层；
 * 2. {@link ViewportEvent.RenderFog}      — 缩短雾的近/远平面，让远处真的"看不清"；
 * 3. {@link PlayerTickEvent.Post}         — 在 waterBounds 的水面随机投点，spawn
 *    {@link ParticleTypes#CAMPFIRE_COSY_SMOKE} 缓慢上漂，给水面真实 3D 蒸汽；
 * 进入/离开 mistBounds 时通过 fadeAlpha 平滑过渡（FADE_MS）。
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class HotSpringMistOverlay {

    private static final float FADE_MS = 400f;
    /** 雾近平面（fade 满时）。 */
    private static final float FOG_NEAR = 2.0f;
    /** 雾远平面（fade 满时）。雾更浓 → 缩短可视距离。 */
    private static final float FOG_FAR = 16.0f;
    /** 雾色暖白偏粉，对齐温泉氛围；分量上限 1.0。 */
    private static final float FOG_R = 0.97f;
    private static final float FOG_G = 0.95f;
    private static final float FOG_B = 0.92f;
    /** 每客户端 tick 在水面试投的粒子数；命中 mist 区域时实际生效。 */
    private static final int PARTICLES_PER_TICK = 4;
    /** 蒸汽上漂初速（沿 +Y）。 */
    private static final double STEAM_VY = 0.02;

    private static long lastNanos = 0L;
    private static float fadeAlpha = 0f;
    private static final Random RNG = new Random();

    private HotSpringMistOverlay() {}

    // ───────── State driver: ClientTick 更新 fade + spawn 3D 蒸汽粒子 ─────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) return;
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            fadeAlpha = 0f;
            lastNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        float deltaMs = lastNanos > 0 ? (now - lastNanos) / 1_000_000f : 0f;
        lastNanos = now;
        if (deltaMs > 200f) deltaMs = 50f;

        boolean inMist = HotSpringAreaRegistry.isInMistArea(
            level.dimension(), player.getX(), player.getY(), player.getZ());

        float target = inMist ? 1f : 0f;
        float step = deltaMs / FADE_MS;
        if (fadeAlpha < target) fadeAlpha = Math.min(target, fadeAlpha + step);
        else if (fadeAlpha > target) fadeAlpha = Math.max(target, fadeAlpha - step);

        // 即使玩家暂时离开 mist，也保留正在上漂的粒子直到它们靠寿命自然消散。
        // 但只要附近还有 waterBounds 在加载范围内，就持续投点 — 真实温泉的蒸汽不应只在玩家泡进去时才有。
        spawnSteamParticles(level, player);
    }

    private static void spawnSteamParticles(ClientLevel level, Player player) {
        List<HotSpringAreaRegistry.Area> areas = HotSpringAreaRegistry.getWaterAreas(level.dimension());
        if (areas.isEmpty()) return;

        double px = player.getX();
        double pz = player.getZ();

        for (HotSpringAreaRegistry.Area area : areas) {
            AABB b = area.bounds();
            // 远离时不投点，省 CPU / 客户端粒子上限
            double cx = (b.minX + b.maxX) * 0.5;
            double cz = (b.minZ + b.maxZ) * 0.5;
            double dx = cx - px;
            double dz = cz - pz;
            double maxDist = Math.max(b.getXsize(), b.getZsize()) * 0.5 + 48.0;
            if (dx * dx + dz * dz > maxDist * maxDist) continue;

            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                double x = b.minX + RNG.nextDouble() * b.getXsize();
                double z = b.minZ + RNG.nextDouble() * b.getZsize();
                // 水面 + 微小抖动
                double y = b.maxY + RNG.nextDouble() * 0.2;
                double vx = (RNG.nextDouble() - 0.5) * 0.01;
                double vz = (RNG.nextDouble() - 0.5) * 0.01;
                level.addParticle(com.stardew.craft.weather.ModParticles.HOT_SPRING_STEAM.get(),
                    x, y, z, vx, STEAM_VY, vz);
            }
        }
    }

    // ───────── 3D 雾色 / 雾距 修改 ─────────

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (fadeAlpha <= 0f) return;
        float a = fadeAlpha;
        event.setRed(lerp(event.getRed(), FOG_R, a));
        event.setGreen(lerp(event.getGreen(), FOG_G, a));
        event.setBlue(lerp(event.getBlue(), FOG_B, a));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (fadeAlpha <= 0f) return;
        float a = fadeAlpha;
        float near = lerp(event.getNearPlaneDistance(), FOG_NEAR, a);
        float far = lerp(event.getFarPlaneDistance(), FOG_FAR, a);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
