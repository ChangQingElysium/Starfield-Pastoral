package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.GarbageCanLootTable;
import com.stardew.craft.block.utility.TrashBinBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.npc.runtime.TrashCanNpcReactionService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 垃圾桶方块实体 — 对齐原版 CheckGarbage / TryGetGarbageItem 逻辑。
 * <p>
 * 每日每桶限翻一次，掉落物通过 {@link GarbageCanLootTable} 计算。
 */
@SuppressWarnings("null")
public class TrashBinBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements GeoBlockEntity {

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("animation");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 上次被翻的绝对天数，-1 表示从未被翻过 */
    private int lastCheckedDay = -1;

    /** 盖子保持打开的倒计时（tick） */
    private int openTicks;

    /** 动画状态追踪 */
    private boolean lastAnimatedOpen;

    public TrashBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRASH_BIN.get(), pos, state);
    }

    // ==================== 核心逻辑 ====================

    /**
     * 玩家右键翻垃圾桶。
     */
    public void checkGarbage(ServerPlayer player) {
        if (level == null || level.isClientSide) return;

        int today = getAbsoluteDay();
        if (lastCheckedDay == today) {
            // 今天已经翻过了 — 对齐原版 CheckedGarbage.Add(id) 返回 false 的行为
            return;
        }

        lastCheckedDay = today;
        setChanged();
        syncToClient();

        // 打开盖子动画
        setOpenState(true);
        openTicks = 40; // ~2 秒

        // 播放翻垃圾桶声音
        level.playSound(null, worldPosition, ModSounds.TRASHCANLID.get(), SoundSource.BLOCKS, 0.7f, 1.0f);

        // 计算掉落
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        double dailyLuck = data.getDailyLuck();
        int trashCansChecked = data.getTrashCansChecked();
        long daySeed = stableDaySeed(((ServerLevel) level).getSeed(), today);

        String namedCanId = level.dimension() == ModDimensions.STARDEW_VALLEY
                ? GarbageCanLootTable.namedCanId(worldPosition)
                : null;
        String canId = namedCanId != null
                ? namedCanId
                : worldPosition.getX() + "_" + worldPosition.getY() + "_" + worldPosition.getZ();
        GarbageCanLootTable.Result result = desertFestivalGarbageResult()
            ? new GarbageCanLootTable.Result(new ItemStack(ModItems.CALICO_EGG.get(), 5 + level.random.nextInt(4)), false, false)
            : GarbageCanLootTable.tryGetItem(canId, dailyLuck, trashCansChecked, daySeed, player);

        // 递增统计
        data.incrementTrashCansChecked();
        PlayerDataEventHandler.syncPlayerData(player, data);

        TrashCanNpcReactionService.reactToSearch(player, worldPosition);

        // 掉落物品
        if (result != null && !result.item().isEmpty()) {
            // Mega 音效
            if (result.isDoubleMegaSuccess()) {
                level.playSound(null, worldPosition, ModSounds.EXPLOSION.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
            } else if (result.isMegaSuccess()) {
                level.playSound(null, worldPosition, ModSounds.CRIT.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
            }

            // 在垃圾桶上方生成物品实体
            double x = worldPosition.getX() + 0.5;
            double y = worldPosition.getY() + 1.2;
            double z = worldPosition.getZ() + 0.5;
            net.minecraft.world.entity.item.ItemEntity itemEntity =
                    new net.minecraft.world.entity.item.ItemEntity(level, x, y, z, result.item().copy());
            itemEntity.setDeltaMovement(0, 0.25, 0);
            itemEntity.setPickUpDelay(10);
            level.addFreshEntity(itemEntity);
        }
    }

    private boolean desertFestivalGarbageResult() {
        return DesertFestivalService.isFestivalOpen() && DesertConstants.isInDesertRegion(worldPosition);
    }

    // ==================== Tick ====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrashBinBlockEntity be) {
        if (be.openTicks > 0) {
            be.openTicks--;
            if (be.openTicks == 0) {
                be.setOpenState(false);
            }
        }
    }

    // ==================== 状态管理 ====================

    private void setOpenState(boolean open) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(TrashBinBlock.OPEN)) return;
        if (state.getValue(TrashBinBlock.OPEN) == open) return;
        level.setBlock(worldPosition, state.setValue(TrashBinBlock.OPEN, open), 3);
    }

    private void syncToClient() {
        if (level == null || level.isClientSide) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    // ==================== 时间工具 ====================

    private static int getAbsoluteDay() {
        StardewTimeManager tm = StardewTimeManager.get();
        return (tm.getCurrentYear() - 1) * 112 + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
    }

    private static long stableDaySeed(long saveSeed, int daysPlayed) {
        // SDV Utility.CreateDaySaveRandom: days played + save ID / 2 + call-specific salt.
        return daysPlayed + saveSeed / 2L;
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("LastCheckedDay", lastCheckedDay);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastCheckedDay = tag.contains("LastCheckedDay") ? tag.getInt("LastCheckedDay") : -1;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            BlockState blockState = getBlockState();
            boolean openNow = blockState.hasProperty(TrashBinBlock.OPEN) && blockState.getValue(TrashBinBlock.OPEN);
            if (openNow && !lastAnimatedOpen) {
                state.setAndContinue(OPEN_ANIM);
                lastAnimatedOpen = true;
            } else if (!openNow && lastAnimatedOpen) {
                state.getController().forceAnimationReset();
                lastAnimatedOpen = false;
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
