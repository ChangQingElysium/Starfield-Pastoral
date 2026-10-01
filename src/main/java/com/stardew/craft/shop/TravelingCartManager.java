package com.stardew.craft.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nonnull;

import java.util.Random;
import java.util.UUID;

public final class TravelingCartManager extends SavedData {
    private static final String DATA_NAME = "stardew_traveling_cart";
    private static final int UNINITIALIZED_GUARANTEE = Integer.MIN_VALUE;

    private UUID entityUuid;
    private int visitsUntilY1Guarantee = UNINITIALIZED_GUARANTEE;
    private int lastProcessedAbsoluteDay = Integer.MIN_VALUE;

    public static TravelingCartManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(TravelingCartManager::new, TravelingCartManager::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(TravelingCartManager::new, TravelingCartManager::load)),
                DATA_NAME
        );
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(UUID uuid) {
        this.entityUuid = uuid;
        setDirty();
    }

    public void clearEntityUuid() {
        if (entityUuid != null) {
            entityUuid = null;
            setDirty();
        }
    }

    public void ensureGuaranteeInitialized(long seed, int currentYear) {
        if (visitsUntilY1Guarantee != UNINITIALIZED_GUARANTEE) {
            return;
        }
        if (currentYear == 1) {
            visitsUntilY1Guarantee = new Random(seed * 12L).nextInt(29) + 2;
        } else {
            visitsUntilY1Guarantee = -1;
        }
        setDirty();
    }

    public void processDay(int absoluteDay, boolean visitDay, int currentYear) {
        if (lastProcessedAbsoluteDay == absoluteDay) {
            return;
        }
        lastProcessedAbsoluteDay = absoluteDay;
        if (currentYear == 1 && visitDay && visitsUntilY1Guarantee >= 0) {
            visitsUntilY1Guarantee--;
        }
        setDirty();
    }

    public int getVisitsUntilY1Guarantee() {
        return visitsUntilY1Guarantee;
    }

    public static TravelingCartManager load(CompoundTag tag, HolderLookup.Provider provider) {
        TravelingCartManager manager = new TravelingCartManager();
        if (tag.hasUUID("EntityUUID")) {
            manager.entityUuid = tag.getUUID("EntityUUID");
        }
        if (tag.contains("VisitsUntilY1Guarantee")) {
            manager.visitsUntilY1Guarantee = tag.getInt("VisitsUntilY1Guarantee");
        }
        if (tag.contains("LastProcessedAbsoluteDay")) {
            manager.lastProcessedAbsoluteDay = tag.getInt("LastProcessedAbsoluteDay");
        }
        return manager;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        if (entityUuid != null) {
            tag.putUUID("EntityUUID", entityUuid);
        }
        tag.putInt("VisitsUntilY1Guarantee", visitsUntilY1Guarantee);
        tag.putInt("LastProcessedAbsoluteDay", lastProcessedAbsoluteDay);
        return tag;
    }
}