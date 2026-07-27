package com.stardew.craft.entity.animal;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

@SuppressWarnings("null")
public class SheepEntity extends BaseCoopAnimalEntity {
    public SheepEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public CoopAnimalVariant getVariant() {
        return isShearedVisual() ? CoopAnimalVariant.SHEARED_SHEEP : CoopAnimalVariant.SHEEP;
    }

    private boolean isShearedVisual() {
        if (this.isBaby()) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel) || getManagedAnimalId() <= 0L) {
            return false;
        }
        FarmAnimalRecord record = AnimalWorldData.get(serverLevel).getAnimal(getManagedAnimalId()).orElse(null);
        return record == null || record.currentProduceId().isBlank();
    }
}
