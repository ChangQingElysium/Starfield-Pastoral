package com.stardew.craft.item.misc;

import com.stardew.craft.item.IStardewItem;
import javax.annotation.Nonnull;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GalaxySoulItem extends Item implements IStardewItem {
    private static final float NAME_CYCLE_SECONDS = 2.8F;

    public GalaxySoulItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 5000;
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
        return prismaticText(Component.translatable(getDescriptionId(stack)).getString(), true, 0.58F);
    }

    public static MutableComponent prismaticText(String raw, boolean bold) {
        return prismaticText(raw, bold, 0.0F);
    }

    public static MutableComponent prismaticText(String raw, boolean bold, float phaseOffset) {
        MutableComponent out = Component.empty();
        int length = raw.codePointCount(0, raw.length());
        float phase = ((System.currentTimeMillis() % 60_000L) / 1000.0F / NAME_CYCLE_SECONDS + phaseOffset) % 1.0F;
        int index = 0;
        for (int offset = 0; offset < raw.length();) {
            int codePoint = raw.codePointAt(offset);
            int charCount = Character.charCount(codePoint);
            float position = length <= 1 ? 0.0F : (float) index / Math.max(1, length - 1);
            int rgb = prismaticRgb(position + phase);
            out.append(Component.literal(new String(Character.toChars(codePoint)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold)));
            offset += charCount;
            index++;
        }
        return out;
    }

    public static int prismaticRgb(float hue) {
        float h = positiveFraction(hue);
        float sector = h * 6.0F;
        int i = (int) Math.floor(sector);
        float f = sector - i;
        float q = 1.0F - f;
        float r;
        float g;
        float b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = 1.0F; g = f; b = 0.0F; }
            case 1 -> { r = q; g = 1.0F; b = 0.0F; }
            case 2 -> { r = 0.0F; g = 1.0F; b = f; }
            case 3 -> { r = 0.0F; g = q; b = 1.0F; }
            case 4 -> { r = f; g = 0.0F; b = 1.0F; }
            default -> { r = 1.0F; g = 0.0F; b = q; }
        }
        r = 0.18F + r * 0.82F;
        g = 0.18F + g * 0.82F;
        b = 0.26F + b * 0.74F;
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    private static float positiveFraction(float value) {
        float out = value - (float) Math.floor(value);
        return out < 0.0F ? out + 1.0F : out;
    }
}