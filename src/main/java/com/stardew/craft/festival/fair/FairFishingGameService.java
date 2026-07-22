package com.stardew.craft.festival.fair;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenFairFishingResultPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FairFishingGameService {
    public static final String TARGET_ID = "fair_fishing_game";
    public static final String MARKER_TAG = "sdv_festival_marker:fair_fishing_game";
    public static final String QUESTION_CONTEXT = "fair_fishing_game";
    private static final String TEMPORARY_ROD_OWNER = "fall16_fishing_game";

    private static final String YES_ID = "yes";
    private static final int ENTRY_COST = 50;
    private static final BlockPos FISHING_ROOM_START = new BlockPos(-2, 56, 16);
    private static final float FISHING_ROOM_YAW_WEST = 90.0F;
    private static final float FISHING_ROOM_PITCH = 0.0F;
    private static final int GAME_DURATION_TICKS = 20 * 100;
    private static final int START_DELAY_TICKS = 20;
    private static final int END_DELAY_TICKS = 20;
    private static final Map<UUID, ActiveFishingGame> ACTIVE_GAMES = new HashMap<>();
    private static final Map<UUID, PendingFishingResult> PENDING_RESULTS = new HashMap<>();

    private FairFishingGameService() {
    }

    public static void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!FairFestivalService.canUseFairInteraction(player)) {
            ObjectDialogueService.show(player, "message.stardewcraft.fair.game.closed");
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            QUESTION_CONTEXT,
            0,
            "",
            Component.Serializer.toJson(Component.translatable("stardewcraft.fair.fishing.question"), player.registryAccess()),
            List.of(
                response(YES_ID, Component.translatable("stardewcraft.fair.fishing.play"), player),
                response("no", Component.translatable("stardewcraft.fair.fishing.leave"), player)
            )
        ));
    }

    public static void handleQuestionResponse(ServerPlayer player, String choiceId) {
        if (player == null || !YES_ID.equals(choiceId)) {
            return;
        }
        if (!FairFestivalService.canUseFairInteraction(player)) {
            ObjectDialogueService.show(player, "message.stardewcraft.fair.game.closed");
            return;
        }
        if (player.server.getLevel(ModDimensions.STARDEW_VALLEY) == null) {
            ObjectDialogueService.show(player, "stardewcraft.warp.farm.unavailable");
            return;
        }
        if (PlayerStardewDataAPI.getMoney(player) < ENTRY_COST
            || !PlayerStardewDataAPI.removeMoney(player, ENTRY_COST)) {
            ObjectDialogueService.show(player, "stardewcraft.fair.fishing.no_money");
            return;
        }
        startFishingGame(player);
    }

    private static void startFishingGame(ServerPlayer player) {
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            ObjectDialogueService.show(player, "stardewcraft.warp.farm.unavailable");
            return;
        }
        // A cast started before entering the booth otherwise survives into the minigame and makes
        // the temporary rod report "already fishing". The booth always starts from a clean session.
        com.stardew.craft.fishing.server.FishingSessionManager.get(player.server).cancel(player);
        PENDING_RESULTS.remove(player.getUUID());
        removeTemporaryFishingRods(player);
        ActiveFishingGame game = new ActiveFishingGame(
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), GAME_DURATION_TICKS
        );
        ACTIVE_GAMES.put(player.getUUID(), game);
        ModTeleport.to(player, stardewLevel, FISHING_ROOM_START, FISHING_ROOM_YAW_WEST, FISHING_ROOM_PITCH);
        giveTemporaryFishingRod(player, game);
        syncFishingHud(player, game, true);
    }

    public static boolean canStartFishingCast(ServerPlayer player) {
        ActiveFishingGame game = ACTIVE_GAMES.get(player.getUUID());
        return game == null || (game.startDelayTicks <= 0 && game.endDelayTicks < 0 && !game.resultsSent);
    }

    public static boolean isFishingGameActive(ServerPlayer player) {
        return player != null && ACTIVE_GAMES.containsKey(player.getUUID());
    }

    public static boolean isUsableFishingGameRod(ServerPlayer player, ItemStack rodStack) {
        if (!isFishingGameActive(player)) {
            return rodStack != null && !rodStack.isEmpty() && rodStack.getItem() instanceof FishingRodItem;
        }
        return FishingRodItem.isFairTemporaryRod(rodStack, TEMPORARY_ROD_OWNER);
    }

    public static void onFishingCatch(ServerPlayer player, boolean fish, int size, boolean perfect) {
        if (player == null) {
            return;
        }
        ActiveFishingGame game = ACTIVE_GAMES.get(player.getUUID());
        if (game == null || game.resultsSent || game.startDelayTicks > 0 || game.endDelayTicks >= 0) {
            return;
        }
        game.score += size <= 0 ? 1 : size + 5;
        if (size > 0 && fish) {
            game.fishCaught++;
        }
        if (perfect) {
            game.perfections++;
        }
        syncFishingHud(player, game, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ActiveFishingGame game = ACTIVE_GAMES.get(player.getUUID());
        if (game == null) {
            return;
        }
        PlayerStardewDataAPI.restoreEnergy(player, PlayerStardewDataAPI.getMaxEnergy(player));
        if (!FairFestivalService.canUseFairInteraction(player)) {
            finishFishingGame(player, game, false);
            return;
        }
        if (game.startDelayTicks > 0) {
            game.startDelayTicks--;
            player.setYRot(FISHING_ROOM_YAW_WEST);
            player.setXRot(FISHING_ROOM_PITCH);
            if (game.startDelayTicks == 0) {
                player.playNotifySound(ModSounds.WHISTLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }
        game.hudSyncTicks--;
        if (game.hudSyncTicks <= 0) {
            game.hudSyncTicks = 20;
            syncFishingHud(player, game, true);
        }
        if (game.endDelayTicks >= 0) {
            game.endDelayTicks--;
            if (game.endDelayTicks <= 0) {
                finishFishingGame(player, game, true);
            }
            return;
        }
        game.ticksRemaining--;
        if (game.ticksRemaining <= 0) {
            game.ticksRemaining = 0;
            com.stardew.craft.fishing.server.FishingSessionManager fishing =
                com.stardew.craft.fishing.server.FishingSessionManager.get(player.server);
            if (shouldWaitForActiveFishingAction(fishing.getState(player))) {
                return;
            }
            fishing.cancel(player);
            player.playNotifySound(ModSounds.WHISTLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            game.endDelayTicks = END_DELAY_TICKS;
        }
    }

    private static boolean shouldWaitForActiveFishingAction(com.stardew.craft.fishing.server.FishingSession.State state) {
        return state == com.stardew.craft.fishing.server.FishingSession.State.HOOKED_ANIM
            || state == com.stardew.craft.fishing.server.FishingSession.State.MINIGAME;
    }

    private static void finishFishingGame(ServerPlayer player, ActiveFishingGame game, boolean showResults) {
        ACTIVE_GAMES.remove(player.getUUID());
        syncFishingHud(player, game, false);
        com.stardew.craft.fishing.server.FishingSessionManager.get(player.server).cancel(player);
        removeTemporaryFishingRods(player);
        restoreDisplacedSelectedItem(player, game);
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel != null) {
            ModTeleport.to(player, stardewLevel, game.returnX, game.returnY, game.returnZ, game.returnYaw, game.returnPitch);
        }
        if (showResults) {
            sendResults(player, game);
        }
    }

    private static void syncFishingHud(ServerPlayer player, ActiveFishingGame game, boolean active) {
        if (player == null) {
            return;
        }
        int remainingMs = game == null ? 0 : Math.max(0, game.ticksRemaining * 50);
        int score = game == null ? 0 : Math.max(0, game.score);
        if (!active) {
            player.displayClientMessage(Component.empty(), true);
            return;
        }
        int seconds = Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        player.displayClientMessage(Component.translatable("stardewcraft.fair.fishing.actionbar", time, score), true);
    }

    private static void sendResults(ServerPlayer player, ActiveFishingGame game) {
        if (game.resultsSent) {
            return;
        }
        game.resultsSent = true;
        int finalScore = calculateFinalScore(game.score, game.fishCaught, game.perfections).score();
        int starTokensWon = calculateStarTokensWon(finalScore);
        PENDING_RESULTS.put(player.getUUID(), new PendingFishingResult(starTokensWon));
        PacketDistributor.sendToPlayer(player, new OpenFairFishingResultPayload(
            game.score,
            game.fishCaught,
            game.perfections,
            PlayerStardewDataAPI.getFairStarTokens(player)
        ));
    }

    public static void claimFishingResultReward(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PendingFishingResult pending = PENDING_RESULTS.remove(player.getUUID());
        if (pending == null || pending.starTokensWon <= 0) {
            return;
        }
        PlayerStardewDataAPI.addFairStarTokens(player, pending.starTokensWon);
    }

    private static ScoreResult calculateFinalScore(int baseScore, int fishCaught, int perfections) {
        int score = baseScore;
        int perfectionBonus = 0;
        if (perfections > 0) {
            score += perfections * 10;
            perfectionBonus = perfections * 10;
            if (fishCaught >= 3 && perfections >= 3) {
                perfectionBonus += score;
                score *= 2;
            }
        }
        return new ScoreResult(score, perfectionBonus);
    }

    private static int calculateStarTokensWon(int score) {
        if (score < 10) {
            return 0;
        }
        return ((score + 5) / 10) * 6 * 2;
    }

    private static void giveTemporaryFishingRod(ServerPlayer player, ActiveFishingGame game) {
        ItemStack rod = new ItemStack(ModItems.FISHING_ROD.get());
        // SDV FishingGame.cs uses BambooPole with AttachmentSlotsCount=2,
        // attachments[0]=(O)690 x99, attachments[1]=(O)687.
        FishingRodItem.configureFairTemporaryRod(
            rod,
            TEMPORARY_ROD_OWNER,
            new ItemStack(ModItems.WARP_TOTEM_BEACH.get(), 99),
            new ItemStack(ModItems.DRESSED_SPINNER.get())
        );
        // Keep the client-selected hotbar index unchanged and replace that slot directly. Merely
        // changing Inventory.selected on the server is not a reliable held-slot sync, especially
        // when this starts from a modal question while collective menu pause is still unwinding.
        int selected = player.getInventory().selected;
        ItemStack displaced = player.getInventory().items.get(selected);
        game.displacedSelectedSlot = selected;
        game.displacedSelectedStack = displaced.copy();
        player.getInventory().items.set(selected, rod);
        syncInventory(player);
    }

    private static void restoreDisplacedSelectedItem(ServerPlayer player, ActiveFishingGame game) {
        if (game.displacedSelectedStack.isEmpty()) {
            return;
        }
        int slot = game.displacedSelectedSlot;
        if (slot >= 0 && slot < player.getInventory().items.size()
            && player.getInventory().items.get(slot).isEmpty()) {
            player.getInventory().items.set(slot, game.displacedSelectedStack.copy());
        } else if (!player.getInventory().add(game.displacedSelectedStack.copy())) {
            player.drop(game.displacedSelectedStack.copy(), false);
        }
        game.displacedSelectedStack = ItemStack.EMPTY;
        game.displacedSelectedSlot = -1;
        syncInventory(player);
    }

    private static void removeTemporaryFishingRods(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (FishingRodItem.isFairTemporaryRod(stack, TEMPORARY_ROD_OWNER)) {
                player.getInventory().items.set(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (FishingRodItem.isFairTemporaryRod(stack, TEMPORARY_ROD_OWNER)) {
                player.getInventory().offhand.set(i, ItemStack.EMPTY);
            }
        }
        syncInventory(player);
        if (player.level() instanceof ServerLevel level) {
            removeDroppedTemporaryFishingRods(level);
        }
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void removeDroppedTemporaryFishingRods(ServerLevel level) {
        AABB cleanupBox = AABB.ofSize(FISHING_ROOM_START.getCenter(), 16.0D, 8.0D, 16.0D);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, cleanupBox)) {
            if (FishingRodItem.isFairTemporaryRod(itemEntity.getItem(), TEMPORARY_ROD_OWNER)) {
                itemEntity.discard();
            }
        }
    }

    private static final class ActiveFishingGame {
        private final double returnX;
        private final double returnY;
        private final double returnZ;
        private final float returnYaw;
        private final float returnPitch;
        private int ticksRemaining;
        private int startDelayTicks = START_DELAY_TICKS;
        private int endDelayTicks = -1;
        private int score;
        private int fishCaught;
        private int perfections;
        private boolean resultsSent;
        private int hudSyncTicks = 20;
        private int displacedSelectedSlot = -1;
        private ItemStack displacedSelectedStack = ItemStack.EMPTY;

        private ActiveFishingGame(double returnX, double returnY, double returnZ,
                                  float returnYaw, float returnPitch, int ticksRemaining) {
            this.returnX = returnX;
            this.returnY = returnY;
            this.returnZ = returnZ;
            this.returnYaw = returnYaw;
            this.returnPitch = returnPitch;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private record ScoreResult(int score, int perfectionBonus) {
    }

    private record PendingFishingResult(int starTokensWon) {
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
    }
}
