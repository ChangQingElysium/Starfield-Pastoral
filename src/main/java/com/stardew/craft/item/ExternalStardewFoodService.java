package com.stardew.craft.item;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemData;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Bridges external vanilla-food items with Stardew item metadata. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ExternalStardewFoodService {
    private static final ThreadLocal<Deque<ItemStack>> USE_CONTEXT = new ThreadLocal<>();

    private ExternalStardewFoodService() {
    }

    /** Called around {@link ItemStack#use} so {@code Player.canEat} can inspect the exact hand stack. */
    public static void pushUseContext(ItemStack stack) {
        Deque<ItemStack> context = USE_CONTEXT.get();
        if (context == null) {
            context = new ArrayDeque<>();
            USE_CONTEXT.set(context);
        }
        context.push(stack);
    }

    public static void popUseContext() {
        Deque<ItemStack> context = USE_CONTEXT.get();
        if (context == null) {
            return;
        }
        if (!context.isEmpty()) {
            context.pop();
        }
        if (context.isEmpty()) {
            USE_CONTEXT.remove();
        }
    }

    /** Returns true only for configured external foods while using Stardew's energy model. */
    public static boolean shouldAllowCurrentFoodAtFullHunger(Player player) {
        Deque<ItemStack> context = USE_CONTEXT.get();
        return context != null && !context.isEmpty() && isConfiguredExternalFood(player, context.peek());
    }

    @SubscribeEvent
    public static void onFoodFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        StardewItemData data = configuredExternalFoodData(player, event.getItem()).orElse(null);
        if (data == null) {
            return;
        }

        boolean meagerMeals = DesertFestivalMineService.isInFestivalSkullCavern(player)
                && DesertFestivalMineService.meagerMealsActive(player.serverLevel());
        applyStardewEffects(player,
                scalePositiveEffect(data.energy(), meagerMeals),
                scalePositiveEffect(data.health(), meagerMeals));
    }

    static boolean isFoodMetadata(StardewItemData data) {
        return data != null && data.isFood();
    }

    static int scalePositiveEffect(int amount, boolean meagerMeals) {
        if (!meagerMeals || amount <= 0) {
            return amount;
        }
        return Math.max(1, amount / 2);
    }

    private static boolean isConfiguredExternalFood(Player player, ItemStack stack) {
        return configuredExternalFoodData(player, stack).isPresent();
    }

    private static Optional<StardewItemData> configuredExternalFoodData(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || stack.getItem() instanceof IStardewItem
                || !isStardewDimension(player)) {
            return Optional.empty();
        }
        if (stack.getFoodProperties(player) == null) {
            return Optional.empty();
        }
        return StardewItemDataApi.resolve(stack)
                .filter(ExternalStardewFoodService::isFoodMetadata);
    }

    private static boolean isStardewDimension(Player player) {
        return ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())
                || ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension());
    }

    private static void applyStardewEffects(ServerPlayer player, int energy, int health) {
        if (health != 0) {
            int current = PlayerStardewDataAPI.getHealth(player);
            int maximum = PlayerStardewDataAPI.getMaxHealth(player);
            PlayerStardewDataAPI.setHealth(player, Math.max(0, Math.min(maximum, current + health)));
        }
        if (energy > 0) {
            PlayerStardewDataAPI.restoreEnergy(player, energy);
        } else if (energy < 0) {
            PlayerStardewDataAPI.consumeEnergy(player, -energy);
        }
    }
}
