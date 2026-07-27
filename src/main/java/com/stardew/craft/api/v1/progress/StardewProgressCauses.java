package com.stardew.craft.api.v1.progress;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

/** Built-in reason IDs used by committed progress transitions. */
public final class StardewProgressCauses {
    public static final ResourceLocation ACCEPT = id("accept");
    public static final ResourceLocation OBJECTIVE_EVENT = id("objective_event");
    public static final ResourceLocation REWARD_CLAIM = id("reward_claim");
    public static final ResourceLocation CANCEL = id("cancel");
    public static final ResourceLocation DAY_TICK = id("day_tick");
    public static final ResourceLocation MAIL_QUEUE = id("mail_queue");
    public static final ResourceLocation MAIL_DELIVERY = id("mail_delivery");
    public static final ResourceLocation MAIL_READ = id("mail_read");
    public static final ResourceLocation BUNDLE_DEPOSIT = id("bundle_deposit");
    public static final ResourceLocation BUNDLE_PURCHASE = id("bundle_purchase");
    public static final ResourceLocation MUSEUM_DONATION = id("museum_donation");
    public static final ResourceLocation FESTIVAL_SESSION = id("festival_session");
    public static final ResourceLocation FESTIVAL_ACTIVITY = id("festival_activity");
    public static final ResourceLocation FESTIVAL_REWARD = id("festival_reward");

    private StardewProgressCauses() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "progress/" + path);
    }
}
