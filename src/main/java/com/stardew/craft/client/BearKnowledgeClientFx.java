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

/** Forest-brown and honey-gold tooltip treatment for Bear's Knowledge. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class BearKnowledgeClientFx {
    private static final int BROWN = 0xFF80502D;
    private static final int HONEY = 0xFFE1B85E;
    private static final int CREAM = 0xFFFFE7AE;

    private BearKnowledgeClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!event.getItemStack().is(ModItems.BEAR_KNOWLEDGE.get())) return;
        float phase = (float) ((System.currentTimeMillis() % 4_200L) / 4_200.0D * Math.PI * 2.0D);
        event.setBorderStart(lerpArgb(BROWN, CREAM, 0.5F + 0.5F * Mth.sin(phase)));
        event.setBorderEnd(lerpArgb(BROWN, HONEY, 0.5F + 0.5F * Mth.sin(phase + 1.7F)));
        event.setBackgroundStart(0xF0140D08);
        event.setBackgroundEnd(0xF0080503);
    }

    public static MutableComponent flowingTypeLabel(String raw) {
        float width = 0.28F;
        float pos = (((System.currentTimeMillis() % 60_000L) / 1000.0F * 0.36F)
                % (1.0F + width * 2.0F)) - width;
        MutableComponent out = Component.empty();
        for (int i = 0; i < raw.length(); i++) {
            float u = raw.length() > 1 ? (float) i / (raw.length() - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / width);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(0x80502D, 0xFFE4A1, k))).withBold(true)));
        }
        return out;
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        return (Math.round(aa + (ba - aa) * k) << 24) | lerpRgb(a, b, k);
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
