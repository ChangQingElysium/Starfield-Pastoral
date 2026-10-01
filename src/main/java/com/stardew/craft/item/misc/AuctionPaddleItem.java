package com.stardew.craft.item.misc;

import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.item.IStardewItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

public class AuctionPaddleItem extends Item implements IStardewItem {
    private static final int NAME_BASE_RGB = 0x8A5528;
    private static final int NAME_HIGHLIGHT_RGB = 0xFFE08A;

    public AuctionPaddleItem(Properties properties) {
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
                                @javax.annotation.Nullable Level level,
                                @Nonnull List<Component> tooltipComponents,
                                @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("stardewcraft.item.auction_paddle.tooltip.flavor")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC8873E))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.auction_paddle.tooltip.effect")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFE08A)).withBold(true)));
        tooltipComponents.add(Component.translatable("stardewcraft.item.auction_paddle.tooltip.special")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player instanceof ServerPlayer serverPlayer) {
            AuctionService.handleAuctionBoardInteraction(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static MutableComponent sweepHighlight(String raw, int baseRgb, int highlightRgb) {
        long ms = System.currentTimeMillis();
        float pos = (((ms % 60_000L) / 1000.0F * 0.42F) % 1.64F) - 0.32F;
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
