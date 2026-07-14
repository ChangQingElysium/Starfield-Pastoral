package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Cool glass-and-moonlight tooltip treatment for the Magnifying Glass special item. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class MagnifyingGlassClientFx {
    private static final int CYAN = 0xFF8EEAF2;
    private static final int BLUE = 0xFF267D9A;
    private static final int PEARL = 0xFFE9FFFF;

    private MagnifyingGlassClientFx() {}

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!event.getItemStack().is(ModItems.MAGNIFYING_GLASS.get())) return;
        float phase = (float) ((System.currentTimeMillis() % 4_000L) / 4_000.0D * Math.PI * 2.0D);
        float topMix = 0.5F + 0.5F * Mth.sin(phase);
        float bottomMix = 0.5F + 0.5F * Mth.sin(phase + 1.7F);
        event.setBorderStart(lerpArgb(CYAN, PEARL, topMix));
        event.setBorderEnd(lerpArgb(BLUE, CYAN, bottomMix));
        event.setBackgroundStart(0xF0081820);
        event.setBackgroundEnd(0xF0020B10);
    }

    public static MutableComponent flowingTypeLabel(String raw) {
        float width = 0.30F;
        float pos = (((System.currentTimeMillis() % 60_000L) / 1000.0F * 0.40F)
                % (1.0F + width * 2.0F)) - width;
        MutableComponent out = Component.empty();
        for (int i = 0; i < raw.length(); i++) {
            float u = raw.length() > 1 ? (float) i / (raw.length() - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / width);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(0x277E9B, 0xD8FFFF, k))).withBold(true)));
        }
        return out;
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int alpha = Math.round(aa + (ba - aa) * k);
        int rgb = lerpRgb(a, b, k);
        return (alpha << 24) | rgb;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
