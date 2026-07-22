package com.stardew.craft.entity.junimo;

import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.entity.npc.NpcPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Junimo entity for the Community Center bundle system.
 * Color is applied via tint layer in the renderer; the entity stores a packed RGB color.
 */
@SuppressWarnings("null")
public class JunimoEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.INT);
    /** What this Junimo is carrying: 0=nothing, 1=bundle, 2=star, 3=orange. */
    private static final EntityDataAccessor<Integer> DATA_HOLDING_TYPE =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.INT);
    /** SDV parity: bundle color (packed RGB) carried by this Junimo. */
    private static final EntityDataAccessor<Integer> DATA_BUNDLE_COLOR =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_HELD_ITEM =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_PRISMATIC =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HARVEST_WORKER_NUMBER =
            SynchedEntityData.defineId(JunimoEntity.class, EntityDataSerializers.INT);

    public static final int HOLDING_NONE = 0;
    public static final int HOLDING_BUNDLE = 1;
    public static final int HOLDING_STAR = 2;
    public static final int HOLDING_ORANGE = 3;
    public static final int HOLDING_ITEM = 4;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation HOLD_WALK = RawAnimation.begin().thenLoop("hold_walk");
    @SuppressWarnings("unused")
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold("jump");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Default Junimo color: lime green (same as area 0 in original). */
    private static final int DEFAULT_COLOR = 0x32CD32; // LimeGreen

    /** 脚本目标位置 (到达后执行 onArrival 动作) */
    @Nullable
    private BlockPos targetPos;
    /** 到达目标后的动作 */
    private Runnable onArrival;
    /** fadeOut 计时 (tick)，-1 表示不淡出 */
    private int fadeOutTicks = -1;
    /** fadeIn 计时 (tick) */
    private int fadeInTicks = 0;
    private static final int FADE_DURATION = 20; // 1秒
    /** 当前透明度 (0~1, 供渲染器使用) */
    private float alpha = 0f;
    /** 存活上限 tick，超时自动移除 (防止泄漏) */
    private int maxLifeTicks = 1200; // 60秒 (玩家可能在 BundleScreen 中停留较久)
    /** 如果 true，不会超时自动移除 (idle Junimos at note positions) */
    private boolean noTimeout = false;
    /** Persistent assignment used by a placed Junimo Hut. */
    @Nullable
    private BlockPos harvestHutPos;
    @Nullable
    private BlockPos harvestTargetPos;
    @Nullable
    private BlockPos harvestApproachPos;
    private boolean returningToHarvestHut;
    private int harvestPickupDelay;
    private final List<ItemStack> carriedHarvest = new ArrayList<>();

    public JunimoEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    private int stuckChecks;
    private int progressCheckTicks;
    private double progressCheckX;
    private double progressCheckZ;
    private static final int PROGRESS_CHECK_INTERVAL = 20;
    private static final int STUCK_REPATH_CHECKS = 4;
    private static final double PROGRESS_MIN_DISP_SQR = 0.04D;
    private static final double HARVEST_ARRIVAL_DISTANCE_SQR = 0.8D;
    private static final double FINISHED_PATH_ARRIVAL_HORIZONTAL_SQR = 2.25D;
    private static final double FINISHED_PATH_ARRIVAL_MAX_Y = 1.25D;

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        NpcPathNavigation navigation = new NpcPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(false);
        return navigation;
    }

    // ── Synched Data ────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COLOR, DEFAULT_COLOR);
        builder.define(DATA_HOLDING_TYPE, HOLDING_NONE);
        builder.define(DATA_BUNDLE_COLOR, 0x00FF00); // default Lime
        builder.define(DATA_HELD_ITEM, ItemStack.EMPTY);
        builder.define(DATA_PRISMATIC, false);
        builder.define(DATA_HARVEST_WORKER_NUMBER, -1);
    }

    public int getJunimoColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setJunimoColor(int rgb) {
        this.entityData.set(DATA_COLOR, rgb);
    }

    public boolean isHolding() {
        return this.entityData.get(DATA_HOLDING_TYPE) != HOLDING_NONE;
    }

    /** @deprecated use {@link #setHoldingType(int)} instead */
    @Deprecated
    public void setHolding(boolean holding) {
        this.entityData.set(DATA_HOLDING_TYPE, holding ? HOLDING_BUNDLE : HOLDING_NONE);
    }

    public int getHoldingType() {
        return this.entityData.get(DATA_HOLDING_TYPE);
    }

    public void setHoldingType(int type) {
        this.entityData.set(DATA_HOLDING_TYPE, type);
    }

    public ItemStack getHeldItem() {
        return this.entityData.get(DATA_HELD_ITEM);
    }

    public boolean isPrismatic() {
        return this.entityData.get(DATA_PRISMATIC);
    }

    public void setPrismatic(boolean prismatic) {
        this.entityData.set(DATA_PRISMATIC, prismatic);
    }

    public int getHarvestWorkerNumber() {
        return this.entityData.get(DATA_HARVEST_WORKER_NUMBER);
    }

    public void setHarvestWorkerNumber(int workerNumber) {
        this.entityData.set(DATA_HARVEST_WORKER_NUMBER, workerNumber);
    }

    /** SDV parity: the color tint applied to the bundle item this Junimo carries. */
    public int getBundleColor() {
        return this.entityData.get(DATA_BUNDLE_COLOR);
    }

    public void setBundleColor(int rgb) {
        this.entityData.set(DATA_BUNDLE_COLOR, rgb);
    }

    /**
     * SDV parity: Bundle.getColorFromColorIndex(int color).
     * Maps bundle color index (0-6) to packed RGB.
     */
    public static int getColorFromBundleColorIndex(int colorIndex) {
        return switch (colorIndex) {
            case 0 -> 0x00FF00; // Lime
            case 1 -> 0xFF1493; // DeepPink
            case 2, 3 -> 0xFFA500; // Orange
            case 4 -> 0xFF0000; // Red
            case 5 -> 0xADD8E6; // LightBlue
            case 6 -> 0x00FFFF; // Cyan
            default -> 0x00FF00; // Lime
        };
    }

    /**
     * Returns the color as an array of [r, g, b] floats in 0..1 range,
     * suitable for tinting render calls.
     */
    public float[] getColorComponents() {
        int c = isPrismatic()
                ? Mth.hsvToRgb(((tickCount + getId() * 17) % 150) / 150.0F, 0.85F, 1.0F)
                : getJunimoColor();
        return new float[]{
                ((c >> 16) & 0xFF) / 255f,
                ((c >> 8) & 0xFF) / 255f,
                (c & 0xFF) / 255f
        };
    }

    // ── Persistence ─────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("JunimoColor", getJunimoColor());
        tag.putInt("HoldingType", getHoldingType());
        tag.putInt("BundleColor", getBundleColor());
        tag.putBoolean("Holding", isHolding());
        tag.putBoolean("Prismatic", isPrismatic());
        tag.putInt("HarvestWorkerNumber", getHarvestWorkerNumber());
        tag.putInt("HarvestPickupDelay", harvestPickupDelay);
        ListTag carried = new ListTag();
        for (ItemStack stack : carriedHarvest) {
            if (!stack.isEmpty()) {
                carried.add(stack.save(level().registryAccess()));
            }
        }
        tag.put("CarriedHarvest", carried);
        if (harvestHutPos != null) {
            tag.putLong("HarvestHut", harvestHutPos.asLong());
        }
        if (harvestTargetPos != null) {
            tag.putLong("HarvestTarget", harvestTargetPos.asLong());
        }
        if (harvestApproachPos != null) {
            tag.putLong("HarvestApproach", harvestApproachPos.asLong());
        }
        tag.putBoolean("ReturningToHarvestHut", returningToHarvestHut);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("JunimoColor")) {
            setJunimoColor(tag.getInt("JunimoColor"));
        }
        if (tag.contains("HoldingType")) {
            setHoldingType(tag.getInt("HoldingType"));
        } else if (tag.contains("Holding")) {
            setHolding(tag.getBoolean("Holding"));
        }
        if (tag.contains("BundleColor")) {
            setBundleColor(tag.getInt("BundleColor"));
        }
        setPrismatic(tag.getBoolean("Prismatic"));
        setHarvestWorkerNumber(tag.contains("HarvestWorkerNumber")
                ? tag.getInt("HarvestWorkerNumber") : -1);
        harvestPickupDelay = tag.getInt("HarvestPickupDelay");
        carriedHarvest.clear();
        ListTag carried = tag.getList("CarriedHarvest", Tag.TAG_COMPOUND);
        for (int i = 0; i < carried.size(); i++) {
            ItemStack stack = ItemStack.parse(level().registryAccess(), carried.getCompound(i))
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                carriedHarvest.add(stack);
            }
        }
        if (!carriedHarvest.isEmpty()) {
            setHeldHarvestItem(carriedHarvest.get(0));
        }
        if (tag.contains("HarvestHut")) {
            harvestHutPos = BlockPos.of(tag.getLong("HarvestHut"));
            setNoTimeout(true);
        }
        if (tag.contains("HarvestTarget")) {
            harvestTargetPos = BlockPos.of(tag.getLong("HarvestTarget"));
        }
        if (tag.contains("HarvestApproach")) {
            harvestApproachPos = BlockPos.of(tag.getLong("HarvestApproach"));
            targetPos = harvestApproachPos;
        }
        returningToHarvestHut = tag.getBoolean("ReturningToHarvestHut");
    }

    // ── AI / Goals ──────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        // Junimos are script-driven — goals are set dynamically via setTarget()
    }

    // ── Scripted Movement ───────────────────────────────────────

    /**
     * 设置脚本目标：Junimo 将寻路到目标位置，到达后执行回调。
     */
    public void setTarget(@Nullable BlockPos pos, @Nullable Runnable arrival) {
        this.targetPos = pos;
        this.onArrival = arrival;
        if (pos != null) {
            resetProgressTracking();
            this.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.5);
        }
    }

    /** 开始 fadeOut 动画，完成后自动移除实体 */
    public void startFadeOut() {
        this.fadeOutTicks = FADE_DURATION;
    }

    /** 设置为不超时 (idle Junimos at note positions — SDV resetSharedState parity) */
    public void setNoTimeout(boolean noTimeout) {
        this.noTimeout = noTimeout;
        if (noTimeout) {
            this.maxLifeTicks = Integer.MAX_VALUE;
        }
    }

    public void assignHarvestTarget(BlockPos hutPos, BlockPos cropPos, BlockPos approachPos) {
        this.harvestHutPos = hutPos.immutable();
        this.harvestTargetPos = cropPos.immutable();
        this.harvestApproachPos = approachPos.immutable();
        this.returningToHarvestHut = false;
        setNoTimeout(true);
        setTarget(this.harvestApproachPos, null);
    }

    public void returnToHarvestHut(BlockPos hutPos, BlockPos entrancePos) {
        this.harvestHutPos = hutPos.immutable();
        this.harvestApproachPos = entrancePos.immutable();
        this.returningToHarvestHut = true;
        setNoTimeout(true);
        setTarget(this.harvestApproachPos, null);
    }

    public void beginCarryingHarvest(BlockPos hutPos, BlockPos entrancePos, List<ItemStack> harvested) {
        carriedHarvest.clear();
        for (ItemStack stack : harvested) {
            if (!stack.isEmpty()) {
                carriedHarvest.add(stack.copy());
            }
        }
        if (carriedHarvest.isEmpty()) {
            returnToHarvestHut(hutPos, entrancePos);
            return;
        }
        setHeldHarvestItem(carriedHarvest.get(0));
        this.harvestHutPos = hutPos.immutable();
        this.harvestApproachPos = entrancePos.immutable();
        this.returningToHarvestHut = true;
        this.harvestPickupDelay = 12;
        this.targetPos = null;
        this.getNavigation().stop();
    }

    public List<ItemStack> takeCarriedHarvest() {
        List<ItemStack> result = carriedHarvest.stream().map(ItemStack::copy).toList();
        carriedHarvest.clear();
        setHeldHarvestItem(ItemStack.EMPTY);
        return result;
    }

    private void setHeldHarvestItem(ItemStack stack) {
        ItemStack displayed = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.entityData.set(DATA_HELD_ITEM, displayed);
        setHoldingType(displayed.isEmpty() ? HOLDING_NONE : HOLDING_ITEM);
    }

    @Nullable
    public BlockPos getHarvestHutPos() {
        return harvestHutPos;
    }

    @Nullable
    public BlockPos getHarvestTargetPos() {
        return harvestTargetPos;
    }

    public boolean isReturningToHarvestHut() {
        return returningToHarvestHut;
    }

    public void clearHarvestAssignment() {
        harvestHutPos = null;
        harvestTargetPos = null;
        harvestApproachPos = null;
        returningToHarvestHut = false;
        targetPos = null;
        harvestPickupDelay = 0;
        carriedHarvest.clear();
        setHeldHarvestItem(ItemStack.EMPTY);
        getNavigation().stop();
    }

    private void resetProgressTracking() {
        stuckChecks = 0;
        progressCheckTicks = 0;
        progressCheckX = getX();
        progressCheckZ = getZ();
    }

    public float getAlpha() {
        return alpha;
    }

    @Override
    public void tick() {
        super.tick();

        // fadeIn
        if (fadeInTicks < FADE_DURATION) {
            fadeInTicks++;
            alpha = (float) fadeInTicks / FADE_DURATION;
        }

        // fadeOut
        if (fadeOutTicks >= 0) {
            fadeOutTicks--;
            alpha = Math.max(0, (float) fadeOutTicks / FADE_DURATION);
            if (fadeOutTicks <= 0) {
                this.discard();
                return;
            }
        }

        // 超时自动移除 (idle Junimos exempt via noTimeout)
        if (!noTimeout) {
            maxLifeTicks--;
            if (maxLifeTicks <= 0) {
                this.discard();
                return;
            }
        }

        if (!level().isClientSide && harvestPickupDelay > 0) {
            harvestPickupDelay--;
            if (harvestPickupDelay == 6) {
                playSound(ModSounds.COIN.get(), 0.55F, 1.2F);
            }
            if (harvestPickupDelay == 0 && harvestApproachPos != null) {
                setTarget(harvestApproachPos, null);
            }
        }

        // 脚本目标到达检测
        if (targetPos != null) {
            double targetX = targetPos.getX() + 0.5D;
            double targetY = targetPos.getY();
            double targetZ = targetPos.getZ() + 0.5D;
            double targetDx = getX() - targetX;
            double targetDy = getY() - targetY;
            double targetDz = getZ() - targetZ;
            double distSq = targetDx * targetDx + targetDy * targetDy + targetDz * targetDz;
            double arrivalDistanceSq = harvestHutPos == null ? 2.5D : HARVEST_ARRIVAL_DISTANCE_SQR;
            // GroundPathNavigation may legitimately finish at the edge of the
            // requested block instead of its exact center. Treat that as an
            // arrival only while still close to the target, so a failed path
            // can never harvest remotely.
            boolean finishedNearby = harvestHutPos != null
                    && getNavigation().isDone()
                    && targetDx * targetDx + targetDz * targetDz <= FINISHED_PATH_ARRIVAL_HORIZONTAL_SQR
                    && Math.abs(targetDy) <= FINISHED_PATH_ARRIVAL_MAX_Y;
            if (distSq < arrivalDistanceSq || finishedNearby) {
                // 到达目标
                resetProgressTracking();
                getNavigation().stop();
                targetPos = null;
                if (!level().isClientSide && harvestHutPos != null) {
                    com.stardew.craft.blockentity.WizardBuildingBlockEntity.onHarvesterArrived(this);
                    return;
                }
                harvestTargetPos = null;
                if (onArrival != null) {
                    Runnable cb = onArrival;
                    onArrival = null;
                    cb.run();
                }
            } else if (!level().isClientSide) {
                progressCheckTicks++;
                if (progressCheckTicks >= PROGRESS_CHECK_INTERVAL) {
                    double dx = getX() - progressCheckX;
                    double dz = getZ() - progressCheckZ;
                    boolean progressed = dx * dx + dz * dz >= PROGRESS_MIN_DISP_SQR;
                    stuckChecks = progressed ? 0 : stuckChecks + 1;
                    progressCheckTicks = 0;
                    progressCheckX = getX();
                    progressCheckZ = getZ();

                    // Match the NPC movement controller: preserve an active path;
                    // rebuild only when navigation ended or actual displacement has
                    // been absent for several checks.
                    if (getNavigation().isDone() || stuckChecks >= STUCK_REPATH_CHECKS) {
                        getNavigation().stop();
                        getNavigation().moveTo(targetPos.getX() + 0.5,
                                targetPos.getY(), targetPos.getZ() + 0.5, 0.5);
                        stuckChecks = 0;
                    }
                }
            }
        }
    }

    // ── GeckoLib Animation ──────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Carrying is a discrete sprite state in SDV. Blending walk and hold_walk
        // makes the item follow the arm downward during pickup/drop-off, so switch
        // these poses on the same tick as the held-item data changes.
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (isHolding()) {
                return state.setAndContinue(HOLD_WALK);
            }
            if (this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
