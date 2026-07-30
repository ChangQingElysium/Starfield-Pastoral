package com.stardew.craft.entity.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@SuppressWarnings("null")
public class StardewNpcEntity extends PathfinderMob implements GeoEntity {
    private static final int INVALID_ID_GRACE_TICKS = 40;
    private static final EntityDataAccessor<String> DATA_NPC_ID = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IS_WALKING = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean hasLastServerWalkPosition;
    private double lastServerWalkX;
    private double lastServerWalkZ;

    /** NPC 转向状态机 */
    private enum FacingState { NONE, TURNING_TO, HOLDING, TURNING_BACK }
    private FacingState facingState = FacingState.NONE;
    /** 转向目标角度 */
    private float facingTargetYaw;
    /** 转向前保存的原始朝向 */
    private float savedYaw;
    /** 转向速度（度/tick），最多约 3 tick 转完 180° */
    private static final float TURN_SPEED = 60f;
    /** 转到位后的保持时间（tick）。单人 GUI 期间 tick 暂停，所以实际保持到对话关闭后 */
    private int facingHoldTicks;
    /** Action opened only after the NPC has finished turning toward the player. */
    @javax.annotation.Nullable
    private Runnable facingOnComplete;
    /** 空闲时自动看向玩家 */
    private static final double LOOK_AT_PLAYER_RANGE = 2.0;
    private static final float IDLE_TURN_SPEED = 15f;
    private boolean lookingAtPlayer = false;
    private float idleSavedYaw;

