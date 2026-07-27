package com.stardew.craft.shop;

import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.requirement.StardewRequirements;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sewer.SewerStoryFlags;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative, read-only evaluation of whether a declared shop row should be
 * listed for one player. Shop filtering and addon-facing diagnostics must both
 * use this service so their answers cannot drift apart.
 */
public final class ShopEntryAvailabilityService {
    private ShopEntryAvailabilityService() {
    }

    public static StardewRequirementReport evaluate(
            ServerPlayer player,
            String shopId,
            ShopItemEntry entry
    ) {
        StardewTimeManager time = StardewTimeManager.get();
        PlayerStardewData data =
                PlayerDataManager.getPlayerData(player);
        List<StardewRequirement> requirements =
                new ArrayList<>();

        if (hasDateConstraint(entry)) {
            requirements.add(requirement(
                    StardewRequirementTypes.SHOP_DATE_AVAILABLE,
                    entry.isAvailableOnDate(
                            time.getCurrentSeason(),
                            time.getCurrentYear(),
                            time.getCurrentDay()),
                    Component.translatable(
                            "stardewcraft.requirement.shop.date",
                            time.getCurrentYear(),
                            time.getCurrentSeason(),
                            time.getCurrentDay())));
        }

        requirements.addAll(StardewRequirements.evaluateAll(
                StardewConditionContext.forPlayer(player),
                entry.availableWhen()).requirements());

        if (entry.minMineLevel() > 0) {
            int reached = player.server.getLevel(
                            ModMiningDimensions.STARDEW_MINING) == null
                    ? 0
                    : MiningDataManager.getPlayerData(player)
                            .getMaxFloorReached();
            requirements.add(requirement(
                    StardewRequirementTypes.SHOP_MINE_LEVEL,
                    reached >= entry.minMineLevel(),
                    Component.translatable(
                            "stardewcraft.requirement.shop.mine_level",
                            entry.minMineLevel(), reached)));
        }

        if (entry.mailFlag() != null
                && !entry.mailFlag().isBlank()) {
            requirements.add(requirement(
                    StardewRequirementTypes.SHOP_MAIL_FLAG,
                    data.hasMailFlag(entry.mailFlag()),
                    Component.translatable(
                            "stardewcraft.requirement.shop.mail_flag",
                            entry.mailFlag())));
        }

        if (entry.itemId().startsWith("recipe:")) {
            String recipeId =
                    SaloonService.extractRecipeId(entry.itemId());
            requirements.add(requirement(
                    StardewRequirementTypes.SHOP_PRODUCT_UNOWNED,
                    !data.isRecipeUnlocked(recipeId),
                    Component.translatable(
                            "stardewcraft.requirement.shop.product_unowned",
                            entry.itemId())));
        }

        if (isOneTimeProduct(shopId, entry.itemId())) {
            requirements.add(requirement(
                    StardewRequirementTypes.SHOP_PRODUCT_UNOWNED,
                    oneTimeProductAvailable(
                            shopId, entry.itemId(), data),
                    Component.translatable(
                            "stardewcraft.requirement.shop.product_unowned",
                            entry.itemId())));
        }

        if (shopId.startsWith(
                "Festival_NightMarket_MagicBoat_")
                && ("stardewcraft:scarecrow_7".equals(
                            entry.itemId())
                    || "stardewcraft:scarecrow_8".equals(
                            entry.itemId()))) {
            requirements.add(requirement(
                    StardewRequirementTypes
                            .SHOP_MUSEUM_PROGRESS,
                    ShopRegistry.meetsNightMarketMuseumCondition(
                            player, entry.itemId()),
                    Component.translatable(
                            "stardewcraft.requirement.shop.museum_progress",
                            entry.itemId())));
        }

        if ("JojaMart".equals(shopId)
                && "stardewcraft:auto_petter".equals(
                        entry.itemId())) {
            boolean eligible = CCStoryFlags.isJojaMember(player)
                    && CCStoryFlags.hasFlag(
                            player, CCStoryFlags.CC_IS_COMPLETE);
            requirements.add(requirement(
                    StardewRequirementTypes
                            .SHOP_STORY_ELIGIBLE,
                    eligible,
                    Component.translatable(
                            "stardewcraft.requirement.shop.story",
                            entry.itemId())));
        }

        return new StardewRequirementReport(requirements);
    }

    private static boolean hasDateConstraint(
            ShopItemEntry entry
    ) {
        return !entry.seasons().isEmpty()
                || entry.minYear() > 1
                || entry.dayOfWeek() >= 0
                || entry.dayOfMonthParity() != 0;
    }

    private static boolean isOneTimeProduct(
            String shopId,
            String itemId
    ) {
        return ("ShadowShop".equals(shopId)
                        && ("stardewcraft:stardrop".equals(itemId)
                            || "stardewcraft:warp_wand".equals(
                                    itemId)))
                || (FairFestivalService.STAR_TOKEN_SHOP_ID
                            .equals(shopId)
                        && "stardewcraft:stardrop".equals(
                                itemId));
    }

    private static boolean oneTimeProductAvailable(
            String shopId,
            String itemId,
            PlayerStardewData data
    ) {
        if ("ShadowShop".equals(shopId)
                && "stardewcraft:stardrop".equals(itemId)) {
            return !data.hasMailFlag(
                    SewerStoryFlags.SEWER_STARDROP_PURCHASED);
        }
        if ("ShadowShop".equals(shopId)
                && "stardewcraft:warp_wand".equals(itemId)) {
            return !data.hasMailFlag(
                    SewerStoryFlags.RETURN_SCEPTER_PURCHASED);
        }
        if (FairFestivalService.STAR_TOKEN_SHOP_ID
                        .equals(shopId)
                && "stardewcraft:stardrop".equals(itemId)) {
            return !data.hasMailFlag(
                    FairFestivalService.FAIR_STARDROP_FLAG);
        }
        return true;
    }

    private static StardewRequirement requirement(
            ResourceLocation type,
            boolean satisfied,
            Component description
    ) {
        return new StardewRequirement(
                type,
                satisfied
                        ? StardewRequirement.State.SATISFIED
                        : StardewRequirement.State.UNSATISFIED,
                description,
                true);
    }
}
