package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.port.PortItemStacks;
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
    private final com.stardew.craft.animal.runtime.LivestockDayState livestock;
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
        this.livestock = null;
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
        this.livestock = null;
        this.level = null;
        this.worldData = null;
        this.record = Objects.requireNonNull(record, "record");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
        this.offlineCatchUp = offlineCatchUp;
    }

    public StardewAnimalDailyContext(com.stardew.craft.animal.runtime.LivestockDayState state,int day,boolean offline) {
        livestock=Objects.requireNonNull(state);level=state.level;worldData=null;record=null;absoluteDaysPlayed=day;offlineCatchUp=offline;
    }

    public ServerLevel level() {
        return level;
    }

    public java.util.UUID entityId(){return livestock==null?null:livestock.record.id();}

    public long animalId() {
        return livestock!=null ? -livestock.record.randomId() : record.animalId();
    }

    public String animalTypeId() {
        return livestock!=null ? livestock.record.species().definitionId() : record.animalTypeId();
    }

    public String buildingId() {
        return livestock!=null ? livestock.record.home().toString() : record.buildingId();
    }

    public int absoluteDaysPlayed() {
        return absoluteDaysPlayed;
    }

    public boolean offlineCatchUp() {
        return offlineCatchUp;
    }

    public int ageDays() {
        return livestock!=null ? livestock.state.ageDays() : record.ageDays();
    }

    public int daysToMature() {
        return livestock!=null ? livestock.record.species().matureDays() : record.daysToMature();
    }

    public boolean isBaby() {
        return livestock!=null ? livestock.state.ageDays()<livestock.record.species().matureDays() : record.isBaby();
    }

    public int friendship() {
        return livestock!=null ? livestock.state.friendship() : record.friendship();
    }

    public int happiness() {
        return livestock!=null ? livestock.state.happiness() : record.happiness();
    }

    public int fullness() {
        return livestock!=null ? livestock.state.fullness() : record.fullness();
    }

    public int daysSinceLastProduce() {
        return livestock!=null ? livestock.state.daysSinceLastProduce() : record.daysSinceLastProduce();
    }

    public boolean hasEatenAnimalCracker() {
        return livestock!=null ? livestock.record.cracker() : record.hasEatenAnimalCracker();
    }

    public void addAgeDays(int days) {
        if(livestock!=null){livestock.state=livestock.state.withAgeDays(livestock.state.ageDays()+Math.max(0,days));return;}
        record.incrementAgeDays(days);
    }

    public void addFriendship(int amount) {
        if(livestock!=null){livestock.state=livestock.state.withFriendship(livestock.state.friendship()+amount);return;}
        record.addFriendship(amount);
    }

    public void addHappiness(int amount) {
        if(livestock!=null){livestock.state=livestock.state.withHappiness(livestock.state.happiness()+amount);return;}
        record.addHappiness(amount);
    }

    public void setFullness(int fullness) {
        if(livestock!=null){livestock.state=livestock.state.withFullness(fullness);return;}
        record.setFullness(fullness);
    }

    public void resetProduceCooldown() {
        if(livestock!=null){livestock.state=livestock.state.withDaysSinceLastProduce(0);return;}
        record.resetDaysSinceLastProduce();
    }

    public Optional<StardewAnimalPersistentData.Value> persistentData(
            StardewAnimalPersistentData.Key key
    ) {
        return livestock!=null?StardewAnimalPersistentData.read(livestock.record,key):StardewAnimalPersistentData.read(record, key);
    }

    public boolean setPersistentData(
            StardewAnimalPersistentData.Key key,
            CompoundTag payload
    ) {
        if(livestock!=null){livestock.record=StardewAnimalPersistentData.with(livestock.record,key,payload);return true;}
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
        if(livestock!=null){
            livestock.state=livestock.state.withCurrentProduceId(itemId.toString()).withProduceQuality(QualityHelper.getQuality(produceStack));
            livestock.record=livestock.record.produce(itemId.toString());
            var extra=livestock.record.extra();extra.put("HeldProduce",PortItemStacks.save(produceStack, level.registryAccess()));livestock.record=livestock.record.extra(extra);return true;
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
        if(livestock!=null)return livestock.product(produceStack,honorAnimalCracker);
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
