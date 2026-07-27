package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

import java.util.Objects;

/**
 * Stable facade for an animal's server-side daily update.
 *
 * <p>The facade deliberately exposes animal state through methods instead of handing addons the
 * internal world-data container. Mutations are applied to the authoritative animal record.
 */
public final class StardewAnimalDailyContext {
    private final ServerLevel level;
    private final AnimalWorldData worldData;
    private final FarmAnimalRecord record;
    private final int absoluteDaysPlayed;
    private final boolean offlineCatchUp;

    public StardewAnimalDailyContext(
            ServerLevel level,
            AnimalWorldData worldData,
            FarmAnimalRecord record,
            int absoluteDaysPlayed,
            boolean offlineCatchUp
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.worldData = Objects.requireNonNull(worldData, "worldData");
        this.record = Objects.requireNonNull(record, "record");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
        this.offlineCatchUp = offlineCatchUp;
    }

    StardewAnimalDailyContext(
            FarmAnimalRecord record,
            int absoluteDaysPlayed,
            boolean offlineCatchUp
    ) {
        this.level = null;
        this.worldData = null;
        this.record = Objects.requireNonNull(record, "record");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
        this.offlineCatchUp = offlineCatchUp;
    }

    public ServerLevel level() {
        return level;
    }

    public long animalId() {
        return record.animalId();
    }

    public String animalTypeId() {
        return record.animalTypeId();
    }

    public String buildingId() {
        return record.buildingId();
    }

    public int absoluteDaysPlayed() {
        return absoluteDaysPlayed;
    }

    public boolean offlineCatchUp() {
        return offlineCatchUp;
    }

    public int ageDays() {
        return record.ageDays();
    }

    public int daysToMature() {
        return record.daysToMature();
    }

    public boolean isBaby() {
        return record.isBaby();
    }

    public int friendship() {
        return record.friendship();
    }

    public int happiness() {
        return record.happiness();
    }

    public int fullness() {
        return record.fullness();
    }

    public int daysSinceLastProduce() {
        return record.daysSinceLastProduce();
    }

    public boolean hasEatenAnimalCracker() {
        return record.hasEatenAnimalCracker();
    }

    public void addAgeDays(int days) {
        record.incrementAgeDays(days);
    }

    public void addFriendship(int amount) {
        record.addFriendship(amount);
    }

    public void addHappiness(int amount) {
        record.addHappiness(amount);
    }

    public void setFullness(int fullness) {
        record.setFullness(fullness);
    }

    public void resetProduceCooldown() {
        record.resetDaysSinceLastProduce();
    }

    public Optional<StardewAnimalPersistentData.Value> persistentData(
            StardewAnimalPersistentData.Key key
    ) {
        return StardewAnimalPersistentData.read(record, key);
    }

    public boolean setPersistentData(
            StardewAnimalPersistentData.Key key,
            CompoundTag payload
    ) {
        return level != null
                && StardewAnimalPersistentData.write(level, record.animalId(), key, payload);
    }

    /**
     * Stores an item for tool-based collection, retaining the quality encoded on the stack.
     */
    public boolean setHeldProduce(ItemStack produceStack) {
        if (produceStack == null || produceStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(produceStack.getItem());
        if (itemId == null) {
            return false;
        }
        record.setCurrentProduceId(itemId.toString());
        record.setProduceQuality(QualityHelper.getQuality(produceStack));
        return true;
    }

    /**
     * Commits an overnight product to the persistent building ledger.
     *
     * <p>Offline catch-up submits the same durable entries without creating a world projection.
     * When {@code honorAnimalCracker} is true, the doubled amount is committed atomically.
     */
    public boolean placeOvernightProduce(ItemStack produceStack, boolean honorAnimalCracker) {
        if (level == null || worldData == null
                || produceStack == null || produceStack.isEmpty()) {
            return false;
        }
        ItemStack submitted = produceStack.copy();
        if (honorAnimalCracker && record.hasEatenAnimalCracker()) {
            submitted.setCount(Math.min(
                    submitted.getMaxStackSize(),
                    submitted.getCount() * 2
            ));
        }
        return AnimalProducePlacementService.submitProduce(
                level,
                worldData,
                record,
                absoluteDaysPlayed,
                submitted,
                !offlineCatchUp
        );
    }
}
