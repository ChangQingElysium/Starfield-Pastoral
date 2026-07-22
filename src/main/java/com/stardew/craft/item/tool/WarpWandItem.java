package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.warp.ReturnScepterService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Return Scepter. Like SDV's {@code Wand}, using it always returns the holder
 * to their own farmhouse entrance; it has no destination-selection UI.
 */
public class WarpWandItem extends Item implements IStardewItem {
    private static final float NAME_SWEEP_SPEED = 0.40F;
    private static final float NAME_SWEEP_WIDTH = 0.30F;
    private static final int NAME_BASE_RGB = 0x8F4AE8;
    private static final int NAME_HIGHLIGHT_RGB = 0xF2D2FF;

    public WarpWandItem(Properties properties) {
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
    public InteractionResultHolder<ItemStack> use(@javax.annotation.Nonnull Level level, @javax.annotation.Nonnull Player player, @javax.annotation.Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!ReturnScepterService.begin(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(@javax.annotation.Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@javax.annotation.Nonnull ItemStack stack,
                               @javax.annotation.Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@javax.annotation.Nonnull ItemStack stack) {
        String raw = Component.translatable(this.getDescriptionId(stack)).getString();
        return sweepHighlight(raw, NAME_BASE_RGB, NAME_HIGHLIGHT_RGB,
                NAME_SWEEP_SPEED, NAME_SWEEP_WIDTH, true);
    }

    private static MutableComponent sweepHighlight(String raw, int baseRgb, int highlightRgb,
                                                    float speedPerSec, float halfWidth, boolean bold) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0F;
        float span = 1.0F + halfWidth * 2.0F;
        float pos = ((t * speedPerSec) % span) - halfWidth;

        int length = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < length; i++) {
            float u = length > 1 ? (float) i / (length - 1) : 0.5F;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0F, 1.0F - dist / halfWidth);
            k = k * k * (3.0F - 2.0F * k);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(baseRgb, highlightRgb, k)));
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
