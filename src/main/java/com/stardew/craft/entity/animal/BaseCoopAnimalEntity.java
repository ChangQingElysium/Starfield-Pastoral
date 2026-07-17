package com.stardew.craft.entity.animal;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.service.AnimalDoorStateService;
import com.stardew.craft.animal.service.AnimalEntityRecoveryState;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.menu.AnimalQueryMenu;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@SuppressWarnings("null")
public abstract class BaseCoopAnimalEntity extends Animal implements GeoEntity {
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation EAT = RawAnimation.begin().thenLoop("eat");
	private static final String TAG_MANAGED_ANIMAL_ID = "stardewManagedAnimalId";
	private static final String TAG_MANAGED_ANIMAL_TYPE = "stardewManagedAnimalType";
	private static final int EMOTE_POP_IN_TICKS = 4;
	private static final int EMOTE_FRAME_HOLD_TICKS = 5;
	private static final int EMOTE_STABLE_FRAME_COUNT = 4;
	private static final int EMOTE_FADE_TICKS = 4;
	private static final int EMOTE_TOTAL_TICKS = EMOTE_POP_IN_TICKS + EMOTE_STABLE_FRAME_COUNT * EMOTE_FRAME_HOLD_TICKS + EMOTE_FADE_TICKS;
	private static final int RETURN_HOME_TIMEOUT_TICKS = 160;
	private static final int LEAVE_HOME_TIMEOUT_TICKS = 120;
	private static final int RETURN_HOME_STUCK_TICKS = 50;
	private static final int LEAVE_HOME_STUCK_TICKS = 45;
	private static final int DOOR_PUSH_STUCK_TICKS = 25;
	private static final double DOOR_PUSH_RANGE_SQR = 4.0D;
	private static final double DOOR_PUSH_SPEED = 0.18D;
	private static final int STAGGER_LEAVE_RANGE_MINUTES = 80;
	private static final int STAGGER_RETURN_RANGE_MINUTES = 60;
	private static final int MINUTES_0600 = 6 * 60;
	private static final int MINUTES_1700 = 17 * 60;
	private static final int MINUTES_2000 = 20 * 60;
	private static final int STATIONARY_RESCUE_TICKS = 120;
	private static final boolean AI_DIAGNOSTICS = true;
	private static final double STUCK_MOVE_THRESHOLD_SQR = 0.01D;
	private static final double DOORWAY_SETTLE_DISTANCE_SQR = 2.25D;
	private static final int WANDER_MIN_RANGE = 2;
	private static final int WANDER_MAX_RANGE = 6;
	private static final int WANDER_PICK_ATTEMPTS = 14;
	private static final double AMBIENT_SOUND_CHANCE_PER_TICK = 0.001D;
	private static final EntityDataAccessor<Integer> DATA_EMOTE_BASE_INDEX = SynchedEntityData.defineId(BaseCoopAnimalEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_EMOTE_TICKS_LEFT = SynchedEntityData.defineId(BaseCoopAnimalEntity.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private long managedAnimalId = -1L;
	private String managedAnimalType = "";
	private int eatAnimationTicks = 0;
	private int stationaryTicks;
	private Vec3 lastServerPosition = Vec3.ZERO;

	protected BaseCoopAnimalEntity(EntityType<? extends Animal> entityType, Level level) {
		super(entityType, level);
		this.setInvulnerable(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 10.0D)
				.add(Attributes.STEP_HEIGHT, 1.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.176D)
				.add(Attributes.FOLLOW_RANGE, 16.0D);
	}

	public abstract CoopAnimalVariant getVariant();

	protected abstract Ingredient getBreedIngredient();

	protected abstract EntityType<? extends Animal> getOffspringType();

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.0D, stack -> getBreedIngredient().test(stack), false));
		this.goalSelector.addGoal(4, new ReturnHomeAtEveningGoal());
		this.goalSelector.addGoal(5, new LeaveHomeInDayGoal());
		this.goalSelector.addGoal(6, new SleepIfNecessaryGoal());
		this.goalSelector.addGoal(7, new FollowSameTypeAdultGoal());
		this.goalSelector.addGoal(8, new EatPastureGrassGoal());
		this.goalSelector.addGoal(9, new StardewRandomMovementGoal());
		this.goalSelector.addGoal(10, new RandomStrollGoal(this, 0.95D, 35) {
			@Override
			public boolean canUse() {
				if (BaseCoopAnimalEntity.this.eatAnimationTicks > 0) {
					return false;
				}
				return super.canUse();
			}
		});
		this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(@Nonnull SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_EMOTE_BASE_INDEX, -1);
		builder.define(DATA_EMOTE_TICKS_LEFT, 0);
	}

	@Override
	public boolean isFood(@Nonnull ItemStack stack) {
		return getBreedIngredient().test(stack);
	}

	@Override
	public @Nonnull InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
		if (isFairTemporaryDecorAnimal()) {
			return InteractionResult.sidedSuccess(this.level().isClientSide);
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return super.mobInteract(player, hand);
		}

		if (this.level().isClientSide) {
			return InteractionResult.sidedSuccess(true);
		}

		if (!(this.level() instanceof ServerLevel serverLevel) || managedAnimalId <= 0L) {
			return super.mobInteract(player, hand);
		}

		AnimalWorldData data = AnimalWorldData.get(serverLevel);
		FarmAnimalRecord record = data.getAnimal(managedAnimalId).orElse(null);
		if (record == null) {
			return super.mobInteract(player, hand);
		}

		if (player.isShiftKeyDown()) {
			openAnimalQueryMenu(player, record);
			return InteractionResult.SUCCESS;
		}

		if (tryFeedGoldenAnimalCracker(player, data, record)) {
			return InteractionResult.SUCCESS;
		}

		if (!record.wasPetToday()) {
			boolean wasAutoPet = record.wasAutoPetToday();
			record.setWasPetToday(true);
			record.setWasAutoPetToday(false);
			record.addFriendship(15);
			int happinessDrain = getHappinessDrain(record.animalTypeId());
			int happinessGain = Math.max(5, 30 + happinessDrain);
			if (player instanceof ServerPlayer serverPlayer && hasHappinessBoostProfession(serverPlayer, record.animalTypeId())) {
				record.addFriendship(15);
			}
			record.addHappiness(happinessGain);
			data.markChanged();
			playPetFeedback(serverLevel, record, wasAutoPet);
			player.sendSystemMessage(Component.translatable("stardewcraft.animal.interact.pet_success"));
		} else {
			if (!player.getMainHandItem().is(ModItems.HAY.get())) {
				openAnimalQueryMenu(player, record);
				return InteractionResult.SUCCESS;
			}
			player.sendSystemMessage(Component.translatable("stardewcraft.animal.interact.already_pet"));
		}
		return InteractionResult.SUCCESS;
	}

	private boolean isFairTemporaryDecorAnimal() {
		return this.getTags().contains(com.stardew.craft.festival.FairFestivalService.FAIR_ANIMAL_MARKER_TAG)
			|| this.getPersistentData().getBoolean(com.stardew.craft.festival.FairFestivalService.FAIR_ANIMAL_PERSISTENT_FLAG);
	}

	private boolean tryFeedGoldenAnimalCracker(Player player, AnimalWorldData data, FarmAnimalRecord record) {
		ItemStack held = player.getMainHandItem();
		if (!held.is(ModItems.GOLDEN_ANIMAL_CRACKER.get())) {
			return false;
		}

		if (record.hasEatenAnimalCracker()) {
			player.sendSystemMessage(Component.translatable("stardewcraft.animal.interact.golden_cracker_already_used"));
			return true;
		}

		if (!canEatGoldenCrackers(record.animalTypeId())) {
			this.level().playSound(null, this.blockPosition(), ModSounds.CANCEL.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
			triggerEmote(8);
			player.sendSystemMessage(Component.translatable("stardewcraft.animal.interact.golden_cracker_not_supported"));
			return true;
		}

		record.setHasEatenAnimalCracker(true);
		data.markChanged();
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}

		this.level().playSound(null, this.blockPosition(), ModSounds.GIVE_GIFT.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
		triggerEmote(56);
		player.sendSystemMessage(Component.translatable("stardewcraft.animal.interact.golden_cracker_applied"));
		return true;
	}

	private boolean canEatGoldenCrackers(String animalTypeId) {
		return !"pig".equals(animalTypeId);
	}

	@Override
	public AgeableMob getBreedOffspring(@Nonnull ServerLevel level, @Nonnull AgeableMob otherParent) {
		EntityType<? extends Animal> offspringType = getOffspringType();
		return offspringType.create(level);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 5, state -> {
			if (shouldPlayEatAnimation()) {
				state.setAndContinue(EAT);
				return PlayState.CONTINUE;
			}

			if (state.isMoving()) {
				state.setAndContinue(WALK);
				return PlayState.CONTINUE;
			}

			state.setAndContinue(IDLE);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	public long getManagedAnimalId() {
		return managedAnimalId;
	}

	public void setManagedAnimalId(long managedAnimalId) {
		this.managedAnimalId = managedAnimalId;
	}

	public String getManagedAnimalType() {
		return managedAnimalType;
	}

	public void setManagedAnimalType(String managedAnimalType) {
		this.managedAnimalType = managedAnimalType == null ? "" : managedAnimalType;
	}

	@Override
	public void addAdditionalSaveData(@Nonnull CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putLong(TAG_MANAGED_ANIMAL_ID, managedAnimalId);
		compound.putString(TAG_MANAGED_ANIMAL_TYPE, managedAnimalType);
	}

	@Override
	public void readAdditionalSaveData(@Nonnull CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		managedAnimalId = compound.contains(TAG_MANAGED_ANIMAL_ID) ? compound.getLong(TAG_MANAGED_ANIMAL_ID) : -1L;
		managedAnimalType = compound.contains(TAG_MANAGED_ANIMAL_TYPE) ? compound.getString(TAG_MANAGED_ANIMAL_TYPE) : "";
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide && this.isNoAi()) {
			this.setNoAi(false);
		}
		if (eatAnimationTicks > 0) {
			eatAnimationTicks--;
		}
		if (entityData.get(DATA_EMOTE_TICKS_LEFT) > 0) {
			entityData.set(DATA_EMOTE_TICKS_LEFT, entityData.get(DATA_EMOTE_TICKS_LEFT) - 1);
			if (entityData.get(DATA_EMOTE_TICKS_LEFT) <= 0) {
				entityData.set(DATA_EMOTE_BASE_INDEX, -1);
			}
		}

		if (this.level().isClientSide || managedAnimalId <= 0L) {
			return;
		}

		updateStationaryRescue();
		logAiStateSnapshot();
		tryPlayAmbientAnimalSound();

		if (this.isBaby()) {
			if (this.getAge() > -24000) {
				this.setAge(-24000);
			}
			return;
		}

		if (this.getAge() != 0) {
			this.setAge(0);
		}
	}

	@Override
	protected void pushEntities() {
		if (this.level() instanceof ServerLevel serverLevel
				&& AnimalEntityRecoveryState.isRecovering(serverLevel, this.chunkPosition())) {
			return;
		}
		super.pushEntities();
	}

	private void updateStationaryRescue() {
		if (this.eatAnimationTicks > 0) {
			stationaryTicks = 0;
			lastServerPosition = this.position();
			return;
		}

		Vec3 current = this.position();
		double moved = current.distanceToSqr(lastServerPosition);
		if (moved < STUCK_MOVE_THRESHOLD_SQR) {
			stationaryTicks++;
		} else {
			stationaryTicks = 0;
			lastServerPosition = current;
		}

		if (stationaryTicks < STATIONARY_RESCUE_TICKS) {
			return;
		}

		if (this.getNavigation().isDone()) {
			forceShortWanderStep();
		}
		stationaryTicks = 0;
		lastServerPosition = this.position();
	}

	private void forceShortWanderStep() {
		BlockPos origin = this.blockPosition();
		Direction[] directions = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
		for (int i = directions.length - 1; i > 0; i--) {
			int j = this.getRandom().nextInt(i + 1);
			Direction temp = directions[i];
			directions[i] = directions[j];
			directions[j] = temp;
		}

		for (Direction direction : directions) {
			for (int dy = 0; dy >= -1; dy--) {
				BlockPos candidate = origin.relative(direction).offset(0, dy, 0);
				if (!canOccupy(candidate)) {
					continue;
				}
				Path path = this.getNavigation().createPath(candidate, 0);
				if (path != null) {
					this.getNavigation().moveTo(path, 1.0D);
					return;
				}
				this.getNavigation().moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, 1.0D);
				if (!this.getNavigation().isDone()) {
					return;
				}
			}
		}
	}

	@Override
	public boolean hurt(@Nonnull DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(@Nonnull DamageSource source) {
		return true;
	}

	@Override
	public void die(@Nonnull DamageSource source) {
	}

	@Override
	public void checkDespawn() {
		// 牲畜永远不消散
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean isPersistenceRequired() {
		return true;
	}

	private boolean shouldPlayEatAnimation() {
		return eatAnimationTicks > 0;
	}

	private void triggerEatAnimation(int ticks) {
		eatAnimationTicks = Math.max(eatAnimationTicks, ticks);
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
	}

	public void triggerForageAnimation() {
		triggerEatAnimation(40);
	}

	private void openAnimalQueryMenu(Player player, FarmAnimalRecord record) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		Component displayName = record.customName() == null || record.customName().isBlank()
			? Component.translatable("entity.stardewcraft." + record.animalTypeId() + (record.isBaby() ? ".baby" : ""))
			: Component.literal(record.customName());

		serverPlayer.openMenu(new SimpleMenuProvider(
			(containerId, playerInventory, ignored) -> new AnimalQueryMenu(
				containerId,
				playerInventory,
				record.animalId(),
				record.ageDays(),
				record.daysOwned(),
				record.daysToMature(),
				record.wasPetToday(),
				record.friendship(),
				record.allowReproduction(),
				record.hasEatenAnimalCracker(),
				getVariant().ordinal(),
				record.moodMessage(),
				record.wasFedToday(),
				record.fullness()
			),
			displayName
		));

	}

	private void playPetFeedback(ServerLevel level, FarmAnimalRecord record, boolean hadAutoPetToday) {
		int emoteIndex = record.moodMessage() == 4 ? 12 : (hadAutoPetToday ? 32 : 20);
		triggerEmote(emoteIndex);

		SoundEvent soundEvent = mapPetSoundEvent(record.animalTypeId());
		if (soundEvent != null) {
			playPetSoundOnce(level.getRandom(), soundEvent);
		}
	}

	private void playPetSoundOnce(net.minecraft.util.RandomSource random, SoundEvent soundEvent) {
		float pitch = (1200 + random.nextInt(401) - 200) / 1000.0F;
		this.level().playSound(null, this.blockPosition(), soundEvent, SoundSource.NEUTRAL, 1.0F, pitch);
	}

	private void triggerEmote(int baseIndex) {
		entityData.set(DATA_EMOTE_BASE_INDEX, baseIndex);
		entityData.set(DATA_EMOTE_TICKS_LEFT, EMOTE_TOTAL_TICKS);
	}

	private void tryPlayAmbientAnimalSound() {
		if (this.isBaby() || this.eatAnimationTicks > 0) {
			return;
		}

		if (this.getRandom().nextDouble() >= AMBIENT_SOUND_CHANCE_PER_TICK) {
			return;
		}

		if (this.level().getNearestPlayer(this, 32.0D) == null) {
			return;
		}

		SoundEvent soundEvent = mapPetSoundEvent(this.managedAnimalType);
		if (soundEvent != null) {
			playPetSoundOnce(this.level().getRandom(), soundEvent);
		}
	}

	private void logAiDiag(String stage, String message) {
		// Diagnostics disabled in production.
	}

	private void logAiDiagNow(String stage, String message) {
		// Diagnostics disabled in production.
	}

	private void logAiStateSnapshot() {
		if (!AI_DIAGNOSTICS || this.level().isClientSide || managedAnimalId <= 0L) {
			return;
		}

		AnimalBuildingRecord homeBuilding = resolveHomeBuilding();
		int current = StardewTimeManager.get().getCurrentTime();
		boolean inside = isInsideHome(homeBuilding);
		boolean doorOpen = isAnimalDoorOpen(homeBuilding);
		String expected = (current >= MINUTES_0600 && current < MINUTES_1700) ? "outside" : "inside";

		String leaveState = evaluateLeaveState(homeBuilding, current, inside, doorOpen);
		String returnState = evaluateReturnState(homeBuilding, current, inside, doorOpen);

		logAiDiag(
			"state.snapshot",
			"minute=" + current
				+ " actual=" + (inside ? "inside" : "outside")
				+ " expected=" + expected
				+ " doorOpen=" + doorOpen
				+ " leave=" + leaveState
				+ " return=" + returnState
				+ " stationaryTicks=" + stationaryTicks
		);
	}

	private String evaluateLeaveState(AnimalBuildingRecord homeBuilding, int current, boolean inside, boolean doorOpen) {
		if (current < MINUTES_0600 || current >= MINUTES_1700) {
			return "idle:not_day_window";
		}
		if (com.stardew.craft.weather.WeatherManager.isRaining(this.level()) || StardewTimeManager.get().getCurrentSeason() == 3) {
			return "blocked:rain_or_winter";
		}
		if (homeBuilding == null) {
			return "blocked:no_home";
		}
		if (!doorOpen) {
			return "blocked:door_closed";
		}
		if (!inside) {
			return "blocked:already_outside";
		}
		DoorTarget doorTarget = resolveNearestDoorTarget(homeBuilding, true);
		BlockPos target = doorTarget != null ? doorTarget.outsidePos() : null;
		if (target == null) {
			return "blocked:no_outside_target";
		}
		return "ready:target=" + target;
	}

	private String evaluateReturnState(AnimalBuildingRecord homeBuilding, int current, boolean inside, boolean doorOpen) {
		if (current < MINUTES_1700) {
			return "idle:too_early";
		}
		if (homeBuilding == null) {
			return "blocked:no_home";
		}
		if (!doorOpen) {
			return "blocked:door_closed";
		}
		if (inside) {
			return "blocked:already_inside";
		}
		DoorTarget doorTarget = resolveNearestDoorTarget(homeBuilding, true);
		BlockPos target = doorTarget != null ? doorTarget.insidePos() : findSafeInteriorPosition(homeBuilding, homeBuilding.managerPos().above());
		if (target == null) {
			return "blocked:no_inside_target";
		}
		return "ready:target=" + target;
	}

	public boolean isEmoteActive() {
		return entityData.get(DATA_EMOTE_BASE_INDEX) >= 0 && entityData.get(DATA_EMOTE_TICKS_LEFT) > 0;
	}

	public int getCurrentEmoteFrameIndex() {
		int baseIndex = entityData.get(DATA_EMOTE_BASE_INDEX);
		int ticksLeft = entityData.get(DATA_EMOTE_TICKS_LEFT);
		if (baseIndex < 0 || ticksLeft <= 0) {
			return -1;
		}

		int elapsed = EMOTE_TOTAL_TICKS - ticksLeft;
		if (elapsed < EMOTE_POP_IN_TICKS) {
			return elapsed;
		}

		int holdWindow = EMOTE_STABLE_FRAME_COUNT * EMOTE_FRAME_HOLD_TICKS;
		if (elapsed < EMOTE_POP_IN_TICKS + holdWindow) {
			int step = (elapsed - EMOTE_POP_IN_TICKS) / EMOTE_FRAME_HOLD_TICKS;
			return baseIndex + Math.min(EMOTE_STABLE_FRAME_COUNT - 1, step);
		}

		int fadeElapsed = elapsed - (EMOTE_POP_IN_TICKS + holdWindow);
		return Math.max(0, EMOTE_FADE_TICKS - 1 - fadeElapsed);
	}

	private SoundEvent mapPetSoundEvent(String animalTypeId) {
		return switch (animalTypeId) {
			case "white_chicken", "brown_chicken", "blue_chicken", "golden_chicken", "void_chicken" -> ModSounds.CLUCK.get();
			case "duck" -> ModSounds.DUCK.get();
			case "rabbit" -> ModSounds.RABBIT.get();
			case "ostrich" -> ModSounds.OSTRICH.get();
			case "cow" -> ModSounds.COW.get();
			case "goat" -> ModSounds.GOAT.get();
			case "sheep" -> ModSounds.SHEEP.get();
			case "pig" -> ModSounds.PIG.get();
			default -> null;
		};
	}

	private boolean hasHappinessBoostProfession(ServerPlayer player, String animalTypeId) {
		ProfessionType profession = switch (animalTypeId) {
			case "white_chicken", "brown_chicken", "blue_chicken", "golden_chicken", "void_chicken", "duck", "rabbit", "dinosaur", "ostrich" -> ProfessionType.COOPMASTER;
			case "cow", "goat", "sheep", "pig" -> ProfessionType.SHEPHERD;
			default -> null;
		};
		return profession != null && PlayerStardewDataAPI.hasProfession(player, profession);
	}

	private int getHappinessDrain(String animalTypeId) {
		return switch (animalTypeId) {
			case "golden_chicken" -> 10;
			case "void_chicken", "duck", "rabbit", "ostrich" -> 5;
			case "dinosaur" -> 4;
			case "cow", "goat", "sheep", "pig" -> 8;
			default -> 7;
		};
	}

	private final class ReturnHomeAtEveningGoal extends Goal {
		private AnimalBuildingRecord homeBuilding;
		private BlockPos targetInside;
		private BlockPos doorPos;
		private int timeoutTicks;
		private int stuckTicks;
		private int repathCooldown;
		private Vec3 lastPosition = Vec3.ZERO;

		private ReturnHomeAtEveningGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (BaseCoopAnimalEntity.this.level().isClientSide) {
				return false;
			}
			int currentMinutes = StardewTimeManager.get().getCurrentTime();
			int staggerDelay = (int) (Math.abs(BaseCoopAnimalEntity.this.managedAnimalId % 8) * (STAGGER_RETURN_RANGE_MINUTES / 8.0));
			if (currentMinutes < MINUTES_1700 + staggerDelay) {
				return false;
			}

			homeBuilding = resolveHomeBuilding();
			if (homeBuilding == null || !isAnimalDoorOpen(homeBuilding)) {
				BaseCoopAnimalEntity.this.logAiDiag("return.canUse.blocked", "no_home_or_door_closed");
				return false;
			}
			if (isInsideHome(homeBuilding)) {
				return false;
			}

			DoorTarget doorTarget = resolveNearestDoorTarget(homeBuilding, true);
			targetInside = doorTarget != null ? doorTarget.insidePos() : findSafeInteriorPosition(homeBuilding, homeBuilding.managerPos().above());
			doorPos = doorTarget != null ? doorTarget.doorPos() : null;
			if (targetInside == null) {
				BaseCoopAnimalEntity.this.logAiDiag("return.canUse.blocked", "no_inside_target");
				return false;
			}
			BaseCoopAnimalEntity.this.logAiDiagNow("return.canUse.start", "minute=" + currentMinutes + " target=" + targetInside);

			timeoutTicks = RETURN_HOME_TIMEOUT_TICKS;
			stuckTicks = 0;
			repathCooldown = 0;
			lastPosition = BaseCoopAnimalEntity.this.position();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (homeBuilding == null || timeoutTicks <= 0 || targetInside == null) {
				return false;
			}
			double distToTarget = BaseCoopAnimalEntity.this.distanceToSqr(targetInside.getX() + 0.5D, targetInside.getY(), targetInside.getZ() + 0.5D);
			return distToTarget > DOORWAY_SETTLE_DISTANCE_SQR;
		}

		@Override
		public void tick() {
			timeoutTicks--;
			if (homeBuilding == null) {
				return;
			}
			if (targetInside != null) {
				double distToTarget = BaseCoopAnimalEntity.this.distanceToSqr(targetInside.getX() + 0.5D, targetInside.getY(), targetInside.getZ() + 0.5D);
				if (distToTarget <= DOORWAY_SETTLE_DISTANCE_SQR) {
					timeoutTicks = 0;
					return;
				}
			}
			if (!isInsideHome(homeBuilding) && timeoutTicks <= 0) {
				forceMoveInsideHome(homeBuilding);
				return;
			}
			if (isInsideHome(homeBuilding) && timeoutTicks <= 0) {
				timeoutTicks = 0;
				return;
			}

			if (BaseCoopAnimalEntity.this.level().players().isEmpty()) {
				forceMoveInsideHome(homeBuilding);
				timeoutTicks = 0;
				return;
			}

			if (targetInside == null || !homeBuilding.isInBounds(targetInside) || !canOccupy(targetInside)) {
				targetInside = findSafeInteriorPosition(homeBuilding, homeBuilding.managerPos().above());
			}
			if (targetInside == null) {
				forceMoveInsideHome(homeBuilding);
				return;
			}

			repathCooldown--;
			if (repathCooldown <= 0) {
				BaseCoopAnimalEntity.this.getNavigation().moveTo(targetInside.getX() + 0.5D, targetInside.getY(), targetInside.getZ() + 0.5D, 1.12D);
				repathCooldown = 15;
			}

			Vec3 current = BaseCoopAnimalEntity.this.position();
			if (current.distanceToSqr(lastPosition) < STUCK_MOVE_THRESHOLD_SQR) {
				stuckTicks++;
			} else {
				stuckTicks = 0;
				lastPosition = current;
			}

			double distToTarget = current.distanceToSqr(targetInside.getX() + 0.5D, targetInside.getY(), targetInside.getZ() + 0.5D);
			if (BaseCoopAnimalEntity.this.getNavigation().isDone() && distToTarget > 4.0D) {
				stuckTicks += 4;
			}

			if (stuckTicks >= RETURN_HOME_STUCK_TICKS) {
				BaseCoopAnimalEntity.this.logAiDiagNow("return.tick", "fallback: stuck timeoutTicks=" + timeoutTicks + " stuckTicks=" + stuckTicks);
				forceMoveInsideHome(homeBuilding);
				timeoutTicks = 0;
			} else if (stuckTicks >= DOOR_PUSH_STUCK_TICKS && doorPos != null) {
				pushThroughDoor(doorPos, targetInside);
			}
		}

		@Override
		public void stop() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
			homeBuilding = null;
			targetInside = null;
			doorPos = null;
			timeoutTicks = 0;
			stuckTicks = 0;
			repathCooldown = 0;
			lastPosition = Vec3.ZERO;
		}
	}

	private final class LeaveHomeInDayGoal extends Goal {
		private AnimalBuildingRecord homeBuilding;
		private BlockPos targetPos;
		private BlockPos doorPos;
		private int timeoutTicks;
		private int stuckTicks;
		private int repathCooldown;
		private Vec3 lastPosition = Vec3.ZERO;

		private LeaveHomeInDayGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (BaseCoopAnimalEntity.this.level().isClientSide) {
				return false;
			}
			int current = StardewTimeManager.get().getCurrentTime();
			int staggerDelay = (int) (Math.abs(BaseCoopAnimalEntity.this.managedAnimalId % 8) * (STAGGER_LEAVE_RANGE_MINUTES / 8.0));
			if (current < MINUTES_0600 + staggerDelay || current >= MINUTES_1700) {
				return false;
			}

			homeBuilding = resolveHomeBuilding();
			if (homeBuilding == null || !isAnimalDoorOpen(homeBuilding)) {
				BaseCoopAnimalEntity.this.logAiDiag("leave.canUse.blocked", "no_home_or_door_closed");
				return false;
			}
			if (!isInsideHome(homeBuilding)) {
				return false;
			}

			DoorTarget doorTarget = resolveNearestDoorTarget(homeBuilding, true);
			targetPos = doorTarget != null ? doorTarget.outsidePos() : null;
			doorPos = doorTarget != null ? doorTarget.doorPos() : null;
			if (targetPos == null) {
				logNoOutsideTargetDiagnostics(homeBuilding, doorTarget);
				BaseCoopAnimalEntity.this.logAiDiag("leave.canUse.blocked", "no_outside_target");
				return false;
			}
			BaseCoopAnimalEntity.this.logAiDiagNow("leave.canUse.start", "minute=" + current + " target=" + targetPos);

			timeoutTicks = LEAVE_HOME_TIMEOUT_TICKS;
			stuckTicks = 0;
			repathCooldown = 0;
			lastPosition = BaseCoopAnimalEntity.this.position();
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (targetPos == null || timeoutTicks <= 0 || homeBuilding == null) {
				return false;
			}
			double distToTarget = BaseCoopAnimalEntity.this.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
			return distToTarget > DOORWAY_SETTLE_DISTANCE_SQR;
		}

		@Override
		public void tick() {
			timeoutTicks--;
			if (targetPos == null || !canOccupy(targetPos) || homeBuilding == null || homeBuilding.isInBounds(targetPos)) {
				DoorTarget doorTarget = homeBuilding == null ? null : resolveNearestDoorTarget(homeBuilding, true);
				targetPos = doorTarget != null ? doorTarget.outsidePos() : null;
			}

			if (targetPos == null) {
				if (homeBuilding != null && timeoutTicks <= 0) {
					BaseCoopAnimalEntity.this.logAiDiagNow("leave.tick", "fallback: no_target timeoutTicks=" + timeoutTicks);
					forceMoveOutsideHome(homeBuilding);
				}
				return;
			}

			repathCooldown--;
			if (repathCooldown <= 0) {
				BaseCoopAnimalEntity.this.getNavigation().moveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 1.1D);
				repathCooldown = 12;
			}

			Vec3 current = BaseCoopAnimalEntity.this.position();
			if (current.distanceToSqr(lastPosition) < STUCK_MOVE_THRESHOLD_SQR) {
				stuckTicks++;
			} else {
				stuckTicks = 0;
				lastPosition = current;
			}

			double distToTarget = current.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
			if (BaseCoopAnimalEntity.this.getNavigation().isDone() && distToTarget > 3.0D) {
				stuckTicks += 4;
			}

			if ((timeoutTicks <= 0 || stuckTicks >= LEAVE_HOME_STUCK_TICKS) && homeBuilding != null) {
				BaseCoopAnimalEntity.this.logAiDiagNow("leave.tick", "fallback: stuck timeoutTicks=" + timeoutTicks + " stuckTicks=" + stuckTicks);
				forceMoveOutsideHome(homeBuilding);
				timeoutTicks = 0;
			} else if (stuckTicks >= DOOR_PUSH_STUCK_TICKS && doorPos != null && targetPos != null) {
				pushThroughDoor(doorPos, targetPos);
			}
		}

		@Override
		public void stop() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
			homeBuilding = null;
			targetPos = null;
			doorPos = null;
			timeoutTicks = 0;
			stuckTicks = 0;
			repathCooldown = 0;
			lastPosition = Vec3.ZERO;
		}

	}

	private final class FollowSameTypeAdultGoal extends Goal {
		private BaseCoopAnimalEntity target;

		private FollowSameTypeAdultGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!BaseCoopAnimalEntity.this.isBaby()) {
				return false;
			}
			double bestDistance = Double.MAX_VALUE;
			target = null;
			for (BaseCoopAnimalEntity candidate : BaseCoopAnimalEntity.this.level().getEntitiesOfClass(
				BaseCoopAnimalEntity.class,
				BaseCoopAnimalEntity.this.getBoundingBox().inflate(8.0D),
				animal -> animal != BaseCoopAnimalEntity.this && !animal.isBaby() && animal.getType() == BaseCoopAnimalEntity.this.getType())) {
				double distance = BaseCoopAnimalEntity.this.distanceToSqr(candidate);
				if (distance < bestDistance) {
					bestDistance = distance;
					target = candidate;
				}
			}
			return target != null;
		}

		@Override
		public boolean canContinueToUse() {
			return BaseCoopAnimalEntity.this.isBaby()
				&& target != null
				&& target.isAlive()
				&& !target.isBaby()
				&& BaseCoopAnimalEntity.this.distanceToSqr(target) < 144.0D;
		}

		@Override
		public void tick() {
			if (target == null) {
				return;
			}
			BaseCoopAnimalEntity.this.getLookControl().setLookAt(target, 10.0F, BaseCoopAnimalEntity.this.getMaxHeadXRot());
			BaseCoopAnimalEntity.this.getNavigation().moveTo(target, 1.1D);
		}

		@Override
		public void stop() {
			target = null;
		}
	}

	private final class SleepIfNecessaryGoal extends Goal {
		private SleepIfNecessaryGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (BaseCoopAnimalEntity.this.level().isClientSide) {
				return false;
			}
			if (StardewTimeManager.get().getCurrentTime() < MINUTES_2000) {
				return false;
			}
			AnimalBuildingRecord homeBuilding = resolveHomeBuilding();
			return isInsideHome(homeBuilding);
		}

		@Override
		public boolean canContinueToUse() {
			if (StardewTimeManager.get().getCurrentTime() < MINUTES_2000) {
				return false;
			}
			AnimalBuildingRecord homeBuilding = resolveHomeBuilding();
			return isInsideHome(homeBuilding);
		}

		@Override
		public void start() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
			BaseCoopAnimalEntity.this.setDeltaMovement(Vec3.ZERO);
		}

		@Override
		public void stop() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
		}
	}

	private final class StardewRandomMovementGoal extends Goal {
		private int movingTicks;
		private int pauseTicks;
		private int failedAttempts;

		private StardewRandomMovementGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (BaseCoopAnimalEntity.this.level().isClientSide) {
				return false;
			}
			if (StardewTimeManager.get().getCurrentTime() >= MINUTES_1700) {
				return false;
			}
			if (BaseCoopAnimalEntity.this.eatAnimationTicks > 0) {
				return false;
			}
			if (pauseTicks > 0) {
				pauseTicks--;
				return false;
			}
			double triggerChance = 0.055D + 0.01D * getIndividualWanderOffset();
			if (failedAttempts < 3 && BaseCoopAnimalEntity.this.getRandom().nextDouble() >= triggerChance) {
				return false;
			}

			BlockPos target = findRandomWanderTarget();
			if (target == null) {
				pauseTicks = 8 + BaseCoopAnimalEntity.this.getRandom().nextInt(14);
				failedAttempts = Math.min(8, failedAttempts + 1);
				tryEscapeMove();
				return false;
			}

			Path path = BaseCoopAnimalEntity.this.getNavigation().createPath(target, 0);
			if (path != null) {
				BaseCoopAnimalEntity.this.getNavigation().moveTo(path, 1.0D);
			} else {
				BaseCoopAnimalEntity.this.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
			}
			if (BaseCoopAnimalEntity.this.getNavigation().isDone()) {
				pauseTicks = 8 + BaseCoopAnimalEntity.this.getRandom().nextInt(14);
				failedAttempts = Math.min(8, failedAttempts + 1);
				tryEscapeMove();
				return false;
			}
			movingTicks = 34 + BaseCoopAnimalEntity.this.getRandom().nextInt(48);
			failedAttempts = 0;
			return true;
		}

		private BlockPos findRandomWanderTarget() {
			int minRange = WANDER_MIN_RANGE + getIndividualWanderOffset();
			int maxRange = WANDER_MAX_RANGE + getIndividualWanderOffset();
			minRange = Math.max(2, minRange);
			maxRange = Math.max(minRange + 2, maxRange);

			BlockPos origin = BaseCoopAnimalEntity.this.blockPosition();
			for (int attempt = 0; attempt < WANDER_PICK_ATTEMPTS; attempt++) {
				int dx = BaseCoopAnimalEntity.this.getRandom().nextInt(maxRange * 2 + 1) - maxRange;
				int dz = BaseCoopAnimalEntity.this.getRandom().nextInt(maxRange * 2 + 1) - maxRange;
				int horizontalDistSqr = dx * dx + dz * dz;
				if (horizontalDistSqr < minRange * minRange || horizontalDistSqr > maxRange * maxRange) {
					continue;
				}

				BlockPos base = origin.offset(dx, 0, dz);
				for (int dy = 1; dy >= -1; dy--) {
					BlockPos candidate = base.offset(0, dy, 0);
					if (canStandAt(candidate)) {
						return candidate;
					}
				}
			}
			return null;
		}

		private int getIndividualWanderOffset() {
			long key = BaseCoopAnimalEntity.this.managedAnimalId > 0L
				? BaseCoopAnimalEntity.this.managedAnimalId
				: BaseCoopAnimalEntity.this.getId();
			return (int) Math.floorMod(key, 3L) - 1;
		}

		private void tryEscapeMove() {
			if (failedAttempts < 4) {
				return;
			}

			BlockPos origin = BaseCoopAnimalEntity.this.blockPosition();
			Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
			for (int i = dirs.length - 1; i > 0; i--) {
				int j = BaseCoopAnimalEntity.this.getRandom().nextInt(i + 1);
				Direction tmp = dirs[i];
				dirs[i] = dirs[j];
				dirs[j] = tmp;
			}

			for (Direction dir : dirs) {
				BlockPos candidate = findStepReachableTarget(origin, dir);
				if (candidate == null) {
					continue;
				}

				Path escapePath = BaseCoopAnimalEntity.this.getNavigation().createPath(candidate, 0);
				if (escapePath == null) {
					continue;
				}

				BaseCoopAnimalEntity.this.getNavigation().moveTo(escapePath, 1.05D);
				movingTicks = 10 + BaseCoopAnimalEntity.this.getRandom().nextInt(12);
				pauseTicks = 2;
				failedAttempts = 0;
				return;
			}
		}

		private BlockPos findStepReachableTarget(BlockPos origin, Direction direction) {
			BlockPos sameLevel = origin.relative(direction);
			if (canStandAt(sameLevel)) {
				return sameLevel;
			}

			BlockPos upOne = sameLevel.above();
			if (canStandAt(upOne)) {
				return upOne;
			}

			BlockPos downOne = sameLevel.below();
			if (canStandAt(downOne)) {
				return downOne;
			}

			return null;
		}

		@Override
		public boolean canContinueToUse() {
			return movingTicks > 0 && !BaseCoopAnimalEntity.this.getNavigation().isDone();
		}

		@Override
		public void tick() {
			movingTicks--;
			if (BaseCoopAnimalEntity.this.getRandom().nextDouble() < 0.004D) {
				BaseCoopAnimalEntity.this.getNavigation().stop();
				movingTicks = 0;
				pauseTicks = 8 + BaseCoopAnimalEntity.this.getRandom().nextInt(16);
			}
		}

		@Override
		public void stop() {
			movingTicks = 0;
			if (pauseTicks <= 0) {
				pauseTicks = 4;
			}
			if (failedAttempts > 0) {
				failedAttempts--;
			}
			BaseCoopAnimalEntity.this.getNavigation().stop();
		}

		private boolean canStandAt(BlockPos pos) {
			return canOccupy(pos);
		}
	}

	private final class EatPastureGrassGoal extends Goal {
		private BlockPos targetGrass;
		private int timeoutTicks;

		private EatPastureGrassGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (BaseCoopAnimalEntity.this.level().isClientSide || BaseCoopAnimalEntity.this.isBaby()) {
				return false;
			}
			AnimalBuildingRecord homeBuilding = resolveHomeBuilding();
			if (isInsideHome(homeBuilding)) {
				return false;
			}
			if (BaseCoopAnimalEntity.this.eatAnimationTicks > 0) {
				return false;
			}
			FarmAnimalRecord record = resolveManagedRecord();
			if (record == null || record.fullness() >= 195) {
				return false;
			}
			if (BaseCoopAnimalEntity.this.getRandom().nextDouble() >= 0.002D) {
				return false;
			}

			targetGrass = findNearestGrass(7);
			if (targetGrass == null) {
				return false;
			}

			timeoutTicks = 100;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return targetGrass != null && timeoutTicks > 0 && isRecognizedPastureGrass(targetGrass);
		}

		@Override
		public void tick() {
			timeoutTicks--;
			if (targetGrass == null) {
				return;
			}

			Vec3 targetCenter = Vec3.atCenterOf(targetGrass);
			BaseCoopAnimalEntity.this.getNavigation().moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 1.05D);

			double distance = BaseCoopAnimalEntity.this.distanceToSqr(targetCenter);
			if (distance > 2.2D) {
				return;
			}

			if (!(BaseCoopAnimalEntity.this.level() instanceof ServerLevel serverLevel)) {
				return;
			}

			if (!isRecognizedPastureGrass(targetGrass)) {
				return;
			}

			boolean ateBlueGrass = isBluePastureGrass(targetGrass);
			int requiredClumps = requiredGrassClumps(ateBlueGrass);
			if (!PastureGrassBlock.consumeForAnimal(serverLevel, targetGrass, requiredClumps)) {
				return;
			}
			applyEatState(serverLevel, ateBlueGrass);
			BaseCoopAnimalEntity.this.triggerEatAnimation(48);
			BaseCoopAnimalEntity.this.getNavigation().stop();
			targetGrass = null;
		}

		@Override
		public void stop() {
			BaseCoopAnimalEntity.this.getNavigation().stop();
			targetGrass = null;
			timeoutTicks = 0;
		}

		private BlockPos findNearestGrass(int radius) {
			BlockPos origin = BaseCoopAnimalEntity.this.blockPosition();
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;

			for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
				for (int y = origin.getY() - 2; y <= origin.getY() + 2; y++) {
					for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
						BlockPos candidate = new BlockPos(x, y, z);
						if (!isRecognizedPastureGrass(candidate)) {
							continue;
						}

						double dist = candidate.distSqr(origin);
						if (dist < bestDist) {
							best = candidate;
							bestDist = dist;
						}
					}
				}
			}

			return best;
		}

		private FarmAnimalRecord resolveManagedRecord() {
			if (!(BaseCoopAnimalEntity.this.level() instanceof ServerLevel serverLevel) || BaseCoopAnimalEntity.this.managedAnimalId <= 0L) {
				return null;
			}
			return AnimalWorldData.get(serverLevel).getAnimal(BaseCoopAnimalEntity.this.managedAnimalId).orElse(null);
		}

		private void applyEatState(ServerLevel serverLevel, boolean ateBlueGrass) {
			FarmAnimalRecord record = resolveManagedRecord();
			if (record == null) {
				return;
			}

			record.setFullness(255);
			record.setWasFedToday(true);
			if (record.moodMessage() != 5 && record.moodMessage() != 6 && !com.stardew.craft.weather.WeatherManager.isRaining(serverLevel)) {
				record.setHappiness(255);
				record.addFriendship(ateBlueGrass ? 16 : 8);
			}
			AnimalWorldData.get(serverLevel).markChanged();
		}

		private boolean isRecognizedPastureGrass(BlockPos pos) {
			return BaseCoopAnimalEntity.this.level().getBlockState(pos).getBlock() instanceof PastureGrassBlock;
		}

		private boolean isBluePastureGrass(BlockPos pos) {
			return BaseCoopAnimalEntity.this.level().getBlockState(pos).is(ModBlocks.BLUE_PASTURE_GRASS.get());
		}

		private int requiredGrassClumps(boolean blueGrass) {
			// One grass block corresponds to one consumable grass tuft object in Stardew's terrain feature model.
			return 1;
		}
	}

	private AnimalBuildingRecord resolveHomeBuilding() {
		if (!(this.level() instanceof ServerLevel serverLevel) || managedAnimalId <= 0L) {
			return null;
		}

		AnimalWorldData data = AnimalWorldData.get(serverLevel);
		FarmAnimalRecord record = data.getAnimal(managedAnimalId).orElse(null);
		if (record == null) {
			return null;
		}

		AnimalBuildingRecord building = data.getBuilding(record.buildingId()).orElse(null);
		if (building == null) {
			return null;
		}
		if (!serverLevel.dimension().location().toString().equals(building.dimensionId())) {
			return null;
		}
		return building;
	}

	private boolean isInsideHome(AnimalBuildingRecord building) {
		if (building == null) {
			return false;
		}

		if (building.isInBounds(this.blockPosition())) {
			return true;
		}

		AABB box = this.getBoundingBox();
		int minX = (int) Math.floor(box.minX);
		int minY = (int) Math.floor(box.minY);
		int minZ = (int) Math.floor(box.minZ);
		int maxX = (int) Math.floor(box.maxX - 1.0E-4D);
		int maxY = (int) Math.floor(box.maxY - 1.0E-4D);
		int maxZ = (int) Math.floor(box.maxZ - 1.0E-4D);

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					cursor.set(x, y, z);
					if (building.isInBounds(cursor)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean canOccupy(BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = this.level().getBlockState(belowPos);
		boolean sturdySupport = below.isFaceSturdy(this.level(), belowPos, Direction.UP)
			|| below.is(Blocks.DIRT_PATH);
		if (!sturdySupport) {
			return false;
		}

		double dx = (pos.getX() + 0.5D) - this.getX();
		double dy = pos.getY() - this.getY();
		double dz = (pos.getZ() + 0.5D) - this.getZ();
		AABB movedBox = this.getBoundingBox().move(dx, dy, dz);
		return this.level().noCollision(this, movedBox);
	}

	/**
	 * When stuck near a door, apply a velocity push toward the target instead of instantly teleporting.
	 * This creates a smoother visual transition through the doorway.
	 */
	private void pushThroughDoor(BlockPos door, BlockPos target) {
		Vec3 animalPos = this.position();
		double distToDoorSqr = animalPos.distanceToSqr(door.getX() + 0.5D, animalPos.y, door.getZ() + 0.5D);
		if (distToDoorSqr > DOOR_PUSH_RANGE_SQR) {
			return;
		}
		double dx = (target.getX() + 0.5D) - animalPos.x;
		double dz = (target.getZ() + 0.5D) - animalPos.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01D) {
			return;
		}
		this.setDeltaMovement(dx / len * DOOR_PUSH_SPEED, this.getDeltaMovement().y, dz / len * DOOR_PUSH_SPEED);
		this.hurtMarked = true;
	}

	private void forceMoveInsideHome(AnimalBuildingRecord building) {
		DoorTarget doorTarget = resolveNearestDoorTarget(building, false);
		BlockPos preferred = doorTarget != null ? doorTarget.insidePos() : building.managerPos().above();
		BlockPos safe = findSafeInteriorPosition(building, preferred);
		if (safe == null) {
			return;
		}

		this.getNavigation().stop();
		this.moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, this.getYRot(), this.getXRot());
	}

	private void forceMoveOutsideHome(AnimalBuildingRecord building) {
		if (building == null || !isInsideHome(building)) {
			return;
		}

		DoorTarget doorTarget = resolveNearestDoorTarget(building, true);
		BlockPos preferred = doorTarget != null ? doorTarget.outsidePos() : null;
		BlockPos safe = findSafeExteriorPosition(building, preferred);
		if (safe == null) {
			logNoOutsideTargetDiagnostics(building, doorTarget);
		}
		if (safe == null) {
			return;
		}

		this.getNavigation().stop();
		this.moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, this.getYRot(), this.getXRot());
	}

	private BlockPos findSafeInteriorPosition(AnimalBuildingRecord building, BlockPos preferred) {
		if (building == null) {
			return null;
		}
		if (preferred != null && building.isInBounds(preferred) && canOccupy(preferred)) {
			return preferred.immutable();
		}

		BlockPos fallback = building.managerPos().above();
		if (building.isInBounds(fallback) && canOccupy(fallback)) {
			return fallback.immutable();
		}

		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos center = building.managerPos();
		for (int y = building.minY(); y <= building.maxY(); y++) {
			for (int z = building.minZ(); z <= building.maxZ(); z++) {
				for (int x = building.minX(); x <= building.maxX(); x++) {
					BlockPos candidate = new BlockPos(x, y, z);
					if (!canOccupy(candidate)) {
						continue;
					}
					double dist = candidate.distSqr(center);
					if (dist < bestDist) {
						bestDist = dist;
						best = candidate.immutable();
					}
				}
			}
		}
		return best;
	}

	private BlockPos findSafeExteriorPosition(AnimalBuildingRecord building, BlockPos preferred) {
		if (building == null) {
			return null;
		}
		if (preferred != null && !building.isInBounds(preferred) && canOccupy(preferred)) {
			return preferred.immutable();
		}

		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos center = building.managerPos();
		for (int y = building.minY() - 1; y <= building.maxY() + 1; y++) {
			for (int z = building.minZ() - 1; z <= building.maxZ() + 1; z++) {
				for (int x = building.minX() - 1; x <= building.maxX() + 1; x++) {
					BlockPos candidate = new BlockPos(x, y, z);
					if (building.isInBounds(candidate) || !canOccupy(candidate)) {
						continue;
					}
					double dist = candidate.distSqr(center);
					if (dist < bestDist) {
						bestDist = dist;
						best = candidate.immutable();
					}
				}
			}
		}
		return best;
	}

	private Set<BlockPos> getBoundaryDoorPositions(AnimalBuildingRecord building) {
		Set<BlockPos> doors = new LinkedHashSet<>();
		if (building == null) {
			return doors;
		}

		if (!building.boundaryDoorCells().isEmpty()) {
			for (Long packed : building.boundaryDoorCells()) {
				BlockPos pos = BlockPos.of(packed);
				if (building.isBoundaryDoor(pos)) {
					doors.add(pos.immutable());
				}
			}
			if (!doors.isEmpty()) {
				return doors;
			}
		}

		for (int y = building.minY() - 1; y <= building.maxY() + 1; y++) {
			for (int z = building.minZ() - 1; z <= building.maxZ() + 1; z++) {
				for (int x = building.minX() - 1; x <= building.maxX() + 1; x++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = this.level().getBlockState(pos);
					if (!AnimalDoorStateService.isDoorOrFenceGate(state)) {
						continue;
					}
					doors.add(pos.immutable());
				}
			}
		}

		return doors;
	}

	private BlockPos resolveOutsideCandidate(AnimalBuildingRecord building, BlockPos sideA, BlockPos sideB) {
		boolean sideAInside = building.isInBounds(sideA);
		boolean sideBInside = building.isInBounds(sideB);
		if (sideAInside == sideBInside) {
			return null;
		}
		return sideAInside ? sideB : sideA;
	}

	private void logNoOutsideTargetDiagnostics(AnimalBuildingRecord building, DoorTarget resolvedTarget) {
		if (building == null) {
			return;
		}

		int openDoors = 0;
		int outsideCandidates = 0;
		int occupiableOutside = 0;
		int occupiableInside = 0;

		for (BlockPos doorPos : getBoundaryDoorPositions(building)) {
			BlockState state = this.level().getBlockState(doorPos);
			if (!AnimalDoorStateService.isDoorOrFenceGate(state) || !AnimalDoorStateService.isOpen(state)) {
				continue;
			}
			openDoors++;

			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos sideA = doorPos.relative(dir);
				BlockPos sideB = doorPos.relative(dir.getOpposite());
				BlockPos outside = resolveOutsideCandidate(building, sideA, sideB);
				if (outside == null) {
					continue;
				}
				BlockPos inside = outside == sideA ? sideB : sideA;
				outsideCandidates++;

				BlockPos resolvedInside = canOccupy(inside)
					? inside
					: findNearbyStandableInside(building, doorPos, inside);
				if (resolvedInside != null) {
					occupiableInside++;
				}

				BlockPos resolvedOutside = canOccupy(outside)
					? outside
					: findNearbyStandableOutside(building, doorPos, outside);
				if (resolvedOutside != null) {
					occupiableOutside++;
				}
			}
		}

		BaseCoopAnimalEntity.this.logAiDiag(
			"leave.no_target.diag",
			"openDoors=" + openDoors
				+ " outsideCandidates=" + outsideCandidates
				+ " occupiableInside=" + occupiableInside
				+ " occupiableOutside=" + occupiableOutside
				+ " resolvedTarget=" + resolvedTarget
		);
	}

	private boolean isAnimalDoorOpen(AnimalBuildingRecord building) {
		return building != null && this.level() instanceof ServerLevel serverLevel
			&& AnimalDoorStateService.isAnyBoundaryDoorOpen(serverLevel, building);
	}

	private DoorTarget resolveNearestDoorTarget(AnimalBuildingRecord building, boolean requireOpenDoor) {
		if (building == null) {
			return null;
		}

		DoorTarget bestTarget = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos selfPos = this.blockPosition();

		for (BlockPos doorPos : getBoundaryDoorPositions(building)) {
			BlockState state = this.level().getBlockState(doorPos);
			if (!AnimalDoorStateService.isDoorOrFenceGate(state)) {
				continue;
			}
			if (requireOpenDoor && !AnimalDoorStateService.isOpen(state)) {
				continue;
			}

			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos sideA = doorPos.relative(dir);
				BlockPos sideB = doorPos.relative(dir.getOpposite());

				BlockPos outside = resolveOutsideCandidate(building, sideA, sideB);
				if (outside == null) {
					continue;
				}
				BlockPos inside = outside == sideA ? sideB : sideA;

				BlockPos resolvedInside = canOccupy(inside)
					? inside
					: findNearbyStandableInside(building, doorPos, inside);
				if (resolvedInside == null) {
					continue;
				}

				BlockPos resolvedOutside = canOccupy(outside)
					? outside
					: findNearbyStandableOutside(building, doorPos, outside);
				if (resolvedOutside == null) {
					continue;
				}

				double dist = resolvedInside.distSqr(selfPos);
				if (dist < bestDist) {
					bestDist = dist;
					bestTarget = new DoorTarget(resolvedInside.immutable(), resolvedOutside.immutable(), doorPos.immutable());
				}
			}
		}

		return bestTarget;
	}

	private BlockPos findNearbyStandableOutside(AnimalBuildingRecord building, BlockPos doorPos, BlockPos preferredOutside) {
		if (building == null) {
			return null;
		}

		BlockPos projected = findProjectedDoorSideTarget(building, doorPos, preferredOutside, false);
		if (projected != null) {
			return projected;
		}

		for (int radius = 1; radius <= 3; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						BlockPos candidate = preferredOutside.offset(dx, dy, dz);
						if (!canOccupy(candidate)) {
							continue;
						}

						double candidateDoorDist = candidate.distSqr(doorPos);
						if (candidateDoorDist > 16.0D) {
							continue;
						}

						return candidate;
					}
				}
			}
		}

		return null;
	}

	private BlockPos findNearbyStandableInside(AnimalBuildingRecord building, BlockPos doorPos, BlockPos preferredInside) {
		if (building == null) {
			return null;
		}

		BlockPos projected = findProjectedDoorSideTarget(building, doorPos, preferredInside, true);
		if (projected != null) {
			return projected;
		}

		for (int radius = 1; radius <= 3; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						BlockPos candidate = preferredInside.offset(dx, dy, dz);
						if (!building.isInBounds(candidate) || !canOccupy(candidate)) {
							continue;
						}

						double candidateDoorDist = candidate.distSqr(doorPos);
						if (candidateDoorDist > 16.0D) {
							continue;
						}

						return candidate;
					}
				}
			}
		}

		return null;
	}

	private BlockPos findProjectedDoorSideTarget(AnimalBuildingRecord building, BlockPos doorPos, BlockPos side, boolean insideTarget) {
		if (building == null || doorPos == null || side == null) {
			return null;
		}

		int dx = Integer.compare(side.getX(), doorPos.getX());
		int dz = Integer.compare(side.getZ(), doorPos.getZ());
		if ((dx == 0 && dz == 0) || (dx != 0 && dz != 0)) {
			return null;
		}

		for (int step = 3; step <= 5; step++) {
			BlockPos lineBase = doorPos.offset(dx * step, 0, dz * step);
			for (int lateral = -1; lateral <= 1; lateral++) {
				int ox = dx == 0 ? lateral : 0;
				int oz = dz == 0 ? lateral : 0;
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos candidate = lineBase.offset(ox, dy, oz);
					boolean inBounds = building.isInBounds(candidate);
					if (insideTarget != inBounds) {
						continue;
					}
					if (!canOccupy(candidate)) {
						continue;
					}
					return candidate;
				}
			}
		}

		return null;
	}

	private record DoorTarget(BlockPos insidePos, BlockPos outsidePos, BlockPos doorPos) {
	}
}
