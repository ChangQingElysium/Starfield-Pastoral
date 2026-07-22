package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class WarpWandClientFx {
    private static final int PURPLE_BRIGHT = 0xFFF4D8FF;
    private static final int PURPLE_MID = 0xFF9C5CFF;
    private static final int PURPLE_DEEP = 0xFF3A145F;
    private static final int BG_TINT_TOP = 0xF011061D;
    private static final int BG_TINT_BOTTOM = 0xF006020D;
    private static final float BORDER_PERIOD_S = 2.7F;

    private static final int TYPE_BASE_RGB = 0x7A35D8;
    private static final int TYPE_HIGHLIGHT_RGB = 0xF1C6FF;
    private static final float TYPE_SWEEP_SPEED = 0.42F;
    private static final float TYPE_SWEEP_HALF = 0.31F;

    private WarpWandClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(ModItems.WARP_WAND.get())) {
            return;
        }

        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float phase = (float) ((t / BORDER_PERIOD_S) * Math.PI * 2.0);
        float k = 0.5F + 0.5F * (float) Math.sin(phase);
        float kShift = 0.5F + 0.5F * (float) Math.sin(phase + Math.PI * 0.58);

        event.setBorderStart(lerpArgb(PURPLE_MID, PURPLE_BRIGHT, k));
        event.setBorderEnd(lerpArgb(PURPLE_DEEP, PURPLE_MID, kShift));
        event.setBackgroundStart(BG_TINT_TOP);
        event.setBackgroundEnd(BG_TINT_BOTTOM);
    }

    public static MutableComponent flowingTypeLabel(String raw) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float span = 1.0F + TYPE_SWEEP_HALF * 2.0F;
        float pos = ((t * TYPE_SWEEP_SPEED) % span) - TYPE_SWEEP_HALF;
        int length = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < length; i++) {
            float u = length > 1 ? (float) i / (length - 1) : 0.5F;
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
