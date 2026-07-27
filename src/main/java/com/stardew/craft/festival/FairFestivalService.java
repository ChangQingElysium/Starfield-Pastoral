package com.stardew.craft.festival;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.festival.fair.FairFishingGameService;
import com.stardew.craft.lostandfound.LostAndFoundService;
import com.stardew.craft.festival.fair.FairSlingshotGameService;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.menu.FairGrangeDisplayMenu;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.network.payload.FairGrangeDisplaySyncPayload;
import com.stardew.craft.network.payload.FairStarTokenPurchaseResultPayload;
import com.stardew.craft.network.payload.FestivalCurrencyHudStatePayload;
import com.stardew.craft.network.payload.FestivalMusicStatePayload;
import com.stardew.craft.network.payload.OpenFairFortunePayload;
import com.stardew.craft.network.payload.OpenFairStarTokenNumberSelectionPayload;
import com.stardew.craft.network.payload.OpenFestivalConfirmPayload;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcCentralMovementService;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.stardew.craft.festival.FestivalNpcActorRuntime.actor;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.actorMap;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.canonical;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.point;
import static com.stardew.craft.festival.FestivalNpcActorRuntime.route;

public final class FairFestivalService {
    public static final String FESTIVAL_ID = "fall16";
    public static final String STAR_TOKEN_SHOP_ID = "Festival_StardewValleyFair_StarTokens";
    public static final String STAR_TOKEN_SHOP_TARGET_ID = "fair_star_token_shop";
    public static final String STAR_TOKEN_SHOP_MARKER_TAG = "sdv_festival_marker:fair_star_token_shop";
    public static final String STAR_TOKEN_PURCHASE_TARGET_ID = "fair_star_token_purchase";
    public static final String STAR_TOKEN_PURCHASE_MARKER_TAG = "sdv_festival_marker:fair_star_token_purchase";
    public static final String FORTUNE_TELLER_TARGET_ID = "fair_fortune_teller";
    public static final String QUESTION_CONTEXT_STAR_TOKEN_PURCHASE = "fair_star_token_purchase";
    public static final String QUESTION_CONTEXT_STAR_TOKEN_PURCHASE_AMOUNT = "fair_star_token_purchase_amount";
    public static final String QUESTION_CONTEXT_FORTUNE_TELLER = "fair_fortune_teller";
    public static final String QUESTION_CONTEXT_GRANGE_JUDGE = "fair_grange_judge";
    public static final String FAIR_ANIMAL_MARKER_TAG = "sdv_festival_marker:fair_animal";
    public static final String FAIR_ANIMAL_PERSISTENT_FLAG = "stardewcraft_fair_temporary_animal";
    private static final String FAIR_ANIMAL_SLOT_TAG_PREFIX = "sdv_festival_marker:fair_animal_slot_";
    public static final String FAIR_STARDROP_FLAG = "CF_Fair";
    private static final String OVERLAY_ID = "Town-Fair";
    private static final String MOVEMENT_OWNER = "stardew_valley_fair";
    private static final String ACTOR_TAG = "stardewcraft_fair_actor";
    private static final String TAG_PARTICIPATING = "stardewcraft_fair_participating";
    private static final String TAG_MUSIC_SYNCED = "stardewcraft_fair_music_synced";
    private static final String TAG_TOKEN_HUD_SYNCED = "stardewcraft_fair_token_hud_synced";
    private static final String TAG_GRANGE_SYNCED = "stardewcraft_fair_grange_synced";
    private static final int STATIC_VERIFY_TICKS = 100;
    private static final int SPAWN_RETRY_TICKS = 100;
    private static final int FESTIVAL_START_MINUTE = 9 * 60;
    private static final int FESTIVAL_END_MINUTE = 22 * 60;
    private static final int GRANGE_UNJUDGED = Integer.MIN_VALUE;
    private static final int GRANGE_PURPLE_SHORTS = -666;
    private static final int GRANGE_CATEGORY_CROP = -75;
    private static final int GRANGE_CATEGORY_FRUIT = -79;
    private static final int GRANGE_CATEGORY_ANIMAL_PRODUCT = -5;
    private static final int GRANGE_CATEGORY_ARTISAN = -26;
    private static final int GRANGE_CATEGORY_FISH = -4;
    private static final int GRANGE_CATEGORY_COOKING = -7;
    private static final int GRANGE_CATEGORY_FORAGE = -81;
    private static final int GRANGE_CATEGORY_MINERAL_ARTIFACT = -12;
    private static final int GRANGE_CATEGORY_MINECRAFT_GEAR = -201;

    private static final AABB ENTRY_EXIT_BOUNDS = inclusiveBox(new BlockPos(-49, 82, -51), new BlockPos(90, 51, 50));
    private static final BlockPos SLINGSHOT_GAME_POS = new BlockPos(-4, 65, 7);
    private static final BlockPos FISHING_GAME_POS = new BlockPos(-4, 65, 16);
    private static final BlockPos STAR_TOKEN_SHOP_POS = new BlockPos(-10, 65, -11);
    private static final BlockPos STAR_TOKEN_PURCHASE_POS = new BlockPos(12, 65, 9);
    private static final BlockPos LEGACY_FORTUNE_TELLER_POS = new BlockPos(52, 65, 39);
    private static final BlockPos GRANGE_DISPLAY_MIN = new BlockPos(11, 64, -6);
    private static final BlockPos GRANGE_DISPLAY_MAX = new BlockPos(13, 64, -4);
    private static final int STAR_TOKEN_PURCHASE_PRICE = 50;
    private static final int MAX_STAR_TOKEN_PURCHASE = 999;
    private static final int ROUTE_WAIT_TICKS = 30;
    private static final int GRANGE_JUDGING_STALL_WAIT_TICKS = 60;
    private static final int GRANGE_JUDGING_RETURN_WAIT_TICKS = 20;
    private static final List<FestivalNpcActorRuntime.Waypoint> GRANGE_JUDGING_ROUTE = List.of(
        point(1, 64, -2, 'N'),
        point(8, 64, -2, 'N'),
        point(12, 64, -2, 'N'),
        point(16, 64, -2, 'N'),
        point(20, 64, -2, 'N'),
        point(1, 64, -11, 'S')
    );
    private static final List<Vec3> GRANGE_JUDGING_ROUTE_TARGETS = GRANGE_JUDGING_ROUTE.stream().map(FestivalNpcActorRuntime.Waypoint::position).toList();
    private static final Map<String, FestivalNpcActorRuntime.ActorDefinition> ACTORS = createActors();
    private static final FestivalNpcActorRuntime NPC_ACTORS = new FestivalNpcActorRuntime(new FestivalNpcActorRuntime.Config(
        "Stardew Valley Fair",
        MOVEMENT_OWNER,
        ACTOR_TAG,
        "fair_",
        ENTRY_EXIT_BOUNDS,
        STATIC_VERIFY_TICKS,
        SPAWN_RETRY_TICKS,
        FestivalNpcActorRuntime.DEFAULT_ROTATE_TICKS,
        false,
        ACTORS
    ), new FestivalNpcActorRuntime.Hooks() {
        @Override
        public boolean beforeMovement(ServerLevel level,
                                      FestivalNpcActorRuntime.ActorDefinition definition,
                                      StardewNpcEntity npc,
                                      FestivalNpcActorRuntime.ActorRuntime actorRuntime,
                                      long now) {
            if ("lewis".equals(definition.npcId()) && grangeJudgingRoutePlayer != null) {
                tickLewisGrangeJudgingRoute(level, npc, now);
                return true;
            }
            return false;
        }
    });
    private static final ActiveFestivalConfirmState CONFIRM_STATE = new ActiveFestivalConfirmState();
    private static final Set<UUID> EXIT_VOTES = CONFIRM_STATE.votes(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> EXIT_VOTE_PARTICIPANTS = CONFIRM_STATE.voteParticipants(OpenFestivalConfirmPayload.Action.EXIT);
    private static final Set<UUID> LEWIS_DIALOGUE_SEEN = new java.util.HashSet<>();
    private static final java.util.Map<UUID, Vec3> LAST_OUTSIDE_ENTRY = new java.util.LinkedHashMap<>();
    private static final java.util.Map<UUID, Vec3> LAST_INSIDE_ENTRY = new java.util.LinkedHashMap<>();
    private static final java.util.Map<UUID, List<ItemStack>> GRANGE_DISPLAYS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<UUID, Integer> GRANGE_SCORES = new java.util.LinkedHashMap<>();
    private static final java.util.Map<UUID, Long> GRANGE_JUDGING_DONE_AT = new java.util.LinkedHashMap<>();
    private static final Set<UUID> GRANGE_REWARD_CLAIMED = new java.util.HashSet<>();
    private static UUID grangeJudgingRoutePlayer;
    private static long grangeJudgingRouteWaitUntil;
    private static boolean grangeJudgingReturned;
    private static Integer frozenMinute;
    private record GrangeScoreProfile(int quality, int price, int category) {
    }

    private record FairAnimalSpawn(Supplier<? extends EntityType<? extends Animal>> type, String managedType, BlockPos pos, float yaw) {
        private String slotTag() {
            return FAIR_ANIMAL_SLOT_TAG_PREFIX + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        }
    }

    private static final List<FairAnimalSpawn> FAIR_ANIMALS = List.of(
        new FairAnimalSpawn(ModEntities.COW, "cow", new BlockPos(53, 64, 13), 180.0F),
        new FairAnimalSpawn(ModEntities.COW, "cow", new BlockPos(55, 64, 13), 180.0F),
        new FairAnimalSpawn(ModEntities.PIG, "pig", new BlockPos(59, 64, 9), 270.0F),
        new FairAnimalSpawn(ModEntities.PIG, "pig", new BlockPos(59, 64, 6), 270.0F),
        new FairAnimalSpawn(ModEntities.WHITE_CHICKEN, "white_chicken", new BlockPos(53, 64, 5), 0.0F),
        new FairAnimalSpawn(ModEntities.WHITE_CHICKEN, "white_chicken", new BlockPos(55, 64, 5), 0.0F)
    );

    private FairFestivalService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean overlayApplied = FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        if (!isActiveFairDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            if (overlayApplied) {
                installFairInteractionBlocks(level);
                ensureFairAnimals(level);
            } else {
                clearRuntimeState(level);
            }
            return;
        }
        if (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            stopTimeFreeze();
        }
        if (overlayApplied || FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            installFairInteractionBlocks(level);
            ensureFairAnimals(level);
        }
        tickGrangeJudging(level);
        syncParticipantClientState(level);
        for (ServerPlayer player : level.players()) {
            tickPlayer(level, player);
        }
    }

