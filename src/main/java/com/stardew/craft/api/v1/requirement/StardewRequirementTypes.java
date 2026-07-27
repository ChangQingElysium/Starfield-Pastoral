package com.stardew.craft.api.v1.requirement;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable identities for built-in non-condition requirements.
 *
 * <p>These identify authoritative facts in a report; they are not operation
 * permissions by themselves.
 */
public final class StardewRequirementTypes {
    public static final ResourceLocation FESTIVAL_EXISTS =
            id("festival/exists");
    public static final ResourceLocation FESTIVAL_SESSION_OPEN =
            id("festival/session_open");
    public static final ResourceLocation FESTIVAL_PARTICIPATING =
            id("festival/participating");
    public static final ResourceLocation FESTIVAL_REWARD_UNCLAIMED =
            id("festival/reward_unclaimed");
    public static final ResourceLocation COST_VALID =
            id("cost/valid");
    public static final ResourceLocation COST_CURRENCY =
            id("cost/currency");
    public static final ResourceLocation COST_ITEM =
            id("cost/item");
    public static final ResourceLocation SHOP_ENTRY_LISTED =
            id("shop/entry_listed");
    public static final ResourceLocation SHOP_DATE_AVAILABLE =
            id("shop/date_available");
    public static final ResourceLocation SHOP_MINE_LEVEL =
            id("shop/mine_level");
    public static final ResourceLocation SHOP_MAIL_FLAG =
            id("shop/mail_flag");
    public static final ResourceLocation SHOP_PRODUCT_UNOWNED =
            id("shop/product_unowned");
    public static final ResourceLocation SHOP_MUSEUM_PROGRESS =
            id("shop/museum_progress");
    public static final ResourceLocation SHOP_STORY_ELIGIBLE =
            id("shop/story_eligible");
    public static final ResourceLocation SHOP_PRODUCT_ACCEPTED =
            id("shop/product_accepted");
    public static final ResourceLocation SHOP_STOCK_AVAILABLE =
            id("shop/stock_available");
    public static final ResourceLocation PROGRESS_ENTRY_EXISTS =
            id("progress/entry_exists");
    public static final ResourceLocation
            PROGRESS_DEFINITION_AVAILABLE =
            id("progress/definition_available");
    public static final ResourceLocation PROGRESS_ACCEPT_AVAILABLE =
            id("progress/accept_available");
    public static final ResourceLocation PROGRESS_REWARD_CLAIMABLE =
            id("progress/reward_claimable");
    public static final ResourceLocation PROGRESS_ACCESS_UNLOCKED =
            id("progress/access_unlocked");
    public static final ResourceLocation
            PROGRESS_ACCEPTANCE_SLOT_AVAILABLE =
            id("progress/acceptance_slot_available");

    private StardewRequirementTypes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
