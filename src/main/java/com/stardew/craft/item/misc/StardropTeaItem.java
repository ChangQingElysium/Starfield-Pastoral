package com.stardew.craft.item.misc;

import com.stardew.craft.item.IStardewItem;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class StardropTeaItem extends Item implements IStardewItem {
    private static final int SELL_PRICE = 77;
    private static final int NAME_BASE_RGB = 0x9A59FF;
    private static final int NAME_HIGHLIGHT_RGB = 0xFFD56D;

    public StardropTeaItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return SELL_PRICE;
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        return sweepHighlight(Component.translatable(getDescriptionId(stack)).getString(), NAME_BASE_RGB, NAME_HIGHLIGHT_RGB);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
            @javax.annotation.Nullable net.minecraft.world.level.Level level,
            @Nonnull List<Component> tooltipComponents,
            @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("stardewcraft.item.stardrop_tea.tooltip.flavor")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xB78DFF))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.stardrop_tea.tooltip.special")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent sweepHighlight(String raw, int baseRgb, int highlightRgb) {
        long ms = System.currentTimeMillis();
        float pos = (((ms % 60_000L) / 1000.0F * 0.40F) % 1.64F) - 0.32F;
        int length = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < length; i++) {
            float u = length > 1 ? (float) i / (length - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / 0.32F);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(baseRgb, highlightRgb, k))).withBold(true)));
        }
        return out;
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