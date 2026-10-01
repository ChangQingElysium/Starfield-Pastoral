package com.stardew.craft.item;

import com.stardew.craft.sewer.SewerService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

public class RustyKeyItem extends Item implements IStardewItem {
    private static final float NAME_SWEEP_SPEED = 0.42F;
    private static final float NAME_SWEEP_WIDTH = 0.28F;
    private static final int NAME_BASE_RGB = 0x5E8F3C;
    private static final int NAME_HIGHLIGHT_RGB = 0xDDF6B0;

    public RustyKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        SewerService.grantRustyKey(player);
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
        String raw = Component.translatable(this.getDescriptionId(stack)).getString();
        return sweepHighlight(raw, NAME_BASE_RGB, NAME_HIGHLIGHT_RGB, NAME_SWEEP_SPEED, NAME_SWEEP_WIDTH, true);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
                                @javax.annotation.Nullable Level level,
                                @Nonnull List<Component> tooltipComponents,
                                @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("stardewcraft.item.rusty_key.tooltip.flavor")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7AA34E))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.rusty_key.tooltip.granted")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xB9D96A)).withBold(true)));
    }

    static MutableComponent sweepHighlight(String raw, int baseRgb, int hiRgb,
                                           float speedPerSec, float halfWidth, boolean bold) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float span = 1.0F + halfWidth * 2.0F;
        float pos = ((t * speedPerSec) % span) - halfWidth;

        int n = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < n; i++) {
            float u = n > 1 ? (float) i / (n - 1) : 0.5F;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0F, 1.0F - dist / halfWidth);
            k = k * k * (3.0F - 2.0F * k);
            int rgb = lerpRgb(baseRgb, hiRgb, k);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            if (bold) {
                style = style.withBold(true);
            }
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(style));
        }
        return out;
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