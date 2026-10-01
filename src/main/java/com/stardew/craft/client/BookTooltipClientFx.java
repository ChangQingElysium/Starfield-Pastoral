package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.StardewBookItem;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class BookTooltipClientFx {
    private static final int BORDER_LIGHT = 0xFFFFE3A8;
    private static final int BORDER_MID = 0xFFC4863E;
    private static final int BORDER_DARK = 0xFF6F3D18;
    private static final int BG_TOP = 0xF8F7DFAF;
    private static final int BG_BOTTOM = 0xF8E8BE78;
    private static final float PERIOD_S = 4.0F;

    private BookTooltipClientFx() {
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof StardewBookItem)) {
            return;
        }

        float t = (System.currentTimeMillis() % 60_000L) / 1000.0F;
        float phase = (float) ((t / PERIOD_S) * Math.PI * 2.0D);
        float topBlend = 0.5F + 0.5F * (float) Math.sin(phase);
        float bottomBlend = 0.5F + 0.5F * (float) Math.sin(phase + Math.PI * 0.55D);

        event.setBorderStart(lerpArgb(BORDER_MID, BORDER_LIGHT, topBlend));
        event.setBorderEnd(lerpArgb(BORDER_DARK, BORDER_MID, bottomBlend));
        event.setBackgroundStart(BG_TOP);
        event.setBackgroundEnd(BG_BOTTOM);
    }

    private static int lerpArgb(int a, int b, float blend) {
        blend = Mth.clamp(blend, 0.0F, 1.0F);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int alpha = Math.round(aa + (ba - aa) * blend);
        int red = Math.round(ar + (br - ar) * blend);
        int green = Math.round(ag + (bg - ag) * blend);
        int blue = Math.round(ab + (bb - ab) * blend);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}