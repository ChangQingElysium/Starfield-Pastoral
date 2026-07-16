package com.stardew.craft.auction;

import com.stardew.craft.auction.AuctionWorldData.AuctionLot;
import com.stardew.craft.auction.AuctionWorldData.AuctionRecord;
import com.stardew.craft.auction.AuctionWorldData.Status;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.interior.InteriorPortalRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.money.SharedMoneyService;
import com.stardew.craft.network.payload.OpenAuctionBidPayload;
import com.stardew.craft.network.payload.OpenAuctionCreatePayload;
import com.stardew.craft.network.payload.OpenAuctionEntryChoicePayload;
import com.stardew.craft.network.payload.OpenAuctionJoinListPayload;
import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import com.stardew.craft.network.payload.SyncAuctionBoardPayload;
import com.stardew.craft.player.PlayerDisplayName;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AuctionService {
    public static final int ROOM_MIN_X = 56;
    public static final int ROOM_MAX_X = 78;
    public static final int ROOM_MIN_Y = 48;
    public static final int ROOM_MAX_Y = 54;
    public static final int ROOM_MIN_Z = -9;
    public static final int ROOM_MAX_Z = 9;

    private static final AABB ROOM_BOX = new AABB(ROOM_MIN_X, ROOM_MIN_Y, ROOM_MIN_Z, ROOM_MAX_X + 1, ROOM_MAX_Y + 1, ROOM_MAX_Z + 1);
    private static final BlockPos EXIT_PORTAL_BASE = new BlockPos(66, 49, 9);
    private static final Vec3 ENTRY_POS = new Vec3(67.5D, 49.0D, 8.5D);
    private static final Vec3 DISPLAY_POS = new Vec3(63.5D, 51.8125D, -7.9375D);
    private static final String DISPLAY_TAG = "stardewcraft_auction_lot_display";
    public static final String AUCTION_HOST_TAG = "stardewcraft_auction_host";
    private static final String EXIT_PORTAL_MARKER = "sdv_portal_marker:auction_room_exit";
    private static final String EXIT_PORTAL_TARGET = "sdv_portal_target:auction_room_exit";
    public static final int LOT_SECONDS = 35;
    public static final int FINAL_EXTENSION_SECONDS = 8;
    public static final int FIRST_CALL_SECONDS = 9;
    public static final int SECOND_CALL_SECONDS = 6;
    public static final int THIRD_CALL_SECONDS = 3;

    private static int tickCounter;
    private static boolean auctionExitTargetRegistered;

    private AuctionService() {
    }

    public static void openCreateScreen(ServerPlayer player) {
        int today = StardewTimeManager.get().getAbsoluteDay();
        PacketDistributor.sendToPlayer(player, new OpenAuctionCreatePayload(
            today,
            StardewTimeManager.get().getCurrentTime(),
            occupiedDayMask(today)));
        play(player, ModSounds.BOOK_READ.get(), 0.65f, 1.05f);
    }

    /** Bit i (0-based) set means day offset (i+1) is unavailable — taken by a live auction or clashing with an active festival. */
    private static int occupiedDayMask(int today) {
        AuctionWorldData data = AuctionWorldData.get();
        int mask = 0;
        for (int offset = 1; offset <= 14; offset++) {
            int scheduledDay = today + offset;
            boolean occupied = isActiveFestivalDay(scheduledDay)
                || data.auctions().values().stream()
                    .anyMatch(auction -> auction.scheduledDay() == scheduledDay
                        && auction.status() != Status.ENDED
                        && auction.status() != Status.CANCELLED);
            if (occupied) {
                mask |= 1 << (offset - 1);
            }
        }
        return mask;
    }

    /** True if the given absolute game day falls on an active (town-wide) festival, which an auction would clash with. */
    private static boolean isActiveFestivalDay(int absoluteDay) {
        int season = ((absoluteDay - 1) / 28) % 4;
        int dayOfSeason = ((absoluteDay - 1) % 28) + 1;
        return FestivalService.isFestivalDay(dayOfSeason, season);
    }

    public static void openJoinList(ServerPlayer player) {
        List<OpenAuctionJoinListPayload.AuctionSummary> summaries = AuctionWorldData.get().auctions().values().stream()
            .filter(auction -> auction.status() == Status.SCHEDULED || auction.status() == Status.PREVIEW || auction.status() == Status.WAITING)
            .filter(auction -> auction.scheduledDay() >= StardewTimeManager.get().getAbsoluteDay())
            .filter(auction -> !auction.creatorId().equals(player.getUUID()))
            .sorted(Comparator.comparingInt(AuctionRecord::scheduledDay).thenComparingInt(AuctionRecord::startMinute))
            .map(auction -> new OpenAuctionJoinListPayload.AuctionSummary(
                auction.id(), auction.name(), PlayerDisplayName.get(player.server, auction.creatorId()),
                auction.scheduledDay(), auction.startMinute(), auction.lots().size()))
            .toList();
        PacketDistributor.sendToPlayer(player, new OpenAuctionJoinListPayload(summaries));
        play(player, ModSounds.BOOK_READ.get(), 0.65f, 1.05f);
    }

    public static void createAuctionFromSlot(ServerPlayer player, int slot, int dayOffset, int startMinute, int startingPrice,
                                             String rawName, String rawPromo) {
        int today = StardewTimeManager.get().getAbsoluteDay();
        int scheduledDay = today + Math.max(1, Math.min(14, dayOffset));
        if (startMinute < 8 * 60 || startMinute > 22 * 60 || startMinute % 10 != 0) {
            reject(player, "stardewcraft.auction.error.time_range");
            return;
        }
        if (startingPrice < 1) {
            reject(player, "stardewcraft.auction.error.price");
            return;
        }
        if (isActiveFestivalDay(scheduledDay)) {
            reject(player, "stardewcraft.auction.error.festival_day");
            return;
        }
        AuctionWorldData data = AuctionWorldData.get();
        boolean occupied = data.auctions().values().stream()
            .anyMatch(auction -> auction.scheduledDay() == scheduledDay
                && auction.status() != Status.ENDED
                && auction.status() != Status.CANCELLED);
        if (occupied) {
            reject(player, "stardewcraft.auction.error.day_taken");
            return;
        }
        ItemStack stack = takeOneFromInventorySlot(player, slot);
        if (stack.isEmpty()) {
            reject(player, "stardewcraft.auction.error.no_item");
            return;
        }

        String name = cleanText(rawName, 48);
        if (name.isBlank()) {
            name = Component.translatable("stardewcraft.auction.default_name",
                    PlayerDisplayName.get(player)).getString();
        }
        String promo = cleanText(rawPromo, 160);
        if (promo.isBlank()) {
            promo = Component.translatable("stardewcraft.auction.default_promo").getString();
        }
        String playerName = PlayerDisplayName.get(player);
        AuctionLot lot = new AuctionLot(UUID.randomUUID(), player.getUUID(), playerName,
            stack, startingPrice, null, "", 0);
        AuctionRecord auction = new AuctionRecord(UUID.randomUUID(), player.getUUID(), playerName,
            scheduledDay, startMinute, name, promo, Status.SCHEDULED, List.of(lot), 0, 0L, 0, false);
        data.auctions().put(auction.id(), auction);
        data.setDirty();
        player.displayClientMessage(Component.translatable("stardewcraft.auction.created", name, formatDayTime(scheduledDay, startMinute)), false);
        play(player, ModSounds.BACKPACK_IN.get(), 0.75f, 1.0f);
        play(player, ModSounds.COIN.get(), 0.55f, 1.18f);
    }

    public static void joinAuctionFromSlot(ServerPlayer player, UUID auctionId, int slot, int startingPrice) {
        AuctionWorldData data = AuctionWorldData.get();
        AuctionRecord auction = data.auctions().get(auctionId);
        if (auction == null || auction.status() != Status.SCHEDULED) {
            reject(player, "stardewcraft.auction.error.not_joinable");
            return;
        }
        if (auction.creatorId().equals(player.getUUID()) || auction.lots().stream().anyMatch(lot -> lot.sellerId().equals(player.getUUID()))) {
            reject(player, "stardewcraft.auction.error.already_seller");
            return;
        }
        if (startingPrice < 1) {
            reject(player, "stardewcraft.auction.error.price");
            return;
        }
        ItemStack stack = takeOneFromInventorySlot(player, slot);
        if (stack.isEmpty()) {
            reject(player, "stardewcraft.auction.error.no_item");
            return;
        }
        List<AuctionLot> lots = new ArrayList<>(auction.lots());
        lots.add(new AuctionLot(UUID.randomUUID(), player.getUUID(), PlayerDisplayName.get(player),
            stack, startingPrice, null, "", 0));
        data.auctions().put(auction.id(), auction.withLots(lots));
        data.setDirty();
        player.displayClientMessage(Component.translatable("stardewcraft.auction.joined", auction.name()), false);
        play(player, ModSounds.BACKPACK_IN.get(), 0.75f, 1.0f);
        play(player, ModSounds.COIN.get(), 0.55f, 1.18f);
    }

    public static void requestCancelAuction(ServerPlayer player) {
        int today = StardewTimeManager.get().getAbsoluteDay();
        Optional<AuctionRecord> mine = AuctionWorldData.get().auctions().values().stream()
            .filter(auction -> auction.creatorId().equals(player.getUUID()))
            .filter(auction -> auction.status() == Status.SCHEDULED)
            .filter(auction -> auction.scheduledDay() >= today)
            .sorted(Comparator.comparingInt(AuctionRecord::scheduledDay).thenComparingInt(AuctionRecord::startMinute))
            .findFirst();
        if (mine.isEmpty()) {
            player.displayClientMessage(Component.translatable("stardewcraft.auction.cancel.none"), true);
            play(player, ModSounds.CANCEL.get(), 0.45f, 0.92f);
            return;
        }
        AuctionRecord auction = mine.get();
        PacketDistributor.sendToPlayer(player, new OpenLewisConfirmPayload(
            auction.id(),
            OpenLewisConfirmPayload.KIND_AUCTION_CANCEL,
            "stardewcraft.auction.cancel.question",
            List.of(auction.name(), String.valueOf(auction.lots().size()),
                formatDayTime(auction.scheduledDay(), auction.startMinute()).getString()),
            "stardewcraft.auction.cancel.accept",
            "stardewcraft.auction.cancel.reject"));
        play(player, ModSounds.BOOK_READ.get(), 0.58f, 1.0f);
    }

    public static void handleCancelConfirm(ServerPlayer player, UUID auctionId, boolean accepted) {
        if (!accepted) {
            play(player, ModSounds.BIG_DESELECT.get(), 0.42f, 0.92f);
            return;
        }
        AuctionWorldData data = AuctionWorldData.get();
        AuctionRecord auction = data.auctions().get(auctionId);
        if (auction == null || !auction.creatorId().equals(player.getUUID()) || auction.status() != Status.SCHEDULED) {
            reject(player, "stardewcraft.auction.error.not_cancellable");
            return;
        }
        MinecraftServer server = player.server;
        for (AuctionLot lot : auction.lots()) {
            deliverItem(server, lot.sellerId(), lot.stack());
            ServerPlayer seller = server.getPlayerList().getPlayer(lot.sellerId());
            if (seller != null && !seller.getUUID().equals(player.getUUID())) {
                seller.displayClientMessage(
                    Component.translatable("stardewcraft.auction.cancel.seller_refund", auction.name()), false);
            }
        }
        data.auctions().put(auction.id(), auction.withStatus(Status.CANCELLED));
        data.setDirty();
        player.displayClientMessage(Component.translatable("stardewcraft.auction.cancel.done", auction.name()), false);
        play(player, ModSounds.BACKPACK_IN.get(), 0.70f, 0.95f);
    }

    public static boolean tryOpenAuctionEntryChoice(ServerPlayer player) {
        if (activeToday(player.server).isEmpty()) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new OpenAuctionEntryChoicePayload());
        return true;
    }

    public static void enterAuctionRoom(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        loadAuctionRoomChunks(level);
        ensureAuctionExitPortal(player.server, true);
        player.teleportTo(level, ENTRY_POS.x, ENTRY_POS.y, ENTRY_POS.z, 180.0f, 0.0f);
        givePaddle(player);
        activeToday(player.server).ifPresent(auction -> {
            if (auction.status() == Status.IN_PROGRESS) {
                syncCurrentLot(player.server, auction);
                player.displayClientMessage(Component.translatable("stardewcraft.auction.enter.bid_hint"), false);
            } else if ((auction.status() == Status.PREVIEW || auction.status() == Status.WAITING)
                    && auction.creatorId().equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("stardewcraft.auction.enter.host_hint"), false);
            } else {
                player.displayClientMessage(Component.translatable("stardewcraft.auction.enter.wait_hint",
                        PlayerDisplayName.get(player.server, auction.creatorId())), false);
            }
        });
        play(player, ModSounds.BIG_SELECT.get(), 0.72f, 1.05f);
    }

    public static void exitAuctionRoom(ServerPlayer player) {
        removePaddles(player);
        Optional<InteriorPortalRegistry.PortalTarget> target = InteriorPortalRegistry.resolve("mayor_house_exit");
        if (target.isPresent()) {
            InteriorPortalRegistry.PortalTarget t = target.get();
            player.teleportTo(player.serverLevel(), t.x(), t.y(), t.z(), t.yaw(), t.pitch());
        } else {
            player.teleportTo(player.getX(), player.getY(), player.getZ());
        }
    }

    public static boolean handleAuctionBoardInteraction(ServerPlayer player) {
        Optional<AuctionRecord> active = activeToday(player.server);
        if (active.isEmpty() || !isInAuctionRoom(player)) {
            return false;
        }
        AuctionRecord auction = active.get();
        if ((auction.status() == Status.PREVIEW || auction.status() == Status.WAITING) && auction.creatorId().equals(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, new OpenLewisConfirmPayload(
                auction.id(),
                OpenLewisConfirmPayload.KIND_AUCTION_START,
                "stardewcraft.auction.start.question",
                List.of(auction.name(), String.valueOf(auction.lots().size())),
                "stardewcraft.auction.start.accept",
                "stardewcraft.auction.start.reject"));
            play(player, ModSounds.BOOK_READ.get(), 0.58f, 1.0f);
            return true;
        }
        if (auction.status() == Status.IN_PROGRESS) {
            openBidScreen(player);
        } else {
            player.displayClientMessage(Component.translatable("stardewcraft.auction.waiting_for_host",
                    PlayerDisplayName.get(player.server, auction.creatorId())), true);
            play(player, ModSounds.BUTTON_TAP.get(), 0.55f, 0.9f);
        }
        return true;
    }

    public static void handleStartConfirm(ServerPlayer player, UUID auctionId, boolean accepted) {
        Optional<AuctionRecord> active = activeToday(player.server);
        if (active.isEmpty() || !isInAuctionRoom(player)) {
            reject(player, "stardewcraft.auction.paddle.inactive");
            return;
        }
        AuctionRecord auction = active.get();
        if (!auction.id().equals(auctionId)
            || !(auction.status() == Status.PREVIEW || auction.status() == Status.WAITING)
            || !auction.creatorId().equals(player.getUUID())) {
            reject(player, "stardewcraft.auction.error.not_host");
            return;
        }
        if (!accepted) {
            player.displayClientMessage(Component.translatable("stardewcraft.auction.start.cancelled"), true);
            play(player, ModSounds.BIG_DESELECT.get(), 0.42f, 0.92f);
            return;
        }
        startAuction(player.server, auction);
    }

    public static void openBidScreen(ServerPlayer player) {
        Optional<AuctionRecord> active = activeToday(player.server);
        if (active.isEmpty() || active.get().status() != Status.IN_PROGRESS || !isInAuctionRoom(player)) {
            player.displayClientMessage(Component.translatable("stardewcraft.auction.paddle.inactive"), true);
            play(player, ModSounds.CANCEL.get(), 0.45f, 0.95f);
            return;
        }
        AuctionRecord auction = active.get();
        AuctionLot lot = auction.currentLot();
        if (lot == null) {
            return;
        }
        long remainingTicks = Math.max(0L, auction.lotEndTick() - player.serverLevel().getGameTime());
        PacketDistributor.sendToPlayer(player, OpenAuctionBidPayload.from(
                player.server, auction, lot, (int) (remainingTicks / 20L), canBid(player, lot)));
    }

    public static void submitBid(ServerPlayer player, int bid) {
        Optional<AuctionRecord> active = activeToday(player.server);
        if (active.isEmpty() || active.get().status() != Status.IN_PROGRESS || !isInAuctionRoom(player)) {
            reject(player, "stardewcraft.auction.paddle.inactive");
            return;
        }
        AuctionRecord auction = active.get();
        AuctionLot lot = auction.currentLot();
        if (lot == null) {
            return;
        }
        if (!canBid(player, lot)) {
            reject(player, "stardewcraft.auction.error.own_lot");
            return;
        }
        int minBid = lot.nextBid();
        if (bid < minBid) {
            reject(player, "stardewcraft.auction.error.low_bid");
            return;
        }
        if (!SharedMoneyService.removeMoney(player.getUUID(), bid)) {
            reject(player, "stardewcraft.auction.error.no_money");
            return;
        }
        if (lot.highestBidderId() != null && lot.highestBid() > 0) {
            SharedMoneyService.addMoney(lot.highestBidderId(), lot.highestBid());
        }

        AuctionLot nextLot = lot.withBid(player.getUUID(), PlayerDisplayName.get(player), bid);
        List<AuctionLot> lots = new ArrayList<>(auction.lots());
        lots.set(auction.currentLotIndex(), nextLot);
        long now = player.serverLevel().getGameTime();
        long endTick = auction.lotEndTick();
        if (endTick - now < FINAL_EXTENSION_SECONDS * 20L) {
            endTick = now + FINAL_EXTENSION_SECONDS * 20L;
        }
        AuctionRecord next = auction.withLots(lots).withRuntime(auction.currentLotIndex(), endTick, 0, Status.IN_PROGRESS);
        AuctionWorldData.get().auctions().put(next.id(), next);
        AuctionWorldData.get().setDirty();
        broadcastInRoom(player.server, Component.translatable("stardewcraft.auction.bid.broadcast",
            PlayerDisplayName.get(player), bid, nextLot.stack().getHoverName()).withStyle(ChatFormatting.GOLD), false);
        playInRoom(player.server, ModSounds.BUTTON_PRESS.get(), 0.45f, 1.1f);
        playInRoom(player.server, ModSounds.COIN.get(), 0.42f, 1.24f);
        play(player, ModSounds.MONEY_DIAL.get(), 0.50f, 1.0f);
        syncCurrentLot(player.server, next);
    }

    public static void onTimeChanged(MinecraftServer server) {
        AuctionWorldData data = AuctionWorldData.get();
        int today = StardewTimeManager.get().getAbsoluteDay();
        int now = StardewTimeManager.get().getCurrentTime();
        for (AuctionRecord auction : new ArrayList<>(data.auctions().values())) {
            if (auction.scheduledDay() != today || auction.status() != Status.SCHEDULED || now < auction.startMinute()) {
                continue;
            }
            AuctionRecord next = auction.withStatus(Status.PREVIEW).withStartNotified(true);
            data.auctions().put(next.id(), next);
            data.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                "stardewcraft.auction.started_notice", next.name()).withStyle(ChatFormatting.GOLD), false);
            playForAll(server, ModSounds.MACHINE_BELL.get(), 0.65f, 1.05f);
        }
    }

    public static void serverTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 10 != 0) {
            return;
        }
        Optional<AuctionRecord> active = activeToday(server);
        if (active.isEmpty()) {
            cleanupRoomIfIdle(server);
            return;
        }
        AuctionRecord auction = active.get();
        ensureAuctionExitPortal(server, false);
        ensureRoomPlayersHavePaddle(server);
        if (auction.status() == Status.PREVIEW || auction.status() == Status.WAITING) {
            AuctionRecord waiting = auction.status() == Status.PREVIEW ? auction.withStatus(Status.WAITING) : auction;
            if (waiting != auction) {
                AuctionWorldData.get().auctions().put(waiting.id(), waiting);
                AuctionWorldData.get().setDirty();
            }
            for (ServerPlayer player : roomPlayers(server)) {
                if (auction.creatorId().equals(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("stardewcraft.auction.actionbar.host_start"), true);
                } else {
                    player.displayClientMessage(Component.translatable("stardewcraft.auction.actionbar.waiting",
                            PlayerDisplayName.get(server, auction.creatorId())), true);
                }
            }
        } else if (auction.status() == Status.IN_PROGRESS) {
            tickRunningAuction(server, auction);
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        for (ItemStack stack : AuctionWorldData.get().takePendingItems(player.getUUID())) {
            giveOrDrop(player, stack);
        }
        removePaddles(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        removePaddles(player);
    }

    public static int bidStep(int price) {
        if (price < 1000) {
            return 100;
        }
        if (price < 10_000) {
            return 500;
        }
        return 1000;
    }

    public static boolean isInAuctionRoom(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
            && ModDimensions.STARDEW_VALLEY.equals(level.dimension())
            && ROOM_BOX.contains(player.position());
    }

    private static void startAuction(MinecraftServer server, AuctionRecord auction) {
        if (auction.lots().isEmpty()) {
            return;
        }
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        loadAuctionRoomChunks(level);
        long endTick = level.getGameTime() + LOT_SECONDS * 20L;
        AuctionRecord next = auction.withRuntime(0, endTick, 0, Status.IN_PROGRESS);
        AuctionWorldData.get().auctions().put(next.id(), next);
        AuctionWorldData.get().setDirty();
        broadcastInRoom(server, Component.translatable("stardewcraft.auction.opening",
            next.name(), next.promo(), next.lots().size()).withStyle(ChatFormatting.GOLD), false);
        spawnCurrentDisplay(level, next.currentLot());
        syncCurrentLot(server, next);
        playInRoom(server, ModSounds.SHWIP.get(), 0.72f, 1.04f);
    }

    private static void tickRunningAuction(MinecraftServer server, AuctionRecord auction) {
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        AuctionLot lot = auction.currentLot();
        if (lot == null || StardewTimeManager.get().getCurrentTime() >= StardewTimeManager.MIDNIGHT) {
            finishAuction(server, auction);
            return;
        }
        long now = level.getGameTime();
        long remaining = auction.lotEndTick() - now;
        if (remaining <= 0) {
            finishCurrentLot(server, auction);
            return;
        }
        int nextStage = auction.callStage();
        if (remaining <= THIRD_CALL_SECONDS * 20L && nextStage < 3) {
            nextStage = 3;
        } else if (remaining <= SECOND_CALL_SECONDS * 20L && nextStage < 2) {
            nextStage = 2;
        } else if (remaining <= FIRST_CALL_SECONDS * 20L && nextStage < 1) {
            nextStage = 1;
        }
        if (nextStage != auction.callStage()) {
            AuctionRecord next = auction.withRuntime(auction.currentLotIndex(), auction.lotEndTick(), nextStage, Status.IN_PROGRESS);
            AuctionWorldData.get().auctions().put(next.id(), next);
            AuctionWorldData.get().setDirty();
            broadcastInRoom(server, Component.translatable("stardewcraft.auction.call." + nextStage,
                lot.currentPrice(), lot.stack().getHoverName()).withStyle(ChatFormatting.GOLD), true);
            playInRoom(server, ModSounds.BUTTON_TAP.get(), 0.55f, 0.82f + nextStage * 0.12f);
        }
        if (tickCounter % 40 == 0) {
            syncCurrentLot(server, auction);
        }
    }

    private static void finishCurrentLot(MinecraftServer server, AuctionRecord auction) {
        AuctionLot lot = auction.currentLot();
        if (lot == null) {
            finishAuction(server, auction);
            return;
        }
        if (lot.highestBidderId() != null && lot.highestBid() > 0) {
            SharedMoneyService.addMoney(lot.sellerId(), lot.highestBid());
            deliverItem(server, lot.highestBidderId(), lot.stack());
            broadcastInRoom(server, Component.translatable("stardewcraft.auction.sold",
                lot.stack().getHoverName(), PlayerDisplayName.get(server, lot.highestBidderId()),
                lot.highestBid()).withStyle(ChatFormatting.GOLD), false);
            playInRoom(server, ModSounds.HAMMER.get(), 0.75f, 1.0f);
            playInRoom(server, ModSounds.COIN.get(), 0.62f, 1.0f);
        } else {
            deliverItem(server, lot.sellerId(), lot.stack());
            broadcastInRoom(server, Component.translatable("stardewcraft.auction.unsold",
                lot.stack().getHoverName()).withStyle(ChatFormatting.GRAY), false);
            playInRoom(server, ModSounds.BIG_DESELECT.get(), 0.55f, 0.92f);
        }
        int nextIndex = auction.currentLotIndex() + 1;
        if (nextIndex >= auction.lots().size()) {
            finishAuction(server, auction.withRuntime(nextIndex, 0L, 0, Status.SETTLING));
            return;
        }
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        long nextEndTick = (level == null ? 0L : level.getGameTime()) + LOT_SECONDS * 20L;
        AuctionRecord next = auction.withRuntime(nextIndex, nextEndTick, 0, Status.IN_PROGRESS);
        AuctionWorldData.get().auctions().put(next.id(), next);
        AuctionWorldData.get().setDirty();
        if (level != null) {
            spawnCurrentDisplay(level, next.currentLot());
        }
        syncCurrentLot(server, next);
        playInRoom(server, ModSounds.THROW_DOWN_ITEM.get(), 0.65f, 1.0f);
    }

    private static void finishAuction(MinecraftServer server, AuctionRecord auction) {
        // Two entry paths:
        //  1) Normal end: finishCurrentLot already settled every lot and advanced currentLotIndex past the end,
        //     so the loop below runs zero times.
        //  2) Midnight cutoff: the current lot is still unsettled, so settle it as a sale when it has a winner;
        //     every remaining (never-run) lot is returned to its seller. Winning-bid money was escrowed at bid time,
        //     so there is no double charge here.
        for (int i = Math.max(auction.currentLotIndex(), 0); i < auction.lots().size(); i++) {
            AuctionLot lot = auction.lots().get(i);
            if (i == auction.currentLotIndex() && lot.highestBidderId() != null && lot.highestBid() > 0) {
                SharedMoneyService.addMoney(lot.sellerId(), lot.highestBid());
                deliverItem(server, lot.highestBidderId(), lot.stack());
            } else {
                if (lot.highestBidderId() != null && lot.highestBid() > 0) {
                    SharedMoneyService.addMoney(lot.highestBidderId(), lot.highestBid());
                }
                deliverItem(server, lot.sellerId(), lot.stack());
            }
        }
        AuctionWorldData data = AuctionWorldData.get();
        data.auctions().put(auction.id(), auction.withStatus(Status.ENDED));
        data.setDirty();
        clearDisplay(server);
        clearDroppedPaddles(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removePaddles(player);
        }
        broadcastInRoom(server, Component.translatable("stardewcraft.auction.finished", auction.name()).withStyle(ChatFormatting.GOLD), false);
        playInRoom(server, ModSounds.MACHINE_BELL.get(), 0.72f, 1.0f);
        playInRoom(server, ModSounds.REWARD.get(), 0.55f, 1.08f);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, SyncAuctionBoardPayload.clear());
        }
    }

    private static boolean canBid(ServerPlayer player, AuctionLot lot) {
        if (lot.sellerId().equals(player.getUUID())) {
            return false;
        }
        return !SharedMoneyService.sameGroup(player.getUUID(), lot.sellerId());
    }

    private static Optional<AuctionRecord> activeToday(MinecraftServer server) {
        int today = StardewTimeManager.get().getAbsoluteDay();
        return AuctionWorldData.get().auctions().values().stream()
            .filter(auction -> auction.scheduledDay() == today)
            .filter(auction -> auction.status() == Status.PREVIEW
                || auction.status() == Status.WAITING
                || auction.status() == Status.IN_PROGRESS
                || auction.status() == Status.SETTLING)
            .findFirst();
    }

    private static ItemStack takeOneFromInventorySlot(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty() || inSlot.is(ModItems.AUCTION_PADDLE.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = inSlot.copyWithCount(1);
        inSlot.shrink(1);
        player.getInventory().setChanged();
        return taken;
    }

    private static void givePaddle(ServerPlayer player) {
        boolean has = player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.AUCTION_PADDLE.get()));
        if (!has) {
            giveOrDrop(player, new ItemStack(ModItems.AUCTION_PADDLE.get()));
        }
    }

    private static void removePaddles(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.AUCTION_PADDLE.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        player.getInventory().setChanged();
    }

    private static void ensureRoomPlayersHavePaddle(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isInAuctionRoom(player)) {
                givePaddle(player);
            } else {
                removePaddles(player);
            }
        }
    }

    private static void cleanupRoomIfIdle(MinecraftServer server) {
        if (tickCounter % 80 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removePaddles(player);
        }
        clearDisplay(server);
        clearDroppedPaddles(server);
    }

    private static void clearDroppedPaddles(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        List<ItemEntity> items = level.getEntities(EntityType.ITEM, ROOM_BOX.inflate(2.0D),
            entity -> entity.getItem().is(ModItems.AUCTION_PADDLE.get()));
        for (ItemEntity item : items) {
            item.discard();
        }
    }

    private static void loadAuctionRoomChunks(ServerLevel level) {
        loadChunkAt(level, ENTRY_POS);
        loadChunkAt(level, DISPLAY_POS);
        loadChunkAt(level, Vec3.atCenterOf(EXIT_PORTAL_BASE));
    }

    private static void loadChunkAt(ServerLevel level, Vec3 pos) {
        level.getChunkAt(BlockPos.containing(pos));
    }

    private static void syncCurrentLot(MinecraftServer server, AuctionRecord auction) {
        AuctionLot lot = auction.currentLot();
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (lot == null || level == null) {
            return;
        }
        long remainingTicks = Math.max(0L, auction.lotEndTick() - level.getGameTime());
        int remainingSeconds = (int) (remainingTicks / 20L);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isInAuctionRoom(player)) {
                PacketDistributor.sendToPlayer(player,
                    SyncAuctionBoardPayload.from(server, auction, lot, remainingSeconds, canBid(player, lot)));
            }
        }
    }

    private static void spawnCurrentDisplay(ServerLevel level, AuctionLot lot) {
        clearDisplay(level.getServer());
        if (lot == null || lot.stack().isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(lot.stack().getItem());
        String command = String.format(Locale.ROOT,
            "summon minecraft:item_display %.4f %.4f %.4f {Tags:[\"%s\"],item:{id:\"%s\",count:1},transformation:{left_rotation:[0.0f,1.0f,0.0f,0.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[3.0f,3.0f,3.0f],translation:[0.0f,0.0f,0.0f]}}",
            DISPLAY_POS.x, DISPLAY_POS.y, DISPLAY_POS.z, DISPLAY_TAG, itemId);
        runCommand(level, DISPLAY_POS, command);
    }

    private static void clearDisplay(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        List<Display.ItemDisplay> displays = level.getEntities(EntityType.ITEM_DISPLAY, new AABB(DISPLAY_POS, DISPLAY_POS).inflate(8.0D),
            entity -> entity.getTags().contains(DISPLAY_TAG));
        for (Entity entity : displays) {
            entity.discard();
        }
    }

    private static void ensureAuctionExitPortal(MinecraftServer server, boolean force) {
        if (!force && tickCounter % 80 != 0) {
            return;
        }
        ServerLevel level = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        registerAuctionExitTarget();
        if (!level.getBlockState(EXIT_PORTAL_BASE).is(com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get())) {
            InteriorSubspaceManager.placePortalTriggerArea(level, EXIT_PORTAL_BASE, 2, 3, 1,
                EXIT_PORTAL_MARKER, EXIT_PORTAL_TARGET);
        }
    }

    private static void registerAuctionExitTarget() {
        if (auctionExitTargetRegistered) {
            return;
        }
        Optional<InteriorPortalRegistry.PortalTarget> mayorExit = InteriorPortalRegistry.resolve("mayor_house_exit");
        if (mayorExit.isPresent()) {
            InteriorPortalRegistry.register("auction_room_exit", mayorExit.get());
            auctionExitTargetRegistered = true;
        }
    }

    private static void runCommand(ServerLevel level, Vec3 pos, String command) {
        CommandSourceStack source = level.getServer().createCommandSourceStack()
            .withLevel(level)
            .withPosition(pos)
            .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static void deliverItem(MinecraftServer server, UUID playerId, ItemStack stack) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            giveOrDrop(online, stack.copy());
        } else {
            AuctionWorldData.get().addPendingItem(playerId, stack.copy());
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
    }

    private static void reject(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        play(player, ModSounds.CANCEL.get(), 0.45f, 0.92f);
    }

    private static String cleanText(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }

    private static Component formatDayTime(int day, int minute) {
        int hour = minute / 60;
        int min = minute % 60;
        int zeroBased = Math.max(0, day - 1);
        int year = zeroBased / 112 + 1;
        int season = (zeroBased / 28) % 4;
        int dayOfSeason = zeroBased % 28 + 1;
        return Component.translatable("stardewcraft.auction.date_time",
            year,
            Component.translatable("stardewcraft.season." + switch (season) {
                case 1 -> "summer";
                case 2 -> "fall";
                case 3 -> "winter";
                default -> "spring";
            }),
            dayOfSeason,
            String.format(Locale.ROOT, "%02d:%02d", hour, min));
    }

    private static void broadcastInRoom(MinecraftServer server, Component message, boolean actionbar) {
        for (ServerPlayer player : roomPlayers(server)) {
            player.displayClientMessage(message, actionbar);
        }
    }

    private static List<ServerPlayer> roomPlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().filter(AuctionService::isInAuctionRoom).toList();
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void playInRoom(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : roomPlayers(server)) {
            play(player, sound, volume, pitch);
        }
    }

    private static void playForAll(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            play(player, sound, volume, pitch);
        }
    }
}
