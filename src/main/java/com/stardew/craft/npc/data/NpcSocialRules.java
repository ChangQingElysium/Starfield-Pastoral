package com.stardew.craft.npc.data;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
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
        return !key.isEmpty() && !CAN_SOCIALIZE_FALSE.contains(key);
    }

    public static boolean canSocialize(String npcId, ServerPlayer player) {
        String key = normalize(npcId);
        if (!canSocialize(key)) {
            return false;
        }
        if ("sandy".equals(key)) {
            return player != null && PlayerStardewDataAPI.getData(player).hasMailFlag(CCStoryFlags.CC_VAULT);
        }
        return true;
    }

    public static boolean canReceiveGifts(String npcId) {
        String key = normalize(npcId);
        return canSocialize(key) && NpcDataRegistry.tastes().containsKey(key);
    }

    public static boolean canReceiveGifts(String npcId, ServerPlayer player) {
        String key = normalize(npcId);
        return canSocialize(key, player) && NpcDataRegistry.tastes().containsKey(key);
    }

    public static boolean shouldShowOnSocialPage(String npcId,
                                                 NpcCapabilityProfile profile,
                                                 NpcFriendshipDataManager.FriendshipState state,
                                                 ServerPlayer player) {
        if (profile == null || !profile.implemented()) {
            return false;
        }
        String key = normalize(npcId);
        if (key.isEmpty() || !canSocialize(key, player)) {
            return false;
        }
        return switch (socialTab(key)) {
            case HIDDEN_ALWAYS -> false;
            case HIDDEN_UNTIL_MET -> state != null;
            case ALWAYS_SHOWN, UNKNOWN_UNTIL_MET -> true;
        };
    }

    public static boolean shouldCreateFriendshipForSocialPage(String npcId) {
        return socialTab(normalize(npcId)) == SocialTab.ALWAYS_SHOWN;
    }

    public static boolean isMet(NpcFriendshipDataManager.FriendshipState state) {
        return state != null;
    }

    public static boolean isIntroductionsNpc(String npcId, NpcCapabilityProfile profile) {
        String key = normalize(npcId);
        return profile != null
            && profile.implemented()
            && profile.canRunPathing()
            && canSocialize(key)
            && !INTRODUCTIONS_EXCLUDED.contains(key);
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
}
