package com.stardew.craft.item;

import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.route.RouteGuidanceService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

public class RouteGuidanceConsumableItem extends Item implements IStardewItem {
    private final String routeId;
    private final int durationTicks;
    private final int sellPrice;
    private final String requiredFestivalId;
    private final String unavailableMessageKey;

    public RouteGuidanceConsumableItem(String routeId, int durationTicks, int sellPrice,
                                       String requiredFestivalId, String unavailableMessageKey,
                                       Properties properties) {
        super(properties);
        this.routeId = routeId;
        this.durationTicks = Math.max(1, durationTicks);
        this.sellPrice = sellPrice;
        this.requiredFestivalId = requiredFestivalId == null ? "" : requiredFestivalId;
        this.unavailableMessageKey = unavailableMessageKey == null ? "" : unavailableMessageKey;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.misc";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return sellPrice <= 0 ? -1 : sellPrice;
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUseNow(level)) {
            showUnavailableMessage(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            if (!canUseNow(level)) {
                showUnavailableMessage(player);
                return stack;
            }
            RouteGuidanceService.start(player, routeId, durationTicks);
            level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.45F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    private boolean canUseNow(Level level) {
        if (requiredFestivalId.isBlank() || level.isClientSide) {
            return true;
        }
        return level instanceof ServerLevel serverLevel
                && FestivalService.isActiveFestivalEntryOpen(serverLevel, requiredFestivalId);
    }

    private void showUnavailableMessage(Player player) {
        if (!unavailableMessageKey.isBlank()) {
            player.displayClientMessage(Component.translatable(unavailableMessageKey), true);
        }
    }
}
