package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.item.trinket.TrinketType;
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
public final class TrinketClientFx {
    private static final int BG_TOP = 0xF00A0716;
    private static final int BG_BOTTOM = 0xF0020108;

    private TrinketClientFx() {}

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof StardewTrinketItem item)) {
            return;
        }

        Palette palette = palette(item.getTrinketType());
        long ms = System.currentTimeMillis();
        float phase = (float) (((ms % 60_000L) / 1000.0F / 2.1F) * Math.PI * 2.0D);
        float k = 0.5F + 0.5F * (float) Math.sin(phase);
        float shifted = 0.5F + 0.5F * (float) Math.sin(phase + Math.PI * 0.62D);
        event.setBorderStart(lerpArgb(palette.midArgb(), palette.topArgb(), k));
        event.setBorderEnd(lerpArgb(palette.bottomArgb(), palette.midArgb(), shifted));
        event.setBackgroundStart(BG_TOP);
        event.setBackgroundEnd(BG_BOTTOM);
    }

    public static MutableComponent trinketTypeLabel(String raw) {
        return flowingLabel(raw, 0xB04CFF, 0xFFE46A, 0.50F, 0.34F);
    }

    private static MutableComponent flowingLabel(String raw, int baseRgb, int highlightRgb, float speed, float halfWidth) {
        long ms = System.currentTimeMillis();
        float span = 1.0F + halfWidth * 2.0F;
        float pos = (((ms % 60_000L) / 1000.0F * speed) % span) - halfWidth;
        int length = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < length; i++) {
            float u = length > 1 ? (float) i / (length - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / halfWidth);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(baseRgb, highlightRgb, k))).withBold(true)));
        }
        return out;
    }

    private static Palette palette(TrinketType type) {
        return switch (type) {
            case MAGIC_HAIR_DYE -> new Palette(0xFFFF64F7, 0xFFC14CFF, 0xFF3B0B66);
            case FROG_EGG -> new Palette(0xFFB9FF6A, 0xFF52D46D, 0xFF143E24);
            case MAGIC_QUIVER -> new Palette(0xFFFFE66D, 0xFFBA6BFF, 0xFF24104A);
            case FAIRY_BOX -> new Palette(0xFFFFD6FF, 0xFFFF77D7, 0xFF4A1037);
            case PARROT_EGG -> new Palette(0xFFFFD05A, 0xFF7AE5FF, 0xFF112F55);
            case ICE_ROD -> new Palette(0xFFE6FAFF, 0xFF72D8FF, 0xFF0C3A66);
            case IRIDIUM_SPUR -> new Palette(0xFFFFE890, 0xFFE0A23E, 0xFF4D2806);
            case BASILISK_PAW -> new Palette(0xFFD0B5FF, 0xFF7A4CFF, 0xFF1A123D);
        };
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int alpha = Math.round(aa + (ba - aa) * k);
        int red = Math.round(ar + (br - ar) * k);
        int green = Math.round(ag + (bg - ag) * k);
        int blue = Math.round(ab + (bb - ab) * k);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int red = Math.round(ar + (br - ar) * k);
        int green = Math.round(ag + (bg - ag) * k);
        int blue = Math.round(ab + (bb - ab) * k);
        return (red << 16) | (green << 8) | blue;
    }

    private record Palette(int topArgb, int midArgb, int bottomArgb) {}
}
