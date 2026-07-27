package com.stardew.craft.entity.animal;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.rule.DuckSwimmingRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.UUID;

public class DuckEntity extends BaseCoopAnimalEntity {
	private static final int EVENING_RETURN_MINUTES = 17 * 60;
	private static final int WATER_SEARCH_RADIUS = 10;
	private static final int WATER_SEARCH_ATTEMPTS = 28;

	public DuckEntity(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		AmphibiousPathNavigation navigation =
				new AmphibiousPathNavigation(this, level);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(7, new SeekFarmWaterGoal());
	}

	@Override
	public CoopAnimalVariant getVariant() {
		return CoopAnimalVariant.DUCK;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel)
				|| getManagedAnimalId() <= 0L) {
			return;
		}
		AnimalBuildingRecord home = resolveHomeBuilding();
		FarmInstance farm = resolveHomeFarm(home);
		if (home != null && farm != null
				&& !farm.contains(blockPosition())) {
			forceMoveInsideHome(home);
		}
	}

	private FarmInstance resolveHomeFarm(
			AnimalBuildingRecord home
	) {
		if (home == null) {
			return null;
		}
		FarmInstanceRegistry registry =
				FarmInstanceRegistry.get();
		UUID owner = null;
		try {
			owner = UUID.fromString(home.ownerPlayerUuid());
		} catch (IllegalArgumentException ignored) {
			// Legacy/corrupt records may not carry a usable owner; coordinate
			// lookup remains a safe compatibility fallback.
		}
		if (owner != null) {
			FarmInstance ownedFarm = registry.getFarm(owner);
			if (ownedFarm != null) {
				return ownedFarm;
			}
		}
		owner = registry.getOwnerAt(home.managerPos());
		return owner == null ? null : registry.getFarm(owner);
	}

	private boolean isOpenWater(BlockPos pos) {
		return level().getFluidState(pos).is(FluidTags.WATER)
				&& level().getFluidState(pos.above()).isEmpty()
				&& level().getBlockState(pos.above())
						.getCollisionShape(level(), pos.above())
						.isEmpty();
	}

	private final class SeekFarmWaterGoal extends Goal {
		private BlockPos target;
		private AnimalBuildingRecord home;
		private FarmInstance farm;
		private int remainingTicks;
		private int repathTicks;

		private SeekFarmWaterGoal() {
			setFlags(EnumSet.of(
					Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!(DuckEntity.this.level()
					instanceof ServerLevel level)
					|| DuckEntity.this.getRandom()
							.nextInt(200) != 0) {
				return false;
			}
			AnimalWorldData data =
					AnimalWorldData.get(level);
			FarmAnimalRecord record = data.getAnimal(
					DuckEntity.this.getManagedAnimalId())
					.orElse(null);
			home = DuckEntity.this.resolveHomeBuilding();
			farm = DuckEntity.this.resolveHomeFarm(home);
			boolean sameFarm = farm != null
					&& farm.contains(
							DuckEntity.this.blockPosition());
			var definition = record == null
					? null
					: FarmAnimalDefinitions.find(
							record.animalTypeId());
			boolean canSwim = definition != null
					&& definition.canSwim();
			if (record == null
					|| !DuckSwimmingRules.canSeekWater(
							canSwim,
							record.wasPetToday(),
							home != null
									&& !DuckEntity.this
											.isInsideHome(home),
							StardewTimeManager.get()
									.getCurrentTime()
									< EVENING_RETURN_MINUTES,
							sameFarm)) {
				return false;
			}
			target = findWaterTarget();
			if (target == null) {
				return false;
			}
			remainingTicks = 240;
			repathTicks = 0;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (target == null
					|| home == null
					|| farm == null
					|| remainingTicks <= 0) {
				return false;
			}
			FarmAnimalRecord record =
					AnimalWorldData.get(
							(ServerLevel) DuckEntity.this.level())
							.getAnimal(
									DuckEntity.this
											.getManagedAnimalId())
							.orElse(null);
			var definition = record == null
					? null
					: FarmAnimalDefinitions.find(
							record.animalTypeId());
			return record != null
					&& DuckSwimmingRules.canSeekWater(
							definition != null
									&& definition.canSwim(),
							record.wasPetToday(),
							!DuckEntity.this
									.isInsideHome(home),
							StardewTimeManager.get()
									.getCurrentTime()
									< EVENING_RETURN_MINUTES,
							farm.contains(
									DuckEntity.this
											.blockPosition()));
		}

		@Override
		public void tick() {
			remainingTicks--;
			repathTicks--;
			if (repathTicks <= 0
					&& DuckEntity.this
							.tryAcquirePathfindingBudget()) {
				if (DuckEntity.this.isInWater()) {
					BlockPos next = findWaterTarget();
					if (next != null) {
						target = next;
					}
				}
				DuckEntity.this.getNavigation().moveTo(
						target.getX() + 0.5D,
						target.getY(),
						target.getZ() + 0.5D,
						DuckEntity.this.isInWater()
								? 1.05D : 1.1D);
				repathTicks = DuckEntity.this.isInWater()
						? 35 : 12;
			}
		}

		@Override
		public void stop() {
			DuckEntity.this.getNavigation().stop();
			target = null;
			home = null;
			farm = null;
			remainingTicks = 0;
			repathTicks = 0;
		}

		private BlockPos findWaterTarget() {
			BlockPos origin = DuckEntity.this.blockPosition();
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int attempt = 0;
				 attempt < WATER_SEARCH_ATTEMPTS;
				 attempt++) {
				int dx = DuckEntity.this.getRandom()
						.nextInt(
								WATER_SEARCH_RADIUS * 2 + 1)
						- WATER_SEARCH_RADIUS;
				int dz = DuckEntity.this.getRandom()
						.nextInt(
								WATER_SEARCH_RADIUS * 2 + 1)
						- WATER_SEARCH_RADIUS;
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos candidate =
							origin.offset(dx, dy, dz);
					if (!farm.contains(candidate)
							|| !DuckEntity.this
									.isOpenWater(candidate)) {
						continue;
					}
					double distance =
							DuckEntity.this.distanceToSqr(
									candidate.getX() + 0.5D,
									candidate.getY(),
									candidate.getZ() + 0.5D);
					if (distance < bestDistance) {
						best = candidate.immutable();
						bestDistance = distance;
					}
				}
			}
			return best;
		}
	}
}
