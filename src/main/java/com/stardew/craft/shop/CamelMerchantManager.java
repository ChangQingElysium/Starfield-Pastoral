package com.stardew.craft.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 沙漠骆驼商人持久化数据：仅记录唯一占位实体的 UUID，确保每存档不重复、不缺失。
 * <p>骆驼商人模型尚未完成，目前以原版沙漠群系村民（NoAI）作为占位。
 */
public final class CamelMerchantManager extends SavedData {
    private static final String DATA_NAME = "stardew_camel_merchant";

    private UUID villagerUuid;

    public static CamelMerchantManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(CamelMerchantManager::new, CamelMerchantManager::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(CamelMerchantManager::new, CamelMerchantManager::load)),
            DATA_NAME
        );
    }

    public UUID getVillagerUuid() {
        return villagerUuid;
    }

    public void setVillagerUuid(UUID uuid) {
        this.villagerUuid = uuid;
        setDirty();
    }

    public void clear() {
        if (villagerUuid != null) {
            this.villagerUuid = null;
            setDirty();
        }
    }

    public static CamelMerchantManager load(CompoundTag tag, HolderLookup.Provider provider) {
        CamelMerchantManager m = new CamelMerchantManager();
        if (tag.hasUUID("VillagerUUID")) {
            m.villagerUuid = tag.getUUID("VillagerUUID");
        }
        return m;
    }

    @Override
    @SuppressWarnings("null")
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        if (villagerUuid != null) {
            tag.putUUID("VillagerUUID", villagerUuid);
        }
        return tag;
    }
}
