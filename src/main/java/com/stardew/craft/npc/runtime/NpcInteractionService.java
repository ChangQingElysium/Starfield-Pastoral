package com.stardew.craft.npc.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.emote.EmoteCatalog;
import com.stardew.craft.emote.EmoteType;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcSocialRules;
import com.stardew.craft.network.payload.EmoteBroadcastPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class NpcInteractionService {
    /** Friendship points per heart level (Stardew Valley canonical value). */
    static final int POINTS_PER_HEART = 250;
    /** Number of days per season. */
    static final int DAYS_PER_SEASON = 28;
    /** Number of seasons per year. */
    static final int SEASONS_PER_YEAR = 4;
    /** Total days per year (4 seasons × 28 days). */
    static final int DAYS_PER_YEAR = SEASONS_PER_YEAR * DAYS_PER_SEASON;

    private static final String[] WEEKDAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String STARDROP_TEA_ID = "stardewcraft:stardrop_tea";
    private static final String GOLDEN_PUMPKIN_ID = "stardewcraft:golden_pumpkin";
    private static final String MAGIC_ROCK_CANDY_ID = "stardewcraft:magic_rock_candy";
    private static final String VANILLA_OBJECTS_RESOURCE = "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static final Set<String> NON_GIFTABLE_TYPE_KEYS = Set.of(
        "stardewcraft.type.tool",
        "stardewcraft.type.weapon",
        "stardewcraft.type.weapon.sword",
        "stardewcraft.type.weapon.dagger",
        "stardewcraft.type.weapon.club",
        "stardewcraft.type.weapon.slingshot",
        "stardewcraft.type.ring",
        "stardewcraft.type.boots",
        "stardewcraft.type.furniture",
        "stardewcraft.type.special_furniture",
        "stardewcraft.type.furniture_painting",
        "stardewcraft.type.utility",
        "stardewcraft.type.scarecrow",
        "stardewcraft.type.special"
    );
    private static final Map<UUID, String> ACTIVE_DIALOGUE_NPC_BY_PLAYER = new HashMap<>();
    private static final Map<String, Integer> ACTIVE_DIALOGUE_LOCK_COUNTS = new HashMap<>();
    private static volatile Map<String, Boolean> vanillaObjectGiftabilityByName;

    private NpcInteractionService() {
    }

    /**
     * Mirrors the mutation pipeline's visible outcome without opening UI,
     * consuming held items, or advancing friendship/quest state.
     */
    public static InteractionHintProbe probeInteractionHint(
            ServerPlayer player,
            StardewNpcEntity npc
    ) {
        if (player == null || npc == null
                || npc.getNpcId() == null || npc.getNpcId().isBlank()) {
            return InteractionHintProbe.NONE;
        }
        String npcId = npc.getNpcId().trim().toLowerCase(Locale.ROOT);
        boolean talkedToday = hasTalkedToday(player, npcId);
        ItemStack held = player.getMainHandItem();

        if ("henchman".equals(npcId)) {
            boolean gift =
                    com.stardew.craft.world.HenchmanService
                            .canOfferVoidMayonnaise(player, held);
            return new InteractionHintProbe(
                    gift ? InteractionHintKind.GIFT
                            : InteractionHintKind.TALK,
                    !gift && talkedToday);
        }
        if ("joja_cashier".equals(npcId)
                || "morris".equals(npcId)
                || com.stardew.craft.casino.CasinoAccessService
                        .BOUNCER_NPC_ID.equals(npcId)
                || com.stardew.craft.casino.CasinoContentService
                        .MR_QI_NPC_ID.equals(npcId)
                || com.stardew.craft.casino.CasinoContentService
                        .STATUE_VENDOR_NPC_ID.equals(npcId)) {
            return new InteractionHintProbe(
                    InteractionHintKind.TALK, talkedToday);
        }

        var activeFestival =
                com.stardew.craft.festival.ActiveFestivalHandlers
                        .getParticipating(player).orElse(null);
        if (activeFestival != null) {
            boolean secretGift =
                    com.stardew.craft.festival.WinterStarFestivalService
                            .FESTIVAL_ID.equalsIgnoreCase(
                                    activeFestival.festivalId())
                    && com.stardew.craft.festival.WinterStarFestivalService
                            .canPromptSecretGift(player, npcId);
            return secretGift
                    ? new InteractionHintProbe(
                            InteractionHintKind.GIFT, false)
                    : new InteractionHintProbe(
                            InteractionHintKind.TALK, talkedToday);
        }

        if (com.stardew.craft.festival.desert
                        .DesertFestivalVendorService
                        .shouldUseVendorDialogue(player, npcId)
                || "willy".equals(npcId)
                        && (com.stardew.craft.festival.desert
                                        .DesertFestivalWillyFishingService
                                        .canCompleteFishingReport(
                                                player, npcId)
                                || com.stardew.craft.festival.squid
                                        .SquidFestService
                                        .isPlayerAtBooth(player)
                                || com.stardew.craft.festival.trout
                                        .TroutDerbyService
                                        .isPlayerAtBooth(player))) {
            return new InteractionHintProbe(
                    InteractionHintKind.TALK, talkedToday);
        }

        if (!held.isEmpty()
                && (com.stardew.craft.specialorder.SpecialOrderManager
                            .canDeliverToNpc(player, npcId, held)
                    || findMatchingDeliveryQuest(player, npcId, held)
                            != null)) {
            return new InteractionHintProbe(
                    InteractionHintKind.GIFT, false);
        }

        if (isShopInteractionContext(player, npcId)) {
            return new InteractionHintProbe(
                    InteractionHintKind.TALK, talkedToday);
        }

        if (!NpcSocialRules.canSocialize(npcId, player)) {
            return InteractionHintProbe.NONE;
        }

        if (!held.isEmpty()
                && NpcSocialRules.canReceiveGifts(npcId, player)
                && canBeGivenAsGift(held)) {
            return new InteractionHintProbe(
                    InteractionHintKind.GIFT, false);
        }

        return new InteractionHintProbe(
                InteractionHintKind.TALK, talkedToday);
    }

    private static boolean hasTalkedToday(
            ServerPlayer player,
            String npcId
    ) {
        NpcFriendshipDataManager.FriendshipState state =
                NpcFriendshipDataManager.get(player.serverLevel())
                        .get(player.getUUID(), npcId);
        return state != null
                && state.lastTalkDayKey() == currentDayKey();
    }

    private static boolean isShopInteractionContext(
            ServerPlayer player,
            String npcId
    ) {
        if (com.stardew.craft.shop.ShopInteractionBindings
                .canOpenNpc(player, npcId)) {
            return true;
        }
        return switch (npcId) {
            case "clint" ->
                    com.stardew.craft.shop.BlacksmithService
                            .isPlayerAtCounter(player);
            case "harvey" ->
                    com.stardew.craft.shop.ClinicService
                            .isPlayerAtCounter(player);
            case "gus" ->
                    com.stardew.craft.shop.SaloonService
                            .isPlayerAtCounter(player);
            case "pierre" ->
                    com.stardew.craft.shop.PierreService
                            .isPlayerAtCounter(player);
            case "marnie" ->
                    com.stardew.craft.shop.MarnieService
                            .isPlayerAtCounter(player);
            case "willy" ->
                    com.stardew.craft.shop.WillyService
                            .isPlayerAtCounter(player);
            case "gunther" ->
                    com.stardew.craft.shop.GuntherService
                            .isPlayerAtCounter(player);
            case "marlon" ->
                    com.stardew.craft.shop.MarlonService
                            .isPlayerAtCounter(player)
                    || com.stardew.craft.shop.MarlonService
                            .isPlayerAtDesertFestivalBooth(player);
            case "robin" ->
                    com.stardew.craft.shop.RobinService
                            .isPlayerAtCounter(player);
            case "sandy" ->
                    com.stardew.craft.shop.SandyService
                            .isPlayerAtCounter(player);
            default -> false;
        };
    }

    public enum InteractionHintKind {
        GIFT,
        TALK
    }

    public record InteractionHintProbe(
            InteractionHintKind kind,
            boolean done
    ) {
        private static final InteractionHintProbe NONE =
                new InteractionHintProbe(null, false);

        public boolean visible() {
            return kind != null;
        }
    }

    public static boolean isDialogueMovementLocked(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        return ACTIVE_DIALOGUE_LOCK_COUNTS.getOrDefault(npcId.trim().toLowerCase(Locale.ROOT), 0) > 0;
    }

    public static void handleDialogueClosed(ServerPlayer player, String npcId) {
        if (player == null) {
            return;
        }
        endDialogueSession(player.getUUID(), npcId);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        endDialogueSession(player.getUUID(), null);
    }

    public static void onServerStopped() {
        ACTIVE_DIALOGUE_NPC_BY_PLAYER.clear();
        ACTIVE_DIALOGUE_LOCK_COUNTS.clear();
    }

    private static void beginDialogueSession(ServerPlayer player, String npcId) {
        if (player == null || npcId == null || npcId.isBlank()) {
            return;
        }

        UUID playerId = player.getUUID();
        String normalizedNpcId = npcId.trim().toLowerCase(Locale.ROOT);
        String previousNpcId = ACTIVE_DIALOGUE_NPC_BY_PLAYER.get(playerId);
        if (normalizedNpcId.equals(previousNpcId)) {
            return;
        }

        endDialogueSession(playerId, previousNpcId);
        ACTIVE_DIALOGUE_NPC_BY_PLAYER.put(playerId, normalizedNpcId);
        ACTIVE_DIALOGUE_LOCK_COUNTS.merge(normalizedNpcId, 1, Integer::sum);
    }

    private static void endDialogueSession(UUID playerId, String expectedNpcId) {
        if (playerId == null) {
            return;
        }

        String activeNpcId = ACTIVE_DIALOGUE_NPC_BY_PLAYER.get(playerId);
        if (activeNpcId == null) {
            return;
        }

        if (expectedNpcId != null && !expectedNpcId.isBlank()) {
            String normalizedExpectedNpcId = expectedNpcId.trim().toLowerCase(Locale.ROOT);
            if (!normalizedExpectedNpcId.equals(activeNpcId)) {
                return;
            }
        }

        ACTIVE_DIALOGUE_NPC_BY_PLAYER.remove(playerId);
        ACTIVE_DIALOGUE_LOCK_COUNTS.computeIfPresent(activeNpcId, (key, count) -> count <= 1 ? null : count - 1);
    }

    public static InteractionResult onInteract(net.minecraft.world.entity.player.Player player,
                                               StardewNpcEntity npc,
                                               InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        Level level = serverPlayer.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        String npcId = npc.getNpcId() == null ? "" : npc.getNpcId().trim().toLowerCase(Locale.ROOT);
        if (npcId.isBlank()) {
            return InteractionResult.PASS;
        }
        InteractionResult addonResult = com.stardew.craft.api.v1.npc.StardewNpcInteractions
                .dispatch(serverPlayer, npc, hand);
        if (addonResult != InteractionResult.PASS) {
            return addonResult;
        }
        // Joja-line NPCs: 不进入通用好感/打招呼流程。
        // 女收银员（joja_cashier）— 无论玩家站在哪里，右键直接打开 Joja 超市商店界面。
        if ("joja_cashier".equals(npcId)) {
            return com.stardew.craft.shop.JojaMartService.handleJojaInteraction(serverPlayer, npc);
        }
        // Morris — 对话 + 入会 + CD form 流程，全在 MorrisService 内部。
        if ("morris".equals(npcId)) {
            return com.stardew.craft.joja.MorrisService.handle(serverPlayer, npc);
        }
        if (com.stardew.craft.casino.CasinoAccessService.BOUNCER_NPC_ID.equals(npcId)) {
            return com.stardew.craft.casino.CasinoAccessService.interactBouncer(
                    serverPlayer, npc, hand);
        }
        if (com.stardew.craft.casino.CasinoContentService.MR_QI_NPC_ID.equals(npcId)) {
            return com.stardew.craft.casino.CasinoContentService.interactMrQi(serverPlayer, npc);
        }
        if (com.stardew.craft.casino.CasinoContentService.STATUE_VENDOR_NPC_ID.equals(npcId)) {
            return com.stardew.craft.casino.CasinoContentService.interactStatueVendor(serverPlayer, npc);
        }
        ItemStack held = serverPlayer.getItemInHand(hand);
        if ("henchman".equals(npcId)) {
            return com.stardew.craft.world.HenchmanService.handleInteraction(serverPlayer, npc, hand);
        }

        DayContext dayContext = currentDayContext(serverLevel);

        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(serverLevel);
        NpcFriendshipDataManager.FriendshipState state = friendshipManager.getOrCreate(serverPlayer.getUUID(), npcId);
        state.normalizeGiftWeek(dayContext.weekKey());

        if (npcId.equals("pierre") && com.stardew.craft.festival.ActiveFestivalHandlers.tryOpenPierreFestivalShop(serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        com.stardew.craft.festival.ActiveFestivalHandler activeFestival = com.stardew.craft.festival.ActiveFestivalHandlers.getParticipating(serverPlayer).orElse(null);
        if (activeFestival != null) {
            if (isLewis(npcId) && activeFestival.tryStartMainEvent(serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (activeFestival.blocksNpcInteractionDuringMainEvent()) {
                return InteractionResult.SUCCESS;
            }
            if ("spring13".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleEggFestivalDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("spring24".equalsIgnoreCase(activeFestival.festivalId())) {
                if (com.stardew.craft.festival.FlowerDanceService.canAskNpcToDance(npcId)
                    && com.stardew.craft.festival.FlowerDanceService.hasFestivalDialogueSeen(serverPlayer, npcId)
                    && com.stardew.craft.festival.FlowerDanceService.isNpcDancePartnerTaken(npcId)) {
                    sendDialoguePacket(serverPlayer, npcId, flowerDanceNpcDialogueKey(npcId, "flowerdance_taken"), state.points());
                    return InteractionResult.SUCCESS;
                }
                if (com.stardew.craft.festival.FlowerDanceService.tryOpenNpcDanceInvite(serverPlayer, npcId)) {
                    return InteractionResult.SUCCESS;
                }
                if (tryHandleFlowerDanceDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                    return InteractionResult.SUCCESS;
                }
            }
            if ("summer11".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleLuauDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("summer28".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleMoonlightJelliesDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("fall16".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleFairDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("fall27".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleSpiritEveDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("winter8".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleFestivalOfIceDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            if ("winter25".equalsIgnoreCase(activeFestival.festivalId())
                && tryHandleWinterStarDialogue(serverPlayer, npc, npcId, state, dayContext, friendshipManager)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalVendorService.tryOpenVendorShop(serverPlayer, npcId)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return InteractionResult.SUCCESS;
        }

        if (com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.tryCompleteFishingReport(serverPlayer, npc, npcId)) {
            return InteractionResult.SUCCESS;
        }

        if (npcId.equals("willy") && com.stardew.craft.festival.squid.SquidFestService.isPlayerAtBooth(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.festival.squid.SquidFestService.handleWillyInteraction(serverPlayer, npc);
        }

        if (npcId.equals("willy") && com.stardew.craft.festival.trout.TroutDerbyService.isPlayerAtBooth(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.festival.trout.TroutDerbyService.handleWillyInteraction(serverPlayer, npc);
        }

        // ═══════════════════════════════════════════════════════════════
        // SDV parity: QUEST DELIVERY 最高优先级 —
        // 玩家拿着正好匹配「已接受的交付任务」的物品 + 右键 target NPC，
        // 直接弹出 "确定要把 X 交给 Y 来完成 Z 吗？" 对话框，
        // **优先于** 店铺柜台 / 送礼流程。不符合则走下面的正常逻辑。
        // ═══════════════════════════════════════════════════════════════
        if (!held.isEmpty()) {
            var specialOrderDelivery = com.stardew.craft.specialorder.SpecialOrderManager.recordNpcDelivery(serverPlayer, npcId, held);
            if (specialOrderDelivery.delivered()) {
                npc.facePlayerTemporarily(serverPlayer, 60, () -> {
                    if (!specialOrderDelivery.messageKey().isBlank()) {
                        sendDialoguePacket(serverPlayer, npcId, specialOrderDelivery.messageKey(), 0, false);
                    }
                });
                return InteractionResult.SUCCESS;
            }
            com.stardew.craft.quest.StardewQuest matchingQuest =
                findMatchingDeliveryQuest(serverPlayer, npcId, held);
            if (matchingQuest != null) {
                npc.facePlayerTemporarily(serverPlayer, 60, () -> {
                    String questTitleJson;
                    try {
                        questTitleJson = net.minecraft.network.chat.Component.Serializer.toJson(
                            matchingQuest.getTitleComponent(), serverLevel.registryAccess());
                    } catch (Exception e) {
                        questTitleJson = "\"" + matchingQuest.getTitleComponent().getString() + "\"";
                    }
                    PacketDistributor.sendToPlayer(serverPlayer,
                        new com.stardew.craft.network.payload.OpenQuestDeliveryConfirmPayload(
                            npcId,
                            matchingQuest.getId(),
                            held.getDescriptionId(),
                            npc.getDisplayName().getString(),
                            questTitleJson
                        ));
                });
                return InteractionResult.SUCCESS;
            }
        }

        // SDV parity: shop counter checks take priority over gift flow
        // (player holding an item at a shop counter should open the shop, not gift)

        // SDV parity: shop counter checks take priority over gift flow.
        // 柜台交互同时触发任务事件（SDV: 杀怪任务找 NPC 复命也可以在商店柜台完成）。
        if (com.stardew.craft.shop.ShopInteractionBindings.tryOpenNpc(serverPlayer, npcId)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return InteractionResult.SUCCESS;
        }
        if (npcId.equals("clint") && com.stardew.craft.shop.BlacksmithService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.BlacksmithService.handleBlacksmithInteraction(serverPlayer, npc);
        }
        if (npcId.equals("harvey") && com.stardew.craft.shop.ClinicService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.ClinicService.handleClinicInteraction(serverPlayer, npc);
        }
        if (npcId.equals("gus") && com.stardew.craft.shop.SaloonService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.SaloonService.handleSaloonInteraction(serverPlayer, npc);
        }
        if (npcId.equals("pierre") && com.stardew.craft.shop.PierreService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.PierreService.handlePierreInteraction(serverPlayer, npc);
        }
        if (npcId.equals("marnie") && com.stardew.craft.shop.MarnieService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.MarnieService.handleMarnieInteraction(serverPlayer, npc);
        }
        if (npcId.equals("willy") && com.stardew.craft.shop.WillyService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.WillyService.handleWillyInteraction(serverPlayer, npc);
        }
        if (npcId.equals("gunther") && com.stardew.craft.shop.GuntherService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.GuntherService.handleGuntherInteraction(serverPlayer, npc);
        }
        if (npcId.equals("marlon") && (com.stardew.craft.shop.MarlonService.isPlayerAtCounter(serverPlayer)
            || com.stardew.craft.shop.MarlonService.isPlayerAtDesertFestivalBooth(serverPlayer))) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.MarlonService.handleMarlonInteraction(serverPlayer, npc);
        }
        if (npcId.equals("robin") && com.stardew.craft.shop.RobinService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.RobinService.handleCarpenterInteraction(serverPlayer, npc);
        }
        if (npcId.equals("sandy") && com.stardew.craft.shop.SandyService.isPlayerAtCounter(serverPlayer)) {
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            return com.stardew.craft.shop.SandyService.handleSandyInteraction(serverPlayer, npc);
        }
        // Wizard tower hub: intercept wizard NPC for quest/teleport dialogue
        if (npcId.equals("wizard")) {
            if (com.stardew.craft.interior.WizardQuestHandler.handleWizardInteraction(serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
        }

        if (!NpcSocialRules.canSocialize(npcId, serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        boolean hasGiftableHeld =
                NpcSocialRules.canReceiveGifts(npcId, serverPlayer)
                        && canBeGivenAsGift(held);
        com.stardew.craft.api.v1.npc.StardewNpcGifts.Confirmation giftConfirmation =
                com.stardew.craft.api.v1.npc.StardewNpcGifts.Confirmation
                        .REQUIRE_CONFIRMATION;
        if (hasGiftableHeld) {
            ResourceLocation publicNpcId =
                    com.stardew.craft.api.v1.npc.StardewNpcInteractions
                            .normalizeNpcId(npcId);
            if (publicNpcId != null) {
                giftConfirmation =
                        com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry
                                .confirmation(
                                        new com.stardew.craft.api.v1.npc.StardewNpcGifts
                                                .OfferContext(
                                                serverPlayer,
                                                npc,
                                                publicNpcId,
                                                hand,
                                                held));
                if (giftConfirmation
                        == com.stardew.craft.api.v1.npc.StardewNpcGifts
                                .Confirmation.SKIP_GIFT) {
                    hasGiftableHeld = false;
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SDV parity: HEART EVENT CHECK — before gifts/dialogue, check
        // if this NPC has a pending interact_npc cutscene event.
        // If so, trigger it instead of opening normal dialogue.
        // ═══════════════════════════════════════════════════════════════
        {
            String pendingEvent = com.stardew.craft.cutscene.server.ServerPreconditionEvaluator
                    .findPendingNpcEvent(serverPlayer, npcId);
            boolean deferKrobusEventUntilAfterTalk = npcId.equals("krobus")
                    && !hasGiftableHeld
                    && state.lastTalkDayKey() != dayContext.dayKey();
            if (pendingEvent != null && !deferKrobusEventUntilAfterTalk) {
                com.stardew.craft.cutscene.server.ServerCutsceneTracker.startEvent(serverPlayer, pendingEvent);
                return InteractionResult.SUCCESS;
            }
        }

        // Gift flow: if player is holding an item, ask for confirmation before gifting
        // SDV parity: you CAN gift Dwarf without understanding, but friendship won't increase
        // and the response dialogue will be garbled (handled in receiveGift / response)
        if (hasGiftableHeld) {
            if (giftConfirmation
                    == com.stardew.craft.api.v1.npc.StardewNpcGifts
                            .Confirmation.GIVE_IMMEDIATELY) {
                handleConfirmedGift(serverPlayer, npcId);
                return InteractionResult.SUCCESS;
            }
            // NPC smoothly turns to face the player, then opens gift confirm screen
            String npcDisplayName = npc.getDisplayName().getString();
            String itemDisplayNameJson = serializeGiftItemDisplayName(held, serverLevel);
            npc.facePlayerTemporarily(serverPlayer, 60, () -> {
                PacketDistributor.sendToPlayer(serverPlayer,
                    new com.stardew.craft.network.payload.OpenGiftConfirmPayload(npcId, itemDisplayNameJson, npcDisplayName));
            });
            return InteractionResult.SUCCESS;
        }

        if (state.lastTalkDayKey() == dayContext.dayKey()) {
            if (npcId.equals("dwarf")
                    && !hasGiftableHeld
                    && com.stardew.craft.shop.DwarfService.canUnderstandDwarves(serverPlayer)) {
                InteractionResult dwarfResult = com.stardew.craft.shop.DwarfService.handleDwarfInteraction(serverPlayer, npc);
                if (dwarfResult == InteractionResult.SUCCESS) {
                    return InteractionResult.SUCCESS;
                }
            }
            syncFriendshipStatus(serverPlayer, npcId, state, dayContext);
            // 当天已对话过不再加好感，但仍要触发任务事件
            // （SDV: 杀完怪/做完条件后回来找 NPC 复命的场景）
            com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
            if (isLewis(npcId) && !hasGiftableHeld) {
                npc.facePlayerTemporarily(serverPlayer, 60, () -> LewisCivicService.openMenu(serverPlayer));
                return InteractionResult.SUCCESS;
            }
            if (npcId.equals("krobus") && !hasGiftableHeld) {
                return com.stardew.craft.shop.ShadowShopService.handleKrobusInteraction(serverPlayer, npc);
            }
            return InteractionResult.SUCCESS;
        }

        // NPC smoothly turns to face the player, then opens dialogue
        String dialogueText = loadCurrentDialogue(serverPlayer, npcId, state, dayContext);
        String scheduleDialogue = resolveScheduleDialogue(serverLevel, npcId);
        if (!scheduleDialogue.isBlank()) {
            dialogueText = scheduleDialogue;
        }
        String nightMarketDialogue = com.stardew.craft.festival.nightmarket.NightMarketNpcVisitService
            .resolveDialogueKey(npcId);
        if (!nightMarketDialogue.isBlank()) {
            dialogueText = nightMarketDialogue;
        }
        if (com.stardew.craft.festival.desert.DesertFestivalVendorService.shouldUseVendorDialogue(serverPlayer, npcId)) {
            dialogueText = "stardewcraft.festival.desertfestival.dialogue.vendor";
        }

        // SDV parity: Dwarf dialogue is garbled if player doesn't have translation guide
        boolean garbleDwarvish = npcId.equals("dwarf") && !com.stardew.craft.shop.DwarfService.canUnderstandDwarves(serverPlayer);
        if (!garbleDwarvish) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, serverPlayer);
            NpcFriendshipRewardService.applyEligibleRewards(serverPlayer, npcId, state.points());
            // Don't grant friendship for unintelligible conversation
        }
        friendshipManager.setDirty();
        syncFriendshipStatus(serverPlayer, npcId, state, dayContext);
        // Quest: NPC socialized (first conversation of the day)
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(serverPlayer, npcId);
        int points = state.points();
        final String finalDialogueText = dialogueText;
        final boolean finalGarble = garbleDwarvish;
        npc.facePlayerTemporarily(serverPlayer, 60, () -> {
            sendDialoguePacket(serverPlayer, npcId, finalDialogueText, points, finalGarble);
        });
        return InteractionResult.SUCCESS;
    }

    private static boolean tryHandleEggFestivalDialogue(ServerPlayer player,
                                                        StardewNpcEntity npc,
                                                        String npcId,
                                                        NpcFriendshipDataManager.FriendshipState state,
                                                        DayContext dayContext,
                                                        NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.EggFestivalNpcService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleFlowerDanceDialogue(ServerPlayer player,
                                                        StardewNpcEntity npc,
                                                        String npcId,
                                                        NpcFriendshipDataManager.FriendshipState state,
                                                        DayContext dayContext,
                                                        NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.FlowerDanceNpcService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> {
            markActiveFestivalDialogueSeen(player, npcId);
            sendDialoguePacket(player, npcId, dialogueText, points, false);
        });
        return true;
    }

    private static boolean tryHandleLuauDialogue(ServerPlayer player,
                                                 StardewNpcEntity npc,
                                                 String npcId,
                                                 NpcFriendshipDataManager.FriendshipState state,
                                                 DayContext dayContext,
                                                 NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.LuauFestivalService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleMoonlightJelliesDialogue(ServerPlayer player,
                                                             StardewNpcEntity npc,
                                                             String npcId,
                                                             NpcFriendshipDataManager.FriendshipState state,
                                                             DayContext dayContext,
                                                             NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.MoonlightJelliesFestivalService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleFairDialogue(ServerPlayer player,
                                                 StardewNpcEntity npc,
                                                 String npcId,
                                                 NpcFriendshipDataManager.FriendshipState state,
                                                 DayContext dayContext,
                                                 NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.FairFestivalService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleSpiritEveDialogue(ServerPlayer player,
                                                      StardewNpcEntity npc,
                                                      String npcId,
                                                      NpcFriendshipDataManager.FriendshipState state,
                                                      DayContext dayContext,
                                                      NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.SpiritEveFestivalService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleFestivalOfIceDialogue(ServerPlayer player,
                                                          StardewNpcEntity npc,
                                                          String npcId,
                                                          NpcFriendshipDataManager.FriendshipState state,
                                                          DayContext dayContext,
                                                          NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.FestivalOfIceService.resolveDialogueKey(player, npcId);
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean tryHandleWinterStarDialogue(ServerPlayer player,
                                                        StardewNpcEntity npc,
                                                        String npcId,
                                                        NpcFriendshipDataManager.FriendshipState state,
                                                        DayContext dayContext,
                                                        NpcFriendshipDataManager friendshipManager) {
        String dialogueText = com.stardew.craft.festival.WinterStarFestivalService.resolveDialogueKey(player, npcId);
        boolean formalFestivalDay = com.stardew.craft.festival.WinterStarFestivalService.isFormalFestivalDay();
        if (com.stardew.craft.festival.WinterStarFestivalService.tryPromptSecretGift(player, npc, npcId)) {
            if (formalFestivalDay && NpcSocialRules.canSocialize(npcId, player)
                && state.lastTalkDayKey() != dayContext.dayKey()) {
                grantConversationFriendship(npcId, state, dayContext,
                    dialogueText == null || dialogueText.isBlank() ? "winter_star_secret_gift" : dialogueText, player);
                NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
                friendshipManager.setDirty();
                syncFriendshipStatus(player, npcId, state, dayContext);
                com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
            }
            return true;
        }
        if (dialogueText == null || dialogueText.isBlank()) {
            return false;
        }

        boolean canGainFriendship = formalFestivalDay && NpcSocialRules.canSocialize(npcId, player);
        if (canGainFriendship && state.lastTalkDayKey() != dayContext.dayKey()) {
            grantConversationFriendship(npcId, state, dayContext, dialogueText, player);
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
        }
        syncFriendshipStatus(player, npcId, state, dayContext);
        com.stardew.craft.quest.StardewQuestEvents.fireNpcSocialized(player, npcId);
        markActiveFestivalDialogueSeen(player, npcId);
        int points = state.points();
        npc.facePlayerTemporarily(player, 60, () -> sendDialoguePacket(player, npcId, dialogueText, points, false));
        return true;
    }

    private static boolean isLewis(String npcId) {
        return "lewis".equals(npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT));
    }

    private static void markActiveFestivalDialogueSeen(ServerPlayer player, String npcId) {
        com.stardew.craft.festival.ActiveFestivalHandlers.getParticipating(player)
            .ifPresent(handler -> handler.onNpcDialogueSeen(player, npcId));
    }

    /**
     * 找到当前玩家已接受、未完成、未销毁、且 target NPC + itemId 与手上物品匹配的
     * 交付任务。返回第一个匹配，没有则返回 null。
     */
    @javax.annotation.Nullable
    private static com.stardew.craft.quest.StardewQuest findMatchingDeliveryQuest(
            ServerPlayer player, String npcId, ItemStack held) {
        if (held.isEmpty()) return null;
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        com.stardew.craft.quest.QuestManager mgr = com.stardew.craft.quest.QuestManager.of(player);
        if (mgr == null) return null;
        for (com.stardew.craft.quest.StardewQuest q : mgr.getQuestLog()) {
            if (q.matchesItemDelivery(npcId, itemId)
                    && held.getCount() >= requiredDeliveryCount(q)) {
                return q;
            }
        }
        return null;
    }

    static int requiredDeliveryCount(com.stardew.craft.quest.StardewQuest quest) {
        int total = quest.getTotalObjectiveCount();
        if (total <= 1) {
            return 1;
        }
        return Math.max(1, total - Math.max(0, quest.getCurrentObjectiveCount()));
    }

    /**
     * 玩家在 "送X给Y完成任务?" 对话框点"是"后，服务端执行交付。
     * 重新校验（手上还拿着、任务还在）后消耗物品 + 完成任务 + 播 SDV questComplete 音效。
     */
    public static void handleConfirmedQuestDelivery(ServerPlayer player, String npcId, String questId) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) held = player.getOffhandItem();
        if (held.isEmpty()) return;

        com.stardew.craft.quest.StardewQuest matching = findMatchingDeliveryQuest(player, npcId, held);
        if (matching == null || !com.stardew.craft.quest.QuestDataLoader.idsEqual(matching.getId(), questId)) {
            // 玩家在等对话框期间换了物品 / 改了任务 — 静默丢弃
            return;
        }

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int deliveryCount = requiredDeliveryCount(matching);
        if (!player.getAbilities().instabuild) {
            held.shrink(deliveryCount);
        }
        boolean desertFestivalWillyQuest = matching instanceof com.stardew.craft.quest.ItemDeliveryQuest delivery
            && com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.isWillyChallengeQuest(delivery);
        // 走 onItemOfferedToNpc → questComplete（带友好度加成）
        com.stardew.craft.quest.StardewQuestEvents.fireItemOfferedToNpc(
                player, npcId, itemId, deliveryCount);

        // SDV parity: "questComplete" — QuestCompletePayload.handleClient 会广播给
        // 完成任务的玩家自己（在 QuestManager.cleanupDestroyed 发包时触发），
        // 这里不再额外播声。

        // 让 NPC 说一句感谢台词
        StardewNpcEntity npcEntity = NpcSpawnManager.getTrackedNpc(serverLevel, npcId);
        if (desertFestivalWillyQuest) {
            com.stardew.craft.festival.desert.DesertFestivalWillyFishingService.handleDeliveredGoldenBobber(
                player, (com.stardew.craft.quest.ItemDeliveryQuest) matching);
            return;
        }
        if (npcEntity != null) {
            boolean garbleGift = npcId.equals("dwarf") && !com.stardew.craft.shop.DwarfService.canUnderstandDwarves(player);
            sendDialoguePacket(player, npcId, questDeliveryDialogueText(matching), 0, garbleGift);
        }
    }

    /**
     * Called from ConfirmGiftPayload when the player confirms giving a gift.
     * Re-validates that the player is still holding an item and processes the gift.
     *
     * SDV parity: Quest item delivery is checked FIRST (NPC.tryToReceiveActiveObject).
     * If a delivery quest matches, the item is consumed by the quest and gift taste
     * processing is entirely skipped.
     */
    public static void handleConfirmedGift(ServerPlayer player, String npcId) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            held = player.getOffhandItem();
        }
        if (held.isEmpty()) return; // Player no longer holding anything

        net.minecraft.world.entity.Entity npcEntity =
                NpcSpawnManager.getTrackedNpc(serverLevel, npcId);
        if (npcEntity == null) {
            ResourceLocation resolvedNpcId =
                    com.stardew.craft.api.v1.npc.StardewNpcInteractions
                            .normalizeNpcId(npcId);
            if (resolvedNpcId != null) {
                npcEntity = com.stardew.craft.api.v1.internal.npc.StardewNpcEntityRegistry
                        .resolve(serverLevel, resolvedNpcId);
            }
        }
        if (npcEntity == null) return;

        String giftItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

        // ── SDV parity: quest delivery intercept (before gift taste processing) ──
        // In SDV NPC.tryToReceiveActiveObject, OnItemOfferedToNpc is checked first.
        // If a delivery quest matches, the item is consumed by the quest; no gift processing.
        com.stardew.craft.quest.StardewQuest matchingQuest = findMatchingDeliveryQuest(player, npcId, held);
        int deliveryCount = matchingQuest == null ? 1 : requiredDeliveryCount(matchingQuest);
        boolean questConsumed = matchingQuest != null
            && com.stardew.craft.quest.StardewQuestEvents.fireItemOfferedToNpc(
                player, npcId, giftItemId, deliveryCount);
        if (questConsumed) {
            // Quest consumed the item — shrink held stack and send quest-specific dialogue
            if (!player.getAbilities().instabuild) {
                held.shrink(deliveryCount);
            }
            boolean garbleGift = npcId.equals("dwarf") && !com.stardew.craft.shop.DwarfService.canUnderstandDwarves(player);
            sendDialoguePacket(player, npcId, questDeliveryDialogueText(matchingQuest), 0, garbleGift);
            return;
        }

        if (!canBeGivenAsGift(held) || !NpcSocialRules.canReceiveGifts(npcId, player)) {
            return;
        }

        // ── Normal gift processing (no quest matched) ──
        DayContext dayContext = currentDayContext(serverLevel);
        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(serverLevel);
        NpcFriendshipDataManager.FriendshipState state =
                friendshipManager.get(player.getUUID(), npcId);
        boolean existingFriendship = state != null;
        if (state == null) {
            state = new NpcFriendshipDataManager.FriendshipState();
        }

        ResourceLocation publicNpcId =
                com.stardew.craft.api.v1.npc.StardewNpcInteractions
                        .normalizeNpcId(npcId);
        if (publicNpcId != null
                && !com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry
                        .before(new com.stardew.craft.api.v1.npc.StardewNpcGifts
                                .BeforeContext(
                                player,
                                npcEntity,
                                publicNpcId,
                                held,
                                friendshipSnapshot(state)))) {
            return;
        }
        if (!existingFriendship) {
            state = friendshipManager.getOrCreate(player.getUUID(), npcId);
        }
        state.normalizeGiftWeek(dayContext.weekKey());

        // SDV NPC.tryToReceiveActiveObject completes Quest 25 before applying
        // daily/weekly gift-limit rejection or consuming the item. Addon vetoes
        // run first so a denied gift has no core side effects.
        com.stardew.craft.quest.QuestManager quests =
                com.stardew.craft.quest.QuestManager.of(player);
        if (quests != null) {
            quests.completeActiveQuest("25", player);
        }

        GiftProcessResult giftResult =
                receiveGift(player, npcEntity, held, npcId, state, dayContext);
        if (publicNpcId != null) {
            com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry.after(
                    new com.stardew.craft.api.v1.npc.StardewNpcGifts.Result(
                            player,
                            npcEntity,
                            publicNpcId,
                            giftResult.gift(),
                            giftResult.status(),
                            giftResult.taste() == null
                                    ? null
                                    : com.stardew.craft.api.v1.npc.StardewNpcGifts
                                            .Taste.valueOf(
                                            giftResult.taste().name()),
                            giftResult.tasteSource(),
                            giftResult.birthday(),
                            giftResult.friendshipDelta(),
                            giftResult.resultingPoints()));
        }
        NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
        friendshipManager.setDirty();
        syncFriendshipStatus(player, npcId, state, dayContext);
        boolean garbleGift = npcId.equals("dwarf") && !com.stardew.craft.shop.DwarfService.canUnderstandDwarves(player);
        sendDialoguePacket(
                player,
                npcId,
                giftResult.responseText(),
                state.points(),
                garbleGift);
    }

    /**
     * Vanilla recipient.receiveGift(... updateGiftLimitInfo:false,
     * friendshipChangeMultiplier:5, showResponse:false).
     */
    public static boolean applyWinterStarSecretGift(ServerPlayer player, StardewNpcEntity npcEntity,
                                                     String npcId, ItemStack gift) {
        if (player == null || npcEntity == null || gift == null || gift.isEmpty() || !canBeGivenAsGift(gift)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        DayContext dayContext = currentDayContext(level);
        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(level);
        NpcFriendshipDataManager.FriendshipState state =
                friendshipManager.get(player.getUUID(), npcId);
        boolean existingFriendship = state != null;
        if (state == null) {
            state = new NpcFriendshipDataManager.FriendshipState();
        }
        ResourceLocation publicNpcId =
                com.stardew.craft.api.v1.npc.StardewNpcInteractions
                        .normalizeNpcId(npcId);
        if (publicNpcId != null
                && !com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry
                        .before(new com.stardew.craft.api.v1.npc.StardewNpcGifts
                                .BeforeContext(
                                player,
                                npcEntity,
                                publicNpcId,
                                gift,
                                friendshipSnapshot(state)))) {
            return false;
        }
        if (!existingFriendship) {
            state = friendshipManager.getOrCreate(player.getUUID(), npcId);
        }
        ItemStack giftSnapshot = gift.copy();
        GiftTasteResult tasteResult = getGiftTasteForThisItem(gift, npcId);
        GiftTaste taste = tasteResult.taste();

        int delta;
        if (isStardropTea(gift)) {
            delta = 750;
        } else {
            int base = friendshipDeltaFromTaste(taste);
            delta = base > 0
                ? (int) (base * 5.0F * qualityMultiplier(gift))
                : (int) (base * 5.0F);
        }
        delta = BookPowerEffects.applyFriendshipGain(PlayerDataManager.getPlayerData(player), delta);
        state.addPoints(delta, getMaxFriendshipPointsFor(npcId));
        PlayerDataManager.getPlayerData(player).revealGiftTaste(npcId, VanillaGiftTasteResolver.objectToken(gift));
        PlayerStardewDataAPI.recordGiftGiven(player);
        if (!player.getAbilities().instabuild) {
            gift.shrink(1);
        }
        level.playSound(null, player.blockPosition(),
            com.stardew.craft.sound.ModSounds.GIVE_GIFT.get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        broadcastGiftEmote(npcEntity, taste);
        if (publicNpcId != null) {
            com.stardew.craft.api.v1.internal.npc.StardewNpcGiftRegistry.after(
                    new com.stardew.craft.api.v1.npc.StardewNpcGifts.Result(
                            player,
                            npcEntity,
                            publicNpcId,
                            giftSnapshot,
                            com.stardew.craft.api.v1.npc.StardewNpcGifts.Status
                                    .ACCEPTED,
                            com.stardew.craft.api.v1.npc.StardewNpcGifts.Taste
                                    .valueOf(taste.name()),
                            tasteResult.source(),
                            false,
                            delta,
                            Math.max(0, state.points())));
        }
        NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
        friendshipManager.setDirty();
        syncFriendshipStatus(player, npcId, state, dayContext);
        return true;
    }

    public static void handleFlowerDanceNpcInviteResponse(ServerPlayer player, String npcId, boolean confirmed) {
        if (!confirmed || player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String canonicalNpcId = npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
        if (canonicalNpcId.isBlank() || !com.stardew.craft.festival.FlowerDanceService.isParticipant(player)) {
            return;
        }
        if (!com.stardew.craft.festival.FlowerDanceService.canAskNpcToDance(canonicalNpcId)) {
            return;
        }

        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get(serverLevel);
        NpcFriendshipDataManager.FriendshipState state = friendshipManager.getOrCreate(player.getUUID(), canonicalNpcId);
        DayContext dayContext = currentDayContext(serverLevel);
        state.normalizeGiftWeek(dayContext.weekKey());

        if (com.stardew.craft.festival.FlowerDanceService.hasDancePartner(player)) {
            sendDialoguePacket(player, canonicalNpcId, "message.stardewcraft.festival.flower_dance.already_have_partner", state.points());
            return;
        }
        if (com.stardew.craft.festival.FlowerDanceService.isNpcDancePartnerTaken(canonicalNpcId)) {
            sendDialoguePacket(player, canonicalNpcId, flowerDanceNpcDialogueKey(canonicalNpcId, "flowerdance_taken"), state.points());
            return;
        }
        if (state.points() < 1000) {
            sendDialoguePacket(player, canonicalNpcId, flowerDanceNpcDialogueKey(canonicalNpcId, "flowerdance_decline"), state.points());
            return;
        }
        if (!com.stardew.craft.festival.FlowerDanceService.setNpcDancePartner(player, canonicalNpcId)) {
            sendDialoguePacket(player, canonicalNpcId, flowerDanceNpcDialogueKey(canonicalNpcId, "flowerdance_taken"), state.points());
            return;
        }

        state.addPoints(250, getMaxFriendshipPointsFor(canonicalNpcId));
        NpcFriendshipRewardService.applyEligibleRewards(player, canonicalNpcId, state.points());
        friendshipManager.setDirty();
        syncFriendshipStatus(player, canonicalNpcId, state, dayContext);
        sendDialoguePacket(player, canonicalNpcId, flowerDanceNpcDialogueKey(canonicalNpcId, "flowerdance_accept"), state.points());
    }

    private static String flowerDanceNpcDialogueKey(String npcId, String suffix) {
        return "stardewcraft.npc." + npcId + "." + suffix;
    }

    public static int getMaxFriendshipPointsFor(String npcId) {
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
        boolean datable = com.stardew.craft.api.v1.npc.StardewNpcProfiles.resolve(npcId)
                .map(definition -> definition.profile().datable())
                .orElse(profile != null && profile.datable());
        // Since we don't have a dating/bouquet system yet, datable NPCs are capped at 8 hearts.
        int maxHearts = datable ? 8 : 10;
        // Vanilla formula: (maxHearts + 1) * POINTS_PER_HEART - 1
        // Thus 8 hearts -> 9 * 250 - 1 = 2249 points (which safely renders as 8 hearts and no more)
        return (maxHearts + 1) * POINTS_PER_HEART - 1;
    }

    private static void syncFriendshipStatus(ServerPlayer player,
                                             String npcId,
                                             NpcFriendshipDataManager.FriendshipState state,
                                             DayContext dayContext) {
        int points = Math.max(0, state.points());
        int hearts = Math.max(0, Math.min(14, points / POINTS_PER_HEART));
        int giftsThisWeek = Math.max(0, Math.min(2, state.giftsThisWeek()));
        boolean giftedToday = state.lastGiftDayKey() == dayContext.dayKey();
        boolean talkedToday = state.lastTalkDayKey() == dayContext.dayKey();
        PacketDistributor.sendToPlayer(player, new SyncNpcFriendshipStatusPayload(
            npcId,
            points,
            hearts,
            giftsThisWeek,
            giftedToday,
            talkedToday
        ));
    }

    private static void grantConversationFriendship(String npcId,
                                                    NpcFriendshipDataManager.FriendshipState state,
                                                    DayContext dayContext,
                                                    String dialogueText,
                                                    net.minecraft.server.level.ServerPlayer player) {
        if (state.lastTalkDayKey() == dayContext.dayKey()) {
            return;
        }
        if (dialogueText == null || dialogueText.isBlank() || "...".equals(dialogueText)) {
            return;
        }
        state.setLastTalkDayKey(dayContext.dayKey());
        // SDV NPC.cs:2933 — Blessing of Friendship: 寒暄好感 60（默认 20）
        int amount = (player != null && player.hasEffect(com.stardew.craft.effect.ModMobEffects.STATUE_OF_BLESSINGS_4)) ? 60 : 20;
        amount = BookPowerEffects.applyFriendshipGain(PlayerDataManager.getPlayerData(player), amount);
        state.addPoints(amount, getMaxFriendshipPointsFor(npcId));
    }

    private static boolean isStardropTea(ItemStack held) {
        return STARDROP_TEA_ID.equals(normalizeItemId(held));
    }

    private static boolean isAlwaysGiftableSpecial(ItemStack held) {
        String id = normalizeItemId(held);
        return STARDROP_TEA_ID.equals(id)
            || GOLDEN_PUMPKIN_ID.equals(id)
            || MAGIC_ROCK_CANDY_ID.equals(id);
    }

    public static boolean canBeGivenAsGift(ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        if (isAlwaysGiftableSpecial(held)) {
            return true;
        }
        if (held.is(ModTags.Items.TOOLS)) {
            return false;
        }

        String typeKey = StardewItemDataApi.getTypeKey(held);
        if (!typeKey.isBlank()) {
            String normalizedTypeKey = typeKey.trim().toLowerCase(Locale.ROOT);
            if (normalizedTypeKey.startsWith("stardewcraft.tool.")
                    || NON_GIFTABLE_TYPE_KEYS.contains(normalizedTypeKey)) {
                return false;
            }
        } else {
            return false;
        }

        Boolean vanillaGiftability = findVanillaObjectGiftability(held);
        return vanillaGiftability == null || vanillaGiftability;
    }

    private static String serializeGiftItemDisplayName(ItemStack held, ServerLevel level) {
        try {
            return net.minecraft.network.chat.Component.Serializer.toJson(
                held.getHoverName(), level.registryAccess());
        } catch (Exception e) {
            return held.getHoverName().getString();
        }
    }

    private static Boolean findVanillaObjectGiftability(ItemStack held) {
        Map<String, Boolean> giftabilityByName = vanillaObjectGiftabilityByName;
        if (giftabilityByName == null) {
            giftabilityByName = loadVanillaObjectGiftabilityByName();
            vanillaObjectGiftabilityByName = giftabilityByName;
        }
        if (giftabilityByName.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        String path = itemId.getPath();
        Boolean found = giftabilityByName.get(normalizeVanillaObjectName(path));
        if (found != null) {
            return found;
        }
        if (path.endsWith("_item")) {
            found = giftabilityByName.get(normalizeVanillaObjectName(path.substring(0, path.length() - "_item".length())));
        }
        return found;
    }

    private static Map<String, Boolean> loadVanillaObjectGiftabilityByName() {
        try (InputStream stream = NpcInteractionService.class.getClassLoader().getResourceAsStream(VANILLA_OBJECTS_RESOURCE)) {
            if (stream == null) {
                return Collections.emptyMap();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, Boolean> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject objectData = entry.getValue().getAsJsonObject();
                    boolean canBeGift = !objectData.has("CanBeGivenAsGift")
                            || objectData.get("CanBeGivenAsGift").getAsBoolean();
                    String normalizedKey = normalizeVanillaObjectName(entry.getKey());
                    if (!normalizedKey.isBlank()) {
                        out.put(normalizedKey, canBeGift);
                    }
                    if (objectData.has("Name") && objectData.get("Name").isJsonPrimitive()) {
                        String normalizedName = normalizeVanillaObjectName(objectData.get("Name").getAsString());
                        if (!normalizedName.isBlank()) {
                            out.merge(normalizedName, canBeGift, (oldValue, newValue) -> oldValue && newValue);
                        }
                    }
                }
                return Collections.unmodifiableMap(out);
            }
        } catch (Exception exception) {
            return Collections.emptyMap();
        }
    }

    private static String normalizeVanillaObjectName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private static GiftProcessResult receiveGift(
            ServerPlayer player,
            net.minecraft.world.entity.Entity npcEntity,
            ItemStack held,
            String npcId,
            NpcFriendshipDataManager.FriendshipState state,
            DayContext dayContext
    ) {
        ItemStack giftSnapshot = held.copy();
        boolean stardropTea = isStardropTea(held);
        boolean birthday = isNpcBirthday(npcId, dayContext);

        // StardropTea bypasses daily & weekly limits (vanilla parity)
        if (!stardropTea) {
            if (state.lastGiftDayKey() == dayContext.dayKey()) {
                return GiftProcessResult.rejected(
                        "stardewcraft.npc.generic.gift.already_today",
                        giftSnapshot,
                        com.stardew.craft.api.v1.npc.StardewNpcGifts.Status
                                .REJECTED_DAILY_LIMIT,
                        state.points());
            }
            if (weeklyGiftLimitBlocks(state.giftsThisWeek(), birthday)) {
                return GiftProcessResult.rejected(
                        "stardewcraft.npc.generic.gift.already_week_limit",
                        giftSnapshot,
                        com.stardew.craft.api.v1.npc.StardewNpcGifts.Status
                                .REJECTED_WEEKLY_LIMIT,
                        state.points());
            }
        }

        float birthdayMul = birthday ? 8f : 1f;

        int finalDelta;
        GiftTasteResult tasteResult;
        GiftTaste taste;
        if (stardropTea) {
            // Vanilla: gift_taste_stardroptea = 7 → min(750, 250 * multiplier)
            tasteResult = new GiftTasteResult(GiftTaste.LOVED, "stardrop_tea_special");
            taste = GiftTaste.LOVED;
            finalDelta = Math.min(750, (int)(250f * birthdayMul));
        } else {
            tasteResult = getGiftTasteForThisItem(held, npcId);
            taste = tasteResult.taste();
            int baseDelta = friendshipDeltaFromTaste(taste);
            float qualityMul = qualityMultiplier(held);
            // Vanilla: quality multiplier only applies to positive tastes
            if (baseDelta > 0) {
                finalDelta = (int)(baseDelta * birthdayMul * qualityMul);
            } else {
                finalDelta = (int)(baseDelta * birthdayMul);
            }
        }

        // SDV parity: gifting Dwarf without translation guide → friendship delta is 0
        boolean dwarfNoUnderstand = npcId.equals("dwarf")
            && player instanceof ServerPlayer sp
            && !com.stardew.craft.shop.DwarfService.canUnderstandDwarves(sp);
        if (dwarfNoUnderstand && finalDelta > 0) {
            finalDelta = 0;
        }

        finalDelta = BookPowerEffects.applyFriendshipGain(PlayerDataManager.getPlayerData(player), finalDelta);
        state.addPoints(finalDelta, getMaxFriendshipPointsFor(npcId));
        com.stardew.craft.player.PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        boolean revealedGiftTaste = playerData.revealGiftTaste(
                npcId, VanillaGiftTasteResolver.objectToken(held));
        // StardropTea does NOT count toward daily/weekly gift limits (vanilla parity)
        if (!stardropTea) {
            state.applyGiftCounters(dayContext.dayKey(), dayContext.weekKey());
            PlayerStardewDataAPI.recordGiftGiven(player);
        } else if (revealedGiftTaste) {
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, playerData);
        }


        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // Broadcast NPC emote to all players (vanilla parity)
        broadcastGiftEmote(npcEntity, taste);

        String responseText = buildGiftResponseText(
                npcId, giftSnapshot, taste, birthday, finalDelta);
        // SDV parity: Dwarf gift response garble flag — applied client-side after translation
        return new GiftProcessResult(
                responseText,
                giftSnapshot,
                com.stardew.craft.api.v1.npc.StardewNpcGifts.Status.ACCEPTED,
                taste,
                tasteResult.source(),
                birthday,
                finalDelta,
                Math.max(0, state.points()));
    }

    static boolean weeklyGiftLimitBlocks(int giftsThisWeek, boolean birthday) {
        // NPC.tryToReceiveActiveObject: birthdays bypass GiftsThisWeek < 2,
        // while the separate GiftsToday check still applies.
        return giftsThisWeek >= 2 && !birthday;
    }

    private static com.stardew.craft.api.v1.npc.StardewNpcFriendshipSnapshot
            friendshipSnapshot(
                    NpcFriendshipDataManager.FriendshipState state
            ) {
        return new com.stardew.craft.api.v1.npc.StardewNpcFriendshipSnapshot(
                Math.max(0, state.points()),
                Math.max(0, state.giftsThisWeek()),
                state.lastGiftDayKey(),
                state.lastGiftWeekKey(),
                state.lastTalkDayKey(),
                state.firstMetDayKey(),
                state.dialogueDayKey(),
                Math.max(0, state.dialogueInteractionsToday()));
    }

    private static GiftTasteResult getGiftTasteForThisItem(ItemStack held, String npcId) {
        String key = normalizeItemId(held);

        // 1. NPC-specific item match (highest priority)
        JsonObject npcTastes = NpcDataRegistry.tastes().get(npcId);
        GiftTaste npcResult = findTasteInTable(npcTastes, key);
        if (npcResult != null) {
            return new GiftTasteResult(npcResult, "npc");
        }

        // 2. NPC-specific category match
        String itemCategory = resolveItemCategory(held);
        if (itemCategory != null) {
            GiftTaste npcCatResult = findTasteByCategory(npcTastes, itemCategory);
            if (npcCatResult != null) {
                return new GiftTasteResult(npcCatResult, "npc-category");
            }
        }

        // 3. Universal item match
        JsonObject universalTastes = NpcDataRegistry.tastes().get("universal");
        GiftTaste universalResult = findTasteInTable(universalTastes, key);
        if (universalResult != null) {
            return new GiftTasteResult(universalResult, "universal");
        }

        // 4. Universal category match
        if (itemCategory != null) {
            GiftTaste univCatResult = findTasteByCategory(universalTastes, itemCategory);
            if (univCatResult != null) {
                return new GiftTasteResult(univCatResult, "universal-category");
            }
        }

        VanillaGiftTasteResolver.Result vanillaResult = VanillaGiftTasteResolver.resolve(held, npcId);
        if (vanillaResult != null) {
            return new GiftTasteResult(fromVanillaTaste(vanillaResult.taste()), vanillaResult.source());
        }

        return new GiftTasteResult(GiftTaste.NEUTRAL, "fallback-neutral");
    }

    private static GiftTaste fromVanillaTaste(VanillaGiftTasteResolver.Taste taste) {
        return switch (taste) {
            case LOVED -> GiftTaste.LOVED;
            case LIKED -> GiftTaste.LIKED;
            case NEUTRAL -> GiftTaste.NEUTRAL;
            case DISLIKED -> GiftTaste.DISLIKED;
            case HATED -> GiftTaste.HATED;
        };
    }

    private static GiftTaste findTasteInTable(JsonObject tastes, String key) {
        if (containsTaste(tastes, "loved", key)) {
            return GiftTaste.LOVED;
        }
        if (containsTaste(tastes, "liked", key)) {
            return GiftTaste.LIKED;
        }
        if (containsTaste(tastes, "neutral", key)) {
            return GiftTaste.NEUTRAL;
        }
        if (containsTaste(tastes, "disliked", key)) {
            return GiftTaste.DISLIKED;
        }
        if (containsTaste(tastes, "hated", key)) {
            return GiftTaste.HATED;
        }
        return null;
    }

    private static boolean containsTaste(JsonObject tastes, String category, String itemId) {
        if (tastes == null || !tastes.has(category) || !tastes.get(category).isJsonArray()) {
            return false;
        }
        JsonArray arr = tastes.getAsJsonArray(category);
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive() && itemId.equals(el.getAsString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check taste table for category-based entries.
     * Checks "loved_categories", "liked_categories", etc. arrays against the item's category tag.
     */
    private static GiftTaste findTasteByCategory(JsonObject tastes, String category) {
        if (tastes == null || category == null) {
            return null;
        }
        if (containsTaste(tastes, "loved_categories", category)) return GiftTaste.LOVED;
        if (containsTaste(tastes, "hated_categories", category)) return GiftTaste.HATED;
        if (containsTaste(tastes, "liked_categories", category)) return GiftTaste.LIKED;
        if (containsTaste(tastes, "disliked_categories", category)) return GiftTaste.DISLIKED;
        if (containsTaste(tastes, "neutral_categories", category)) return GiftTaste.NEUTRAL;
        return null;
    }

    /**
     * Resolve item category from NBT custom data tag "StardewCategory".
     * Returns a lowercase category string (e.g. "fish", "gem", "cooking", "archaeology")
     * or null if no category is set.
     */
    private static String resolveItemCategory(ItemStack held) {
        if (held.isEmpty()) return null;
        net.minecraft.world.item.component.CustomData customData =
            held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                              net.minecraft.world.item.component.CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        if (tag.contains("StardewCategory")) {
            String cat = tag.getString("StardewCategory");
            return (cat != null && !cat.isBlank()) ? cat.toLowerCase(Locale.ROOT) : null;
        }
        return null;
    }

    private static String resolveScheduleDialogue(ServerLevel level, String npcId) {
        NpcRuntimeState runtimeState = NpcRuntimeDataManager.get(level).states().get(npcId);
        if (runtimeState == null) {
            return "";
        }
        String behavior = runtimeState.routeBehaviorToken();
        String prefix = "dialogue:";
        return behavior != null && behavior.startsWith(prefix)
                ? behavior.substring(prefix.length()).trim()
                : "";
    }

    private static String loadCurrentDialogue(ServerPlayer player,
                                              String npcId,
                                              NpcFriendshipDataManager.FriendshipState state,
                                              DayContext dayContext) {
        JsonObject dialogueRoot = NpcDataRegistry.dialogues().get(npcId);
        if (dialogueRoot == null) {
            return "...";
        }
        int hearts = Math.max(0, Math.min(14, state.points() / POINTS_PER_HEART));
        NpcRuntimeState runtimeState = NpcRuntimeDataManager.get(player.serverLevel())
                .states().get(npcId);
        String locationName = runtimeState == null ? "" : runtimeState.locationName();
        int tileX = runtimeState == null ? 0 : runtimeState.tileX();
        int tileY = runtimeState == null ? 0 : runtimeState.tileY();
        String weather = dayContext.weatherLower();
        boolean greenRain = weather.equals("greenrain") || weather.equals("green_rain");
        boolean raining = weather.equals("rain") || weather.equals("storm");
        com.stardew.craft.util.StardewDeterministicRandom dailyRandom = vanillaDialogueRandom(
                player.serverLevel(), dayContext.dayKey(), tileX, tileY);
        // NPC.loadCurrentDialogue always consumes the gossip roll before its rain roll.
        dailyRandom.nextDouble();
        var playerData = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        boolean useRainDialogue = raining
                && dailyRandom.nextDouble() < 0.5D
                && !(npcId.equals("krobus") && dayContext.weekdayShort().equals("Fri"))
                && !(npcId.equals("penny") && playerData.hasMailFlag("pamHouseUpgrade"))
                && !(npcId.equals("emily")
                        && dayContext.seasonLower().equals("fall")
                        && dayContext.dayInSeason() == 15);
        boolean communityCenterAccessible = playerData
                .hasMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.CC_DOOR_UNLOCKED);
        boolean isFirstMeeting = state.lastTalkDayKey() == Integer.MIN_VALUE;
        NpcDialogueEventData dialogueEvents = NpcDialogueEventData.get(player.getServer());
        // Save migration: events watched before this vanilla dialogue-event
        // ledger existed must still produce their NPC reactions exactly once.
        for (String seenEvent : com.stardew.craft.cutscene.server.EventSeenData
                .get(player.serverLevel())
                .getSeenEvents(player.getUUID())) {
            dialogueEvents.activateEventSeen(player.getUUID(), seenEvent);
        }
        NpcDialogueResolver.Context context = new NpcDialogueResolver.Context(
                npcId,
                dayContext.dayInSeason(),
                dayContext.dayKey(),
                StardewTimeManager.get().getCurrentYear(),
                dayContext.seasonLower(),
                dayContext.weekdayShort(),
                hearts,
                isFirstMeeting,
                greenRain,
                raining,
                useRainDialogue,
                locationName,
                tileX,
                tileY,
                playerData.hasMailFlag("pamHouseUpgrade"),
                communityCenterAccessible,
                dialogueEvents.activeKeys(player.getUUID(), npcId));
        NpcDialogueResolver.Selection selection = NpcDialogueResolver.select(
                dialogueRoot,
                NpcDataRegistry.dialogues().get("rainy"),
                context);
        if (selection.source() == NpcDialogueResolver.Source.ACTIVE_EVENT) {
            dialogueEvents.markConsumed(player.getUUID(), npcId, selection.key());
        }
        return selection.present() ? selection.text() : "...";
    }

    private static com.stardew.craft.util.StardewDeterministicRandom vanillaDialogueRandom(
            ServerLevel level,
            int dayKey,
            int tileX,
            int tileY
    ) {
        long daysPlayed = Math.max(0L, dayKey - 1L);
        // Utility.CreateDaySaveRandom(DaysPlayed * 77,
        //     2 + defaultPosition.X * 77, defaultPosition.Y * 777)
        return com.stardew.craft.util.StardewDeterministicRandom.create(
                daysPlayed,
                level.getSeed() / 2L,
                daysPlayed * 77L,
                2L + (long) tileX * 77L,
                (long) tileY * 777L);
    }

    private static String buildGiftResponseText(String npcId,
                                                ItemStack held,
                                                GiftTaste taste,
                                                boolean birthday,
                                                int finalDelta) {
        JsonObject dialogueRoot = NpcDataRegistry.dialogues().get(npcId);
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
        int manners = com.stardew.craft.api.v1.npc.StardewNpcProfiles.resolve(npcId)
                .map(definition -> definition.profile().manners())
                .orElse(profile != null
                        ? profile.manners()
                        : NpcCapabilityProfile.MANNERS_NEUTRAL);

        String dialoguePrefix = birthday ? "AcceptBirthdayGift" : "AcceptGift";
        String specificDialogue = resolveGiftDialogueText(
                dialogueRoot,
                dialoguePrefix,
                stardewObjectToken(held),
                VanillaGiftTasteResolver.contextTags(held),
                taste,
                currentDayKey());
        if (specificDialogue != null && !specificDialogue.isBlank()) {
            return specificDialogue;
        }

        if (birthday) {
            // Personality-branched birthday responses (vanilla NPC.cs parity)
            boolean positive = (taste == GiftTaste.LOVED || taste == GiftTaste.LIKED || taste == GiftTaste.NEUTRAL);
            boolean negative = (taste == GiftTaste.DISLIKED || taste == GiftTaste.HATED);

            if (positive) {
                String birthdayKey = "AcceptBirthdayGift_Positive";
                String birthdayText = resolveDialogueTextByKey(dialogueRoot, birthdayKey, currentDayKey());
                if (birthdayText != null && !birthdayText.isBlank()) {
                    return "$h" + birthdayText;
                }
                // Manners-based fallback (vanilla NPC.cs:4274-4277)
                if (manners == NpcCapabilityProfile.MANNERS_RUDE) {
                    return "$h" + "stardewcraft.npc.generic.birthday.positive_rude";
                }
                return "$h" + "stardewcraft.npc.generic.birthday.positive";
            }
            if (negative) {
                String birthdayKey = "AcceptBirthdayGift_Negative";
                String birthdayText = resolveDialogueTextByKey(dialogueRoot, birthdayKey, currentDayKey());
                if (birthdayText != null && !birthdayText.isBlank()) {
                    return "$s" + birthdayText;
                }
                // Manners-based fallback (vanilla NPC.cs:4278-4279)
                if (manners == NpcCapabilityProfile.MANNERS_RUDE) {
                    return "$s" + "stardewcraft.npc.generic.birthday.negative_rude";
                }
                return "$s" + "stardewcraft.npc.generic.birthday.negative";
            }
            // Neutral birthday
            if (manners == NpcCapabilityProfile.MANNERS_RUDE) {
                return "stardewcraft.npc.generic.birthday.neutral_rude";
            }
            return "stardewcraft.npc.generic.birthday.neutral";
        }

        // NPC-specific taste response messages from taste data (vanilla parity)
        String tasteMsg = findNpcTasteMessage(npcId, taste);
        if (tasteMsg != null && !tasteMsg.isBlank()) {
            String emotionPrefix = switch (taste) {
                case LOVED, LIKED -> "$h";
                case HATED, DISLIKED -> "$s";
                case NEUTRAL -> "";
            };
            return emotionPrefix + tasteMsg;
        }

        // Generic fallback with portrait emotion tags
        return switch (taste) {
            case LOVED -> "$h" + "stardewcraft.npc.generic.gift.loved";
            case LIKED -> "$h" + "stardewcraft.npc.generic.gift.liked";
            case NEUTRAL -> "stardewcraft.npc.generic.gift.neutral";
            case DISLIKED -> "$s" + "stardewcraft.npc.generic.gift.disliked";
            case HATED -> "$s" + "stardewcraft.npc.generic.gift.hated";
        };
    }

    /** Mirrors NPC.GetGiftReaction's item, taste/context-tag, tag, and taste key priority. */
    static String resolveGiftDialogueText(
            JsonObject dialogueRoot,
            String prefix,
            String objectToken,
            Set<String> contextTags,
            GiftTaste taste,
            int dayKey
    ) {
        if (dialogueRoot == null || prefix == null || prefix.isBlank()) {
            return null;
        }
        if (objectToken != null && !objectToken.isBlank()) {
            String exact = resolveDialogueTextByKey(
                    dialogueRoot, prefix + "_(O)" + objectToken, dayKey);
            if (exact != null && !exact.isBlank()) {
                return exact;
            }
        }

        Set<String> tags = contextTags == null ? Set.of() : contextTags;
        for (String tag : tags) {
            for (String tasteLabel : giftTasteDialogueLabels(taste)) {
                String taggedTaste = resolveDialogueTextByKey(
                        dialogueRoot, prefix + "_" + tasteLabel + "_" + tag, dayKey);
                if (taggedTaste != null && !taggedTaste.isBlank()) {
                    return taggedTaste;
                }
            }
        }
        for (String tag : tags) {
            String tagged = resolveDialogueTextByKey(dialogueRoot, prefix + "_" + tag, dayKey);
            if (tagged != null && !tagged.isBlank()) {
                return tagged;
            }
        }
        for (String tasteLabel : giftTasteDialogueLabels(taste)) {
            String tasteDialogue = resolveDialogueTextByKey(
                    dialogueRoot, prefix + "_" + tasteLabel, dayKey);
            if (tasteDialogue != null && !tasteDialogue.isBlank()) {
                return tasteDialogue;
            }
        }
        return resolveDialogueTextByKey(dialogueRoot, prefix, dayKey);
    }

    private static List<String> giftTasteDialogueLabels(GiftTaste taste) {
        if (taste == null) {
            return List.of("Neutral", "Positive");
        }
        return switch (taste) {
            case LOVED -> List.of("Loved", "Positive");
            case LIKED -> List.of("Liked", "Positive");
            case NEUTRAL -> List.of("Neutral", "Positive");
            case DISLIKED -> List.of("Disliked", "Negative");
            case HATED -> List.of("Hated", "Negative");
        };
    }

    /**
     * Look up NPC-specific taste response message from the taste data table.
     * Vanilla NPCGiftTastes stores per-NPC messages for each taste level.
     * Our taste JSON supports optional "loved_msg", "liked_msg", "neutral_msg", "disliked_msg", "hated_msg" fields.
     */
    private static String findNpcTasteMessage(String npcId, GiftTaste taste) {
        JsonObject npcTastes = NpcDataRegistry.tastes().get(npcId);
        if (npcTastes == null) {
            return null;
        }
        String msgKey = switch (taste) {
            case LOVED -> "loved_msg";
            case LIKED -> "liked_msg";
            case NEUTRAL -> "neutral_msg";
            case DISLIKED -> "disliked_msg";
            case HATED -> "hated_msg";
        };
        if (npcTastes.has(msgKey) && npcTastes.get(msgKey).isJsonPrimitive()) {
            String msg = npcTastes.get(msgKey).getAsString();
            if (!msg.isBlank()) {
                // Wrap in translatable with NPC-specific key
                String langKey = "stardewcraft.npc." + npcId + ".gift_taste." + taste.name().toLowerCase(Locale.ROOT);
                return langKey;
            }
        }
        return null;
    }

    /**
     * Broadcast an emote bubble above the NPC entity based on gift taste.
     * Vanilla parity: loved → heart(20), liked → happy(32), hated → angry(12),
     * disliked → sad(28), neutral → no emote.
     */
    private static void broadcastGiftEmote(
            net.minecraft.world.entity.Entity npcEntity,
            GiftTaste taste
    ) {
        EmoteType emote = switch (taste) {
            case LOVED -> EmoteCatalog.byId("heart");
            case LIKED -> EmoteCatalog.byId("happy");
            case HATED -> EmoteCatalog.byId("angry");
            case DISLIKED -> EmoteCatalog.byId("sad");
            case NEUTRAL -> null;
        };
        if (emote != null) {
            int baseIndex = EmoteCatalog.getBubbleBaseIndex(emote);
            PacketDistributor.sendToAllPlayers(new EmoteBroadcastPayload(npcEntity.getId(), baseIndex));
        }
    }

    private static int friendshipDeltaFromTaste(GiftTaste taste) {
        return switch (taste) {
            case LOVED -> 80;
            case LIKED -> 45;
            case NEUTRAL -> 20;
            case DISLIKED -> -20;
            case HATED -> -40;
        };
    }

    /** Vanilla quality multiplier: silver=1.1, gold=1.25, iridium=1.5, otherwise 1.0 */
    private static float qualityMultiplier(ItemStack held) {
        if (held.isEmpty()) return 1f;
        net.minecraft.world.item.component.CustomData customData =
            held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                              net.minecraft.world.item.component.CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        if (tag.contains("StardewQuality")) {
            int q = tag.getInt("StardewQuality");
            return switch (q) {
                case 1 -> 1.1f;  // silver
                case 2 -> 1.25f; // gold
                case 4 -> 1.5f;  // iridium
                default -> 1f;
            };
        }
        return 1f;
    }

    private static String resolveDialogueTextByKey(JsonObject root, String key, int dayKey) {
        if (root == null || key == null || key.isBlank()) {
            return null;
        }
        JsonObject scope = dialogueScope(root);
        String actual = findKeyCaseInsensitive(scope, key);
        if (actual == null) {
            return null;
        }
        return pickTextFromEntry(scope.get(actual), dayKey);
    }

    private static JsonObject dialogueScope(JsonObject root) {
        if (root != null && root.has("entries") && root.get("entries").isJsonObject()) {
            return root.getAsJsonObject("entries");
        }
        return root;
    }

    private static String pickTextFromEntry(JsonElement entry, int dayKey) {
        if (entry == null) {
            return null;
        }
        if (entry.isJsonPrimitive()) {
            return entry.getAsString();
        }
        if (entry.isJsonObject()) {
            JsonObject obj = entry.getAsJsonObject();
            if (obj.has("translate") && obj.get("translate").isJsonPrimitive()) {
                return obj.get("translate").getAsString();
            }
            return null;
        }
        if (entry.isJsonArray()) {
            JsonArray arr = entry.getAsJsonArray();
            if (arr.isEmpty()) {
                return null;
            }
            JsonElement el = arr.get(Math.floorMod(dayKey, arr.size()));
            return pickTextFromEntry(el, dayKey);
        }
        return null;
    }

    /**
     * Send dialogue to client. Accepts either a plain translate key or the
     * internal {@code tr::key::base64} format (strips it down to just the key).
     */
    private static void sendDialoguePacket(ServerPlayer player, String npcId, String translateKey, int points) {
        sendDialoguePacket(player, npcId, translateKey, points, false);
    }

    private static String questDeliveryDialogueText(com.stardew.craft.quest.StardewQuest quest) {
        String targetMessage = quest != null ? quest.getDeliveryTargetMessage() : "";
        return targetMessage == null || targetMessage.isBlank()
            ? "stardewcraft.npc.generic.quest_delivery_thanks"
            : targetMessage;
    }

    private static void sendDialoguePacket(ServerPlayer player, String npcId, String translateKey, int points, boolean garbleDwarvish) {
        if (translateKey == null || translateKey.isBlank()) {
            translateKey = "...";
        }
        beginDialogueSession(player, npcId);
        PacketDistributor.sendToPlayer(player,
                new OpenNpcDialogueScreenPayload(
                        npcId,
                        translateKey,
                        points,
                        "",
                        garbleDwarvish,
                        List.copyOf(NpcDialogueEventData.get(player.getServer())
                                .answeredDialogueIds(player.getUUID()))));
    }

    public static void handleClientQuestionAnswer(
            ServerPlayer player,
            String npcId,
            String nextDialogueNode,
            int friendshipDelta,
            String answerId
    ) {
        if (npcId == null || npcId.isBlank()) return;

        if (answerId != null && answerId.matches("[A-Za-z0-9_-]{1,64}")) {
            NpcDialogueEventData.get(player.getServer())
                    .markDialogueAnswer(player.getUUID(), answerId);
        }

        // Wizard tower hub: intercept wizard-specific answer nodes (teleport commands)
        if ("wizard".equals(npcId) && com.stardew.craft.interior.WizardQuestHandler.handleWizardQuestionAnswer(player, nextDialogueNode)) {
            endDialogueSession(player.getUUID(), npcId);
            return;
        }
        // Morris: Yes/No on membership / CD form
        if ("morris".equals(npcId) && com.stardew.craft.joja.MorrisService.handleAnswer(player, nextDialogueNode)) {
            endDialogueSession(player.getUUID(), npcId);
            return;
        }

        NpcFriendshipDataManager friendshipManager = NpcFriendshipDataManager.get((net.minecraft.server.level.ServerLevel) player.level());
        NpcFriendshipDataManager.FriendshipState state = friendshipManager.getOrCreate(player.getUUID(), npcId);
        
        if (friendshipDelta != 0) {
            int adjustedDelta = BookPowerEffects.applyFriendshipGain(PlayerDataManager.getPlayerData(player), friendshipDelta);
            state.addPoints(adjustedDelta, getMaxFriendshipPointsFor(npcId));
            NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
            friendshipManager.setDirty();
            DayContext dayContext = currentDayContext((net.minecraft.server.level.ServerLevel) player.level());
            syncFriendshipStatus(player, npcId, state, dayContext);
        }

        if (nextDialogueNode != null
            && nextDialogueNode.startsWith(OpenNpcDialogueScreenPayload.DIRECT_DIALOGUE_PREFIX)) {
            String text = decodeDirectDialogue(nextDialogueNode.substring(OpenNpcDialogueScreenPayload.DIRECT_DIALOGUE_PREFIX.length()));
            if (!text.isBlank()) {
                sendDialoguePacket(player, npcId, text, state.points());
                return;
            }
        }

        if (nextDialogueNode != null && !nextDialogueNode.isBlank() && !nextDialogueNode.equals("null")) {
            JsonObject dialogueRoot = NpcDataRegistry.dialogues().get(npcId);
            String text = resolveDialogueTextByKey(dialogueRoot, nextDialogueNode, currentDayKey());
            if (text != null && !text.isBlank()) {
                sendDialoguePacket(player, npcId, text, state.points());
                return;
            }
        }

        endDialogueSession(player.getUUID(), npcId);
    }

    private static String decodeDirectDialogue(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String findKeyCaseInsensitive(JsonObject obj, String candidate) {
        for (String key : obj.keySet()) {
            if (key.equalsIgnoreCase(candidate)) {
                return key;
            }
        }
        return null;
    }

    private static boolean isNpcBirthday(String npcId, DayContext dayContext) {
        JsonObject root = NpcDataRegistry.events().get("npc_birthdays");
        if (root == null || !root.has("birthdays") || !root.get("birthdays").isJsonObject()) {
            return false;
        }
        JsonObject birthdays = root.getAsJsonObject("birthdays");
        String key = findKeyCaseInsensitive(birthdays, npcId);
        if (key == null) {
            return false;
        }
        JsonElement el = birthdays.get(key);
        if (el == null || !el.isJsonObject()) {
            return false;
        }

        JsonObject birthday = el.getAsJsonObject();
        String season = birthday.has("season") && birthday.get("season").isJsonPrimitive()
            ? birthday.get("season").getAsString().toLowerCase(Locale.ROOT)
            : "";
        int day = birthday.has("day") && birthday.get("day").isJsonPrimitive() ? birthday.get("day").getAsInt() : -1;
        return day == dayContext.dayInSeason() && season.equals(dayContext.seasonLower());
    }

    private static String normalizeItemId(ItemStack held) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        return itemId == null ? "minecraft:air" : itemId.toString().toLowerCase(Locale.ROOT);
    }

    private static String stardewObjectToken(ItemStack held) {
        return VanillaGiftTasteResolver.objectToken(held);
    }

    private static DayContext currentDayContext(ServerLevel level) {
        StardewTimeManager tm = StardewTimeManager.get();
        if (tm == null) return new DayContext(1, 1, 0, "spring", "Mon", "sunny");
        int dayInSeason = tm.getCurrentDay();
        int dayKey = currentDayKey();
        int weekKey = dayKey / 7;
        String seasonLower = tm.getSeasonName().toLowerCase(Locale.ROOT);
        String weekdayShort = WEEKDAY_SHORT[(Math.max(1, dayInSeason) - 1) % WEEKDAY_SHORT.length];
        String weatherLower = WeatherManager.getCurrentWeather(level).toLowerCase(Locale.ROOT);
        return new DayContext(dayInSeason, dayKey, weekKey, seasonLower, weekdayShort, weatherLower);
    }

    private static int currentDayKey() {
        StardewTimeManager tm = StardewTimeManager.get();
        if (tm == null) return 1;
        return (tm.getCurrentYear() - 1) * DAYS_PER_YEAR + tm.getCurrentSeason() * DAYS_PER_SEASON + tm.getCurrentDay();
    }

    private record GiftProcessResult(
            String responseText,
            ItemStack gift,
            com.stardew.craft.api.v1.npc.StardewNpcGifts.Status status,
            GiftTaste taste,
            String tasteSource,
            boolean birthday,
            int friendshipDelta,
            int resultingPoints
    ) {
        private GiftProcessResult {
            gift = gift.copy();
        }

        @Override
        public ItemStack gift() {
            return gift.copy();
        }

        private static GiftProcessResult rejected(
                String responseText,
                ItemStack gift,
                com.stardew.craft.api.v1.npc.StardewNpcGifts.Status status,
                int resultingPoints
        ) {
            return new GiftProcessResult(
                    responseText,
                    gift,
                    status,
                    null,
                    "",
                    false,
                    0,
                    Math.max(0, resultingPoints));
        }
    }

    enum GiftTaste {
        LOVED,
        LIKED,
        NEUTRAL,
        DISLIKED,
        HATED
    }

    private record GiftTasteResult(
        GiftTaste taste,
        String source
    ) {
    }

    private record DayContext(
        int dayInSeason,
        int dayKey,
        int weekKey,
        String seasonLower,
        String weekdayShort,
        String weatherLower
    ) {
    }
}
