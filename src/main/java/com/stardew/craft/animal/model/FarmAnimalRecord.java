package com.stardew.craft.animal.model;

import com.stardew.craft.api.v1.agriculture.StardewAnimalPersistentData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class FarmAnimalRecord {
    private final long animalId;
    private final String animalTypeId;
    private String customName;
    private String buildingId;
    private String ownerPlayerUuid = "";
    private long parentAnimalId = -1L;
    private final AnimalAcquisitionSource acquisitionSource;
    private final int createdDay;
    private final int createdSeason;
    private final int createdYear;
    private int ageDays;
    private final int daysToMature;
    private boolean wasPetToday;
    private boolean wasAutoPetToday;
    private int friendship;
    private boolean allowReproduction;
    private int daysOwned;
    private boolean wasFedToday;
    private int fullness;
    private int happiness;
    private int moodMessage;
    private int daysSinceLastProduce;
    private String currentProduceId;
    private int produceQuality;
    private boolean hasEatenAnimalCracker;
    private StardewAnimalPersistentData persistentData = StardewAnimalPersistentData.empty();
    /** 上次处理的绝对天数（用于离线追赶）。新动物默认 0，首次 growDaily 时会初始化。 */
    private int lastProcessedAbsDay;
    private String projectionDimensionId = "";
    private long projectionPos;
    private boolean hasProjectionAnchor;

    public FarmAnimalRecord(long animalId,
                            String animalTypeId,
                            String customName,
                            String buildingId,
                            AnimalAcquisitionSource acquisitionSource,
                            int createdDay,
                            int createdSeason,
                            int createdYear,
                            int ageDays,
                            int daysToMature) {
        this(
            animalId,
            animalTypeId,
            customName,
            buildingId,
            acquisitionSource,
            createdDay,
            createdSeason,
            createdYear,
            ageDays,
            daysToMature,
            false,
            false,
            0,
            true,
            0,
            false,
            255,
            255,
            0,
            0,
            "",
            0,
            false
        );
    }

    public FarmAnimalRecord(long animalId,
                            String animalTypeId,
                            String customName,
                            String buildingId,
                            AnimalAcquisitionSource acquisitionSource,
                            int createdDay,
                            int createdSeason,
                            int createdYear,
                            int ageDays,
                            int daysToMature,
                            boolean wasPetToday,
                            boolean wasAutoPetToday,
                            int friendship,
                            boolean allowReproduction,
                            int daysOwned,
                            boolean wasFedToday,
                            int fullness,
                            int happiness,
                            int moodMessage,
                            int daysSinceLastProduce,
                            String currentProduceId,
                            int produceQuality,
                            boolean hasEatenAnimalCracker) {
        this.animalId = animalId;
        this.animalTypeId = animalTypeId;
        this.customName = customName;
        this.buildingId = buildingId;
        this.acquisitionSource = acquisitionSource;
        this.createdDay = createdDay;
        this.createdSeason = createdSeason;
        this.createdYear = createdYear;
        this.ageDays = ageDays;
        this.daysToMature = daysToMature;
        this.wasPetToday = wasPetToday;
        this.wasAutoPetToday = wasAutoPetToday;
        this.friendship = Math.max(0, friendship);
        this.allowReproduction = allowReproduction;
        this.daysOwned = Math.max(0, daysOwned);
        this.wasFedToday = wasFedToday;
        this.fullness = Math.max(0, Math.min(255, fullness));
        this.happiness = Math.max(0, Math.min(255, happiness));
        this.moodMessage = Math.max(0, moodMessage);
        this.daysSinceLastProduce = Math.max(0, daysSinceLastProduce);
        this.currentProduceId = currentProduceId == null ? "" : currentProduceId;
        this.produceQuality = Math.max(0, produceQuality);
        this.hasEatenAnimalCracker = hasEatenAnimalCracker;
        this.lastProcessedAbsDay = 0; // 新动物默认 0，首次处理时会初始化
    }

    public long animalId() {
        return animalId;
    }

    public String animalTypeId() {
        return animalTypeId;
    }

    public String customName() {
        return customName;
    }


    public int ageDays() {
        return ageDays;
    }

    public int daysToMature() {
        return daysToMature;
    }

    public boolean isBaby() {
        return ageDays < daysToMature;
    }

    public void incrementAgeDays(int delta) {
        if (delta <= 0) {
            return;
        }
        this.ageDays = Math.max(0, this.ageDays + delta);
    }

    public boolean wasPetToday() {
        return wasPetToday;
    }

    public void setWasPetToday(boolean wasPetToday) {
        this.wasPetToday = wasPetToday;
    }

    public boolean wasAutoPetToday() {
        return wasAutoPetToday;
    }

    public void setWasAutoPetToday(boolean wasAutoPetToday) {
        this.wasAutoPetToday = wasAutoPetToday;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public int friendship() {
        return friendship;
    }

    public void addFriendship(int amount) {
        if (amount == 0) {
            return;
        }
        this.friendship = Math.max(0, Math.min(1000, this.friendship + amount));
    }

    public boolean allowReproduction() {
        return allowReproduction;
    }

    public void setAllowReproduction(boolean allowReproduction) {
        this.allowReproduction = allowReproduction;
    }

    public int daysOwned() {
        return daysOwned;
    }

    public boolean wasFedToday() {
        return wasFedToday;
    }

    public void setWasFedToday(boolean wasFedToday) {
        this.wasFedToday = wasFedToday;
    }

    public void incrementDaysOwned() {
        this.daysOwned = Math.max(0, this.daysOwned + 1);
    }

    public int fullness() {
        return fullness;
    }

    public void setFullness(int fullness) {
        this.fullness = Math.max(0, Math.min(255, fullness));
    }

    public int happiness() {
        return happiness;
    }

    public void addHappiness(int delta) {
        if (delta == 0) {
            return;
        }
        this.happiness = Math.max(0, Math.min(255, this.happiness + delta));
    }

    public void setHappiness(int happiness) {
        this.happiness = Math.max(0, Math.min(255, happiness));
    }

    public int moodMessage() {
        return moodMessage;
    }

    public void setMoodMessage(int moodMessage) {
        this.moodMessage = Math.max(0, moodMessage);
    }

    public int daysSinceLastProduce() {
        return daysSinceLastProduce;
    }

    public void incrementDaysSinceLastProduce() {
        this.daysSinceLastProduce = Math.max(0, this.daysSinceLastProduce + 1);
    }

    public void setDaysSinceLastProduce(int daysSinceLastProduce) {
        this.daysSinceLastProduce = Math.max(0, daysSinceLastProduce);
    }

    public void resetDaysSinceLastProduce() {
        this.daysSinceLastProduce = 0;
    }

    public String currentProduceId() {
        return currentProduceId;
    }

    public void setCurrentProduceId(String currentProduceId) {
        this.currentProduceId = currentProduceId == null ? "" : currentProduceId;
    }

    public int produceQuality() {
        return produceQuality;
    }

    public void setProduceQuality(int produceQuality) {
        this.produceQuality = Math.max(0, produceQuality);
    }

    public boolean hasEatenAnimalCracker() {
        return hasEatenAnimalCracker;
    }

    public void setHasEatenAnimalCracker(boolean hasEatenAnimalCracker) {
        this.hasEatenAnimalCracker = hasEatenAnimalCracker;
    }

    public int lastProcessedAbsDay() {
        return lastProcessedAbsDay;
    }

    public void setLastProcessedAbsDay(int day) {
        this.lastProcessedAbsDay = day;
    }

    public String buildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public boolean hasProjectionAnchor() {
        return hasProjectionAnchor;
    }

    public String projectionDimensionId() {
        return projectionDimensionId;
    }

    public BlockPos projectionPos() {
        return BlockPos.of(projectionPos);
    }

    public boolean updateProjectionAnchor(
            String dimensionId,
            BlockPos pos
    ) {
        if (dimensionId == null || dimensionId.isBlank()
                || pos == null) {
            return false;
        }
        long packed = pos.asLong();
        if (hasProjectionAnchor
                && projectionDimensionId.equals(dimensionId)
                && projectionPos == packed) {
            return false;
        }
        projectionDimensionId = dimensionId;
        projectionPos = packed;
        hasProjectionAnchor = true;
        return true;
    }

    public boolean clearProjectionAnchor() {
        if (!hasProjectionAnchor) {
            return false;
        }
        projectionDimensionId = "";
        projectionPos = 0L;
        hasProjectionAnchor = false;
        return true;
    }

    public String ownerPlayerUuid() {
        return ownerPlayerUuid;
    }

    public void setOwnerPlayerUuid(String ownerPlayerUuid) {
        this.ownerPlayerUuid = ownerPlayerUuid == null ? "" : ownerPlayerUuid;
    }

    public long parentAnimalId() {
        return parentAnimalId;
    }

    public void setParentAnimalId(long parentAnimalId) {
        this.parentAnimalId = parentAnimalId > 0L ? parentAnimalId : -1L;
    }

    public AnimalAcquisitionSource acquisitionSource() {
        return acquisitionSource;
    }

    public int createdDay() {
        return createdDay;
    }

    public int createdSeason() {
        return createdSeason;
    }

    public int createdYear() {
        return createdYear;
    }

    public StardewAnimalPersistentData persistentData() {
        return persistentData;
    }

    @SuppressWarnings("null")
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("animalId", animalId);
        tag.putString("animalTypeId", animalTypeId);
        tag.putString("customName", customName);
        tag.putString("buildingId", buildingId);
        tag.putString("ownerPlayerUuid", ownerPlayerUuid);
        tag.putLong("parentAnimalId", parentAnimalId);
        tag.putString("acquisitionSource", acquisitionSource.name());
        tag.putInt("createdDay", createdDay);
        tag.putInt("createdSeason", createdSeason);
        tag.putInt("createdYear", createdYear);
        tag.putInt("ageDays", ageDays);
        tag.putInt("daysToMature", daysToMature);
        tag.putBoolean("wasPetToday", wasPetToday);
        tag.putBoolean("wasAutoPetToday", wasAutoPetToday);
        tag.putInt("friendship", friendship);
        tag.putBoolean("allowReproduction", allowReproduction);
        tag.putInt("daysOwned", daysOwned);
        tag.putBoolean("wasFedToday", wasFedToday);
        tag.putInt("fullness", fullness);
        tag.putInt("happiness", happiness);
        tag.putInt("moodMessage", moodMessage);
        tag.putInt("daysSinceLastProduce", daysSinceLastProduce);
        tag.putString("currentProduceId", currentProduceId);
        tag.putInt("produceQuality", produceQuality);
        tag.putBoolean("hasEatenAnimalCracker", hasEatenAnimalCracker);
        tag.putInt("lastProcessedAbsDay", lastProcessedAbsDay);
        if (hasProjectionAnchor) {
            tag.putString(
                    "projectionDimensionId",
                    projectionDimensionId);
            tag.putLong("projectionPos", projectionPos);
        }
        CompoundTag addonData = persistentData.toTag();
        if (!addonData.isEmpty()) {
            tag.put("addonData", addonData);
        }
        return tag;
    }

    public static FarmAnimalRecord load(CompoundTag tag) {
        FarmAnimalRecord record = new FarmAnimalRecord(
            tag.getLong("animalId"),
            tag.getString("animalTypeId"),
            tag.getString("customName"),
            tag.getString("buildingId"),
            AnimalAcquisitionSource.fromId(tag.getString("acquisitionSource")),
            tag.getInt("createdDay"),
            tag.getInt("createdSeason"),
            tag.getInt("createdYear"),
            tag.contains("ageDays") ? tag.getInt("ageDays") : 0,
            tag.contains("daysToMature") ? tag.getInt("daysToMature") : 5,
            tag.getBoolean("wasPetToday"),
            tag.getBoolean("wasAutoPetToday"),
            tag.contains("friendship") ? tag.getInt("friendship") : 0,
            !tag.contains("allowReproduction") || tag.getBoolean("allowReproduction"),
            tag.contains("daysOwned") ? tag.getInt("daysOwned") : 0,
            tag.contains("wasFedToday") && tag.getBoolean("wasFedToday"),
            tag.contains("fullness") ? tag.getInt("fullness") : 255,
            tag.contains("happiness") ? tag.getInt("happiness") : 255,
            tag.contains("moodMessage") ? tag.getInt("moodMessage") : 0,
            tag.contains("daysSinceLastProduce") ? tag.getInt("daysSinceLastProduce") : 0,
            tag.contains("currentProduceId") ? tag.getString("currentProduceId") : "",
            tag.contains("produceQuality") ? tag.getInt("produceQuality") : 0,
            tag.contains("hasEatenAnimalCracker") && tag.getBoolean("hasEatenAnimalCracker")
        );
        // 读取时间戳（旧存档兼容：不存在则为 0，首次处理时会初始化）
        record.lastProcessedAbsDay = tag.contains("lastProcessedAbsDay") ? tag.getInt("lastProcessedAbsDay") : 0;
        record.ownerPlayerUuid = tag.contains("ownerPlayerUuid")
                ? tag.getString("ownerPlayerUuid")
                : "";
        record.parentAnimalId = tag.contains("parentAnimalId")
                ? tag.getLong("parentAnimalId")
                : -1L;
        if (tag.contains("projectionDimensionId", Tag.TAG_STRING)
                && tag.contains("projectionPos", Tag.TAG_LONG)
                && !tag.getString("projectionDimensionId")
                        .isBlank()) {
            record.projectionDimensionId =
                    tag.getString("projectionDimensionId");
            record.projectionPos = tag.getLong("projectionPos");
            record.hasProjectionAnchor = true;
        }
        if (tag.contains("addonData", Tag.TAG_COMPOUND)) {
            record.persistentData =
                    StardewAnimalPersistentData.fromTag(tag.getCompound("addonData"));
        }
        return record;
    }
}
