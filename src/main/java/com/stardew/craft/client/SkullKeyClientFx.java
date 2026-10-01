package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

/**
 * 骷髅钥匙的 tooltip 高级视觉：
 * <ul>
 *     <li>边框上下两端在暖金 ↔ 古铜之间按时间相位差缓慢流动</li>
 *     <li>背景轻微着色（深棕黑），让暖金边框更跳出，不抢内容</li>
 * </ul>
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SkullKeyClientFx {

    private SkullKeyClientFx() {}

    // 边框两个关键色（ARGB，alpha=0xFF；MinecraftForge 期望 0xAARRGGBB）
    private static final int GOLD_BRIGHT = 0xFFFFE6A0; // 暖白金
    private static final int GOLD_DEEP   = 0xFF8A5A12; // 古铜
    private static final int GOLD_AMBER  = 0xFFD4A24A; // 琥珀

    // 半透深棕，叠在原版黑底上做出"沉稳"感
    private static final int BG_TINT_TOP    = 0xF01A0E03;
    private static final int BG_TINT_BOTTOM = 0xF00A0500;

    private static final float BORDER_PERIOD_S = 2.6f;

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!stack.is(ModItems.SKULL_KEY.get())) return;

        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0f;
        float phase = (float) ((t / BORDER_PERIOD_S) * Math.PI * 2.0);
        float k = 0.5f + 0.5f * (float) Math.sin(phase);          // 0..1
        float kShift = 0.5f + 0.5f * (float) Math.sin(phase + Math.PI * 0.6); // 错相

        // 上边框：bright ↔ amber
        int top = lerpArgb(GOLD_AMBER, GOLD_BRIGHT, k);
        // 下边框：amber ↔ deep （让上下色差形成流动感）
        int bottom = lerpArgb(GOLD_DEEP, GOLD_AMBER, kShift);

        event.setBorderStart(top);
        event.setBorderEnd(bottom);
        event.setBackgroundStart(BG_TINT_TOP);
        event.setBackgroundEnd(BG_TINT_BOTTOM);
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int A = Math.round(aa + (ba - aa) * k);
        int R = Math.round(ar + (br - ar) * k);
        int G = Math.round(ag + (bg - ag) * k);
        int B = Math.round(ab + (bb - ab) * k);
        return (A << 24) | (R << 16) | (G << 8) | B;
    }

    // ─────────────────────── 类型标签流光（紫金）───────────────────────

    /** 紫色家族 — 深紫 → 亮紫罗兰 → 紫金 之间扫光。 */
    private static final int TYPE_BASE_RGB      = 0x6E2FB0; // 深紫
    private static final int TYPE_HIGHLIGHT_RGB = 0xE7B4FF; // 月光紫
    private static final float TYPE_SWEEP_SPEED = 0.45f;
    private static final float TYPE_SWEEP_HALF  = 0.32f;

    /**
     * 给 "特殊物品" 这种短类型标签加流动高光。粗体，紫金主题。
     */
    public static MutableComponent flowingTypeLabel(String raw) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0f;
        float span = 1.0f + TYPE_SWEEP_HALF * 2.0f;
        float pos = ((t * TYPE_SWEEP_SPEED) % span) - TYPE_SWEEP_HALF;
        int n = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < n; i++) {
            float u = n > 1 ? (float) i / (n - 1) : 0.5f;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0f, 1.0f - dist / TYPE_SWEEP_HALF);
            k = k * k * (3.0f - 2.0f * k);
            int rgb = lerpRgb(TYPE_BASE_RGB, TYPE_HIGHLIGHT_RGB, k);
            out.append(Component.literal(String.valueOf(raw.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
        }
        return out;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
