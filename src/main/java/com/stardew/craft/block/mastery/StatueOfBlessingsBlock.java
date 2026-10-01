package com.stardew.craft.block.mastery;

import com.stardew.craft.blockentity.MasteryStatueBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.mastery.PrismaticButterflyEntity;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * 农业精通奖励 — Statue of Blessings。
 * 严格按 SDV Object.cs:4320-4358 (CheckForActionOnBlessedStatue) 实现：
 *  - 要求 Farming Mastery ≥ 1
 *  - 玩家身上无 statue_of_blessings_* buff，且今天未被祝福过
 *  - 随机选 0..6 中一个 buff（雨天/节日时只到 5）→ 应用
 *  - 标记 hasBeenBlessedByStatueToday，早晨重置
 */
@SuppressWarnings("null")
public class StatueOfBlessingsBlock extends TallMasteryBlock implements EntityBlock {
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");

    public StatueOfBlessingsBlock(Properties properties) {
        super(properties, "stardewcraft:block/mastery/statue_of_blessings", Direction.EAST);
        registerDefaultState(defaultBlockState().setValue(ACTIVATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVATED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
        return new MasteryStatueBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION || type != ModBlockEntities.MASTERY_STATUE.get() || level.isClientSide) return null;
        return (lvl, pos, st, be) -> ((MasteryStatueBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) return;
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MasteryStatueBlockEntity statue) {
            statue.setOwner(sp);
        }
    }

    private static final List<Holder<MobEffect>> BUFFS = List.of(
        ModMobEffects.STATUE_OF_BLESSINGS_0,
        ModMobEffects.STATUE_OF_BLESSINGS_1,
        ModMobEffects.STATUE_OF_BLESSINGS_2,
        ModMobEffects.STATUE_OF_BLESSINGS_3,
        ModMobEffects.STATUE_OF_BLESSINGS_4,
        ModMobEffects.STATUE_OF_BLESSINGS_5,
        ModMobEffects.STATUE_OF_BLESSINGS_6
    );

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            pos = TallMasteryBlock.getMainPos(pos, state);
            state = level.getBlockState(pos);
        }

        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
        if (data == null) return InteractionResult.SUCCESS;

        // Mastery 校验
        if (!data.hasClaimedMasteryReward(SkillType.FARMING)) {
            sp.displayClientMessage(Component.translatable("stardewcraft.mastery.statue.requirement"), true);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6f, 0.6f);
            return InteractionResult.SUCCESS;
        }

        if (state.getValue(ACTIVATED)) {
            if (data.isBlessedByStatueToday()) return InteractionResult.SUCCESS;
            setActivated(level, pos, state, false);
            state = state.setValue(ACTIVATED, false);
        }

        // 已被祝福过 / 已有 buff 时不再触发
        if (data.isBlessedByStatueToday()) return InteractionResult.SUCCESS;
        for (Holder<MobEffect> b : BUFFS) {
            if (sp.hasEffect(b)) return InteractionResult.SUCCESS;
        }

        // 抽 buff：原版雨天/节日时排除 _6 (Butterfly)；这里以 isRaining 近似（Stardew 雨/节日检测略复杂，先用雨）
        boolean restrict = level.isRaining();
        int max = restrict ? 6 : 7;
        int idx = chooseDailyBlessingIndex(level, max);
        Holder<MobEffect> chosen = BUFFS.get(idx);

        // SDV Duration=-2 -> 持续到次日；MC 里用无限时长，PlayerStardewDataAPI.sleep 会清掉。
        // ambient=false, visible=false（关粒子）, showIcon=true（HUD 仍显示）。
        sp.addEffect(new MobEffectInstance(chosen, -1, 0, false, false, true));
        data.setBlessedByStatueToday(true);
        data.setBlessingOfWatersRemaining(idx == 3 ? 3 : 0);
        PlayerDataEventHandler.syncPlayerData(sp, data);
        setActivated(level, pos, state, true);
        if (level.getBlockEntity(pos) instanceof MasteryStatueBlockEntity statue) {
            statue.markBlessingsActivated(StardewTimeManager.get().getAbsoluteDay());
        }
        sp.sendSystemMessage(Component.translatable("stardewcraft.mastery.statue_of_blessings.granted",
            Component.translatable(chosen.value().getDescriptionId()),
            blessingDescription(idx)));

        if (level instanceof ServerLevel sl) {
            spawnActivationButterflies(sl, pos);
        }
        level.playSound(null, pos, ModSounds.STATUE_OF_BLESSINGS.get(), SoundSource.BLOCKS, 0.8f, 1.0f);

        return InteractionResult.SUCCESS;
    }

    private static int chooseDailyBlessingIndex(Level level, int maxExclusive) {
        int absoluteDay = StardewTimeManager.get().getAbsoluteDay();
        long seed = (long) absoluteDay * 778L;
        if (level instanceof ServerLevel serverLevel) {
            seed += serverLevel.getSeed() / 2L;
        }
        Random random = new Random(seed);
        for (int i = 0; i < 8; i++) {
            random.nextInt();
        }
        return random.nextInt(maxExclusive);
    }

    private static void spawnActivationButterflies(ServerLevel level, BlockPos pos) {
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 1.25D;
        double centerZ = pos.getZ() + 0.5D;
        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2.0D / 3.0D) * i + level.getRandom().nextDouble() * 0.45D;
            double radius = 0.45D + level.getRandom().nextDouble() * 0.35D;
            PrismaticButterflyEntity butterfly = PrismaticButterflyEntity.createBlessingVisual(level,
                centerX + Math.cos(angle) * radius,
                centerY + level.getRandom().nextDouble() * 0.35D,
                centerZ + Math.sin(angle) * radius);
            level.addFreshEntity(butterfly);
        }
    }

    private static Component blessingDescription(int idx) {
        String key = "stardewcraft.mastery.statue_of_blessings.desc_" + idx;
        return idx == 3 ? Component.translatable(key, 3) : Component.translatable(key);
    }

    public static void setActivated(Level level, BlockPos pos, BlockState state, boolean activated) {
        level.setBlock(pos, state.setValue(ACTIVATED, activated), 3);
        BlockPos extensionPos = pos.above();
        BlockState extensionState = level.getBlockState(extensionPos);
        if (extensionState.getBlock() instanceof StatueOfBlessingsBlock && extensionState.hasProperty(ACTIVATED)) {
            level.setBlock(extensionPos, extensionState.setValue(ACTIVATED, activated), 3);
        }
    }
}
