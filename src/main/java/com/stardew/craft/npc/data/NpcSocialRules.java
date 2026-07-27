package com.stardew.craft.npc.data;

import com.stardew.craft.api.v1.internal.npc.StardewNpcSocialRuleRegistry;
import com.stardew.craft.api.v1.internal.npc.StardewNpcProfileRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcFriendshipSnapshot;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.npc.StardewNpcProfile;
import com.stardew.craft.api.v1.npc.StardewNpcSocialContext;
import com.stardew.craft.api.v1.npc.StardewNpcSocialRules;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

/** Vanilla-derived NPC social/gift eligibility rules from Data/Characters and NPC.cs. */
public final class NpcSocialRules {
    private enum SocialTab {
        ALWAYS_SHOWN,
        HIDDEN_UNTIL_MET,
        UNKNOWN_UNTIL_MET,
        HIDDEN_ALWAYS
    }

    private static final Set<String> CAN_SOCIALIZE_FALSE = Set.of(
        "gunther",
        "marlon",
        "morris",
        "joja_cashier",
        "governor",
        "henchman"
    );

    private static final Set<String> SOCIAL_TAB_ALWAYS_SHOWN = Set.of(
        "lewis"
    );

    private static final Set<String> SOCIAL_TAB_HIDDEN_UNTIL_MET = Set.of(
        "krobus",
        "dwarf"
    );

    private static final Set<String> SOCIAL_TAB_HIDDEN_ALWAYS = Set.of(
        "gunther",
        "morris",
        "joja_cashier",
        "governor",
        "henchman"
    );

    private static final Set<String> INTRODUCTIONS_EXCLUDED = Set.of(
        "gunther",
        "marlon",
        "wizard",
        "dwarf",
        "krobus",
        "sandy",
        "morris",
        "joja_cashier",
        "governor",
        "henchman"
    );

    private NpcSocialRules() {
    }

    public static boolean canSocialize(String npcId) {
        String key = normalize(npcId);
        boolean proposed = !key.isEmpty() && !CAN_SOCIALIZE_FALSE.contains(key);
        return evaluate(key, null, null, null,
                StardewNpcSocialRules.Rule.CAN_SOCIALIZE, proposed);
    }

    public static boolean canSocialize(String npcId, ServerPlayer player) {
        String key = normalize(npcId);
        boolean proposed = !key.isEmpty() && !CAN_SOCIALIZE_FALSE.contains(key);
        if (proposed && "sandy".equals(key)) {
            proposed = player != null
                    && PlayerStardewDataAPI.getData(player).hasMailFlag(CCStoryFlags.CC_VAULT);
        }
        return evaluate(key, player, null, null,
                StardewNpcSocialRules.Rule.CAN_SOCIALIZE, proposed);
    }

    public static boolean canReceiveGifts(String npcId) {
        String key = normalize(npcId);
        boolean proposed = canSocialize(key) && NpcDataRegistry.tastes().containsKey(key);
        return evaluate(key, null, null, null,
                StardewNpcSocialRules.Rule.CAN_RECEIVE_GIFTS, proposed);
    }

    public static boolean canReceiveGifts(String npcId, ServerPlayer player) {
        String key = normalize(npcId);
        boolean proposed = canSocialize(key, player)
                && NpcDataRegistry.tastes().containsKey(key);
        return evaluate(key, player, null, null,
                StardewNpcSocialRules.Rule.CAN_RECEIVE_GIFTS, proposed);
    }

    public static boolean shouldShowOnSocialPage(String npcId,
                                                 NpcCapabilityProfile profile,
                                                 NpcFriendshipDataManager.FriendshipState state,
                                                 ServerPlayer player) {
        String key = normalize(npcId);
        ResourceLocation normalizedId = StardewNpcInteractions.normalizeNpcId(key);
        StardewNpcProfile publicProfile = normalizedId == null
                ? null
                : StardewNpcProfileRegistry.publicProfile(normalizedId, profile);
        boolean proposed = publicProfile != null
                && publicProfile.implemented()
                && !key.isEmpty()
                && canSocialize(key, player);
        if (proposed) {
            proposed = switch (socialTab(key)) {
                case HIDDEN_ALWAYS -> false;
                case HIDDEN_UNTIL_MET -> state != null;
                case ALWAYS_SHOWN, UNKNOWN_UNTIL_MET -> true;
            };
        }
        return evaluate(key, player, profile, state,
                StardewNpcSocialRules.Rule.SHOW_ON_SOCIAL_PAGE, proposed);
    }

    public static boolean shouldCreateFriendshipForSocialPage(String npcId) {
        String key = normalize(npcId);
        boolean proposed = socialTab(key) == SocialTab.ALWAYS_SHOWN;
        return evaluate(key, null, null, null,
                StardewNpcSocialRules.Rule.CREATE_FRIENDSHIP_FOR_SOCIAL_PAGE, proposed);
    }

    public static boolean isMet(NpcFriendshipDataManager.FriendshipState state) {
        return state != null;
    }

    public static boolean isIntroductionsNpc(String npcId, NpcCapabilityProfile profile) {
        String key = normalize(npcId);
        ResourceLocation normalizedId = StardewNpcInteractions.normalizeNpcId(key);
        StardewNpcProfile publicProfile = normalizedId == null
                ? null
                : StardewNpcProfileRegistry.publicProfile(normalizedId, profile);
        boolean proposed = publicProfile != null
                && publicProfile.implemented()
                && publicProfile.pathingEnabled()
                && canSocialize(key)
                && !INTRODUCTIONS_EXCLUDED.contains(key);
        return evaluate(key, null, profile, null,
                StardewNpcSocialRules.Rule.INCLUDE_IN_INTRODUCTIONS, proposed);
    }

    private static SocialTab socialTab(String npcId) {
        if (SOCIAL_TAB_HIDDEN_ALWAYS.contains(npcId)) {
            return SocialTab.HIDDEN_ALWAYS;
        }
        if (SOCIAL_TAB_HIDDEN_UNTIL_MET.contains(npcId)) {
            return SocialTab.HIDDEN_UNTIL_MET;
        }
        if (SOCIAL_TAB_ALWAYS_SHOWN.contains(npcId)) {
            return SocialTab.ALWAYS_SHOWN;
        }
        return SocialTab.UNKNOWN_UNTIL_MET;
    }

    private static String normalize(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean evaluate(
            String npcId,
            ServerPlayer player,
            NpcCapabilityProfile profile,
            NpcFriendshipDataManager.FriendshipState state,
            StardewNpcSocialRules.Rule rule,
            boolean proposed
    ) {
        ResourceLocation normalizedId = StardewNpcInteractions.normalizeNpcId(npcId);
        if (normalizedId == null) {
            return proposed;
        }
        StardewNpcProfile publicProfile =
                StardewNpcProfileRegistry.publicProfile(normalizedId, profile);
        StardewNpcFriendshipSnapshot publicState = state == null ? null
                : new StardewNpcFriendshipSnapshot(
                        Math.max(0, state.points()),
                        Math.max(0, state.giftsThisWeek()),
                        state.lastGiftDayKey(),
                        state.lastGiftWeekKey(),
                        state.lastTalkDayKey(),
                        state.firstMetDayKey(),
                        state.dialogueDayKey(),
                        Math.max(0, state.dialogueInteractionsToday()));
        return StardewNpcSocialRuleRegistry.evaluate(
                new StardewNpcSocialContext(
                        normalizedId, player, publicProfile, publicState),
                rule,
                proposed);
    }
}
