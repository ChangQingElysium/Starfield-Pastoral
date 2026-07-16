package com.stardew.craft.block.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 不稳定岩层方块 — 骷髅矿洞延迟陷阱。
 * <p>
 * 玩家踩上后 1.5 秒延迟碎裂（变成空气），
 * 掉入下方深坑或熔岩。碎裂过程中显示逐步破碎动画。
 */
@SuppressWarnings("null")
public class UnstableRockBlock extends Block {

    /** 是否已被触发（踩过） */
    public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");

    /** 碎裂延迟（tick），1.5 秒 */
    private static final int BREAK_DELAY = 30;

    /** 破碎动画阶段数（0-9 共10级，每级间隔 tick） */
    private static final int CRACK_STAGES = 10;
    private static final int TICKS_PER_STAGE = BREAK_DELAY / CRACK_STAGES; // 3 ticks per stage

    /** 用于 destroyBlockProgress 的唯一 ID 偏移（防止和玩家采掘冲突） */
    private static final int DESTROY_ID_OFFSET = 0x5C_0000;

    public UnstableRockBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(TRIGGERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity && !state.getValue(TRIGGERED)) {
            // 标记为已触发
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_ALL);

            // 播放裂纹警告音效
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS,
                    0.5F, 0.8F);

            // 安排第一个破碎阶段（立即开始，stage 0）
            level.scheduleTick(pos, this, TICKS_PER_STAGE);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(TRIGGERED)) return;

        // 计算当前破碎进度：通过检查已经过的时间来推断 stage
        // 使用 destroyBlockProgress 发送破碎纹理 (0-9)
        int destroyId = DESTROY_ID_OFFSET + pos.hashCode();

        // 查找当前阶段 — 通过在 tick 里递进
        // 我们用一个简单的方式：每次 tick 推进一级，直到第10级时碎裂
        // 通过读取 block entity 或 tag 来追踪 stage 太复杂，直接用连续 scheduleTick
        // 每次 tick 带一个递增的 stage，用 block 的 hashCode + pos 来追踪
        // 简化方案：用 level.getBlockTicks 不方便，改用内部计数
        // 最简方案：直接连续调度 10 次 tick，每次根据顺序推进 stage
        advanceCrackStage(level, pos, state, destroyId, 0);
    }

    private void advanceCrackStage(ServerLevel level, BlockPos pos, BlockState state,
                                    int destroyId, int currentStage) {
        if (!level.getBlockState(pos).is(this) || !level.getBlockState(pos).getValue(TRIGGERED)) {
            // 方块已经被移除或重置
            level.destroyBlockProgress(destroyId, pos, -1); // 清除破碎纹理
            return;
        }

        if (currentStage >= CRACK_STAGES) {
            // 最终碎裂！
            level.destroyBlockProgress(destroyId, pos, -1); // 清除破碎纹理
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.3, 0.3, 0.3, 0.05);
            level.destroyBlock(pos, false);
            return;
        }

        // 显示破碎进度（0-9）
        level.destroyBlockProgress(destroyId, pos, currentStage);

        // 偶数阶段播放轻微裂纹声
        if (currentStage % 3 == 0 && currentStage > 0) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS,
                    0.4F, 0.7F + currentStage * 0.03F);
        }

        // 调度下一阶段
        final int nextStage = currentStage + 1;
        // 无法通过 scheduleTick 传参数，改用嵌套调度方案：
        // 重写 tick 只处理 stage 0，后续阶段在服务端 tick 事件中推进
        // 实际上最干净的方法是直接在这里用 ServerLevel 的 tick scheduler
        // 但 scheduleTick 不携带数据。改用 BlockEntityTicker 也太重。
        // 最简方案：直接在当前 tick 里一次性设置所有未来阶段
        // 不行——scheduleTick 只能对同一 block 调度一次。
        // 最终方案：在这一次 tick 里同步循环处理所有阶段，每阶段之间无真实延迟，
        // 改为用一个回调机制。
        // 使用关卡模拟时间，菜单时停期间裂纹阶段不会越过实体/方块模拟偷跑。
        com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(
                level, TICKS_PER_STAGE,
                () -> advanceCrackStage(level, pos, state, destroyId, nextStage));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(TRIGGERED)) {
            // 已触发：显示碎裂粒子警告
            for (int i = 0; i < 2; i++) {
                double x = pos.getX() + random.nextDouble();
                double y = pos.getY() + 1.0;
                double z = pos.getZ() + random.nextDouble();
                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.1,
                        0.05,
                        (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }
}
