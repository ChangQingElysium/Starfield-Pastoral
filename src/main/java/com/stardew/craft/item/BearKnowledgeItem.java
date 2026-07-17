package com.stardew.craft.item;

import com.stardew.craft.secretnote.SecretNote23Service;
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

/** Permanent special item awarded by the Secret Note 23 bear event. */
public final class BearKnowledgeItem extends Item implements IStardewItem {
    private static final int NAME_BASE_RGB = 0x8A5A32;
    private static final int NAME_HIGHLIGHT_RGB = 0xF2D38A;
    private static final float SWEEP_SPEED = 0.36F;
    private static final float SWEEP_WIDTH = 0.28F;

    public BearKnowledgeItem(Properties properties) {
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
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        SecretNote23Service.grantBearKnowledge(player);
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull ItemStack stack,
                               @Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        String raw = Component.translatable(getDescriptionId(stack)).getString();
        float pos = ((((System.currentTimeMillis() % 60_000L) / 1000.0F) * SWEEP_SPEED)
                % (1.0F + SWEEP_WIDTH * 2.0F)) - SWEEP_WIDTH;
        MutableComponent out = Component.empty();
        for (int i = 0; i < raw.length(); i++) {
            float u = raw.length() > 1 ? (float) i / (raw.length() - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / SWEEP_WIDTH);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(
                    Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(NAME_BASE_RGB, NAME_HIGHLIGHT_RGB, k)))
                            .withBold(true)));
        }
        return out;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("event.secret_note23.reward.knowledge")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA77443))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.bear_knowledge.tooltip.granted")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xDFC77D)).withBold(true)));
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
