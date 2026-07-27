package com.stardew.craft.api.v1.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalAccess;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalType;
import com.stardew.craft.festival.FestivalWorldData;
import com.stardew.craft.shop.ShopDataLoader;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.shop.ShopService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Read-only festival shop directory and authoritative server open action.
 *
 * <p>The service accepts canonical IDs while retaining the runtime shop ID
 * needed by existing saves and purchase packets.
 */
public final class StardewFestivalShops {
    private StardewFestivalShops() {
    }

    public static List<StardewFestivalShopSnapshot> list(
            ResourceLocation festivalId
    ) {
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        if (definition == null) {
            return List.of();
        }
        return definition.shopIds().stream()
                .filter(runtimeId -> runtimeId != null
                        && !runtimeId.isBlank())
                .map(runtimeId -> snapshot(definition, runtimeId))
                .distinct()
                .toList();
    }

    public static StardewFestivalShopOpenResult open(
            ServerPlayer player,
            ResourceLocation festivalId,
            ResourceLocation shopId
    ) {
        if (player == null || festivalId == null || shopId == null) {
            return result(
                    StardewFestivalShopOpenResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        FestivalDefinition definition = FestivalRegistry.get(festivalId)
                .orElse(null);
        if (definition == null) {
            return result(
                    StardewFestivalShopOpenResult.Status
                            .FESTIVAL_NOT_FOUND,
                    null);
        }
        StardewFestivalShopSnapshot shop = list(festivalId).stream()
                .filter(candidate -> candidate.shopId().equals(shopId))
                .findFirst()
                .orElse(null);
        if (shop == null) {
            return result(
                    StardewFestivalShopOpenResult.Status.SHOP_NOT_LISTED,
                    null);
        }
        FestivalSessionState session = FestivalWorldData
                .get(player.serverLevel())
                .getSession(definition.id())
                .orElse(null);
        if (!isOpen(session)) {
            return result(
                    StardewFestivalShopOpenResult.Status
                            .SESSION_NOT_OPEN,
                    shop);
        }
        if (!session.participants().contains(player.getUUID())) {
            if (definition.type() == FestivalType.ACTIVE) {
                return result(
                        StardewFestivalShopOpenResult.Status
                                .PARTICIPATION_REQUIRED,
                        shop);
            }
        }
        if (!StardewFestivalAccess.isAtFestivalLocation(
                player, definition)) {
            return result(
                    StardewFestivalShopOpenResult.Status.WRONG_LOCATION,
                    shop);
        }
        if (!session.participants().contains(player.getUUID())) {
            StardewFestivalParticipantResult joined =
                    StardewFestivalParticipants.join(player, festivalId);
            if (joined.status()
                    != StardewFestivalParticipantResult.Status.JOINED
                    && joined.status()
                    != StardewFestivalParticipantResult.Status
                            .ALREADY_PARTICIPATING) {
                return result(
                        StardewFestivalShopOpenResult.Status
                                .SESSION_NOT_OPEN,
                        shop);
            }
        }
        if (ShopRegistry.get(shop.runtimeShopId()) == null) {
            return result(
                    StardewFestivalShopOpenResult.Status.SHOP_NOT_FOUND,
                    shop);
        }
        return result(
                ShopService.open(player, shop.runtimeShopId())
                        ? StardewFestivalShopOpenResult.Status.OPENED
                        : StardewFestivalShopOpenResult.Status
                                .SHOP_NOT_FOUND,
                shop);
    }

    private static StardewFestivalShopSnapshot snapshot(
            FestivalDefinition festival,
            String runtimeId
    ) {
        ResourceLocation direct = ResourceLocation.tryParse(runtimeId);
        if (direct != null && runtimeId.indexOf(':') >= 0) {
            return new StardewFestivalShopSnapshot(direct, runtimeId);
        }
        ResourceLocation canonical = ShopDataLoader.snapshot()
                .definitions().entrySet().stream()
                .filter(entry -> entry.getValue().legacyId()
                        .equals(runtimeId))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> fallbackId(festival, runtimeId));
        return new StardewFestivalShopSnapshot(canonical, runtimeId);
    }

    private static ResourceLocation fallbackId(
            FestivalDefinition festival,
            String runtimeId
    ) {
        String path = runtimeId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]+", "_");
        ResourceLocation id = ResourceLocation.tryBuild(
                festival.resourceId().getNamespace(), path);
        return id != null ? id : ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "unknown_shop");
    }

    private static boolean isOpen(FestivalSessionState session) {
        return session != null
                && (session.phase() == FestivalSessionPhase.OPEN
                || session.phase() == FestivalSessionPhase.MAIN_EVENT);
    }

    private static StardewFestivalShopOpenResult result(
            StardewFestivalShopOpenResult.Status status,
            StardewFestivalShopSnapshot shop
    ) {
        return new StardewFestivalShopOpenResult(
                status, Optional.ofNullable(shop));
    }
}
