package com.stardew.craft.api.v1.communitycenter;

import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import com.stardew.craft.communitycenter.network.BundleClaimRewardPayload;
import com.stardew.craft.communitycenter.network.BundleSyncPayload;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterRewardRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Server-authoritative Community Center operations for addon payloads and core networking.
 *
 * <p>Every call re-reads the player's active menu and authoritative inventory/progress state.
 */
public final class StardewCommunityCenterActions {
    private StardewCommunityCenterActions() {
    }

    public static Result deposit(ServerPlayer player, int bundleId, int ingredientSlot) {
        Objects.requireNonNull(player, "player");
        if (!(player.containerMenu instanceof BundleMenu menu)) {
            return Result.NO_ACTIVE_BUNDLE_MENU;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()
                || !menu.tryDeposit(player, bundleId, ingredientSlot, carried)) {
            return Result.REJECTED;
        }
        player.containerMenu.broadcastChanges();
        BundleSyncPayload.sendFullSync(player);
        return Result.SUCCESS;
    }

    public static Result partialDeposit(
            ServerPlayer player,
            int bundleId,
            int ingredientSlot,
            int amount
    ) {
        Objects.requireNonNull(player, "player");
        if (!(player.containerMenu instanceof BundleMenu menu)) {
            return Result.NO_ACTIVE_BUNDLE_MENU;
        }
        ItemStack carried = menu.getCarried();
        if (amount <= 0 || carried.isEmpty()
                || !menu.handlePartialDeposit(
                        player, bundleId, ingredientSlot, amount, carried)) {
            return Result.REJECTED;
        }
        player.containerMenu.broadcastChanges();
        return Result.SUCCESS;
    }

    public static Result purchaseVault(ServerPlayer player, int bundleId) {
        Objects.requireNonNull(player, "player");
        if (!(player.containerMenu instanceof BundleMenu menu)) {
            return Result.NO_ACTIVE_BUNDLE_MENU;
        }
        if (!menu.tryPurchaseVault(player, bundleId)) {
            return Result.REJECTED;
        }
        player.containerMenu.broadcastChanges();
        BundleSyncPayload.sendFullSync(player);
        return Result.SUCCESS;
    }

    public static Result claimReward(ServerPlayer player, int bundleId) {
        Objects.requireNonNull(player, "player");
        if (!(player.containerMenu instanceof BundleMenu)) {
            return Result.NO_ACTIVE_BUNDLE_MENU;
        }
        CommunityCenterSavedData data = CommunityCenterSavedData.get(player.serverLevel());
        if (!data.isRewardAvailable(player.getUUID(), bundleId)) {
            return Result.REJECTED;
        }
        BundleDefinition definition =
                StardewCommunityCenterVariantRegistry.bundle(player.getUUID(), bundleId);
        if (definition == null) {
            return Result.REJECTED;
        }

        ItemStack reward =
                BundleClaimRewardPayload.parseRewardString(definition.rewardString());
        reward = StardewCommunityCenterRewardRegistry.resolve(
                player, bundleId, definition.rewardString(), reward);
        if (!reward.isEmpty() && !player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        data.setRewardAvailable(player.getUUID(), bundleId, false);
        BundleSyncPayload.sendFullSync(player);
        return Result.SUCCESS;
    }

    public enum Result {
        SUCCESS,
        NO_ACTIVE_BUNDLE_MENU,
        REJECTED
    }
}
