package com.stardew.craft.entity.passive;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 乌鸦实体 — 还原 Stardew Valley 1.6 BellsAndWhistles/Crow.cs。
 *
 * 4 状态：
 *   PECKING(0)：1~4 次啄食循环，每次播音；结束 50% 继续 / 50% → STOPPED
 *   FLYING_AWAY(1)：玩家 ≤ 4 格触发，背离玩家斜向飞走 + 拍翅膀音
 *   SLEEPING(2)：静止 idle，0.3%/tick → STOPPED
 *   STOPPED(3)：0.8%/tick 五选一：sleep / peck / hop / 翻身 hop / 飞走
 *
 * 速度换算（SDV 60fps → MC 20tps，64px = 1 tile = 1 block）：
 *   水平飞走 6px/frame -> 0.28 b/tick
 *   垂直上升 2px/frame -> 0.094 b/tick
 *
 * 由 CrowAttackScheduler 在每天清晨生成；30s 寿命到了主动飞走。
 * 不可伤害、无 AI Goal、无碰撞推动、无 vanilla travel()（手动管理速度）。
 */
@SuppressWarnings("null")
public class CrowEntity extends Mob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation EAT  = RawAnimation.begin().thenLoop("eat");
    private static final RawAnimation FLY  = RawAnimation.begin().thenLoop("fly");

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(CrowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GROUNDED =
            SynchedEntityData.defineId(CrowEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int STATE_PECKING      = 0;
    public static final int STATE_FLYING_AWAY  = 1;
    public static final int STATE_SLEEPING     = 2;
    public static final int STATE_STOPPED      = 3;

    private static final double FLEE_RANGE     = 4.0;
    private static final double FLEE_RANGE_SQR = FLEE_RANGE * FLEE_RANGE;
    private static final int    DEFAULT_LIFETIME_TICKS = 600; // 30s
    private static final int    MAX_FLEE_TICKS         = 200; // 10s safety

    // 速度常量（SDV 原版按 60fps，已换算到 20tps × 1block=64px）
    private static final double FLEE_VX  = 0.28;  // 水平 0.28 b/tick ≈ 5.6 b/s
    private static final double FLEE_VY  = 0.094; // 垂直 0.094 b/tick ≈ 1.88 b/s
    private static final double HOP_VY   = 0.32;  // 跳跃初速度
    private static final double GRAVITY  = 0.04;  // 自由下落
    private static final double AIR_FRIC = 0.7;   // 空中水平摩擦

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int    lifetimeTicks    = DEFAULT_LIFETIME_TICKS;
    private int    stateTimer       = 0;
    private int    peckTimer        = 0;
    private int    peckCount        = 0;
    private int    targetPecks      = 0;
    private double fleeDirX         = 0.0;
    private double fleeDirZ         = 0.0;
    private boolean playedFlapThisFlee = false;

    public CrowEntity(EntityType<? extends CrowEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_PECKING);
        this.entityData.define(DATA_GROUNDED, true);
    }

    public int  getCrowState()       { return entityData.get(DATA_STATE); }
    public boolean isGroundedSync()  { return entityData.get(DATA_GROUNDED); }

    public void setCrowState(int s) {
        if (entityData.get(DATA_STATE) != s) {
            entityData.set(DATA_STATE, s);
            stateTimer = 0;
        }
    }

    public void setLifetimeTicks(int t) { this.lifetimeTicks = t; }

    @Override public boolean hurt(@Nonnull DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(@Nonnull net.minecraft.world.entity.Entity other) { /* noop */ }
    @Override protected void pushEntities() { /* noop */ }
    @Override public boolean removeWhenFarAway(double distSqr) { return true; }

    /** 关闭 vanilla travel()：手动管理 deltaMovement / 重力 / 摩擦。 */
    @Override
    public void travel(@Nonnull Vec3 input) { /* manual control in tick() */ }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        stateTimer++;
        if (lifetimeTicks > 0) {
            lifetimeTicks--;
            if (lifetimeTicks <= 0 && getCrowState() != STATE_FLYING_AWAY) {
                triggerFlee(null);
            }
        }
        entityData.set(DATA_GROUNDED, onGround());

        int state = getCrowState();
        if (state != STATE_FLYING_AWAY) {
            Player nearest = level().getNearestPlayer(this, FLEE_RANGE);
            if (nearest != null && distanceToSqr(nearest) <= FLEE_RANGE_SQR) {
                triggerFlee(nearest);
                state = STATE_FLYING_AWAY;
            }
        }

        switch (state) {
            case STATE_FLYING_AWAY -> tickFlee();
            case STATE_PECKING     -> tickPecking();
            case STATE_SLEEPING    -> tickSleeping();
            case STATE_STOPPED     -> tickStopped();
            default -> tickStopped();
        }
    }

    /* =================== 状态实现 =================== */

    private void triggerFlee(@Nullable Player player) {
        if (player != null) {
            Vec3 away = position().subtract(player.position());
            double len = Math.sqrt(away.x * away.x + away.z * away.z);
            if (len > 1.0e-3) {
                fleeDirX = away.x / len;
                fleeDirZ = away.z / len;
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                fleeDirX = Math.cos(a);
                fleeDirZ = Math.sin(a);
            }
        } else {
            double a = random.nextDouble() * Math.PI * 2;
            fleeDirX = Math.cos(a);
            fleeDirZ = Math.sin(a);
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-fleeDirX, fleeDirZ));
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;

        if (random.nextFloat() < 0.85F) {
            // SDV cue: "crow" — singular caw on flee
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.CROW_CAW.get(),
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        playedFlapThisFlee = false;
        setCrowState(STATE_FLYING_AWAY);
    }

    private void tickFlee() {
        Vec3 dv = new Vec3(fleeDirX * FLEE_VX, FLEE_VY, fleeDirZ * FLEE_VX);
        setDeltaMovement(dv);
        // 飞走时直接 setPos 穿过低矮障碍（避免被农田旁的栅栏卡住）
        setPos(getX() + dv.x, getY() + dv.y, getZ() + dv.z);

        // 起飞瞬间一次振翅（≈ SDV 第 5 帧）
        if (!playedFlapThisFlee && stateTimer == 3) {
            // SDV cue: "batFlap" — initial wing burst at takeoff
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.CROW_FLAP.get(),
                    SoundSource.NEUTRAL, 0.7F, 1.0F);
            playedFlapThisFlee = true;
        }
        // 持续轻微振翅（每 0.7s）
        if (stateTimer > 3 && stateTimer % 14 == 0) {
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.CROW_FLAP.get(),
                    SoundSource.NEUTRAL, 0.45F, 1.0F);
        }

        if (getY() > level().getMaxBuildHeight() - 8 || stateTimer > MAX_FLEE_TICKS) {
            discard();
        }
    }

    private void tickPecking() {
        applyGroundedDecay();

        if (targetPecks == 0) {
            targetPecks = 1 + random.nextInt(4);
            peckCount   = 0;
            peckTimer   = 30; // 第一啄前的 idle 1.5s
        }

        if (peckTimer > 0) {
            peckTimer--;
            return;
        }
        peckCount++;
        // SDV cue: "shiny4" — peck/pickup chime
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.SHINY4.get(),
                SoundSource.NEUTRAL, 0.6F, 1.0F);

        if (peckCount >= targetPecks) {
            targetPecks = 0;
            peckCount   = 0;
            // 原版 donePecking: random(0,3) → 50% pecking / 50% stopped
            setCrowState(random.nextBoolean() ? STATE_PECKING : STATE_STOPPED);
        } else {
            peckTimer = 18 + random.nextInt(20); // 0.9~1.9s 间隔
        }
    }

    private void tickSleeping() {
        applyGroundedDecay();
        if (random.nextFloat() < 0.003F) {
            setCrowState(STATE_STOPPED);
        }
    }

    private void tickStopped() {
        applyGroundedDecay();
        if (!onGround()) return;

        if (random.nextFloat() < 0.008F) {
            switch (random.nextInt(5)) {
                case 0 -> setCrowState(STATE_SLEEPING);
                case 1 -> setCrowState(STATE_PECKING);
                case 2 -> hop();
                case 3 -> { flipYaw(); hop(); }
                case 4 -> triggerFlee(null);
                default -> { /* noop */ }
            }
        }
    }

    /** 手动重力 + 摩擦：travel() 已禁，所有非 flee 状态的物理靠这里。 */
    private void applyGroundedDecay() {
        Vec3 v = getDeltaMovement();
        if (onGround() && v.y <= 0.0) {
            setDeltaMovement(0, 0, 0);
        } else {
            setDeltaMovement(v.x * AIR_FRIC, v.y - GRAVITY, v.z * AIR_FRIC);
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    private void hop() {
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x, HOP_VY, v.z);
    }

    private void flipYaw() {
        float yaw = getYRot() + 180.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    /* =================== 渲染 =================== */

    @Override
    public void registerControllers(@Nonnull AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            int s = getCrowState();
            if (s == STATE_FLYING_AWAY || !isGroundedSync()) {
                return state.setAndContinue(FLY);
            }
            if (s == STATE_PECKING) {
                return state.setAndContinue(EAT);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.0);
    }

    /* =================== 持久化 =================== */

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CrowState", getCrowState());
        tag.putInt("LifetimeTicks", lifetimeTicks);
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("PeckTimer", peckTimer);
        tag.putInt("PeckCount", peckCount);
        tag.putInt("TargetPecks", targetPecks);
        tag.putDouble("FleeDirX", fleeDirX);
        tag.putDouble("FleeDirZ", fleeDirZ);
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CrowState"))     setCrowState(tag.getInt("CrowState"));
        if (tag.contains("LifetimeTicks")) lifetimeTicks = tag.getInt("LifetimeTicks");
        if (tag.contains("StateTimer"))    stateTimer    = tag.getInt("StateTimer");
        if (tag.contains("PeckTimer"))     peckTimer     = tag.getInt("PeckTimer");
        if (tag.contains("PeckCount"))     peckCount     = tag.getInt("PeckCount");
        if (tag.contains("TargetPecks"))   targetPecks   = tag.getInt("TargetPecks");
        if (tag.contains("FleeDirX"))      fleeDirX      = tag.getDouble("FleeDirX");
        if (tag.contains("FleeDirZ"))      fleeDirZ      = tag.getDouble("FleeDirZ");
    }

    /** 在指定坐标生成乌鸦（服务端调用）。 */
    public static CrowEntity spawnAt(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        CrowEntity crow = new CrowEntity(com.stardew.craft.entity.ModEntities.CROW.get(), level);
        crow.moveTo(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0);
        crow.setCrowState(STATE_PECKING);
        crow.setLifetimeTicks(DEFAULT_LIFETIME_TICKS);
        level.addFreshEntity(crow);
        return crow;
    }
}
