package com.stardew.craft.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class GoldenChickenEntity extends BaseCoopAnimalEntity {
	public GoldenChickenEntity(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public CoopAnimalVariant getVariant() {
		return CoopAnimalVariant.GOLDEN_CHICKEN;
	}

}
