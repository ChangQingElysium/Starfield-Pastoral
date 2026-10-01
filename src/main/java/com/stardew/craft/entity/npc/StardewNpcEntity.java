package com.stardew.craft.entity.npc;

import com.stardew.craft.npc.animation.SamActivity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.npc.attention.SamAttentionController;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@SuppressWarnings("null")
public class StardewNpcEntity extends PathfinderMob implements GeoEntity {
    private static final int INVALID_ID_GRACE_TICKS = 40;
    private static final EntityDataAccessor<String> DATA_NPC_ID = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> DATA_MOTION_PROFILE = SynchedEntityData.defineId(StardewNpcEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private long motionRevision = -1;
    private static final EntityDataAccessor<Boolean> DATA_IS_WALKING = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_WALK_ANIMATION = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_ATTENTION = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Long> DATA_GUITAR_START = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> DATA_NATIVE_ACTIVITY = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> DATA_SCHEDULE_ACTIVITY = SynchedEntityData.defineId(StardewNpcEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final com.stardew.craft.npc.animation.NpcScheduleActivity scheduleActivity = new com.stardew.craft.npc.animation.NpcScheduleActivity(this);
    private final SamAttentionController attention = new SamAttentionController(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean hasLastServerWalkPosition;
    private double lastServerWalkX;
    private double lastServerWalkZ;
    private boolean resolvingWalkCollision;

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
    private boolean facingSessionSeen;
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
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_OTHER, -1.0F);
        // Replace the default LookControl with one that yields to our facing state machine.
        // Vanilla LookControl.tick() sets yHeadRot every tick, fighting our smooth rotation.
        this.lookControl = new NpcLookControl(this);
        this.moveControl = new NpcMoveControl(this);
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
            if (owner.facingState != FacingState.NONE || owner.isIdleLookActive()) {
                return; // Our state machine controls rotation — don't interfere.
            }
            super.tick();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.20D * com.stardew.craft.npc.runtime.NpcMotionProfile.TRAVEL_SPEED_MULTIPLIER)
            .add(Attributes.FOLLOW_RANGE, 96.0D)
            .add(Attributes.STEP_HEIGHT, 0.6D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MOTION_PROFILE,new CompoundTag());
        this.entityData.define(DATA_NPC_ID, "");
        this.entityData.define(DATA_IS_WALKING, false);
        this.entityData.define(DATA_HAS_WALK_ANIMATION, false);
        this.entityData.define(DATA_ATTENTION, new CompoundTag());
        this.entityData.define(DATA_GUITAR_START, -1L);
        this.entityData.define(DATA_NATIVE_ACTIVITY, "");
        this.entityData.define(DATA_SCHEDULE_ACTIVITY, new CompoundTag());
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
        if (facingOnComplete != null || scheduleActivity.hasPendingInteraction()) {
            return InteractionResult.SUCCESS;
        }
        return NpcInteractionService.onInteract(player, this, hand);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    public String getNpcId() {
        return this.entityData.get(DATA_NPC_ID);
    }

    public void setNpcId(String npcId) {
        motionRevision = -1;
        this.entityData.set(DATA_NPC_ID, npcId == null ? "" : npcId.toLowerCase());
        if (!this.level().isClientSide) {
            syncAnimationCapabilities();
        }
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
    public void move(net.minecraft.world.entity.MoverType type, net.minecraft.world.phys.Vec3 movement) {
        boolean previous = resolvingWalkCollision;
        resolvingWalkCollision = !level().isClientSide && type == net.minecraft.world.entity.MoverType.SELF;
        try {
            super.move(type, movement);
        } finally {
            resolvingWalkCollision = previous;
        }
    }

    @Override
    public float maxUpStep() {
        float maximum = super.maxUpStep();
        // Path search must retain the configured capability, including full-block hills.
        // Only physical movement is limited to the rise requested by its steering target.
        return resolvingWalkCollision && moveControl instanceof NpcMoveControl control
                ? control.collisionStepHeight(maximum) : maximum;
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
            // Refresh after data-pack reloads as well as when an observer first tracks this NPC.
            syncAnimationCapabilities();
            if (tickCount % 20 == 0) com.stardew.craft.npc.runtime.NpcActorPersistence.capture(this);
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
            boolean autonomous = com.stardew.craft.npc.runtime.NpcExecutionCoordinator.autonomous(this);
            if (autonomous) {
                scheduleActivity.tick();
            } else if (isNativeActivityMovementLocked()) {
                scheduleActivity.cancel();
            }
            // Festival staging stops the daily schedule, but an accepted conversation still
            // owns its turn/hold/return sequence. Higher-priority scene takeovers can revoke it.
            boolean dialogue = (isFacingOverrideActive() || NpcInteractionService.isDialogueMovementLocked(getNpcId()))
                    && com.stardew.craft.npc.runtime.NpcExecutionCoordinator.claim(this,
                            com.stardew.craft.npc.runtime.NpcExecutionCoordinator.DIALOGUE,80,2) >= 0;
            if (autonomous || dialogue) {
                tickFacingState();
            } else {
                if (isAttentionActive()) attention.cancel();
                facingOnComplete=null;
                facingState=FacingState.NONE;
            }
        }
    }

    /** All authoritative relocations revoke motion/activity callbacks before moving the entity. */
    public void prepareForNpcRelocation() {
        cancelAutonomousActions();
        getNavigation().stop();
        com.stardew.craft.npc.runtime.NpcExecutionCoordinator.cancel(this);
    }

    /** Cancel autonomous callbacks on ownership transfer without revoking the new owner's lease. */
    public void cancelAutonomousActions() {
        scheduleActivity.cancel();
        attention.cancel();
        if(!level().isClientSide) NpcInteractionService.cancelNpcSessions(getNpcId());
        facingOnComplete=null;
        facingState=FacingState.NONE;
    }

    public long getGuitarStartTick() { return entityData.get(DATA_GUITAR_START); }
    public long getNativeActivityStartTick() { return getGuitarStartTick(); }
    public SamActivity getNativeActivity() { return SamActivity.fromAnimation(entityData.get(DATA_NATIVE_ACTIVITY)); }
    public boolean isPlayingNativeActivity() { return !getScheduleActivityEvent().isEmpty() || getNativeActivity() != null && getNativeActivityStartTick() >= 0; }
    public boolean isNativeActivityMovementLocked() { return isPlayingNativeActivity() || scheduleActivity.isSettling(); }
    public boolean isPlayingGuitar() { return isPlayingNativeActivity() && getNativeActivity() == SamActivity.GUITAR; }

    public CompoundTag getScheduleActivityEvent() { return entityData.get(DATA_SCHEDULE_ACTIVITY); }
    public void setScheduleActivityEvent(CompoundTag event) {
        entityData.set(DATA_SCHEDULE_ACTIVITY,event);
        entityData.set(DATA_NATIVE_ACTIVITY,event.getString("action"));
        entityData.set(DATA_GUITAR_START,event.isEmpty() ? -1L : event.getLong("start"));
    }

    /**
     * Suppress vanilla head-turn interpolation while we are controlling rotation.
     * LivingEntity.tickHeadTurn() normally adjusts yBodyRot toward movement direction,
     * which fights our facing override / idle-look system.
     */
    @Override
    protected float tickHeadTurn(float renderYawOffset, float distance) {
        if (facingState != FacingState.NONE || isIdleLookActive() || isNativeActivityMovementLocked()) {
            // Don't let vanilla adjust body rotation; return 0 delta.
            return distance;
        }
        return super.tickHeadTurn(renderYawOffset, distance);
    }

    /** 每 tick 处理 NPC 平滑转向 */
    private void tickFacingState() {
        if (attention.enabled() && facingState != FacingState.NONE && getAttentionEvent().getBoolean("dialogue")) {
            tickNativeDialogueFacing();
            return;
        }
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
                    facingSessionSeen = NpcInteractionService.isDialogueMovementLocked(getNpcId());
                }
                break;
            }
            case HOLDING: {
                // Multiplayer keeps ticking while the dialogue window is open.
                if (NpcInteractionService.isDialogueMovementLocked(getNpcId())) {
                    facingSessionSeen = true;
                } else if (facingSessionSeen || facingHoldTicks-- <= 0) {
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

        // Only explicit conversations may turn festival actors away from their authored heading.
        if (!com.stardew.craft.npc.runtime.NpcExecutionCoordinator.autonomous(this)) return;

        // 空闲时自动朝向附近玩家
        if (attention.enabled()) {
            lookingAtPlayer = false;
            attention.tick();
        } else if (facingState == FacingState.NONE) {
            tickIdleLookAtPlayer();
        } else if (lookingAtPlayer) {
            // 对话系统接管了，取消 idle look 状态
            lookingAtPlayer = false;
        }
    }

    /** Keep the server heading stable; the synchronized pose supplies the actual turning steps. */
    private void tickNativeDialogueFacing() {
        var event=getAttentionEvent();
        long now=level().getGameTime();
        applyYaw(event.getFloat("baseYaw"));
        getNavigation().stop();
        setWalking(false);
        switch (facingState) {
            case TURNING_TO -> {
                var target=level().getEntity(event.getInt("target"));
                boolean valid=target instanceof Player player && player.isAlive() && !player.isSpectator()
                        && target.distanceToSqr(this)<=64;
                // Each queued callback validates its own player; the first observer leaving must not drop the others.
                // Finish planting the feet even if the initiating player leaves before the GUI opens.
                if ((now-event.getLong("start"))/20.0>=SamAttentionController.dialogueReadyTime(event)) {
                    facingState=FacingState.HOLDING;
                    Runnable onComplete=facingOnComplete;
                    facingOnComplete=null;
                    if (onComplete!=null) onComplete.run();
                    facingSessionSeen=NpcInteractionService.isDialogueMovementLocked(getNpcId());
                    if (!valid) facingHoldTicks=0;
                }
            }
            case HOLDING -> {
                if (NpcInteractionService.isDialogueMovementLocked(getNpcId())) {
                    facingSessionSeen=true;
                } else if (facingSessionSeen || facingHoldTicks--<=0) {
                    attention.releaseDialogue();
                    facingState=FacingState.TURNING_BACK;
                }
            }
            case TURNING_BACK -> {
                if (!SamAttentionController.isActive(event,now)) {
                    setAttentionEvent(new CompoundTag());
                    applyYaw(savedYaw);
                    facingState=FacingState.NONE;
                }
            }
            default -> { }
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
        return lookingAtPlayer || isAttentionActive();
    }

    public CompoundTag getAttentionEvent() { return entityData.get(DATA_ATTENTION); }
    public void setAttentionEvent(CompoundTag event) { entityData.set(DATA_ATTENTION,event); }
    public boolean isAttentionActive() {
        return SamAttentionController.isActive(getAttentionEvent(),level().getGameTime());
    }

    /**
     * 转到位后执行 onComplete；原生 Sam 使用同步转步，保持至对话会话关闭后转回。
     * 其他角色暂时沿用原朝向状态机，待模型迁移后再接入原生转步。
     *
     * @param target     要面对的玩家
     * @param holdTicks  无对话会话的交互／旧角色转向使用的回退保持时长
     * @param onComplete 转身完成后的回调（用于发送对话/礼物确认包），可为 null
     */
    public void facePlayerTemporarily(Player target, int holdTicks, @javax.annotation.Nullable Runnable onComplete) {
        if (this.level().isClientSide) return;
        if (isPlayingNativeActivity()) {
            scheduleActivity.interrupt(() -> {
                if (target.isAlive() && target.level() == level() && distanceToSqr(target) < 64)
                    facePlayerTemporarily(target,holdTicks,onComplete);
            });
            return;
        }
        if (com.stardew.craft.npc.runtime.NpcExecutionCoordinator.claim(this,
                com.stardew.craft.npc.runtime.NpcExecutionCoordinator.DIALOGUE,80,2)<0) return;
        if(facingState==FacingState.TURNING_TO) {
            var previous=facingOnComplete;
            facingOnComplete=()->{
                if(previous!=null) previous.run();
                if(onComplete!=null && target.isAlive() && target.level()==level() && distanceToSqr(target)<64) onComplete.run();
            };
            facingHoldTicks=Math.max(facingHoldTicks,holdTicks);
            return;
        }
        if (attention.enabled() && facingState==FacingState.HOLDING && getAttentionEvent().getBoolean("dialogue")) {
            // A second conversation shares the existing facing; do not restart or retarget the body.
            facingHoldTicks=Math.max(facingHoldTicks,holdTicks);
            if (onComplete!=null) onComplete.run();
            facingSessionSeen |= NpcInteractionService.isDialogueMovementLocked(getNpcId());
            return;
        }
        if (!attention.enabled()) attention.cancel();
        // If currently idle-looking, save the original schedule yaw (not the
        // mid-turn yaw) so TURNING_BACK returns to the correct orientation.
        if (lookingAtPlayer) {
            this.savedYaw = this.idleSavedYaw;
            lookingAtPlayer = false;
        } else {
            this.savedYaw = attention.enabled() ? this.yBodyRot : this.getYRot();
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        this.facingTargetYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        this.facingHoldTicks = holdTicks;
        this.facingSessionSeen = false;
        this.facingOnComplete = onComplete==null?null:()->{
            if(target.isAlive() && target.level()==level() && distanceToSqr(target)<64) onComplete.run();
        };
        this.facingState = FacingState.TURNING_TO;
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.setWalking(false);
        this.hasLastServerWalkPosition = true;
        this.lastServerWalkX = this.getX();
        this.lastServerWalkZ = this.getZ();
        if (attention.enabled()) attention.beginDialogue(target);
    }

    public boolean isPathingEnabled() {
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(getNpcId());
        return profile != null && profile.canRunPathing();
    }

    private void syncAnimationCapabilities() {
        if (motionRevision != NpcDataRegistry.revision()) {
            var motion=com.stardew.craft.npc.runtime.NpcMotionProfile.forActor(getNpcId());
            CompoundTag tag=new CompoundTag();
            tag.putFloat("width",motion.width()); tag.putFloat("height",motion.height()); tag.putFloat("eye",motion.eyeHeight());
            tag.putBoolean("attention",motion.attention());
            this.entityData.set(DATA_MOTION_PROFILE,tag);
            var speed=getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed!=null) speed.setBaseValue(motion.travelSpeed());
            var step=getAttribute(Attributes.STEP_HEIGHT);
            if (step!=null) step.setBaseValue(motion.stepHeight());
            if (getNavigation() instanceof NpcPathNavigation navigation) navigation.refreshSearchBudget();
            var range=getAttribute(Attributes.FOLLOW_RANGE);
            if (range!=null) range.setBaseValue(com.stardew.craft.npc.runtime.NpcNavigationPolicy.current().searchRange());
            refreshDimensions();
            motionRevision=NpcDataRegistry.revision();
        }
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(getNpcId());
        this.entityData.set(DATA_HAS_WALK_ANIMATION, profile != null && profile.hasWalkAnimation());
    }

    public boolean usesNativeAttention() { return entityData.get(DATA_MOTION_PROFILE).getBoolean("attention"); }

    // PORT(1.20.1): 1.21 LivingEntity#getDimensions is sleeping ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose)
    // (times the 1.20.5+ scale attribute, always 1 here); 1.20.1 only has getDimensions.
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return pose == net.minecraft.world.entity.Pose.SLEEPING ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose);
    }
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        var profile=entityData.get(DATA_MOTION_PROFILE);
        return profile.isEmpty() ? super.getDimensions(pose)
                : net.minecraft.world.entity.EntityDimensions.scalable(profile.getFloat("width"),profile.getFloat("height"));
    }

    // PORT(1.20.1): 1.20.5+ EntityDimensions#withEyeHeight carried the profile eye height; 1.20.1 asks the entity.
    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dimensions) {
        var profile=entityData.get(DATA_MOTION_PROFILE);
        return profile.isEmpty() ? super.getStandingEyeHeight(pose, dimensions) : profile.getFloat("eye");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MOTION_PROFILE.equals(key)) refreshDimensions();
    }

    /** Remote clients do not load NpcDataRegistry; animation capability travels with entity metadata. */
    public boolean hasWalkAnimation() {
        return this.entityData.get(DATA_HAS_WALK_ANIMATION);
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
    public void remove(RemovalReason reason) {
        if (!isRemoved() && reason == RemovalReason.UNLOADED_TO_CHUNK
                && level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.stardew.craft.npc.runtime.NpcSpawnManager.onNpcChunkUnloaded(serverLevel,this);
        }
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        String npcId = getNpcId();
        return !"henchman".equals(npcId)
                && !"bouncer".equals(npcId)
                && super.isPushable();
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
        if (entity instanceof StardewNpcEntity other
                && com.stardew.craft.npc.runtime.NpcCentralMovementService.isScheduleTravelling(this)
                && com.stardew.craft.npc.runtime.NpcCentralMovementService.isScheduleTravelling(other)) return;
        super.doPush(entity);
    }

    @Override
    public void push(double x, double y, double z) {
        String npcId = getNpcId();
        if ("henchman".equals(npcId) || "bouncer".equals(npcId)) {
            return;
        }
        super.push(x, y, z);
    }
}
