package com.stardew.craft.world.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.StardewWorldEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent inverse ledger for active bounded world events. */
public final class WorldEventSavedData extends SavedData {
    private static final String DATA_NAME =
            StardewCraft.MODID + "_world_events";
    private static final int FORMAT_VERSION = 1;

    private final Map<UUID, ActiveEvent> active =
            new LinkedHashMap<>();

    public static WorldEventSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WorldEventSavedData::new,
                        WorldEventSavedData::load),
                DATA_NAME);
    }

    public Optional<ActiveEvent> find(UUID instanceId) {
        return Optional.ofNullable(active.get(instanceId));
    }

    public void put(ActiveEvent event) {
        active.put(event.instanceId(), event);
        setDirty();
    }

    public boolean remove(UUID instanceId) {
        if (active.remove(instanceId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public void markRecoveryRequired(UUID instanceId) {
        ActiveEvent event = active.get(instanceId);
        if (event != null && !event.recoveryRequired()) {
            active.put(instanceId, event.withRecoveryRequired());
            setDirty();
        }
    }

    public List<ActiveEvent> snapshot() {
        return active.values().stream()
                .sorted(Comparator.comparing(
                        event -> event.instanceId().toString()))
                .toList();
    }

    @Override
    @Nonnull
    public CompoundTag save(
            @Nonnull CompoundTag tag,
            @Nonnull HolderLookup.Provider registries
    ) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag entries = new ListTag();
        for (ActiveEvent event : snapshot()) {
            entries.add(saveEvent(event));
        }
        tag.put("Active", entries);
        return tag;
    }

    static WorldEventSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        WorldEventSavedData data = new WorldEventSavedData();
        ListTag entries = tag.getList("Active", Tag.TAG_COMPOUND);
        HolderLookup.RegistryLookup<
                net.minecraft.world.level.block.Block> blocks =
                registries.lookupOrThrow(Registries.BLOCK);
        for (int index = 0; index < entries.size(); index++) {
            try {
                ActiveEvent event = loadEvent(
                        entries.getCompound(index), blocks);
                if (event.changes().isEmpty()
                        || event.changes().size()
                        > StardewWorldEvents.MAX_BLOCK_CHANGES) {
                    throw new IllegalArgumentException(
                            "invalid block-change count");
                }
                data.active.put(event.instanceId(), event);
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "[World event] Preserved world load by ignoring "
                                + "malformed platform ledger row {}",
                        index, exception);
            }
        }
        StardewCraft.LOGGER.info(
                "[World event] Loaded {} active event instances",
                data.active.size());
        return data;
    }

    private static CompoundTag saveEvent(ActiveEvent event) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("InstanceId", event.instanceId());
        tag.putString("EventType", event.eventType().toString());
        tag.putString(
                "Dimension", event.dimension().location().toString());
        tag.put("Origin", NbtUtils.writeBlockPos(event.origin()));
        tag.putLong("CommittedGameTime", event.committedGameTime());
        tag.putBoolean(
                "RecoveryRequired", event.recoveryRequired());
        tag.put("PersistentData", event.persistentData());
        ListTag changes = new ListTag();
        for (StardewWorldEvents.BlockChange change
                : event.changes()) {
            CompoundTag changeTag = new CompoundTag();
            changeTag.put(
                    "Position",
                    NbtUtils.writeBlockPos(change.position()));
            changeTag.put(
                    "Expected",
                    NbtUtils.writeBlockState(change.expected()));
            changeTag.put(
                    "Replacement",
                    NbtUtils.writeBlockState(change.replacement()));
            changes.add(changeTag);
        }
        tag.put("Changes", changes);
        return tag;
    }

    private static ActiveEvent loadEvent(
            CompoundTag tag,
            HolderLookup.RegistryLookup<
                    net.minecraft.world.level.block.Block> blocks
    ) {
        UUID instanceId = tag.getUUID("InstanceId");
        ResourceLocation eventType = requireId(
                tag.getString("EventType"), "event type");
        ResourceLocation dimensionId = requireId(
                tag.getString("Dimension"), "dimension");
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, dimensionId);
        net.minecraft.core.BlockPos origin =
                NbtUtils.readBlockPos(tag, "Origin")
                        .orElseThrow(() -> new IllegalArgumentException(
                                "missing origin"))
                        .immutable();
        ListTag changeTags = tag.getList(
                "Changes", Tag.TAG_COMPOUND);
        ArrayList<StardewWorldEvents.BlockChange> changes =
                new ArrayList<>(changeTags.size());
        for (int index = 0; index < changeTags.size(); index++) {
            CompoundTag changeTag = changeTags.getCompound(index);
            net.minecraft.core.BlockPos position =
                    NbtUtils.readBlockPos(changeTag, "Position")
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "missing position"))
                            .immutable();
            changes.add(new StardewWorldEvents.BlockChange(
                    position,
                    NbtUtils.readBlockState(
                            blocks,
                            changeTag.getCompound("Expected")),
                    NbtUtils.readBlockState(
                            blocks,
                            changeTag.getCompound("Replacement"))));
        }
        return new ActiveEvent(
                instanceId,
                eventType,
                dimension,
                origin,
                changes,
                tag.getCompound("PersistentData"),
                tag.getLong("CommittedGameTime"),
                tag.getBoolean("RecoveryRequired"));
    }

    private static ResourceLocation requireId(
            String raw,
            String label
    ) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return id;
    }

    public record ActiveEvent(
            UUID instanceId,
            ResourceLocation eventType,
            ResourceKey<Level> dimension,
            net.minecraft.core.BlockPos origin,
            List<StardewWorldEvents.BlockChange> changes,
            CompoundTag persistentData,
            long committedGameTime,
            boolean recoveryRequired
    ) {
        public ActiveEvent {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(dimension, "dimension");
            origin = Objects.requireNonNull(
                    origin, "origin").immutable();
            changes = List.copyOf(Objects.requireNonNull(
                    changes, "changes"));
            persistentData = persistentData == null
                    ? new CompoundTag()
                    : persistentData.copy();
        }

        @Override
        public CompoundTag persistentData() {
            return persistentData.copy();
        }

        public ActiveEvent withRecoveryRequired() {
            return new ActiveEvent(
                    instanceId,
                    eventType,
                    dimension,
                    origin,
                    changes,
                    persistentData,
                    committedGameTime,
                    true);
        }

        public StardewWorldEvents.Receipt receipt() {
            return new StardewWorldEvents.Receipt(
                    instanceId,
                    eventType,
                    dimension,
                    origin,
                    changes.size(),
                    committedGameTime,
                    recoveryRequired);
        }
    }
}
