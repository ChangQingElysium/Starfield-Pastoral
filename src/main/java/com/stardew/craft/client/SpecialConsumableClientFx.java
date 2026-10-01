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
public final class SpecialConsumableClientFx {
    private static final int STARDROP_TOP = 0xFFEFC7FF;
    private static final int STARDROP_MID = 0xFF9A5CFF;
    private static final int STARDROP_BOTTOM = 0xFF2A124F;
    private static final int STARDROP_BG_TOP = 0xF0180928;
    private static final int STARDROP_BG_BOTTOM = 0xF0060312;

    private static final int IRIDIUM_TOP = 0xFFE6FAFF;
    private static final int IRIDIUM_MID = 0xFF68C8FF;
    private static final int IRIDIUM_BOTTOM = 0xFF3F4A7A;
    private static final int IRIDIUM_BG_TOP = 0xF0061620;
    private static final int IRIDIUM_BG_BOTTOM = 0xF0020810;

    private static final int SHORTS_TOP = 0xFFFFD9FF;
    private static final int SHORTS_MID = 0xFFB15CFF;
    private static final int SHORTS_BOTTOM = 0xFF4D157A;
    private static final int SHORTS_BG_TOP = 0xF01B0628;
    private static final int SHORTS_BG_BOTTOM = 0xF0070210;

    private static final int CONTRACT_TOP = 0xFFFFF2B8;
    private static final int CONTRACT_MID = 0xFFFFC857;
    private static final int CONTRACT_BOTTOM = 0xFF7A4A17;
    private static final int CONTRACT_BG_TOP = 0xF0211305;
    private static final int CONTRACT_BG_BOTTOM = 0xF0090501;

    private static final int PADDLE_TOP = 0xFFFFE3A0;
    private static final int PADDLE_MID = 0xFFD08A39;
    private static final int PADDLE_BOTTOM = 0xFF5B3218;
    private static final int PADDLE_BG_TOP = 0xF0201208;
    private static final int PADDLE_BG_BOTTOM = 0xF0080301;

    private static final int GOLDEN_PUMPKIN_TOP = 0xFFFFF2A8;
    private static final int GOLDEN_PUMPKIN_MID = 0xFFFFB83D;
    private static final int GOLDEN_PUMPKIN_BOTTOM = 0xFF7A4B11;
    private static final int GOLDEN_PUMPKIN_BG_TOP = 0xF0221404;
    private static final int GOLDEN_PUMPKIN_BG_BOTTOM = 0xF00A0501;

    private SpecialConsumableClientFx() {}

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        if (stack.is(ModItems.STARDROP.get()) || stack.is(ModItems.STARDROP_TEA.get())) {
            applyPalette(event, STARDROP_TOP, STARDROP_MID, STARDROP_BOTTOM, STARDROP_BG_TOP, STARDROP_BG_BOTTOM);
        } else if (stack.is(ModItems.IRIDIUM_MILK.get())) {
            applyPalette(event, IRIDIUM_TOP, IRIDIUM_MID, IRIDIUM_BOTTOM, IRIDIUM_BG_TOP, IRIDIUM_BG_BOTTOM);
        } else if (stack.is(ModItems.LUCKY_PURPLE_SHORTS.get())) {
            applyPalette(event, SHORTS_TOP, SHORTS_MID, SHORTS_BOTTOM, SHORTS_BG_TOP, SHORTS_BG_BOTTOM);
        } else if (stack.is(ModItems.MONEY_CONTRACT.get())) {
            applyPalette(event, CONTRACT_TOP, CONTRACT_MID, CONTRACT_BOTTOM, CONTRACT_BG_TOP, CONTRACT_BG_BOTTOM);
        } else if (stack.is(ModItems.AUCTION_PADDLE.get())) {
            applyPalette(event, PADDLE_TOP, PADDLE_MID, PADDLE_BOTTOM, PADDLE_BG_TOP, PADDLE_BG_BOTTOM);
        } else if (stack.is(ModItems.GOLDEN_PUMPKIN.get())) {
            applyPalette(event, GOLDEN_PUMPKIN_TOP, GOLDEN_PUMPKIN_MID, GOLDEN_PUMPKIN_BOTTOM,
                GOLDEN_PUMPKIN_BG_TOP, GOLDEN_PUMPKIN_BG_BOTTOM);
        }
    }

    public static MutableComponent stardropTypeLabel(String raw) {
        return flowingLabel(raw, 0x8E4CFF, 0xFFD56D, 0.40F, 0.34F);
    }

    public static MutableComponent iridiumMilkTypeLabel(String raw) {
        return flowingLabel(raw, 0x5BBEEA, 0xF4FDFF, 0.44F, 0.30F);
    }

    public static MutableComponent luckyPurpleShortsTypeLabel(String raw) {
        return flowingLabel(raw, 0x9C42FF, 0xFFE28A, 0.48F, 0.34F);
    }

    public static MutableComponent moneyContractTypeLabel(String raw) {
        return flowingLabel(raw, 0xC88522, 0xFFF2A8, 0.38F, 0.32F);
    }

    public static MutableComponent auctionPaddleTypeLabel(String raw) {
        return flowingLabel(raw, 0x8A5528, 0xFFE08A, 0.42F, 0.32F);
    }

    public static MutableComponent goldenPumpkinTypeLabel(String raw) {
        return flowingLabel(raw, 0xD68A18, 0xFFF2A8, 0.36F, 0.32F);
    }

    private static void applyPalette(RenderTooltipEvent.Color event, int top, int mid, int bottom, int bgTop, int bgBottom) {
        long ms = System.currentTimeMillis();
        float phase = (float) (((ms % 60_000L) / 1000.0F / 2.4F) * Math.PI * 2.0D);
        float k = 0.5F + 0.5F * (float) Math.sin(phase);
        float shifted = 0.5F + 0.5F * (float) Math.sin(phase + Math.PI * 0.58D);
        event.setBorderStart(lerpArgb(mid, top, k));
        event.setBorderEnd(lerpArgb(bottom, mid, shifted));
        event.setBackgroundStart(bgTop);
        event.setBackgroundEnd(bgBottom);
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
}