    public static void tickNpcActors(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean activeDay = isActiveFairDay();
        boolean debugActive = NPC_ACTORS.isDebugRequested() && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        boolean venueActive = activeDay
            && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)
            && (FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID) || hasCurrentSessionParticipant(level));
        tickActors(level, venueActive || debugActive || FestivalService.isDebugActiveFestival(FESTIVAL_ID));
    }

    public static void startDebugFestival(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalService.setDebugActiveFestival(FESTIVAL_ID);
        startTimeFreeze(level);
        CONFIRM_STATE.clearAll();
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        installFairInteractionBlocks(level);
        ensureFairAnimals(level);
        requestDebugNpcs(level);
        for (ServerPlayer player : level.players()) {
            PlayerStardewDataAPI.setFairStarTokens(player, 0);
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            syncFestivalMusic(player, FestivalMusicStatePayload.FALL_FEST);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            syncStarTokenHud(player, true);
            player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, true);
        }
    }

    public static void restoreDebugFestival(ServerLevel level) {
        FestivalService.clearDebugActiveFestival(FESTIVAL_ID);
        clearRuntimeState(level);
        restoreNpcs(level);
    }

    public static void onMapOverlayApplied(ServerLevel level) {
        installFairInteractionBlocks(level);
        ensureFairAnimals(level);
    }

    public static void requestDebugNpcs(ServerLevel level) {
        NPC_ACTORS.requestDebugStart(level);
        if (level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)) {
            tickActors(level, true);
        }
    }

    public static void restoreNpcs(ServerLevel level) {
        NPC_ACTORS.restore(level);
    }

    public static String debugNpcStatus(ServerLevel level) {
        StardewNpcEntity lewis = level == null ? null : NPC_ACTORS.findActorEntity(level, "lewis");
        String lewisPos = lewis == null ? "missing" : fmt(lewis.position());
        return "Stardew Valley Fair NPC actors: active=" + NPC_ACTORS.isActorsActive()
            + ", debugRequested=" + NPC_ACTORS.isDebugRequested()
            + ", Lewis target=(1.5,64.0,-10.5) yaw=0(S)"
            + ", Lewis actual=" + lewisPos;
    }

    public static boolean controlsNpc(String npcId) {
        return NPC_ACTORS.controlsNpc(npcId);
    }

    public static boolean isParticipant(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.getPersistentData().getBoolean(TAG_PARTICIPATING)) {
            return true;
        }
        return currentSession(player.serverLevel())
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
    }

    public static boolean canUseFairInteraction(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isParticipant(player) || FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            return true;
        }
        return player.level().dimension() == ModDimensions.STARDEW_VALLEY
            && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID)
            && ENTRY_EXIT_BOUNDS.contains(player.position());
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActiveFairDay() && !FestivalService.isDebugActiveFestival(FESTIVAL_ID)) {
            clearFairClientStateIfNeeded(player);
            return;
        }
        boolean sessionParticipant = currentSession(level)
            .map(session -> session.participants().contains(player.getUUID()))
            .orElse(false);
        if (sessionParticipant || player.getPersistentData().getBoolean(TAG_PARTICIPATING)) {
            player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
            startTimeFreeze(level);
            syncFestivalMusic(player, FestivalMusicStatePayload.FALL_FEST);
            player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
            syncStarTokenHud(player, true);
            player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, true);
            syncGrangeDisplay(player, true);
            player.getPersistentData().putBoolean(TAG_GRANGE_SYNCED, true);
            return;
        }
        clearFairClientStateIfNeeded(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        CONFIRM_STATE.clearPlayerDialogs(player.getUUID());
        LAST_OUTSIDE_ENTRY.remove(player.getUUID());
        LAST_INSIDE_ENTRY.remove(player.getUUID());
        if (!EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            checkExitVote(player.serverLevel());
        }
    }

    public static boolean handlesConfirmation(ServerPlayer player, OpenFestivalConfirmPayload.Action action) {
        return CONFIRM_STATE.handlesConfirmation(
            player,
            action,
            Set.of(OpenFestivalConfirmPayload.Action.ENTER, OpenFestivalConfirmPayload.Action.EXIT),
            FairFestivalService::isParticipant,
            FairFestivalService::isActiveFairDay
        );
    }

    public static void onPlayerConfirmed(ServerPlayer player, OpenFestivalConfirmPayload.Action action, boolean confirmed) {
        if (player == null || action == null) {
            return;
        }
        if (action == OpenFestivalConfirmPayload.Action.ENTER) {
            CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.ENTER);
            if (confirmed) {
                enterFestival(player);
            }
            return;
        }
        if (action != OpenFestivalConfirmPayload.Action.EXIT) {
            return;
        }
        CONFIRM_STATE.closeDialog(player, OpenFestivalConfirmPayload.Action.EXIT);
        if (!isParticipant(player)) {
            return;
        }
        if (confirmed) {
            castExitVote(player);
        } else {
            EXIT_VOTES.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.exit_vote_cancelled"), true);
        }
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        return false;
    }

    public static boolean openStarTokenShop(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return false;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(STAR_TOKEN_SHOP_ID);
        if (shop == null) {
            player.displayClientMessage(Component.literal("Unknown shopId: " + STAR_TOKEN_SHOP_ID), true);
            return false;
        }
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(STAR_TOKEN_SHOP_ID, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            STAR_TOKEN_SHOP_ID,
            PlayerStardewDataAPI.getFairStarTokens(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        ));
        return true;
    }

    public static boolean openStarTokenPurchase(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return false;
        }
        sendQuestion(
            player,
            QUESTION_CONTEXT_STAR_TOKEN_PURCHASE,
            0,
            Component.translatable("stardewcraft.fair.star_tokens.buy.question"),
            List.of(
                response("buy", Component.translatable("stardewcraft.fair.star_tokens.buy"), player),
                response("leave", Component.translatable("stardewcraft.fair.star_tokens.leave"), player)
            )
        );
        return true;
    }

    public static boolean openFortuneTeller(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return false;
        }
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (data.hasMailFlag(fortuneTellerFlag())) {
            ObjectDialogueService.show(player, "stardewcraft.fair.fortune.already_read");
            return true;
        }
        if (PlayerStardewDataAPI.getMoney(player) < 100) {
            ObjectDialogueService.show(player, "stardewcraft.fair.fortune.no_money");
            return true;
        }
        sendQuestion(
            player,
            QUESTION_CONTEXT_FORTUNE_TELLER,
            0,
            Component.translatable("stardewcraft.fair.fortune.question"),
            List.of(
                response("read", Component.translatable("stardewcraft.fair.fortune.read"), player),
                response("no", Component.translatable("stardewcraft.fair.fortune.no"), player)
            )
        );
        return true;
    }

    public static boolean openGrangeDisplay(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return false;
        }
        SimpleContainer container = new SimpleContainer(FairGrangeDisplayMenu.DISPLAY_SIZE);
        loadGrangeDisplayContainer(container, displayFor(player));
        Runnable saveDisplay = () -> saveGrangeDisplayContainer(player, container);
        player.openMenu(new SimpleMenuProvider(
            (containerId, playerInventory, menuPlayer) -> new FairGrangeDisplayMenu(
                containerId,
                playerInventory,
                container,
                saveDisplay,
                currentPlayer -> currentPlayer == player && canUseFairInteraction(player)
            ),
            Component.translatable("stardewcraft.fair.grange.title")
        ));
        syncGrangeDisplay(player, true);
        return true;
    }

    public static boolean openLewisGrangeJudge(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return false;
        }
        if (GRANGE_JUDGING_DONE_AT.containsKey(player.getUUID())) {
            ObjectDialogueService.show(player, "stardewcraft.fair.grange.judging_in_progress");
            return true;
        }
        if (grangeJudgingRoutePlayer != null) {
            ObjectDialogueService.show(player, "stardewcraft.fair.grange.judging_in_progress");
            return true;
        }
        int score = GRANGE_SCORES.getOrDefault(player.getUUID(), GRANGE_UNJUDGED);
        if (score != GRANGE_UNJUDGED && !GRANGE_REWARD_CLAIMED.contains(player.getUUID())) {
            giveGrangeReward(player, score);
            return true;
        }
        if (score != GRANGE_UNJUDGED) {
            ObjectDialogueService.show(player, "stardewcraft.fair.grange.already_claimed");
            return true;
        }
        sendQuestion(
            player,
            QUESTION_CONTEXT_GRANGE_JUDGE,
            0,
            Component.translatable("stardewcraft.fair.grange.judge.question"),
            List.of(
                response("yes", Component.translatable("stardewcraft.fair.grange.judge.yes"), player),
                response("no", Component.translatable("stardewcraft.fair.grange.judge.no"), player)
            )
        );
        return true;
    }

    public static ItemInteractionResult handleFairSpruceTableUseItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!shouldHandleFairSpruceTable(state, level, pos)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && isPlayerGrangeDisplayTable(pos)) {
            openGrangeDisplay(serverPlayer);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult handleFairSpruceTableUse(BlockState state, Level level, BlockPos pos, Player player) {
        if (!shouldHandleFairSpruceTable(state, level, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && isPlayerGrangeDisplayTable(pos)) {
            openGrangeDisplay(serverPlayer);
        }
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, String choiceId) {
        if (player == null || context == null || choiceId == null) {
            return;
        }
        if (QUESTION_CONTEXT_STAR_TOKEN_PURCHASE.equals(context)) {
            if ("buy".equals(choiceId)) {
                openStarTokenPurchaseAmount(player);
            }
            return;
        }
        if (QUESTION_CONTEXT_FORTUNE_TELLER.equals(context) && "read".equals(choiceId)) {
            readFortune(player);
            return;
        }
        if (QUESTION_CONTEXT_GRANGE_JUDGE.equals(context) && "yes".equals(choiceId)) {
            startGrangeJudging(player);
        }
    }

    public static void purchaseStarTokensFromNumberSelection(ServerPlayer player, int requestedAmount) {
        if (player == null || !canUseFairInteraction(player)) {
            sendStarTokenPurchaseResult(player, false);
            return;
        }
        int amount = Math.max(0, Math.min(MAX_STAR_TOKEN_PURCHASE, requestedAmount));
        long cost = (long) amount * STAR_TOKEN_PURCHASE_PRICE;
        var payment = amount > 0
            ? StardewCosts.pay(player, StardewCost.of(
                new StardewCurrencyCost(StardewCurrencies.MONEY, cost)))
            : null;
        if (payment != null && payment.success()
                && StardewCurrencies.deposit(
                    StardewCurrencies.FAIR_STAR_TOKENS, player, amount)) {
            player.playNotifySound(ModSounds.PURCHASE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            sendStarTokenPurchaseResult(player, true);
            return;
        }
        if (payment != null && payment.success()) {
            payment.receipt().orElseThrow().refund();
        }
        sendStarTokenPurchaseResult(player, false);
    }

    public static boolean isMainEventActive() {
        return false;
    }

    public static boolean tryStartMainEvent(ServerPlayer player) {
        if (player == null || !isParticipant(player) || !LEWIS_DIALOGUE_SEEN.contains(player.getUUID())) {
            return false;
        }
        return openLewisGrangeJudge(player);
    }

    public static void markFestivalDialogueSeen(ServerPlayer player, String npcId) {
        if (player != null && "lewis".equals(canonical(npcId))) {
            LEWIS_DIALOGUE_SEEN.add(player.getUUID());
        }
    }

    public static String resolveDialogueKey(ServerPlayer player, String npcId) {
        String canonicalId = canonical(npcId);
        if (canonicalId.isBlank()) {
            return null;
        }
        if (player != null && GRANGE_JUDGING_DONE_AT.containsKey(player.getUUID())) {
            String judgingKey = fairJudgingDialogueKey(canonicalId);
            if (judgingKey != null) {
                return judgingKey;
            }
        }
        if (player != null) {
            int score = GRANGE_SCORES.getOrDefault(player.getUUID(), GRANGE_UNJUDGED);
            if (score != GRANGE_UNJUDGED) {
                String judgedKey = fairJudgedDialogueKey(canonicalId, score);
                if (judgedKey != null) {
                    return judgedKey;
                }
            }
        }
        int year = StardewTimeManager.get().getCurrentYear();
        return FestivalDialogueService.resolveDialogueKey(FESTIVAL_ID, canonicalId, year);
    }

    private static String fairJudgingDialogueKey(String npcId) {
        return switch (canonical(npcId)) {
            case "pierre" -> "stardewcraft.npc.pierre.fair_judging";
            case "marnie" -> "stardewcraft.npc.marnie.fair_judging";
            case "willy" -> "stardewcraft.npc.willy.fair_judging";
            default -> null;
        };
    }

    private static String fairJudgedDialogueKey(String npcId, int score) {
        return switch (canonical(npcId)) {
            case "pierre" -> score >= 90
                ? "stardewcraft.npc.pierre.fair_judged_playerwon"
                : "stardewcraft.npc.pierre.fair_judged_playerlost";
            case "marnie" -> score == GRANGE_PURPLE_SHORTS
                ? "stardewcraft.npc.marnie.fair_judged_playerlost_purpleshorts"
                : "stardewcraft.npc.marnie.fair_judged";
            case "willy" -> "stardewcraft.npc.willy.fair_judged";
            default -> null;
        };
    }

    public static boolean isTimeFreezeActive() {
        if (frozenMinute == null) {
            return false;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(ModDimensions.STARDEW_VALLEY);
        return stardewLevel != null && stardewLevel.players().stream().anyMatch(FairFestivalService::isParticipant);
    }

    public static long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
        long currentVirtual = timeManager.getVirtualDayTime();
        if (frozenMinute == null || (!hasCurrentSessionParticipant(level) && !FestivalService.isDebugActiveFestival(FESTIVAL_ID))) {
            return currentVirtual;
        }
        long dayBase = Math.floorDiv(currentVirtual, 24000L) * 24000L;
        long target = dayBase + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(frozenMinute);
        if (currentVirtual != target) {
            timeManager.setVirtualDayTime(target);
        }
        return target;
    }

    public static String debugStatus(ServerLevel level) {
        int participants = level == null ? 0 : (int) level.players().stream().filter(FairFestivalService::isParticipant).count();
        boolean overlayApplied = level != null && FestivalMapOverlayManager.isApplied(level, OVERLAY_ID);
        String slingshotBlock = "unloaded";
        String slingshotTarget = "none";
        String fishingBlock = "unloaded";
        String fishingTarget = "none";
        String shopBlock = "unloaded";
        String shopTarget = "none";
        String tokenPurchaseBlock = "unloaded";
        String tokenPurchaseTarget = "none";
        int fairAnimals = 0;
        if (level != null) {
            slingshotBlock = String.valueOf(level.getBlockState(SLINGSHOT_GAME_POS).getBlock());
            if (level.getBlockEntity(SLINGSHOT_GAME_POS) instanceof PortalTriggerBlockEntity blockEntity) {
                slingshotTarget = blockEntity.getTargetId();
            }
            fishingBlock = String.valueOf(level.getBlockState(FISHING_GAME_POS).getBlock());
            if (level.getBlockEntity(FISHING_GAME_POS) instanceof PortalTriggerBlockEntity blockEntity) {
                fishingTarget = blockEntity.getTargetId();
            }
            shopBlock = String.valueOf(level.getBlockState(STAR_TOKEN_SHOP_POS).getBlock());
            if (level.getBlockEntity(STAR_TOKEN_SHOP_POS) instanceof PortalTriggerBlockEntity blockEntity) {
                shopTarget = blockEntity.getTargetId();
            }
            tokenPurchaseBlock = String.valueOf(level.getBlockState(STAR_TOKEN_PURCHASE_POS).getBlock());
            if (level.getBlockEntity(STAR_TOKEN_PURCHASE_POS) instanceof PortalTriggerBlockEntity blockEntity) {
                tokenPurchaseTarget = blockEntity.getTargetId();
            }
            fairAnimals = level.getEntitiesOfClass(BaseCoopAnimalEntity.class, ENTRY_EXIT_BOUNDS,
                entity -> entity.getTags().contains(FAIR_ANIMAL_MARKER_TAG)).size();
        }
        return "Stardew Valley Fair: participants=" + participants
            + ", overlayApplied=" + overlayApplied
            + ", timeFreeze=" + isTimeFreezeActive()
            + ", entryExitBounds=(-49,51,-51)..(90,82,50)"
            + ", slingshotInteractionPos=" + SLINGSHOT_GAME_POS.toShortString()
            + ", slingshotBlock=" + slingshotBlock
            + ", slingshotTarget=" + slingshotTarget
            + ", fishingInteractionPos=" + FISHING_GAME_POS.toShortString()
            + ", fishingBlock=" + fishingBlock
            + ", fishingTarget=" + fishingTarget
            + ", starTokenShopPos=" + STAR_TOKEN_SHOP_POS.toShortString()
            + ", starTokenShopBlock=" + shopBlock
            + ", starTokenShopTarget=" + shopTarget
            + ", starTokenPurchasePos=" + STAR_TOKEN_PURCHASE_POS.toShortString()
            + ", starTokenPurchaseBlock=" + tokenPurchaseBlock
            + ", starTokenPurchaseTarget=" + tokenPurchaseTarget
            + ", grangeDisplayTables=(11,64,-6)..(13,64,-4)"
            + ", lewisNpcPos=1,64,-11,S"
            + ", npcStatus={" + debugNpcStatus(level) + "}"
            + ", fairAnimals=" + fairAnimals
            + ", npc/minigame zones pending confirmation";
    }

    private static List<ItemStack> displayFor(ServerPlayer player) {
        List<ItemStack> display = GRANGE_DISPLAYS.computeIfAbsent(player.getUUID(), ignored -> emptyDisplay());
        while (display.size() < 9) {
            display.add(ItemStack.EMPTY);
        }
        if (display.size() > 9) {
            return display.subList(0, 9);
        }
        return display;
    }

    private static List<ItemStack> emptyDisplay() {
        List<ItemStack> display = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            display.add(ItemStack.EMPTY);
        }
        return display;
    }

    private static List<ItemStack> copyDisplay(List<ItemStack> display) {
        List<ItemStack> copy = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            copy.add(i < display.size() ? display.get(i).copy() : ItemStack.EMPTY);
        }
        return List.copyOf(copy);
    }

    private static void syncGrangeDisplay(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayer(player, new FairGrangeDisplaySyncPayload(active, active ? copyDisplay(displayFor(player)) : emptyDisplay()));
    }

    private static void loadGrangeDisplayContainer(Container container, List<ItemStack> display) {
        for (int i = 0; i < FairGrangeDisplayMenu.DISPLAY_SIZE; i++) {
            ItemStack stack = i < display.size() ? display.get(i).copy() : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                stack.setCount(1);
            }
            container.setItem(i, stack);
        }
    }

    private static void saveGrangeDisplayContainer(ServerPlayer player, Container container) {
        if (player == null || container == null || !canUseFairInteraction(player)) {
            return;
        }
        List<ItemStack> display = displayFor(player);
        boolean changed = false;
        for (int i = 0; i < FairGrangeDisplayMenu.DISPLAY_SIZE; i++) {
            ItemStack stack = container.getItem(i);
            ItemStack normalized = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!normalized.isEmpty()) {
                normalized.setCount(1);
            }
            if (!ItemStack.matches(display.get(i), normalized)) {
                display.set(i, normalized);
                changed = true;
            }
        }
        if (changed) {
            GRANGE_SCORES.remove(player.getUUID());
            GRANGE_JUDGING_DONE_AT.remove(player.getUUID());
            GRANGE_REWARD_CLAIMED.remove(player.getUUID());
            cancelGrangeJudgingFor(player.serverLevel(), player.getUUID());
            syncGrangeDisplay(player, true);
        }
    }

    private static boolean shouldHandleFairSpruceTable(BlockState state, Level level, BlockPos pos) {
        if (state == null || level == null || pos == null || !state.is(ModBlocks.SPRUCE_TABLE.get())) {
            return false;
        }
        if (!ENTRY_EXIT_BOUNDS.contains(Vec3.atCenterOf(pos))) {
            return false;
        }
        if (level.isClientSide) {
            return isPlayerGrangeDisplayTable(pos);
        }
        return level instanceof ServerLevel serverLevel
            && serverLevel.dimension() == ModDimensions.STARDEW_VALLEY
            && (FestivalMapOverlayManager.isApplied(serverLevel, OVERLAY_ID) || FestivalService.isDebugActiveFestival(FESTIVAL_ID));
    }

    public static boolean isPlayerGrangeDisplayTable(BlockPos pos) {
        return pos != null
            && pos.getY() == GRANGE_DISPLAY_MIN.getY()
            && pos.getX() >= GRANGE_DISPLAY_MIN.getX()
            && pos.getX() <= GRANGE_DISPLAY_MAX.getX()
            && pos.getZ() >= GRANGE_DISPLAY_MIN.getZ()
            && pos.getZ() <= GRANGE_DISPLAY_MAX.getZ();
    }

    public static boolean isEligibleGrangeDisplayItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.LUCKY_PURPLE_SHORTS.get())) {
            return true;
        }
        return grangeScoreProfile(stack) != null;
    }

    private static int judgeGrange(ServerPlayer player) {
        int pointsEarned = 14;
        Set<Integer> categoriesRepresented = new java.util.HashSet<>();
        int nullsCount = 0;
        boolean purpleShorts = false;
        for (ItemStack stack : displayFor(player)) {
            if (stack == null || stack.isEmpty()) {
                nullsCount++;
                continue;
            }
            if (stack.is(ModItems.LUCKY_PURPLE_SHORTS.get())) {
                purpleShorts = true;
            }
            GrangeScoreProfile profile = grangeScoreProfile(stack);
            if (profile == null) {
                continue;
            }
            int quality = Math.max(0, Math.min(QualityHelper.IRIDIUM, profile.quality()));
            pointsEarned += quality + 1;
            int price = Math.max(0, profile.price());
            if (price >= 20) {
                pointsEarned++;
            }
            if (price >= 90) {
                pointsEarned++;
            }
            if (price >= 200) {
                pointsEarned++;
            }
            if (price >= 300 && quality < QualityHelper.GOLD) {
                pointsEarned++;
            }
            if (price >= 400 && quality < QualityHelper.SILVER) {
                pointsEarned++;
            }
            int category = profile.category();
            if (category != 0) {
                categoriesRepresented.add(category);
            }
        }
        pointsEarned += Math.min(30, categoriesRepresented.size() * 5);
        pointsEarned += 9 - (2 * nullsCount);
        return purpleShorts ? GRANGE_PURPLE_SHORTS : pointsEarned;
    }

    private static void startGrangeJudging(ServerPlayer player) {
        if (player == null || !canUseFairInteraction(player)) {
            return;
        }
        int score = judgeGrange(player);
        UUID playerId = player.getUUID();
        GRANGE_SCORES.put(playerId, score);
        GRANGE_REWARD_CLAIMED.remove(playerId);
        GRANGE_JUDGING_DONE_AT.put(playerId, player.serverLevel().getGameTime());
        grangeJudgingRoutePlayer = playerId;
        grangeJudgingRouteWaitUntil = 0L;
        grangeJudgingReturned = false;
        NpcCentralMovementService.resetAuthoredMovementPlan("lewis", MOVEMENT_OWNER);
    }

    private static void tickGrangeJudging(ServerLevel level) {
        if (level == null || grangeJudgingRoutePlayer == null) {
            return;
        }
        if (!GRANGE_JUDGING_DONE_AT.containsKey(grangeJudgingRoutePlayer)) {
            resetLewisGrangeJudgingRoute(level);
        }
    }

    private static GrangeScoreProfile grangeScoreProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        if (!typeKey.isBlank()) {
            int price = Math.max(0, StardewItemDataApi.getSellPrice(stack));
            if (price <= 0
                || "stardewcraft.type.tool".equals(typeKey)
                || "stardewcraft.type.furniture".equals(typeKey)
                || "stardewcraft.type.seed".equals(typeKey)) {
                return null;
            }
            int quality = Math.max(0, Math.min(QualityHelper.IRIDIUM, QualityHelper.getQuality(stack)));
            return new GrangeScoreProfile(quality, price, grangeCategory(typeKey));
        }
        return vanillaGrangeScoreProfile(stack);
    }

    private static GrangeScoreProfile vanillaGrangeScoreProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"minecraft".equals(itemId.getNamespace()) || isDisallowedVanillaGrangeItem(stack, itemId)) {
            return null;
        }
        if (isVanillaGear(stack, itemId)) {
            return vanillaGearProfile(stack, itemId);
        }
        int category = vanillaGrangeCategory(stack, itemId);
        int price = vanillaGrangePrice(stack, itemId, category);
        if (price <= 0) {
            return null;
        }
        int quality = vanillaDisplayQuality(stack, itemId, price, category);
        return new GrangeScoreProfile(clamp(quality, QualityHelper.NORMAL, QualityHelper.IRIDIUM), price, category);
    }

    private static boolean isDisallowedVanillaGrangeItem(ItemStack stack, ResourceLocation itemId) {
        String path = itemId.getPath();
        if (path.endsWith("shulker_box") || path.equals("bundle") || path.contains("spawner")) {
            return true;
        }
        return isAny(stack,
            Items.AIR,
            Items.BEDROCK,
            Items.BARRIER,
            Items.COMMAND_BLOCK,
            Items.CHAIN_COMMAND_BLOCK,
            Items.REPEATING_COMMAND_BLOCK,
            Items.STRUCTURE_BLOCK,
            Items.STRUCTURE_VOID,
            Items.JIGSAW,
            Items.DEBUG_STICK,
            Items.KNOWLEDGE_BOOK,
            Items.LIGHT,
            Items.PLAYER_HEAD
        );
    }

    private static GrangeScoreProfile vanillaGearProfile(ItemStack stack, ResourceLocation itemId) {
        String path = itemId.getPath();
        int price = vanillaGearBasePrice(path);
        EnchantmentScore enchantments = enchantmentScore(stack);
        if (enchantments.weight() > 0) {
            price += Math.min(150, enchantments.weight() * 18 + enchantments.count() * 12);
        }
        price -= enchantments.curses() * 35;

        int quality = enchantmentQuality(enchantments);
        double durability = durabilityRatio(stack);
        if (durability < 0.25D) {
            price = Math.round(price * 0.50F);
            quality -= 2;
        } else if (durability < 0.65D) {
            price = Math.round(price * 0.75F);
            quality -= 1;
        } else if (durability >= 0.98D && (path.startsWith("diamond_") || path.startsWith("netherite_"))) {
            quality = Math.max(quality, QualityHelper.SILVER);
        }

        return new GrangeScoreProfile(
            clamp(quality, QualityHelper.NORMAL, QualityHelper.IRIDIUM),
            clamp(price, 20, 400),
            GRANGE_CATEGORY_MINECRAFT_GEAR
        );
    }

    private static int vanillaGearBasePrice(String path) {
        if (path.startsWith("netherite_")) {
            return 400;
        }
        if (path.equals("elytra")) {
            return 400;
        }
        if (path.startsWith("diamond_") || path.equals("trident") || path.equals("mace")) {
            return 300;
        }
        if (path.equals("turtle_helmet")) {
            return 250;
        }
        if (path.startsWith("iron_") || path.equals("shield")) {
            return 200;
        }
        if (path.equals("crossbow")) {
            return 150;
        }
        if (path.startsWith("chainmail_")) {
            return 125;
        }
        if (path.startsWith("golden_") || path.equals("bow")) {
            return 90;
        }
        if (path.startsWith("leather_")) {
            return 60;
        }
        if (path.startsWith("stone_")) {
            return 35;
        }
        return 20;
    }

    private static boolean isVanillaGear(ItemStack stack, ResourceLocation itemId) {
        String path = itemId.getPath();
        return stack.is(ItemTags.SWORDS)
            || stack.is(ItemTags.PICKAXES)
            || stack.is(ItemTags.AXES)
            || stack.is(ItemTags.SHOVELS)
            || stack.is(ItemTags.HOES)
            || path.endsWith("_helmet")
            || path.endsWith("_chestplate")
            || path.endsWith("_leggings")
            || path.endsWith("_boots")
            || path.equals("bow")
            || path.equals("crossbow")
            || path.equals("trident")
            || path.equals("shield")
            || path.equals("mace");
    }

    private static int vanillaGrangeCategory(ItemStack stack, ResourceLocation itemId) {
        String path = itemId.getPath();
        if (isAny(stack, Items.WHEAT, Items.CARROT, Items.POTATO, Items.POISONOUS_POTATO,
            Items.BEETROOT, Items.PUMPKIN, Items.SUGAR_CANE, Items.CACTUS, Items.COCOA_BEANS)) {
            return GRANGE_CATEGORY_CROP;
        }
        if (isAny(stack, Items.APPLE, Items.MELON_SLICE, Items.MELON, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.CHORUS_FRUIT)) {
            return GRANGE_CATEGORY_FRUIT;
        }
        if (path.endsWith("_wool") || isAny(stack, Items.EGG, Items.TURTLE_EGG, Items.MILK_BUCKET, Items.LEATHER, Items.RABBIT_HIDE,
            Items.RABBIT_FOOT, Items.FEATHER, Items.HONEYCOMB, Items.HONEY_BOTTLE, Items.TURTLE_SCUTE,
            Items.ARMADILLO_SCUTE)) {
            return GRANGE_CATEGORY_ANIMAL_PRODUCT;
        }
        if (isAny(stack, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH)) {
            return GRANGE_CATEGORY_FISH;
        }
        if (isVanillaCookedFood(stack)) {
            return GRANGE_CATEGORY_COOKING;
        }
        if (path.endsWith("_flower") || path.endsWith("_sapling") || path.endsWith("_mushroom")
            || isAny(stack, Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET,
                Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY,
                Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.WITHER_ROSE, Items.SUNFLOWER, Items.LILAC,
                Items.ROSE_BUSH, Items.PEONY, Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, Items.KELP, Items.SEAGRASS,
                Items.VINE, Items.BAMBOO, Items.FERN, Items.LARGE_FERN, Items.DEAD_BUSH, Items.LILY_PAD)) {
            return GRANGE_CATEGORY_FORAGE;
        }
        if (isVanillaMineralOrArtifact(stack, itemId)) {
            return GRANGE_CATEGORY_MINERAL_ARTIFACT;
        }
        if (isAny(stack, Items.PAPER, Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.FILLED_MAP,
            Items.MAP, Items.CLOCK, Items.COMPASS, Items.SPYGLASS, Items.FIREWORK_ROCKET)) {
            return GRANGE_CATEGORY_ARTISAN;
        }
        return 0;
    }

    private static int vanillaGrangePrice(ItemStack stack, ResourceLocation itemId, int category) {
        String path = itemId.getPath();
        if (path.endsWith("_smithing_template") || path.equals("dragon_egg") || path.equals("nether_star")
            || path.equals("elytra")) {
            return 400;
        }
        if (path.startsWith("music_disc_") || path.endsWith("_pottery_sherd") || path.equals("totem_of_undying")
            || path.equals("heart_of_the_sea") || path.equals("enchanted_golden_apple")) {
            return path.equals("enchanted_golden_apple") ? 400 : 300;
        }
        if (path.equals("netherite_ingot") || path.equals("netherite_block")) {
            return 400;
        }
        if (path.equals("ancient_debris") || path.equals("netherite_scrap")) {
            return 300;
        }
        if (stack.is(Items.ENCHANTED_BOOK)) {
            EnchantmentScore score = enchantmentScore(stack);
            return clamp(90 + score.weight() * 28 + score.count() * 12 - score.curses() * 35, 20, 400);
        }
        if (path.endsWith("_block")) {
            return vanillaResourceBlockPrice(path);
        }
        if (category == GRANGE_CATEGORY_CROP) {
            if (stack.is(Items.PUMPKIN) || stack.is(Items.COCOA_BEANS)) {
                return 90;
            }
            if (stack.is(Items.CACTUS)) {
                return 75;
            }
            return 35;
        }
        if (category == GRANGE_CATEGORY_FRUIT) {
            if (stack.is(Items.MELON)) {
                return 90;
            }
            if (stack.is(Items.CHORUS_FRUIT) || stack.is(Items.GLOW_BERRIES)) {
                return 80;
            }
            return 50;
        }
        if (category == GRANGE_CATEGORY_ANIMAL_PRODUCT) {
            if (stack.is(Items.TURTLE_EGG) || stack.is(Items.RABBIT_FOOT)) {
                return 200;
            }
            if (stack.is(Items.TURTLE_SCUTE) || stack.is(Items.MILK_BUCKET) || stack.is(Items.HONEY_BOTTLE)) {
                return 125;
            }
            if (stack.is(Items.ARMADILLO_SCUTE)) {
                return 80;
            }
            if (path.endsWith("_wool")) {
                return 80;
            }
            return 50;
        }
        if (category == GRANGE_CATEGORY_FISH) {
            if (stack.is(Items.PUFFERFISH)) {
                return 200;
            }
            if (stack.is(Items.TROPICAL_FISH)) {
                return 150;
            }
            return stack.is(Items.SALMON) ? 100 : 75;
        }
        if (category == GRANGE_CATEGORY_COOKING) {
            return vanillaFoodPrice(stack);
        }
        if (category == GRANGE_CATEGORY_FORAGE) {
            if (stack.is(Items.WITHER_ROSE) || stack.is(Items.LILY_OF_THE_VALLEY)) {
                return 90;
            }
            if (stack.is(Items.BAMBOO) || stack.is(Items.KELP) || stack.is(Items.SEAGRASS) || stack.is(Items.DEAD_BUSH)) {
                return 20;
            }
            return 40;
        }
        if (category == GRANGE_CATEGORY_MINERAL_ARTIFACT) {
            return vanillaMineralOrArtifactPrice(stack, path);
        }
        if (category == GRANGE_CATEGORY_ARTISAN) {
            return stack.is(Items.SPYGLASS) || stack.is(Items.CLOCK) ? 150 : 40;
        }
        return stack.getItem() instanceof BlockItem ? 5 : 10;
    }

    private static int vanillaResourceBlockPrice(String path) {
        if (path.equals("diamond_block") || path.equals("emerald_block")) {
            return 400;
        }
        if (path.equals("gold_block") || path.equals("iron_block")) {
            return 300;
        }
        if (path.equals("lapis_block") || path.equals("redstone_block") || path.equals("amethyst_block")) {
            return 200;
        }
        if (path.equals("coal_block") || path.equals("copper_block") || path.equals("raw_copper_block")
            || path.equals("raw_iron_block") || path.equals("raw_gold_block")) {
            return 90;
        }
        return 20;
    }

    private static boolean isVanillaCookedFood(ItemStack stack) {
        return isAny(stack,
            Items.BREAD, Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.MUSHROOM_STEW, Items.RABBIT_STEW,
            Items.BEETROOT_SOUP, Items.SUSPICIOUS_STEW, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON,
            Items.DRIED_KELP, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.ROTTEN_FLESH, Items.SPIDER_EYE
        );
    }

    private static int vanillaFoodPrice(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 400;
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            return 300;
        }
        if (stack.is(Items.GOLDEN_CARROT) || stack.is(Items.CAKE) || stack.is(Items.RABBIT_STEW)) {
            return 200;
        }
        if (stack.is(Items.PUMPKIN_PIE) || stack.is(Items.MUSHROOM_STEW) || stack.is(Items.SUSPICIOUS_STEW)) {
            return 120;
        }
        if (stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) || stack.is(Items.COOKED_SALMON)) {
            return 100;
        }
        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE)) {
            return 20;
        }
        return 60;
    }

    private static boolean isVanillaMineralOrArtifact(ItemStack stack, ResourceLocation itemId) {
        String path = itemId.getPath();
        return path.endsWith("_ore")
            || path.endsWith("_ingot")
            || path.endsWith("_nugget")
            || path.endsWith("_crystal")
            || path.endsWith("_shard")
            || path.endsWith("_sherd")
            || path.endsWith("_smithing_template")
            || path.startsWith("music_disc_")
            || isResourceBlockPath(path)
            || isAny(stack, Items.COAL, Items.CHARCOAL, Items.RAW_COPPER, Items.RAW_IRON, Items.RAW_GOLD,
                Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.QUARTZ, Items.AMETHYST_SHARD, Items.DIAMOND, Items.EMERALD, Items.NETHERITE_SCRAP,
                Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS, Items.BLAZE_ROD, Items.BLAZE_POWDER,
                Items.ENDER_PEARL, Items.ECHO_SHARD, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD,
                Items.NAUTILUS_SHELL, Items.HEART_OF_THE_SEA, Items.TOTEM_OF_UNDYING, Items.NETHER_STAR,
                Items.DRAGON_EGG, Items.ELYTRA, Items.ENCHANTED_BOOK, Items.EXPERIENCE_BOTTLE, Items.NAME_TAG, Items.SADDLE);
    }

    private static boolean isResourceBlockPath(String path) {
        return path.equals("diamond_block")
            || path.equals("emerald_block")
            || path.equals("gold_block")
            || path.equals("iron_block")
            || path.equals("lapis_block")
            || path.equals("redstone_block")
            || path.equals("amethyst_block")
            || path.equals("coal_block")
            || path.equals("copper_block")
            || path.equals("raw_copper_block")
            || path.equals("raw_iron_block")
            || path.equals("raw_gold_block");
    }

    private static int vanillaMineralOrArtifactPrice(ItemStack stack, String path) {
        if (path.endsWith("_smithing_template") || path.equals("dragon_egg") || path.equals("nether_star")) {
            return 400;
        }
        if (path.startsWith("music_disc_") || path.endsWith("_pottery_sherd") || path.equals("totem_of_undying")
            || path.equals("heart_of_the_sea") || path.equals("ancient_debris") || path.equals("netherite_scrap")) {
            return 300;
        }
        if (stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.NAUTILUS_SHELL)
            || stack.is(Items.ECHO_SHARD) || stack.is(Items.SADDLE) || stack.is(Items.NAME_TAG)) {
            return 250;
        }
        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.BLAZE_ROD) || stack.is(Items.ENDER_PEARL)
            || stack.is(Items.PRISMARINE_CRYSTALS)) {
            return 150;
        }
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.AMETHYST_SHARD)
            || stack.is(Items.QUARTZ) || stack.is(Items.EXPERIENCE_BOTTLE)) {
            return 90;
        }
        if (stack.is(Items.REDSTONE) || stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD)
            || stack.is(Items.COPPER_INGOT)) {
            return 60;
        }
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || path.endsWith("_nugget")) {
            return 20;
        }
        return 40;
    }

    private static int vanillaDisplayQuality(ItemStack stack, ResourceLocation itemId, int price, int category) {
        EnchantmentScore enchantments = enchantmentScore(stack);
        int quality = enchantmentQuality(enchantments);
        String path = itemId.getPath();
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) || path.equals("dragon_egg") || path.equals("nether_star")) {
            quality = Math.max(quality, QualityHelper.GOLD);
        } else if (price >= 400 || path.endsWith("_smithing_template")) {
            quality = Math.max(quality, QualityHelper.SILVER);
        } else if (category == GRANGE_CATEGORY_MINERAL_ARTIFACT && price >= 300) {
            quality = Math.max(quality, QualityHelper.NORMAL);
        }
        return quality;
    }

    private record EnchantmentScore(int count, int weight, int curses) {
    }

    private static EnchantmentScore enchantmentScore(ItemStack stack) {
        EnchantmentScore direct = enchantmentScore(stack.get(DataComponents.ENCHANTMENTS));
        EnchantmentScore stored = enchantmentScore(stack.get(DataComponents.STORED_ENCHANTMENTS));
        return new EnchantmentScore(
            direct.count() + stored.count(),
            direct.weight() + stored.weight(),
            direct.curses() + stored.curses()
        );
    }

    private static EnchantmentScore enchantmentScore(ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return new EnchantmentScore(0, 0, 0);
        }
        int count = 0;
        int weight = 0;
        int curses = 0;
        for (var entry : enchantments.entrySet()) {
            int level = Math.max(0, entry.getIntValue());
            if (level <= 0) {
                continue;
            }
            count++;
            weight += level;
            String path = entry.getKey().unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("");
            if (path.contains("curse")) {
                curses++;
            }
        }
        return new EnchantmentScore(count, weight, curses);
    }

    private static int enchantmentQuality(EnchantmentScore enchantments) {
        int adjustedWeight = Math.max(0, enchantments.weight() - enchantments.curses() * 2);
        if (adjustedWeight >= 12 || enchantments.count() >= 5) {
            return QualityHelper.IRIDIUM;
        }
        if (adjustedWeight >= 7 || enchantments.count() >= 3) {
            return QualityHelper.GOLD;
        }
        if (adjustedWeight >= 2 || enchantments.count() >= 1) {
            return QualityHelper.SILVER;
        }
        return QualityHelper.NORMAL;
    }

    private static double durabilityRatio(ItemStack stack) {
        if (stack == null || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage()));
    }

    private static boolean isAny(ItemStack stack, Item... items) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Item item : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int grangeCategory(String itemTypeKey) {
        if (itemTypeKey == null || itemTypeKey.isBlank()) {
            return 0;
        }
        return switch (itemTypeKey) {
            case "stardewcraft.type.crop", "stardewcraft.type.vegetable" -> GRANGE_CATEGORY_CROP;
            case "stardewcraft.type.fruit" -> GRANGE_CATEGORY_FRUIT;
            case "stardewcraft.type.animal_product" -> GRANGE_CATEGORY_ANIMAL_PRODUCT;
            case "stardewcraft.type.artisan_goods", "stardewcraft.type.artisan_animal_quality" -> GRANGE_CATEGORY_ARTISAN;
            case "stardewcraft.type.fish" -> GRANGE_CATEGORY_FISH;
            case "stardewcraft.type.cooking", "stardewcraft.type.cooking_ingredient" -> GRANGE_CATEGORY_COOKING;
            case "stardewcraft.type.forage", "stardewcraft.type.flower" -> GRANGE_CATEGORY_FORAGE;
            case "stardewcraft.type.mineral", "stardewcraft.type.artifact", "stardewcraft.type.artifact_quality" -> GRANGE_CATEGORY_MINERAL_ARTIFACT;
            default -> 0;
        };
    }

    private static void giveGrangeReward(ServerPlayer player, int score) {
        int tokens;
        String key;
        var sound = ModSounds.NEW_ARTIFACT.get();
        if (score == GRANGE_PURPLE_SHORTS) {
            tokens = 750;
            key = "stardewcraft.fair.grange.result.purple_shorts";
        } else if (score >= 90) {
            tokens = 1000;
            key = "stardewcraft.fair.grange.result.first";
            sound = ModSounds.REWARD.get();
        } else if (score >= 75) {
            tokens = 500;
            key = "stardewcraft.fair.grange.result.second";
            sound = ModSounds.REWARD.get();
        } else if (score >= 60) {
            tokens = 250;
            key = "stardewcraft.fair.grange.result.third";
        } else {
            tokens = 50;
            key = "stardewcraft.fair.grange.result.fourth";
        }
        PlayerStardewDataAPI.addFairStarTokens(player, tokens);
        syncStarTokenHud(player, true);
        GRANGE_REWARD_CLAIMED.add(player.getUUID());
        player.playNotifySound(sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        ObjectDialogueService.show(player, Component.translatable(key, score,
                com.stardew.craft.player.PlayerDisplayName.get(player)));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static void enterFestival(ServerPlayer player) {
        if (player == null || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(player.serverLevel(), FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(player.serverLevel()), true);
            return;
        }
        boolean wasParticipant = isParticipant(player);
        if (FestivalService.openActiveFestival(player, FESTIVAL_ID).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.festival.fair.unavailable"), true);
            return;
        }
        if (!wasParticipant) {
            PlayerStardewDataAPI.setFairStarTokens(player, 0);
            GRANGE_DISPLAYS.remove(player.getUUID());
            GRANGE_SCORES.remove(player.getUUID());
            GRANGE_JUDGING_DONE_AT.remove(player.getUUID());
            GRANGE_REWARD_CLAIMED.remove(player.getUUID());
            cancelGrangeJudgingFor(player.serverLevel(), player.getUUID());
        }
        startTimeFreeze(player.serverLevel());
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, true);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, false);
        syncFestivalMusic(player, FestivalMusicStatePayload.FALL_FEST);
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
        syncStarTokenHud(player, true);
        player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, true);
        syncGrangeDisplay(player, true);
        player.getPersistentData().putBoolean(TAG_GRANGE_SYNCED, true);
        Vec3 target = LAST_INSIDE_ENTRY.get(player.getUUID());
        if (!isInsideEntryBounds(target)) {
            target = pushInsideEntry(player.position());
        }
        target = safeInsideEntryTarget(player, target);
        if (target == null) {
            return;
        }
        ModTeleport.to(player, player.serverLevel(), target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (player == null || player.isSpectator() || player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean insideEntry = ENTRY_EXIT_BOUNDS.contains(player.position());
        if (insideEntry) {
            LAST_INSIDE_ENTRY.put(player.getUUID(), player.position());
        } else {
            LAST_OUTSIDE_ENTRY.put(player.getUUID(), player.position());
        }

        if (isParticipant(player)) {
            if (!insideEntry) {
                promptExit(player);
                moveToLastInsideEntry(level, player);
            }
            return;
        }

        if (!insideEntry) {
            return;
        }
        if (!FestivalService.isActiveFestivalEntryOpen(level, FESTIVAL_ID)) {
            player.displayClientMessage(blockedEntryMessage(level), true);
            moveToLastOutsideEntry(level, player);
            return;
        }
        promptEnter(player);
        moveToLastOutsideEntry(level, player);
    }

    private static void promptEnter(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.ENTER);
    }

    private static void promptExit(ServerPlayer player) {
        CONFIRM_STATE.prompt(player, OpenFestivalConfirmPayload.Action.EXIT);
    }

    private static void moveToLastOutsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_OUTSIDE_ENTRY.get(player.getUUID());
        if (isInsideEntryBounds(target) || target == null) {
            target = pushOutsideEntry(player.position());
        }
        Vec3 safeTarget = FestivalBoundaryReturn.findSafeOutside(player, ENTRY_EXIT_BOUNDS, target);
        if (safeTarget != null) {
            target = safeTarget;
        }
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_OUTSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static void moveToLastInsideEntry(ServerLevel level, ServerPlayer player) {
        Vec3 target = LAST_INSIDE_ENTRY.get(player.getUUID());
        if (!isInsideEntryBounds(target)) {
            target = pushInsideEntry(player.position());
        }
        target = safeInsideEntryTarget(player, target);
        if (target == null) {
            return;
        }
        ModTeleport.to(player, level, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        LAST_INSIDE_ENTRY.put(player.getUUID(), target);
    }

    private static Vec3 pushOutsideEntry(Vec3 current) {
        return FestivalBoundaryReturn.pushOutside(ENTRY_EXIT_BOUNDS, current);
    }

    private static Vec3 pushInsideEntry(Vec3 current) {
        return FestivalBoundaryReturn.pushInside(ENTRY_EXIT_BOUNDS, current);
    }

    private static Vec3 safeInsideEntryTarget(ServerPlayer player, Vec3 preferred) {
        return FestivalBoundaryReturn.findSafeInside(player, ENTRY_EXIT_BOUNDS, preferred, pushInsideEntry(player.position()));
    }

    private static boolean isInsideEntryBounds(Vec3 position) {
        return position != null && ENTRY_EXIT_BOUNDS.contains(position);
    }

    private static Component blockedEntryMessage(ServerLevel level) {
        String key = FestivalService.isActiveFestivalEntryClosedForToday(level, FESTIVAL_ID)
            ? "message.stardewcraft.festival.ended"
            : "message.stardewcraft.festival.fair.setup";
        return Component.translatable(key);
    }

    private static void castExitVote(ServerPlayer player) {
        if (player == null || !isParticipant(player)) {
            return;
        }
        if (EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            EXIT_VOTE_PARTICIPANTS.addAll(onlineParticipants(player.serverLevel()).stream().map(ServerPlayer::getUUID).toList());
        }
        EXIT_VOTES.retainAll(EXIT_VOTE_PARTICIPANTS);
        EXIT_VOTES.add(player.getUUID());
        checkExitVote(player.serverLevel());
    }

    private static void checkExitVote(ServerLevel level) {
        List<ServerPlayer> voters = onlineExitVoteParticipants(level);
        int voteCount = exitVoteCount(voters);
        if (voters.isEmpty() || voteCount >= voters.size()) {
            finishFestival(level);
            return;
        }
        for (ServerPlayer participant : voters) {
            if (!EXIT_VOTES.contains(participant.getUUID())) {
                CONFIRM_STATE.prompt(participant, OpenFestivalConfirmPayload.Action.EXIT);
            }
            participant.displayClientMessage(Component.translatable(
                "message.stardewcraft.festival.exit_vote_waiting", voteCount, voters.size()), true);
        }
    }

    private static void finishFestival(ServerLevel level) {
        List<ServerPlayer> participants = onlineParticipants(level);
        setSessionPhase(level, FestivalSessionPhase.ENDING);
        jumpToFestivalEndTime(level, participants);
        for (ServerPlayer participant : participants) {
            participant.closeContainer();
        }
        LostAndFoundService.queueDisplays(level, GRANGE_DISPLAYS);
        for (ServerPlayer participant : participants) {
            participant.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
            participant.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
            participant.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, false);
            syncFestivalMusic(participant, FestivalMusicStatePayload.RELEASE);
            syncStarTokenHud(participant, false);
            returnToFarm(participant);
        }
        clearFestivalState();
        if (level != null) {
            removeFairAnimals(level);
            restoreNpcs(level);
            FestivalService.endFestival(level, FESTIVAL_ID);
        }
    }

    private static void jumpToFestivalEndTime(ServerLevel level, List<ServerPlayer> participants) {
        if (level == null) {
            return;
        }
        StardewTimeManager timeManager = StardewTimeManager.get();
        long currentVirtual = timeManager.getVirtualDayTime(level);
        long dayBase = Math.floorDiv(currentVirtual, 24000L) * 24000L;
        long targetVirtual = dayBase + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(FESTIVAL_END_MINUTE);
        timeManager.setVirtualDayTime(level, targetVirtual);
        timeManager.setCurrentTime(FESTIVAL_END_MINUTE);
        level.setDayTime(targetVirtual);
        ServerLevel miningLevel = level.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
        if (miningLevel != null) {
            miningLevel.setDayTime(targetVirtual);
        }
        TimeSyncPacket packet = TimeSyncPacket.fromTimeManager(timeManager);
        for (ServerPlayer participant : participants) {
            PacketDistributor.sendToPlayer(participant, packet);
        }
    }

    private static void returnToFarm(ServerPlayer player) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (farm == null || stardewLevel == null) {
            player.displayClientMessage(Component.translatable("stardewcraft.warp.farm.unavailable"), true);
            return;
        }
        ModTeleport.to(player, stardewLevel, farm.getSpawnPoint(), farm.getSpawnYaw(), 0.0F);
    }

    private static List<ServerPlayer> onlineParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        return level.players().stream().filter(FairFestivalService::isParticipant).toList();
    }

    private static List<ServerPlayer> onlineExitVoteParticipants(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        if (EXIT_VOTE_PARTICIPANTS.isEmpty()) {
            return onlineParticipants(level);
        }
        return level.players().stream()
            .filter(player -> EXIT_VOTE_PARTICIPANTS.contains(player.getUUID()))
            .filter(FairFestivalService::isParticipant)
            .toList();
    }

    private static int exitVoteCount(List<ServerPlayer> voters) {
        int count = 0;
        for (ServerPlayer voter : voters) {
            if (EXIT_VOTES.contains(voter.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isActiveFairDay() {
        return FestivalService.getActiveFestivalToday()
            .filter(definition -> FESTIVAL_ID.equalsIgnoreCase(definition.id()))
            .isPresent();
    }

    private static boolean hasCurrentSessionParticipant(ServerLevel level) {
        return currentSession(level)
            .map(session -> !session.participants().isEmpty())
            .orElse(false);
    }

    private static Optional<FestivalSessionState> currentSession(ServerLevel level) {
        if (level == null) {
            return Optional.empty();
        }
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalWorldData.get(level).getSession(FESTIVAL_ID)
            .filter(session -> session.year() == time.getCurrentYear()
                && session.season() == time.getCurrentSeason()
                && session.day() == time.getCurrentDay())
            .filter(session -> session.phase() != FestivalSessionPhase.ENDING
                && session.phase() != FestivalSessionPhase.RESTORING_MAP
                && session.phase() != FestivalSessionPhase.CLOSED);
    }

    private static void setSessionPhase(ServerLevel level, FestivalSessionPhase phase) {
        FestivalDefinition definition = FestivalRegistry.get(FESTIVAL_ID).orElse(null);
        if (level == null || definition == null) {
            return;
        }
        StardewTimeManager time = StardewTimeManager.get();
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalSessionState session = data.getOrCreateSession(
            definition,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        session.setPhase(phase);
        data.setDirty();
    }

    private static void startTimeFreeze(ServerLevel level) {
        if (frozenMinute == null) {
            frozenMinute = FESTIVAL_START_MINUTE;
        }
    }

    private static void stopTimeFreeze() {
        frozenMinute = null;
    }

    private static void clearRuntimeState(ServerLevel level) {
        if (level != null) {
            LostAndFoundService.queueDisplays(level, GRANGE_DISPLAYS);
            removeFairAnimals(level);
            removeFairInteractionBlocks(level);
            restoreNpcs(level);
            for (ServerPlayer player : level.players()) {
                clearFairClientStateIfNeeded(player);
            }
        }
        clearFestivalState();
    }

    private static void clearFestivalState() {
        LAST_OUTSIDE_ENTRY.clear();
        LAST_INSIDE_ENTRY.clear();
        GRANGE_DISPLAYS.clear();
        GRANGE_SCORES.clear();
        GRANGE_JUDGING_DONE_AT.clear();
        GRANGE_REWARD_CLAIMED.clear();
        grangeJudgingRoutePlayer = null;
        grangeJudgingRouteWaitUntil = 0L;
        grangeJudgingReturned = false;
        CONFIRM_STATE.clearAll();
        LEWIS_DIALOGUE_SEEN.clear();
        stopTimeFreeze();
    }

    private static void clearFairClientStateIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean hadFairState = player.getPersistentData().getBoolean(TAG_PARTICIPATING)
            || player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)
            || player.getPersistentData().getBoolean(TAG_TOKEN_HUD_SYNCED)
            || player.getPersistentData().getBoolean(TAG_GRANGE_SYNCED);
        if (!hadFairState) {
            return;
        }
        player.getPersistentData().putBoolean(TAG_PARTICIPATING, false);
        if (player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED)) {
            syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
        }
        if (player.getPersistentData().getBoolean(TAG_TOKEN_HUD_SYNCED)) {
            syncStarTokenHud(player, false);
        }
        if (player.getPersistentData().getBoolean(TAG_GRANGE_SYNCED)) {
            syncGrangeDisplay(player, false);
        }
        player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
        player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, false);
        player.getPersistentData().putBoolean(TAG_GRANGE_SYNCED, false);
    }

    private static void syncParticipantClientState(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean shouldSync = isParticipant(player);
            boolean musicSynced = player.getPersistentData().getBoolean(TAG_MUSIC_SYNCED);
            if (shouldSync && !musicSynced) {
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, true);
                syncFestivalMusic(player, FestivalMusicStatePayload.FALL_FEST);
            } else if (!shouldSync && musicSynced) {
                player.getPersistentData().putBoolean(TAG_MUSIC_SYNCED, false);
                syncFestivalMusic(player, FestivalMusicStatePayload.RELEASE);
            }
            boolean hudSynced = player.getPersistentData().getBoolean(TAG_TOKEN_HUD_SYNCED);
            if (shouldSync && !hudSynced) {
                player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, true);
                syncStarTokenHud(player, true);
            } else if (!shouldSync && hudSynced) {
                player.getPersistentData().putBoolean(TAG_TOKEN_HUD_SYNCED, false);
                syncStarTokenHud(player, false);
            }
            boolean grangeSynced = player.getPersistentData().getBoolean(TAG_GRANGE_SYNCED);
            if (shouldSync && !grangeSynced) {
                player.getPersistentData().putBoolean(TAG_GRANGE_SYNCED, true);
                syncGrangeDisplay(player, true);
            } else if (!shouldSync && grangeSynced) {
                player.getPersistentData().putBoolean(TAG_GRANGE_SYNCED, false);
                syncGrangeDisplay(player, false);
            }
        }
    }

    private static void syncFestivalMusic(ServerPlayer player, String track) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new FestivalMusicStatePayload(track));
        }
    }

    private static void syncStarTokenHud(ServerPlayer player, boolean active) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new FestivalCurrencyHudStatePayload(
                active ? FestivalCurrencyHudStatePayload.FAIR_STAR_TOKEN : FestivalCurrencyHudStatePayload.NONE));
        }
    }

    private static void openStarTokenPurchaseAmount(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Component question = Component.translatable("stardewcraft.fair.star_tokens.amount_question");
        PacketDistributor.sendToPlayer(player, new OpenFairStarTokenNumberSelectionPayload(
            Component.Serializer.toJson(question, player.registryAccess()),
            STAR_TOKEN_PURCHASE_PRICE,
            0,
            MAX_STAR_TOKEN_PURCHASE,
            0
        ));
    }

    private static void sendStarTokenPurchaseResult(ServerPlayer player, boolean success) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new FairStarTokenPurchaseResultPayload(success));
        }
    }

    private static void readFortune(ServerPlayer player) {
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        String flag = fortuneTellerFlag();
        if (data.hasMailFlag(flag)) {
            ObjectDialogueService.show(player, "stardewcraft.fair.fortune.already_read");
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, 100)) {
            ObjectDialogueService.show(player, "stardewcraft.fair.fortune.no_money");
            return;
        }
        data.addMailFlag(flag);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new OpenFairFortunePayload(createFortunePages(player).stream()
            .map(component -> Component.Serializer.toJson(component, player.registryAccess()))
            .toList()));
    }

    private static List<Component> createFortunePages(ServerPlayer player) {
        return List.of(
            friendFortune(player),
            romanceFortune(player),
            Component.translatable(skillFortuneKey(highestSkill(player))),
            Component.translatable("stardewcraft.fair.fortune.end1"),
            Component.translatable("stardewcraft.fair.fortune.end2")
        );
    }

    private static Component friendFortune(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Component.translatable("stardewcraft.fair.fortune.friend.none");
        }
        NpcFriendshipDataManager friendships = NpcFriendshipDataManager.get(level);
        Map<String, Integer> pointsByNpc = friendships.getPointsForPlayer(player.getUUID());
        String topNpcId = null;
        int topPoints = 0;
        for (Map.Entry<String, Integer> entry : pointsByNpc.entrySet()) {
            NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(entry.getKey());
            if (profile == null || profile.datable() || !profile.implemented()) {
                continue;
            }
            int points = Math.max(0, entry.getValue());
            if (points > topPoints) {
                topNpcId = entry.getKey();
                topPoints = points;
            }
        }
        if (topNpcId == null || topPoints <= 100) {
            return Component.translatable("stardewcraft.fair.fortune.friend.none");
        }
        NpcCapabilityProfile topProfile = NpcDataRegistry.capabilities().get(topNpcId);
        if (countFriendsWithinRange(pointsByNpc, topPoints - 100, topPoints, false) > 3 && level.random.nextBoolean()) {
            return Component.translatable("stardewcraft.fair.fortune.friend.many");
        }
        Component displayName = Component.translatable("entity.stardewcraft.npc." + topNpcId);
        return switch (level.random.nextInt(4)) {
            case 0 -> Component.translatable("stardewcraft.fair.fortune.friend.beach", displayName);
            case 1 -> Component.empty()
                .append(Component.translatable("stardewcraft.fair.fortune.friend.birthday", displayName))
                .append(Component.translatable(topProfile != null && topProfile.gender() == NpcCapabilityProfile.GENDER_MALE
                    ? "stardewcraft.fair.fortune.friend.birthday.male"
                    : "stardewcraft.fair.fortune.friend.birthday.female"));
            case 2 -> Component.translatable("stardewcraft.fair.fortune.friend.hospital", displayName);
            default -> Component.empty()
                .append(Component.translatable(topProfile != null && topProfile.gender() == NpcCapabilityProfile.GENDER_MALE
                    ? "stardewcraft.fair.fortune.friend.room.male"
                    : "stardewcraft.fair.fortune.friend.room.female"))
                .append(Component.translatable("stardewcraft.fair.fortune.friend.room.end", displayName));
        };
    }

    private static Component romanceFortune(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Component.translatable("stardewcraft.fair.fortune.romance.none");
        }
        NpcFriendshipDataManager friendships = NpcFriendshipDataManager.get(level);
        Map<String, Integer> pointsByNpc = friendships.getPointsForPlayer(player.getUUID());
        String topNpcId = null;
        int topPoints = 0;
        for (Map.Entry<String, Integer> entry : pointsByNpc.entrySet()) {
            NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(entry.getKey());
            if (profile == null || !profile.datable() || !profile.implemented()) {
                continue;
            }
            int points = Math.max(0, entry.getValue());
            if (points > topPoints) {
                topNpcId = entry.getKey();
                topPoints = points;
            }
        }
        if (topNpcId == null || topPoints <= 250) {
            return Component.translatable("stardewcraft.fair.fortune.romance.none");
        }
        if (countFriendsWithinRange(pointsByNpc, topPoints - 100, topPoints, true) > 2) {
            return Component.translatable("stardewcraft.fair.fortune.romance.many");
        }
        NpcCapabilityProfile topProfile = NpcDataRegistry.capabilities().get(topNpcId);
        Component displayName = Component.translatable("entity.stardewcraft.npc." + topNpcId);
        return switch (level.random.nextInt(4)) {
            case 0 -> Component.translatable("stardewcraft.fair.fortune.romance.field", displayName);
            case 1 -> Component.translatable("stardewcraft.fair.fortune.romance.dim_room", displayName);
            case 2 -> Component.empty()
                .append(Component.translatable(darkRomanceKey(topProfile)))
                .append(" ")
                .append(Component.translatable(letterRomanceKey(topProfile), Component.translatable(displayInitialKey(topNpcId))));
            default -> Component.translatable("stardewcraft.fair.fortune.romance.farm", displayName);
        };
    }

    private static String darkRomanceKey(NpcCapabilityProfile profile) {
        boolean male = profile != null && profile.gender() == NpcCapabilityProfile.GENDER_MALE;
        boolean shy = profile != null && profile.socialAnxiety() == NpcCapabilityProfile.SOCIAL_SHY;
        if (male) {
            return shy ? "stardewcraft.fair.fortune.romance.dark.male.shy" : "stardewcraft.fair.fortune.romance.dark.male";
        }
        return shy ? "stardewcraft.fair.fortune.romance.dark.female.shy" : "stardewcraft.fair.fortune.romance.dark.female";
    }

    private static String letterRomanceKey(NpcCapabilityProfile profile) {
        return profile != null && profile.gender() == NpcCapabilityProfile.GENDER_MALE
            ? "stardewcraft.fair.fortune.romance.letter.male"
            : "stardewcraft.fair.fortune.romance.letter.female";
    }

    private static String displayInitialKey(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return "stardewcraft.fair.fortune.initial.unknown";
        }
        return "stardewcraft.fair.fortune.initial." + npcId;
    }

    private static int countFriendsWithinRange(Map<String, Integer> pointsByNpc, int minPoints, int maxPoints, boolean romanceOnly) {
        int count = 0;
        for (Map.Entry<String, Integer> entry : pointsByNpc.entrySet()) {
            NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(entry.getKey());
            if (profile == null || !profile.implemented() || (romanceOnly && !profile.datable())) {
                continue;
            }
            int points = Math.max(0, entry.getValue());
            if (points >= minPoints && points <= maxPoints) {
                count++;
            }
        }
        return count;
    }

    private static SkillType highestSkill(ServerPlayer player) {
        SkillType best = SkillType.FARMING;
        int bestExperience = PlayerStardewDataAPI.getSkillExperience(player, best);
        for (SkillType skill : List.of(SkillType.FARMING, SkillType.MINING, SkillType.COMBAT, SkillType.FISHING, SkillType.FORAGING)) {
            int experience = PlayerStardewDataAPI.getSkillExperience(player, skill);
            if (experience > bestExperience) {
                best = skill;
                bestExperience = experience;
            }
        }
        return best;
    }

    private static String skillFortuneKey(SkillType skill) {
        return switch (skill) {
            case MINING -> "stardewcraft.fair.fortune.skill.mining";
            case COMBAT -> "stardewcraft.fair.fortune.skill.combat";
            case FISHING -> "stardewcraft.fair.fortune.skill.fishing";
            case FORAGING -> "stardewcraft.fair.fortune.skill.foraging";
            default -> "stardewcraft.fair.fortune.skill.farming";
        };
    }

    private static String fortuneTellerFlag() {
        return "fortuneTeller" + StardewTimeManager.get().getCurrentYear();
    }

    private static void sendQuestion(
        ServerPlayer player,
        String context,
        int questionIndex,
        Component question,
        List<OpenDesertFestivalQuestionPayload.ResponseOption> responses
    ) {
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            context,
            questionIndex,
            "",
            Component.Serializer.toJson(question, player.registryAccess()),
            responses
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
    }

    private static void installFairInteractionBlocks(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        installFairInteractionBlock(level, SLINGSHOT_GAME_POS, FairSlingshotGameService.TARGET_ID, FairSlingshotGameService.MARKER_TAG);
        installFairInteractionBlock(level, FISHING_GAME_POS, FairFishingGameService.TARGET_ID, FairFishingGameService.MARKER_TAG);
        installFairInteractionBlock(level, STAR_TOKEN_SHOP_POS, STAR_TOKEN_SHOP_TARGET_ID, STAR_TOKEN_SHOP_MARKER_TAG);
        installFairInteractionBlock(level, STAR_TOKEN_PURCHASE_POS, STAR_TOKEN_PURCHASE_TARGET_ID, STAR_TOKEN_PURCHASE_MARKER_TAG);
        removeLegacyFortuneTellerBlock(level);
    }

    private static void installFairInteractionBlock(ServerLevel level, BlockPos pos, String targetId, String markerTag) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
            && targetId.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(targetId, markerTag);
        }
    }

    private static void removeFairInteractionBlocks(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (level.getBlockState(SLINGSHOT_GAME_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(SLINGSHOT_GAME_POS) instanceof PortalTriggerBlockEntity blockEntity
            && FairSlingshotGameService.TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(SLINGSHOT_GAME_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (level.getBlockState(FISHING_GAME_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(FISHING_GAME_POS) instanceof PortalTriggerBlockEntity blockEntity
            && FairFishingGameService.TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(FISHING_GAME_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (level.getBlockState(STAR_TOKEN_SHOP_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(STAR_TOKEN_SHOP_POS) instanceof PortalTriggerBlockEntity blockEntity
            && STAR_TOKEN_SHOP_TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(STAR_TOKEN_SHOP_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (level.getBlockState(STAR_TOKEN_PURCHASE_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(STAR_TOKEN_PURCHASE_POS) instanceof PortalTriggerBlockEntity blockEntity
            && STAR_TOKEN_PURCHASE_TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(STAR_TOKEN_PURCHASE_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        removeLegacyFortuneTellerBlock(level);
    }

    private static void removeLegacyFortuneTellerBlock(ServerLevel level) {
        if (level.getBlockState(LEGACY_FORTUNE_TELLER_POS).is(ModBlocks.PORTAL_TRIGGER.get())
            && level.getBlockEntity(LEGACY_FORTUNE_TELLER_POS) instanceof PortalTriggerBlockEntity blockEntity
            && FORTUNE_TELLER_TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(LEGACY_FORTUNE_TELLER_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void tickActors(ServerLevel level, boolean activeRequested) {
        NPC_ACTORS.tick(level, activeRequested);
    }

    private static void tickLewisGrangeJudgingRoute(ServerLevel level, StardewNpcEntity npc, long now) {
        if (grangeJudgingReturned) {
            NpcCentralMovementService.stopAuthoredMovement(npc);
            npc.setWalking(false);
            if (now >= grangeJudgingRouteWaitUntil) {
                finishGrangeJudging(level);
            }
            return;
        }
        if (now < grangeJudgingRouteWaitUntil) {
            NpcCentralMovementService.stopAuthoredMovement(npc);
            npc.setWalking(false);
            return;
        }
        int reachedRouteIndex = NpcCentralMovementService.tickAuthoredWalkRoute(
            level,
            npc,
            MOVEMENT_OWNER,
            "fair_lewis_grange_judging",
            GRANGE_JUDGING_ROUTE_TARGETS,
            false
        );
        if (reachedRouteIndex < 0 || reachedRouteIndex >= GRANGE_JUDGING_ROUTE.size()) {
            return;
        }
        FestivalNpcActorRuntime.Waypoint reached = GRANGE_JUDGING_ROUTE.get(reachedRouteIndex);
        applyYaw(npc, reached.yaw());
        npc.setWalking(false);
        if (reachedRouteIndex == GRANGE_JUDGING_ROUTE.size() - 1) {
            grangeJudgingReturned = true;
            grangeJudgingRouteWaitUntil = now + GRANGE_JUDGING_RETURN_WAIT_TICKS;
        } else if (reachedRouteIndex > 0) {
            grangeJudgingRouteWaitUntil = now + GRANGE_JUDGING_STALL_WAIT_TICKS;
        }
    }

    private static void finishGrangeJudging(ServerLevel level) {
        UUID playerId = grangeJudgingRoutePlayer;
        resetLewisGrangeJudgingRoute(level);
        if (playerId == null) {
            return;
        }
        GRANGE_JUDGING_DONE_AT.remove(playerId);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null && isParticipant(player)) {
            ObjectDialogueService.show(player, "stardewcraft.fair.grange.judged");
        }
    }

    private static void cancelGrangeJudgingFor(ServerLevel level, UUID playerId) {
        if (playerId != null && playerId.equals(grangeJudgingRoutePlayer)) {
            resetLewisGrangeJudgingRoute(level);
        }
    }

    private static void resetLewisGrangeJudgingRoute(ServerLevel level) {
        grangeJudgingRoutePlayer = null;
        grangeJudgingRouteWaitUntil = 0L;
        grangeJudgingReturned = false;
        NpcCentralMovementService.resetAuthoredMovementPlan("lewis", MOVEMENT_OWNER);
        if (level != null) {
            StardewNpcEntity lewis = NPC_ACTORS.findActorEntity(level, "lewis");
            if (lewis != null) {
                NpcCentralMovementService.stopAuthoredMovement(lewis);
            }
        }
    }

    private static void applyYaw(StardewNpcEntity npc, float yaw) {
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
    }

    private static void ensureFairAnimals(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        List<BaseCoopAnimalEntity> existing = new ArrayList<>(level.getEntitiesOfClass(
            BaseCoopAnimalEntity.class,
            ENTRY_EXIT_BOUNDS.inflate(8.0D),
            FairFestivalService::isFairAnimal
        ));
        Set<UUID> retained = new HashSet<>();
        for (FairAnimalSpawn spawn : FAIR_ANIMALS) {
            BaseCoopAnimalEntity fairAnimal = findFairAnimalForSpawn(existing, retained, spawn);
            boolean newlyCreated = false;
            if (fairAnimal == null) {
                Animal animal = spawn.type().get().create(level);
                if (!(animal instanceof BaseCoopAnimalEntity createdAnimal)) {
                    continue;
                }
                fairAnimal = createdAnimal;
                newlyCreated = true;
            }
            prepareFairAnimal(fairAnimal, spawn);
            if (newlyCreated) {
                existing.add(fairAnimal);
                level.addFreshEntity(fairAnimal);
            }
            retained.add(fairAnimal.getUUID());
        }
        for (BaseCoopAnimalEntity fairAnimal : existing) {
            if (!retained.contains(fairAnimal.getUUID())) {
                fairAnimal.discard();
            }
        }
    }

    private static BaseCoopAnimalEntity findFairAnimalForSpawn(
        List<BaseCoopAnimalEntity> existing,
        Set<UUID> retained,
        FairAnimalSpawn spawn
    ) {
        for (BaseCoopAnimalEntity fairAnimal : existing) {
            if (retained.contains(fairAnimal.getUUID()) || fairAnimal.getType() != spawn.type().get()) {
                continue;
            }
            if (fairAnimal.getTags().contains(spawn.slotTag())) {
                return fairAnimal;
            }
        }
        return null;
    }

    private static void prepareFairAnimal(BaseCoopAnimalEntity fairAnimal, FairAnimalSpawn spawn) {
        BlockPos pos = spawn.pos();
        Vec3 target = Vec3.atBottomCenterOf(pos);
        if (fairAnimal.distanceToSqr(target) > 1.0D) {
            fairAnimal.moveTo(target.x, target.y, target.z, spawn.yaw(), 0.0F);
        }
        fairAnimal.setManagedAnimalId(-1L);
        fairAnimal.setManagedAnimalType(spawn.managedType());
        fairAnimal.addTag(FAIR_ANIMAL_MARKER_TAG);
        fairAnimal.addTag(spawn.slotTag());
        fairAnimal.getPersistentData().putBoolean(FAIR_ANIMAL_PERSISTENT_FLAG, true);
        fairAnimal.getNavigation().stop();
        fairAnimal.setDeltaMovement(Vec3.ZERO);
        fairAnimal.setNoAi(true);
        fairAnimal.setInvulnerable(true);
    }

    private static boolean isFairAnimal(BaseCoopAnimalEntity fairAnimal) {
        return fairAnimal.getTags().contains(FAIR_ANIMAL_MARKER_TAG)
            || fairAnimal.getPersistentData().getBoolean(FAIR_ANIMAL_PERSISTENT_FLAG);
    }

    private static void removeFairAnimals(ServerLevel level) {
        if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        level.getEntitiesOfClass(BaseCoopAnimalEntity.class, ENTRY_EXIT_BOUNDS.inflate(8.0D),
            FairFestivalService::isFairAnimal
        ).forEach(BaseCoopAnimalEntity::discard);
    }

    private static String canonical(String npcId) {
        return npcId == null ? "" : npcId.trim().toLowerCase(Locale.ROOT);
    }

    private static String fmt(Vec3 position) {
        if (position == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", position.x, position.y, position.z);
    }

    private static AABB inclusiveBox(BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static FestivalNpcActorRuntime.ActorDefinition route(String npcId, FestivalNpcActorRuntime.Waypoint... points) {
        return FestivalNpcActorRuntime.route(npcId, true, ROUTE_WAIT_TICKS, points);
    }

    private static Map<String, FestivalNpcActorRuntime.ActorDefinition> createActors() {
        List<FestivalNpcActorRuntime.ActorDefinition> definitions = new ArrayList<>();
        definitions.add(actor("lewis", point(1, 64, -11, 'S')));
        definitions.add(actor("caroline", point(5, 64, -5, 'W')));
        definitions.add(actor("pierre", point(6, 64, -4, 'S')));
        definitions.add(route("marnie", point(15, 64, -3, 'N'), point(17, 64, -3, 'N')));
        definitions.add(actor("willy", point(22, 64, -5, 'S')));
        definitions.add(route("abigail", point(0, 64, 7, 'E'), point(7, 64, 10, 'W')));
        definitions.add(actor("alex", point(3, 64, 10, 'S')));
        definitions.add(actor("haley", point(2, 64, 11, 'E')));
        definitions.add(actor("pam", point(-6, 64, 17, 'E')));
        definitions.add(actor("penny", point(15, 64, 14, 'W')));
        definitions.add(actor("george", point(-8, 66, -39, 'E')));
        definitions.add(actor("evelyn", point(-8, 66, -37, 'E')));
        definitions.add(actor("gus", point(-5, 66, -38, 'N')));
        definitions.add(actor("elliott", point(0, 66, -38, 'W')));
        definitions.add(actor("maru", point(5, 66, -39, 'E')));
        definitions.add(actor("emily", point(1, 64, 27, 'W')));
        definitions.add(actor("jodi", point(-1, 64, 30, 'N')));
        definitions.add(actor("demetrius", point(5, 64, 20, 'S')));
        definitions.add(actor("leah", point(8, 64, 25, 'N')));
        definitions.add(actor("marlon", point(18, 64, 22, 'S')));
        definitions.add(actor("clint", point(21, 64, 24, 'S')));
        definitions.add(actor("sebastian", point(32, 64, 23, 'E')));
        definitions.add(actor("sam", point(33, 64, 22, 'S')));
        definitions.add(actor("wizard", point(27, 64, 36, 'S')));
        definitions.add(actor("shane", point(49, 64, 13, 'W')));
        definitions.add(actor("jas", point(53, 64, 8, 'N')));
        definitions.add(actor("vincent", point(55, 64, 9, 'E')));
        definitions.add(actor("harvey", point(49, 64, 5, 'E')));
        definitions.add(actor("linus", point(63, 64, 12, 'W')));

        return actorMap(definitions);
    }
}
