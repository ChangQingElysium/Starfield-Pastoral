package com.stardew.craft.entity.animal;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

public class OstrichEntity extends BaseCoopAnimalEntity {
	public OstrichEntity(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public CoopAnimalVariant getVariant() {
		return CoopAnimalVariant.OSTRICH;
	}

}
