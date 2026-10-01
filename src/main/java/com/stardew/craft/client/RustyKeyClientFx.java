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

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class RustyKeyClientFx {
    private static final int GREEN_BRIGHT = 0xFFE3F7B7;
    private static final int GREEN_MID = 0xFF8FB85D;
    private static final int GREEN_DEEP = 0xFF34501F;
    private static final int BG_TINT_TOP = 0xF0081705;
    private static final int BG_TINT_BOTTOM = 0xF0030802;
    private static final float BORDER_PERIOD_S = 2.8F;

    private static final int TYPE_BASE_RGB = 0x4E8B36;
    private static final int TYPE_HIGHLIGHT_RGB = 0xDDF6B0;
    private static final float TYPE_SWEEP_SPEED = 0.42F;
    private static final float TYPE_SWEEP_HALF = 0.30F;

    private RustyKeyClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(ModItems.RUSTY_KEY.get())) {
            return;
        }

        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float phase = (float) ((t / BORDER_PERIOD_S) * Math.PI * 2.0);
        float k = 0.5F + 0.5F * (float) Math.sin(phase);
        float kShift = 0.5F + 0.5F * (float) Math.sin(phase + Math.PI * 0.55);

        event.setBorderStart(lerpArgb(GREEN_MID, GREEN_BRIGHT, k));
        event.setBorderEnd(lerpArgb(GREEN_DEEP, GREEN_MID, kShift));
        event.setBackgroundStart(BG_TINT_TOP);
        event.setBackgroundEnd(BG_TINT_BOTTOM);
    }

    public static MutableComponent flowingTypeLabel(String raw) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float span = 1.0F + TYPE_SWEEP_HALF * 2.0F;
        float pos = ((t * TYPE_SWEEP_SPEED) % span) - TYPE_SWEEP_HALF;
        int n = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < n; i++) {
            float u = n > 1 ? (float) i / (n - 1) : 0.5F;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0F, 1.0F - dist / TYPE_SWEEP_HALF);
            k = k * k * (3.0F - 2.0F * k);
            int rgb = lerpRgb(TYPE_BASE_RGB, TYPE_HIGHLIGHT_RGB, k);
            out.append(Component.literal(String.valueOf(raw.charAt(i)))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
        }
        return out;
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int alpha = Math.round(aa + (ba - aa) * k);
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}