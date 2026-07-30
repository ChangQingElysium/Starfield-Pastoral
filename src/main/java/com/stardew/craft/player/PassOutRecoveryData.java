package com.stardew.craft.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable per-player pass-out ledger.
 *
 * <p>Combat knockouts and overnight pass-outs cross client fades, dimension
 * changes, cutscenes, multiplayer settlement and sometimes reconnects. Keeping
 * those transitions in static collections made a server restart capable of
 * forgetting an already charged knockout or leaving a player at zero health.
 * This ledger is the authoritative transaction record for both flows.</p>
 */
public final class PassOutRecoveryData extends SavedData {
    private static final String DATA_NAME = "stardew_pass_out_recovery";

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public PassOutRecoveryData() {
    }

    public static PassOutRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PassOutRecoveryData::new, PassOutRecoveryData::load),
                DATA_NAME
        );
    }

    @Nullable
    public Entry get(UUID playerId) {
        return entries.get(playerId);
    }

    public boolean hasCombat(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.combat();
    }

    public void put(UUID playerId, Entry entry) {
        if (playerId == null || entry == null) {
            return;
        }
        entries.put(playerId, entry.copy());
        setDirty();
    }

    @Nullable
    public Entry remove(UUID playerId) {
        Entry removed = entries.remove(playerId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    @Nullable
    public Entry consumeOvernight(UUID playerId, int settlementDay) {
        Entry entry = entries.get(playerId);
        if (entry == null || entry.combat() || entry.settlementDay() > settlementDay) {
            return null;
        }
        entries.remove(playerId, entry);
        setDirty();
        return entry.settlementDay() == settlementDay ? entry.copy() : null;
    }

    public static PassOutRecoveryData load(CompoundTag tag, HolderLookup.Provider provider) {
        PassOutRecoveryData data = new PassOutRecoveryData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            if (!raw.hasUUID("Player")) {
                continue;
            }
            Entry entry = Entry.load(raw, provider);
            if (entry != null) {
                data.entries.put(raw.getUUID("Player"), entry);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> mapEntry : entries.entrySet()) {
            CompoundTag raw = mapEntry.getValue().save(provider);
            raw.putUUID("Player", mapEntry.getKey());
            list.add(raw);
        }
        tag.put("Entries", list);
        return tag;
    }

    public enum Stage {
        COLLAPSING,
        WAITING_FOR_DESTINATION,
        PLAYING_RESCUE,
        OUTCOME_PENDING,
        AWAITING_OVERNIGHT;

        static Stage fromName(String name, boolean combat) {
            try {
                return Stage.valueOf(name);
            } catch (RuntimeException ignored) {
                return combat ? COLLAPSING : AWAITING_OVERNIGHT;
            }
        }
    }

    /**
     * Mutable transaction value. Mutators are intentionally package-visible:
     * only the pass-out service advances the state machine.
     */
    public static final class Entry {
        private final long transactionId;
        private final PassOutService.PassOutType type;
        private final boolean combat;
        private Stage stage;
        private int moneyLost;
        private final List<ItemStack> lostItems;
        private final int settlementDay;
        private String rescuerNpcId;
        private String dialogueName;

        public Entry(
                long transactionId,
                PassOutService.PassOutType type,
                boolean combat,
                Stage stage,
                int moneyLost,
                List<ItemStack> lostItems,
                int settlementDay,
                String rescuerNpcId,
                String dialogueName
        ) {
            this.transactionId = Math.max(1L, transactionId);
            this.type = type == null ? PassOutService.PassOutType.EXHAUSTION_2AM : type;
            this.combat = combat;
            this.stage = stage == null
                    ? (combat ? Stage.COLLAPSING : Stage.AWAITING_OVERNIGHT)
                    : stage;
            this.moneyLost = Math.max(0, moneyLost);
            this.lostItems = copyStacks(lostItems);
            this.settlementDay = settlementDay;
            this.rescuerNpcId = rescuerNpcId == null ? "" : rescuerNpcId;
            this.dialogueName = dialogueName == null ? "" : dialogueName;
        }

        public long transactionId() {
            return transactionId;
        }

        public PassOutService.PassOutType type() {
            return type;
        }

        public boolean combat() {
            return combat;
        }

        public Stage stage() {
            return stage;
        }

        public int moneyLost() {
            return moneyLost;
        }

        public List<ItemStack> lostItems() {
            return copyStacks(lostItems);
        }

        public int settlementDay() {
            return settlementDay;
        }

        public String rescuerNpcId() {
            return rescuerNpcId;
        }

        public String dialogueName() {
            return dialogueName;
        }

        void setStage(Stage value) {
            stage = value;
        }

        void setOutcome(int money, List<ItemStack> items) {
            moneyLost = Math.max(0, money);
            lostItems.clear();
            lostItems.addAll(copyStacks(items));
        }

        void setRescuer(String npcId, String dialogue) {
            rescuerNpcId = npcId == null ? "" : npcId;
            dialogueName = dialogue == null ? "" : dialogue;
        }

        Entry copy() {
            return new Entry(transactionId, type, combat, stage, moneyLost,
                    lostItems, settlementDay, rescuerNpcId, dialogueName);
        }

        private CompoundTag save(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Transaction", transactionId);
            tag.putInt("Type", type.getId());
            tag.putBoolean("Combat", combat);
            tag.putString("Stage", stage.name());
            tag.putInt("MoneyLost", moneyLost);
            tag.putInt("SettlementDay", settlementDay);
            tag.putString("Rescuer", rescuerNpcId);
            tag.putString("Dialogue", dialogueName);
            ListTag items = new ListTag();
            for (ItemStack stack : lostItems) {
                if (!stack.isEmpty()) {
                    items.add(stack.save(provider));
                }
            }
            tag.put("LostItems", items);
            return tag;
        }

        @Nullable
        private static Entry load(CompoundTag tag, HolderLookup.Provider provider) {
            boolean combat = tag.getBoolean("Combat");
            List<ItemStack> items = new ArrayList<>();
            ListTag list = tag.getList("LostItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parse(provider, list.getCompound(i)).orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            return new Entry(
                    tag.contains("Transaction", Tag.TAG_LONG) ? tag.getLong("Transaction") : 1L,
                    PassOutService.PassOutType.fromId(tag.getInt("Type")),
                    combat,
                    Stage.fromName(tag.getString("Stage"), combat),
                    tag.getInt("MoneyLost"),
                    items,
                    tag.contains("SettlementDay", Tag.TAG_INT)
                            ? tag.getInt("SettlementDay")
                            : Integer.MIN_VALUE,
                    tag.getString("Rescuer"),
                    tag.getString("Dialogue")
            );
        }

        private static List<ItemStack> copyStacks(List<ItemStack> source) {
            if (source == null || source.isEmpty()) {
                return new ArrayList<>();
            }
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                if (stack != null && !stack.isEmpty()) {
                    copy.add(stack.copy());
                }
            }
            return copy;
        }
    }
}