    public StardewNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.setNoGravity(false);
        // Keep AI loop enabled so MC navigation/pathfinder can run.
        this.setNoAi(false);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        // Replace the default LookControl with one that yields to our facing state machine.
        // Vanilla LookControl.tick() sets yHeadRot every tick, fighting our smooth rotation.
        this.lookControl = new NpcLookControl(this);
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        NpcPathNavigation nav = new NpcPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(false);
        return nav;
    }

    /**
     * Custom LookControl that becomes a no-op while the NPC is in a facing override
     * (dialogue, gift, or idle-look-at-player). Without this, vanilla's LookControl.tick()
     * overwrites yHeadRot every tick, causing the NPC to snap away from the player.
     */
    private static class NpcLookControl extends LookControl {
        private final StardewNpcEntity owner;
        NpcLookControl(StardewNpcEntity entity) {
            super(entity);
            this.owner = entity;
        }
        @Override
        public void tick() {
            if (owner.facingState != FacingState.NONE || owner.lookingAtPlayer) {
                return; // Our state machine controls rotation — don't interfere.
            }
            super.tick();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.20D)
            .add(Attributes.FOLLOW_RANGE, 384.0D)
            .add(Attributes.STEP_HEIGHT, 0.6D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NPC_ID, "");
        builder.define(DATA_IS_WALKING, false);
    }

    @Override
    protected void registerGoals() {
        // Intentionally empty: NPC movement is centrally managed by runtime services.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcId", getNpcId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NpcId")) {
            setNpcId(tag.getString("NpcId"));
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (facingOnComplete != null) {
            return InteractionResult.SUCCESS;
        }
        return NpcInteractionService.onInteract(player, this, hand);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public String getNpcId() {
        return this.entityData.get(DATA_NPC_ID);
    }

    public void setNpcId(String npcId) {
        this.entityData.set(DATA_NPC_ID, npcId == null ? "" : npcId.toLowerCase());
    }

    @Override
    public Component getName() {
        String id = getNpcId();
        if (id != null && !id.isBlank()) {
            return Component.translatable("entity.stardewcraft.npc." + id);
        }
        return super.getName();
    }

    public boolean hasValidNpcId() {
        String npcId = getNpcId();
        return npcId != null && !npcId.isBlank() && NpcDataRegistry.capabilities().containsKey(npcId);
    }

    @Override
    public void tick() {
        if (this.isNoGravity() && !this.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
            this.setNoGravity(false);
        }
        if (!this.level().isClientSide) {
            if (!hasValidNpcId()) {
                if (this.tickCount >= INVALID_ID_GRACE_TICKS) {
                    StardewCraft.LOGGER.warn(
                        "Discarding StardewNpcEntity with invalid npcId='{}' at tickCount={} pos=({}, {}, {})",
                        getNpcId(),
                        this.tickCount,
                        this.getX(),
                        this.getY(),
                        this.getZ()
                    );
                    this.discard();
                    return;
                }
            } else {
                if (this.tickCount >= 10
                    && !this.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)
                    && !com.stardew.craft.npc.runtime.NpcSpawnManager.isOfficialInstance(this)) {
                    this.discard();
                    return;
                }
            }
        }
        super.tick();
        if (!this.level().isClientSide) {
            // 同步行走状态到客户端（用于 GeckoLib 动画控制器）
            boolean walking = false;
            if (hasLastServerWalkPosition) {
                double dx = this.getX() - lastServerWalkX;
                double dz = this.getZ() - lastServerWalkZ;
                double horizontalMoveSqr = dx * dx + dz * dz;
                walking = horizontalMoveSqr > 1.0E-5D && horizontalMoveSqr < 4.0D;
            }
            hasLastServerWalkPosition = true;
            lastServerWalkX = this.getX();
            lastServerWalkZ = this.getZ();
            if (walking != isWalking()) {
                setWalking(walking);
            }
        }
        // Run facing state machine AFTER super.tick() so that our yaw overrides
        // whatever Mob.tick() → LookControl.tick() / body rotation logic set.
        // This is the fix for "NPC turns briefly then snaps back" — the vanilla
        // Mob AI loop was overwriting our rotation every tick.
        if (!this.level().isClientSide) {
            tickFacingState();
        }
    }

    /**
     * Suppress vanilla head-turn interpolation while we are controlling rotation.
     * LivingEntity.tickHeadTurn() normally adjusts yBodyRot toward movement direction,
     * which fights our facing override / idle-look system.
     */
    @Override
    protected float tickHeadTurn(float renderYawOffset, float distance) {
        if (facingState != FacingState.NONE || lookingAtPlayer) {
            // Don't let vanilla adjust body rotation; return 0 delta.
            return distance;
        }
        return super.tickHeadTurn(renderYawOffset, distance);
    }

    /** 每 tick 处理 NPC 平滑转向 */
    private void tickFacingState() {
        switch (facingState) {
            case TURNING_TO: {
                // 平滑转向目标角度
                if (smoothRotateToward(facingTargetYaw)) {
                    facingState = FacingState.HOLDING;
                    Runnable onComplete = facingOnComplete;
                    facingOnComplete = null;
                    setWalking(false);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
                break;
            }
            case HOLDING: {
                // 保持面朝玩家。单人 GUI 打开后 tick 暂停，这里不执行。
                // GUI 关闭后 tick 恢复，倒计时 → 转回去。
                if (facingHoldTicks > 0) {
                    facingHoldTicks--;
                } else {
                    facingState = FacingState.TURNING_BACK;
                }
                break;
            }
            case TURNING_BACK: {
                // 平滑转回原始朝向
                if (smoothRotateToward(savedYaw)) {
                    facingState = FacingState.NONE;
                }
                break;
            }
            default:
                break;
        }

        // 空闲时自动朝向附近玩家
        if (facingState == FacingState.NONE) {
            tickIdleLookAtPlayer();
        } else if (lookingAtPlayer) {
            // 对话系统接管了，取消 idle look 状态
            lookingAtPlayer = false;
        }
    }

    /** 空闲时检测附近玩家，平滑转向 */
    private void tickIdleLookAtPlayer() {
        // NPC 正在行走时不触发
        if (this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5D) {
            if (lookingAtPlayer) {
                lookingAtPlayer = false;
            }
            return;
        }

        Player nearest = this.level().getNearestPlayer(this, LOOK_AT_PLAYER_RANGE);
        if (nearest != null && nearest.isAlive() && !nearest.isSpectator()) {
            double dx = nearest.getX() - this.getX();
            double dz = nearest.getZ() - this.getZ();
            float targetYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));

            if (!lookingAtPlayer) {
                // 刚进入范围，记住当前朝向以便回头
                idleSavedYaw = this.getYRot();
                lookingAtPlayer = true;
            }

            // 平滑追踪玩家位置
            idleSmoothRotateToward(targetYaw);
        } else if (lookingAtPlayer) {
            // 玩家离开范围，平滑转回原朝向
            if (idleSmoothRotateToward(idleSavedYaw)) {
                lookingAtPlayer = false;
            }
        }
    }

    private boolean idleSmoothRotateToward(float targetYaw) {
        float current = this.getYRot();
        float diff = Mth.wrapDegrees(targetYaw - current);
        if (Math.abs(diff) < IDLE_TURN_SPEED) {
            applyYaw(targetYaw);
            return true;
        }
        float step = Math.signum(diff) * IDLE_TURN_SPEED;
        applyYaw(current + step);
        return false;
    }

    /**
     * 每 tick 向 targetYaw 平滑插值。
     * @return true 如果已经到达目标角度
     */
    private boolean smoothRotateToward(float targetYaw) {
        float current = this.getYRot();
        float diff = Mth.wrapDegrees(targetYaw - current);
        if (Math.abs(diff) <= TURN_SPEED) {
            // 足够接近，直接对齐
            applyYaw(targetYaw);
            return true;
        }
        float step = Math.signum(diff) * TURN_SPEED;
        applyYaw(current + step);
        return false;
    }

    private void applyYaw(float yaw) {
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.hasImpulse = true;
    }

    /**
     * Whether the NPC is currently in a facing override state (turning to player,
     * holding, or turning back). External systems (e.g. NpcCentralMovementService)
     * must NOT overwrite yaw while this returns true.
     */
    public boolean isFacingOverrideActive() {
        return facingState != FacingState.NONE;
    }

    /** Whether the NPC is currently idle-looking at a nearby player. */
    public boolean isIdleLookActive() {
        return lookingAtPlayer;
    }

    /**
     * 让 NPC 快速平滑转向玩家，转到位后再执行 onComplete 回调。
     * 对话结束（GUI 关闭、tick 恢复）后 NPC 会平滑转回原始朝向。
     *
     * @param target     要面对的玩家
     * @param holdTicks  转到位后保持的 tick 数（单人 GUI 期间 tick 冻结，不消耗）
     * @param onComplete 转身完成后的回调（用于发送对话/礼物确认包），可为 null
     */
    public void facePlayerTemporarily(Player target, int holdTicks, @javax.annotation.Nullable Runnable onComplete) {
        if (this.level().isClientSide) return;
        // If currently idle-looking, save the original schedule yaw (not the
        // mid-turn yaw) so TURNING_BACK returns to the correct orientation.
        if (lookingAtPlayer) {
            this.savedYaw = this.idleSavedYaw;
            lookingAtPlayer = false;
        } else {
            this.savedYaw = this.getYRot();
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        this.facingTargetYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        this.facingHoldTicks = holdTicks;
        this.facingOnComplete = onComplete;
        this.facingState = FacingState.TURNING_TO;
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.setWalking(false);
        this.hasLastServerWalkPosition = true;
        this.lastServerWalkX = this.getX();
        this.lastServerWalkZ = this.getZ();
    }

    public boolean isPathingEnabled() {
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(getNpcId());
        return profile != null && profile.canRunPathing();
    }

    /** 客户端使用：当前 NPC 是否有行走动画资源。 */
    public boolean hasWalkAnimation() {
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(getNpcId());
        return profile != null && profile.hasWalkAnimation();
    }

    /** 服务端设置行走状态，通过 SynchedEntityData 自动同步到客户端。 */
    public void setWalking(boolean walking) {
        this.entityData.set(DATA_IS_WALKING, walking);
    }

    /** 客户端/服务端均可读取的行走状态。 */
    public boolean isWalking() {
        return this.entityData.get(DATA_IS_WALKING);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isWalking() && hasWalkAnimation()) {
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

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // NPC 永远不消散
    }

    @Override
    public void die(DamageSource source) {
    }

    /**
     * NPCs are fully managed by NpcSpawnManager — never persist to chunk NBT.
     * This prevents the #1 source of duplicate entities (old save data reloading).
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !"henchman".equals(getNpcId())
                && !"bouncer".equals(getNpcId())
                && super.canBeCollidedWith();
    }

    @Override
    protected void doPush(Entity entity) {
        // These story gates use an explicit per-player eviction volume. Letting
        // their shared NPC entity run LivingEntity#doPush would still apply
        // velocity to a player for whom the NPC has already been hidden.
        String npcId = getNpcId();
        if ("henchman".equals(npcId) || "bouncer".equals(npcId)) {
            return;
        }
        super.doPush(entity);
    }

    @Override
    public void push(double x, double y, double z) {
        // NPC cannot be displaced by player or entity collisions.
    }
}
