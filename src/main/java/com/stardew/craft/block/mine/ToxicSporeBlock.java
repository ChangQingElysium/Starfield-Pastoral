package com.stardew.craft.block.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.skill.HitCooldownDamageSource;

import java.util.List;

/**
 * 毒气孢子方块 — 骷髅矿洞区域危害。
 * <p>
 * 方块上方 3 格持续释放绿色毒雾粒子。
 * 进入范围的玩家通过统一 hurt 生命周期承受等比例环境伤害。
 * 非玩家生物承受 Poison；玩家承受 Nausea，并由方块粒子表达毒雾。
 * 使用 scheduleTick 每 20 tick 检测一次上方实体，保证可靠触发。
 */
@SuppressWarnings("null")
public class ToxicSporeBlock extends Block {

    /** 毒气向上影响的格数 */
    private static final int TOXIC_HEIGHT = 3;

    /** 每次伤害扣减的星露谷生命值（SDV 大致为 2/秒） */
    private static final int SD_DAMAGE_PER_TICK = 2;

    /** Poison 视觉效果持续时间（tick） */
    private static final int POISON_DURATION = 100;

    /** 恶心效果持续时间（tick） */
    private static final int NAUSEA_DURATION = 140;

    /** scheduleTick 检测间隔（tick） */
    private static final int CHECK_INTERVAL = 20;

    /** 玩家中毒受伤冷却（tick）— 无敌帧 */
    private static final int PLAYER_HURT_COOLDOWN_TICKS = 20;

    /** PersistentData key — 上次被毒气孢子伤害的 gameTime */
    private static final String NBT_LAST_TOXIC_TICK = "stardewcraft:last_toxic_spore_tick";

    public ToxicSporeBlock(Properties props) {
        super(props);
    }

    /* ---- 可靠的定时检测 ---- */

    @Override
    @SuppressWarnings({"deprecation", "null"})
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        }
    }

    @Override
    @SuppressWarnings({"deprecation", "null"})
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 检测上方实体
        AABB toxicZone = new AABB(
                pos.getX(), pos.getY() + 0.2, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0 + TOXIC_HEIGHT, pos.getZ() + 1.0
        );
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, toxicZone);
        for (LivingEntity living : entities) {
            applyEffects(living);
        }
        // 重新调度下一次检测
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    @Override
    @SuppressWarnings({"deprecation", "null"})
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 直接踩在孢子方块里也中毒
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            applyEffects(living);
        }
    }

    /** stepOn — 站在方块上方也中毒 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            applyEffects(living);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 持续向上方释放绿色粒子，营造毒雾效果
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.0 + random.nextDouble() * TOXIC_HEIGHT;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.DRAGON_BREATH,
                    x, y, z,
                    (random.nextDouble() - 0.5) * 0.02,
                    random.nextDouble() * 0.02,
                    (random.nextDouble() - 0.5) * 0.02);
        }

        // 额外孢子粒子
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.0 + random.nextDouble() * 0.5;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                    x, y, z, 0.0, 0.02, 0.0);
        }
    }

    private static void applyEffects(LivingEntity living) {
        // 玩家伤害统一进入 Incoming / Pre / Post；本方块自己的 1 秒冷却
        // 取代 vanilla cooldown，但不再绕过防御、戒指、受击技能与 Applied Post。
        if (living instanceof ServerPlayer player) {
            // 无敌帧：1 秒内最多被毒一次
            long now = player.serverLevel().getGameTime();
            long last = player.getPersistentData().getLong(NBT_LAST_TOXIC_TICK);
            if (last != 0L && now - last < PLAYER_HURT_COOLDOWN_TICKS) {
                return;
            }
            player.getPersistentData().putLong(NBT_LAST_TOXIC_TICK, now);

            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            if (data.getHealth() > 0) {
                float minecraftDamage = DimensionDamageMapper
                        .toMinecraftHealthDamage(
                                SD_DAMAGE_PER_TICK,
                                data.getMaxHealth(),
                                player.getMaxHealth()
                        );
                player.hurt(
                        HitCooldownDamageSource.bypassVanillaCooldown(
                                player.damageSources().magic()
                        ),
                        minecraftDamage
                );
            }
        } else {
            // 非玩家实体：用原版 Poison II
            MobEffectInstance existingPoison = living.getEffect(MobEffects.POISON);
            if (existingPoison == null || existingPoison.getDuration() < POISON_DURATION / 2) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.POISON, POISON_DURATION, 1,
                        false, true, true));
            }
        }

        // Nausea（恶心）— 屏幕扭曲效果
        MobEffectInstance existingNausea = living.getEffect(MobEffects.CONFUSION);
        if (existingNausea == null || existingNausea.getDuration() < NAUSEA_DURATION / 2) {
            living.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, NAUSEA_DURATION, 0,
                    false, true, true));
        }
    }
}
