package com.stardew.craft.auction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public class AuctionWorldData extends SavedData {
    private static final String DATA_NAME = "stardewcraft_auctions";

    private final Map<UUID, AuctionRecord> auctions = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingItems = new LinkedHashMap<>();

    public static AuctionWorldData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new AuctionWorldData();
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public Map<UUID, AuctionRecord> auctions() {
        return auctions;
    }

    public List<ItemStack> takePendingItems(UUID playerId) {
        List<ItemStack> stacks = pendingItems.remove(playerId);
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        setDirty();
        return stacks;
    }

    public void addPendingItem(UUID playerId, ItemStack stack) {
        if (playerId == null || stack == null || stack.isEmpty()) {
            return;
        }
        pendingItems.computeIfAbsent(playerId, id -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        ListTag auctionsTag = new ListTag();
        for (AuctionRecord auction : auctions.values()) {
            auctionsTag.add(auction.save(provider));
        }
        tag.put("Auctions", auctionsTag);

        ListTag pendingTag = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingItems.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            ListTag stacksTag = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) {
                    stacksTag.add(stack.save(provider));
                }
            }
            ownerTag.put("Stacks", stacksTag);
            pendingTag.add(ownerTag);
        }
        tag.put("PendingItems", pendingTag);
        return tag;
    }

    private static AuctionWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        AuctionWorldData data = new AuctionWorldData();
        ListTag auctionsTag = tag.getList("Auctions", Tag.TAG_COMPOUND);
        for (int i = 0; i < auctionsTag.size(); i++) {
            AuctionRecord record = AuctionRecord.load(auctionsTag.getCompound(i), provider);
            if (record != null) {
                data.auctions.put(record.id(), record);
            }
        }

        ListTag pendingTag = tag.getList("PendingItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            CompoundTag ownerTag = pendingTag.getCompound(i);
            if (!ownerTag.hasUUID("Owner")) {
                continue;
            }
            UUID ownerId = ownerTag.getUUID("Owner");
            List<ItemStack> stacks = new ArrayList<>();
            ListTag stacksTag = ownerTag.getList("Stacks", Tag.TAG_COMPOUND);
            for (int j = 0; j < stacksTag.size(); j++) {
                ItemStack stack = ItemStack.parse(provider, stacksTag.getCompound(j)).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            if (!stacks.isEmpty()) {
                data.pendingItems.put(ownerId, stacks);
            }
        }
        return data;
    }

    public static SavedData.Factory<AuctionWorldData> factory() {
        return new SavedData.Factory<>(AuctionWorldData::new, AuctionWorldData::load);
    }

    public enum Status {
        SCHEDULED,
        PREVIEW,
        WAITING,
        IN_PROGRESS,
        SETTLING,
        ENDED,
        CANCELLED
    }

    public record AuctionRecord(
        UUID id,
        UUID creatorId,
        String creatorName,
        int scheduledDay,
        int startMinute,
        String name,
        String promo,
        Status status,
        List<AuctionLot> lots,
        int currentLotIndex,
        long lotEndTick,
        int callStage,
        boolean startNotified
    ) {
        public AuctionRecord withStatus(Status nextStatus) {
            return new AuctionRecord(id, creatorId, creatorName, scheduledDay, startMinute, name, promo, nextStatus,
                lots, currentLotIndex, lotEndTick, callStage, startNotified);
        }

        public AuctionRecord withStartNotified(boolean notified) {
            return new AuctionRecord(id, creatorId, creatorName, scheduledDay, startMinute, name, promo, status,
                lots, currentLotIndex, lotEndTick, callStage, notified);
        }

        public AuctionRecord withRuntime(int nextLotIndex, long nextLotEndTick, int nextCallStage, Status nextStatus) {
            return new AuctionRecord(id, creatorId, creatorName, scheduledDay, startMinute, name, promo, nextStatus,
                lots, nextLotIndex, nextLotEndTick, nextCallStage, startNotified);
        }

        public AuctionRecord withLots(List<AuctionLot> nextLots) {
            return new AuctionRecord(id, creatorId, creatorName, scheduledDay, startMinute, name, promo, status,
                List.copyOf(nextLots), currentLotIndex, lotEndTick, callStage, startNotified);
        }

        public AuctionLot currentLot() {
            return currentLotIndex >= 0 && currentLotIndex < lots.size() ? lots.get(currentLotIndex) : null;
        }

        CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putUUID("Creator", creatorId);
            tag.putString("CreatorName", creatorName);
            tag.putInt("ScheduledDay", scheduledDay);
            tag.putInt("StartMinute", startMinute);
            tag.putString("Name", name);
            tag.putString("Promo", promo);
            tag.putString("Status", status.name());
            tag.putInt("CurrentLotIndex", currentLotIndex);
            tag.putLong("LotEndTick", lotEndTick);
            tag.putInt("CallStage", callStage);
            tag.putBoolean("StartNotified", startNotified);
            ListTag lotsTag = new ListTag();
            for (AuctionLot lot : lots) {
                lotsTag.add(lot.save(provider));
            }
            tag.put("Lots", lotsTag);
            return tag;
        }

        static AuctionRecord load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.hasUUID("Id") || !tag.hasUUID("Creator")) {
                return null;
            }
            Status status;
            try {
                status = Status.valueOf(tag.getString("Status"));
            } catch (IllegalArgumentException ex) {
                status = Status.SCHEDULED;
            }
            List<AuctionLot> lots = new ArrayList<>();
            ListTag lotsTag = tag.getList("Lots", Tag.TAG_COMPOUND);
            for (int i = 0; i < lotsTag.size(); i++) {
                AuctionLot lot = AuctionLot.load(lotsTag.getCompound(i), provider);
                if (lot != null) {
                    lots.add(lot);
                }
            }
            if (lots.isEmpty()) {
                return null;
            }
            return new AuctionRecord(
                tag.getUUID("Id"),
                tag.getUUID("Creator"),
                tag.getString("CreatorName"),
                tag.getInt("ScheduledDay"),
                tag.getInt("StartMinute"),
                tag.getString("Name"),
                tag.getString("Promo"),
                status,
                List.copyOf(lots),
                tag.getInt("CurrentLotIndex"),
                tag.getLong("LotEndTick"),
                tag.getInt("CallStage"),
                tag.getBoolean("StartNotified"));
        }
    }

    public record AuctionLot(
        UUID id,
        UUID sellerId,
        String sellerName,
        ItemStack stack,
        int startingPrice,
        UUID highestBidderId,
        String highestBidderName,
        int highestBid
    ) {
        public AuctionLot withBid(UUID bidderId, String bidderName, int bid) {
            return new AuctionLot(id, sellerId, sellerName, stack, startingPrice, bidderId, bidderName, bid);
        }

        public int currentPrice() {
            return highestBid > 0 ? highestBid : startingPrice;
        }

        public int nextBid() {
            int base = highestBid > 0 ? highestBid : Math.max(0, startingPrice);
            if (highestBid <= 0) {
                return startingPrice;
            }
            return base + AuctionService.bidStep(base);
        }

        CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putUUID("Seller", sellerId);
            tag.putString("SellerName", sellerName);
            tag.put("Stack", stack.save(provider));
            tag.putInt("StartingPrice", startingPrice);
            if (highestBidderId != null) {
                tag.putUUID("HighestBidder", highestBidderId);
                tag.putString("HighestBidderName", highestBidderName == null ? "" : highestBidderName);
            }
            tag.putInt("HighestBid", highestBid);
            return tag;
        }

        static AuctionLot load(CompoundTag tag, HolderLookup.Provider provider) {
            if (!tag.hasUUID("Id") || !tag.hasUUID("Seller")) {
                return null;
            }
            ItemStack stack = ItemStack.parse(provider, tag.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                return null;
            }
            UUID bidderId = tag.hasUUID("HighestBidder") ? tag.getUUID("HighestBidder") : null;
            return new AuctionLot(
                tag.getUUID("Id"),
                tag.getUUID("Seller"),
                tag.getString("SellerName"),
                stack,
                Math.max(0, tag.getInt("StartingPrice")),
                bidderId,
                tag.getString("HighestBidderName"),
                Math.max(0, tag.getInt("HighestBid")));
        }
    }
}
