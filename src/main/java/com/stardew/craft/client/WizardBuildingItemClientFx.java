package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.WizardBuildingItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Animated tooltip treatment shared by all six Wizard Tower buildings. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class WizardBuildingItemClientFx {
    private WizardBuildingItemClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!(event.getItemStack().getItem() instanceof WizardBuildingItem item)) {
            return;
        }
        WizardBuildingItem.Kind kind = item.kind();
        float phase = (float) ((System.currentTimeMillis() % 3_800L) / 3_800.0D * Math.PI * 2.0D);
        event.setBorderStart(lerpArgb(kind.borderBaseArgb(), kind.borderHighlightArgb(),
                0.5F + 0.5F * Mth.sin(phase)));
        event.setBorderEnd(lerpArgb(kind.borderBaseArgb(), kind.borderHighlightArgb(),
                0.5F + 0.5F * Mth.sin(phase + 1.9F)));
        event.setBackgroundStart(kind.backgroundStartArgb());
        event.setBackgroundEnd(kind.backgroundEndArgb());
    }

    public static MutableComponent flowingTypeLabel(String raw, WizardBuildingItem.Kind kind) {
        float width = 0.30F;
        float pos = (((System.currentTimeMillis() % 60_000L) / 1000.0F * 0.36F)
                % (1.0F + width * 2.0F)) - width;
        MutableComponent result = Component.empty();
        for (int i = 0; i < raw.length(); i++) {
            float u = raw.length() > 1 ? (float) i / (raw.length() - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / width);
            k = k * k * (3.0F - 2.0F * k);
            result.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(
                            kind.nameBaseRgb(), kind.nameHighlightRgb(), k))).withBold(true)));
        }
        return result;
    }

    private static int lerpArgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        return (Math.round(aa + (ba - aa) * k) << 24) | lerpRgb(a, b, k);
    }

    private static int lerpRgb(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
