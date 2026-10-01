package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.menu.MiniShippingBinMenu;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** SDV separate-wallet inventories: each player has nine recoverable slots until overnight. */
public final class MiniShippingBinBlockEntity extends WoodenChestBlockEntity {
    private static final Set<MiniShippingBinBlockEntity> LOADED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    public final com.stardew.craft.model.ShippingBinLidMotion lidMotion = new com.stardew.craft.model.ShippingBinLidMotion();
    private int viewers;
    private final Map<UUID, BinInventory> accounts = new HashMap<>();

    public MiniShippingBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINI_SHIPPING_BIN.get(), pos, state, 9);
    }
    @Override public void onLoad() { super.onLoad(); LOADED.add(this); }
    @Override public void setRemoved() { LOADED.remove(this); super.setRemoved(); }
    @Override public Component getDisplayName() { return Component.translatable("block.stardewcraft.mini_shipping_bin"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        flush(true);
        return new MiniShippingBinMenu(id, inventory, account(player.getUUID()));
    }
    public Container account(UUID player) { return accounts.computeIfAbsent(player, key -> new BinInventory()); }
    @Override public boolean isEmpty() { return accounts.values().stream().allMatch(SimpleContainer::isEmpty); }
    // An anonymous hopper must never acquire or mix another player's settlement inventory.
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public void setColorSelection(int selection) { }

    @Override public void startOpen(Player player) { if (!player.isSpectator()) viewers++; }
    @Override public void stopOpen(Player player) { if (!player.isSpectator()) viewers = Math.max(0, viewers - 1); }
    @Override public boolean isInUse() { return viewers > 0; }

    public void tick() {
        var open = com.stardew.craft.block.utility.WoodenChestBlock.OPEN;
        if (level.isClientSide) {
            lidMotion.tick(getBlockState().getValue(open), 10f / 3f);
            return;
        }
        flushExpired();
        boolean nearby = level.players().stream().anyMatch(player -> !player.isSpectator()
                && Math.abs(player.blockPosition().getX() - worldPosition.getX()) <= 1
                && Math.abs(player.blockPosition().getZ() - worldPosition.getZ()) <= 1
                && Math.abs(player.getY() - worldPosition.getY()) < 2.5);
        var state = getBlockState();
        if (state.getValue(open) != nearby) {
            level.setBlock(worldPosition, state.setValue(open, nearby), 3);
            level.playSound(null, worldPosition, nearby ? com.stardew.craft.sound.ModSounds.DOOR_CREAK.get()
                    : com.stardew.craft.sound.ModSounds.DOOR_CREAK_REVERSE.get(), net.minecraft.sounds.SoundSource.BLOCKS, .7f, 1);
        }
    }

    public static void flushAllForOvernight() {
        java.util.List<MiniShippingBinBlockEntity> bins;
        synchronized (LOADED) { bins = java.util.List.copyOf(LOADED); }
        for (var bin : bins) bin.flush(false);
    }
    public void flushExpired() {
        if (level != null && !level.isClientSide && level.getGameTime() % 20 == 0) flush(true);
    }
    private static int today() {
        var time = StardewTimeManager.get();
        return time == null ? 1 : time.getAbsoluteDay();
    }
    private void flush(boolean expiredOnly) {
        if (!(level instanceof ServerLevel server)) return;
        int day = today();
        for (var entry : accounts.entrySet()) {
            BinInventory inventory = entry.getValue();
            if (inventory.isEmpty() || (expiredOnly && inventory.day >= day)) continue;
            UUID owner = entry.getKey();
            var player = server.getServer().getPlayerList().getPlayer(owner);
            var data = PlayerDataManager.getPlayerData(owner);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) continue;
                var quote = player == null ? ProfessionSellPriceService.quoteItem(data, stack, SellSource.SHIPPING_BIN)
                        : ProfessionSellPriceService.quoteItem(player, stack, SellSource.SHIPPING_BIN);
                if (!quote.sellable() || quote.finalUnitPrice() <= 0) continue;
                OvernightSettlementTracker.recordShipping(server.getServer(), owner, stack, quote.finalUnitPrice(),
                        expiredOnly ? day : Math.max(day, inventory.day + 1));
                inventory.removeItemNoUpdate(slot);
            }
            inventory.setChanged();
            if (expiredOnly && player != null) {
                var payload = OvernightSettlementTracker.consumePayload(player);
                com.stardew.craft.player.PlayerStardewDataAPI.recordOvernightShippedItems(player, payload.shippedItems());
            }
        }
    }
    @Override public void dropAllContents(Level world, BlockPos pos) {
        if (world.isClientSide) return;
        for (BinInventory inventory : accounts.values()) {
            Containers.dropContents(world, pos, inventory);
            inventory.clearContent();
        }
        accounts.clear();
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        ListTag saved = new ListTag();
        accounts.forEach((owner, inventory) -> {
            if (inventory.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putInt("Day", inventory.day);
            ListTag items = new ListTag();
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).isEmpty()) continue;
                CompoundTag item = new CompoundTag();
                item.putInt("Slot", slot);
                item.put("Stack", PortItemStacks.save(inventory.getItem(slot), registries));
                items.add(item);
            }
            entry.put("Items", items);
            saved.add(entry);
        });
        tag.put("ShippingAccounts", saved);
    }
    @Override public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        accounts.clear();
        ListTag saved = tag.getList("ShippingAccounts", 10);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            if (!entry.hasUUID("Owner")) continue;
            BinInventory inventory = new BinInventory();
            ListTag items = entry.getList("Items", 10);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag item = items.getCompound(j);
                int slot = item.getInt("Slot");
                if (slot >= 0 && slot < 9) inventory.setItem(slot,
                        PortItemStacks.parse(registries, item.getCompound("Stack")).orElse(ItemStack.EMPTY));
            }
            inventory.day = entry.getInt("Day");
            accounts.put(entry.getUUID("Owner"), inventory);
        }
    }
    @Override public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        // Menu slot synchronization is private to its viewer; other players only need OPEN.
        return new CompoundTag();
    }
    private final class BinInventory extends SimpleContainer {
        private int day = today();
        private BinInventory() { super(9); }
        @Override public void setItem(int slot, ItemStack stack) {
            if (isEmpty() && !stack.isEmpty()) day = today();
            super.setItem(slot, stack);
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return ShippingBinBlockEntity.canShip(stack); }
        @Override public void setChanged() { super.setChanged(); MiniShippingBinBlockEntity.this.setChanged(); }
        @Override public boolean stillValid(Player player) { return MiniShippingBinBlockEntity.this.stillValid(player); }
        @Override public void startOpen(Player player) { MiniShippingBinBlockEntity.this.startOpen(player); }
        @Override public void stopOpen(Player player) { MiniShippingBinBlockEntity.this.stopOpen(player); }
    }
}
