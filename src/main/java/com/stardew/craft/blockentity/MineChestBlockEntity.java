package com.stardew.craft.blockentity;

import com.stardew.craft.block.mine.MineChestBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.menu.WoodenChestMenu;
import com.stardew.craft.mining.MineChestLootTable;
import com.stardew.craft.mining.MineRewardClaimManager;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

import javax.annotation.Nullable;
import java.util.*;

/**
 * 矿井宝箱 BlockEntity — Lootr 风格的 per-player 库存。
 * <p>
 * 每个玩家打开同一个宝箱看到的是属于自己的独立物品，互不干扰。
 * 物品在首次打开时生成，放在第二行正中间（slot 13）。
 */
@SuppressWarnings("null")
public class MineChestBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements MenuProvider, GeoBlockEntity {

    private static final int SLOT_COUNT = 27;
    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("OPEN");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("CLOSE");

    /** 每个玩家独立的库存 */
    private final Map<UUID, NonNullList<ItemStack>> playerInventories = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int openCount;
    private boolean lastAnimatedOpen;
    private int colorSelection = -1;
    /** 骷髅矿井宝藏室（220/320/420）每日刷新用：记录上次生成奖励的绝对天数。 */
    private int lastRefreshDay = -1;

    public MineChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINE_CHEST.get(), pos, state);
    }

    // ── per-player 库存 ──

    /**
     * 获取（或首次生成）指定玩家的库存。
     */
    public NonNullList<ItemStack> getOrCreatePlayerInventory(UUID playerId) {
        return getOrCreatePlayerInventory(playerId, null);
    }

    private NonNullList<ItemStack> getOrCreatePlayerInventory(UUID playerId, @Nullable net.minecraft.server.level.ServerPlayer player) {
        int floor = getFloorNumber();
        // 骷髅矿井宝藏室每日刷新：新的一天清空所有玩家的宝箱库存，重新生成奖励
        if (MineChestLootTable.isSkullCavernTreasureFloor(floor)) {
            int today = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
            if (lastRefreshDay != today) {
                lastRefreshDay = today;
                playerInventories.clear();
                setChanged();
            }
        }
        return playerInventories.computeIfAbsent(playerId, id -> {
            NonNullList<ItemStack> inv = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
            // 根据宝箱所在层数生成奖励，但如果玩家已在本存档领过该层，就不再给
            if (!hasClaimedReward(playerId, floor)) {
                ItemStack reward;
                if (MineChestLootTable.isSkullCavernTreasureFloor(floor)) {
                    // 骷髅矿井宝藏室（220/320/420）：26 选 1 随机池
                    // 使用 (floor, playerId, chestPos) 作为种子，保证同一玩家同一宝箱奖励稳定
                    long seed = ((long) floor * 341873128712L)
                            ^ playerId.getMostSignificantBits()
                            ^ playerId.getLeastSignificantBits()
                            ^ (worldPosition.asLong() * 132897987541L);
                        reward = com.stardew.craft.mining.SkullCavernTreasurePool.roll(
                            net.minecraft.util.RandomSource.create(seed), player);
                } else {
                    if (!(level instanceof ServerLevel serverLevel)) return inv;
                    long seed = ((long) floor * 341873128712L)
                            ^ playerId.getMostSignificantBits()
                            ^ playerId.getLeastSignificantBits()
                            ^ (worldPosition.asLong() * 132897987541L);
                    reward = MineChestLootTable.getRewardForFloor(
                            serverLevel, player, floor, new java.util.Random(seed));
                }
                if (reward != null && !reward.isEmpty()) {
                    inv.set(MineChestLootTable.REWARD_SLOT, reward.copy());
                }
            }
            setChanged();
            syncToClient();
            return inv;
        });
    }

    private boolean hasClaimedReward(UUID playerId, int floor) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        return MineRewardClaimManager.get(serverLevel).hasClaimed(playerId, claimKeyForFloor(floor));
    }

    private void markRewardClaimed(UUID playerId, int floor) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        MineRewardClaimManager.get(serverLevel).markClaimed(playerId, claimKeyForFloor(floor));
    }

    /**
     * 骷髅矿井宝藏室 (220/320/420) 一层多个宝箱，需按宝箱位置区分 claim 状态；
     * 且每天刷新，所以 claim key 还要包含绝对天数。
     * 普通楼层一层一个宝箱，沿用 floor 作为 key 即可（保持老存档兼容）。
     */
    private int claimKeyForFloor(int floor) {
        if (!MineChestLootTable.isSkullCavernTreasureFloor(floor)) {
            return floor;
        }
        int day = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        return Objects.hash(floor, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), day);
    }

    /**
     * 根据方块坐标反推矿井层数。
     */
    private int getFloorNumber() {
        int z = worldPosition.getZ();
        if (z <= 0) return 0;
        // floor = (z - 14) / FLOOR_SPACING
        return (z - 14) / MiningCoordinates.FLOOR_SPACING;
    }

    // ── 颜色 ──

    public int getColorSelection() {
        return colorSelection;
    }

    public void setColorSelection(int selection) {
        int clamped = WoodenChestColorPalette.clampIndex(selection);
        if (colorSelection == clamped) return;
        colorSelection = clamped;
        setChanged();
        syncToClient();
    }

    // ── 开关动画 ──

    public void startOpen(Player player) {
        if (player.isSpectator()) return;
        openCount++;
        if (openCount == 1 && level != null) {
            level.playSound(null, worldPosition, ModSounds.OPEN_CHEST.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        updateOpenState();
    }

    public void stopOpen(Player player) {
        if (player.isSpectator()) return;
        openCount = Math.max(0, openCount - 1);
        if (openCount == 0 && level != null) {
            level.playSound(null, worldPosition, ModSounds.DOOR_CREAK_REVERSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        updateOpenState();
    }

    private void updateOpenState() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(MineChestBlock.OPEN)) return;
        boolean openNow = openCount > 0;
        if (state.getValue(MineChestBlock.OPEN) != openNow) {
            currentLevel.setBlock(worldPosition, state.setValue(MineChestBlock.OPEN, openNow), 3);
        }
    }

    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) return;
        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        if (currentLevel instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    // ── MenuProvider ──

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.stardew_craft.mine_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        UUID playerId = player.getUUID();
        NonNullList<ItemStack> inv = getOrCreatePlayerInventory(playerId,
            player instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null);
        int floor = getFloorNumber();
        boolean rewardPresentOnOpen = !inv.get(MineChestLootTable.REWARD_SLOT).isEmpty()
                && !hasClaimedReward(playerId, floor);

        // 包装成一个 SimpleContainer 供 WoodenChestMenu 使用
        // 用标志位防止填充期间 setChanged 回写覆盖 inv
        boolean[] initializing = {true};
        boolean[] rewardPending = {rewardPresentOnOpen};
        SimpleContainer container = new SimpleContainer(SLOT_COUNT) {
            @Override
            public void startOpen(Player p) {
                MineChestBlockEntity.this.startOpen(p);
            }

            @Override
            public void stopOpen(Player p) {
                MineChestBlockEntity.this.stopOpen(p);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (initializing[0]) return;
                // 检测 slot 13 奖励是否被拿走（或数量变少）→ 标记为已领取
                if (rewardPending[0] && getItem(MineChestLootTable.REWARD_SLOT).isEmpty()) {
                    markRewardClaimed(playerId, floor);
                    rewardPending[0] = false;
                }
                // 同步回 BlockEntity 的 per-player 库存
                for (int i = 0; i < getContainerSize(); i++) {
                    inv.set(i, getItem(i).copy());
                }
                MineChestBlockEntity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player p) {
                return Container.stillValidBlockEntity(MineChestBlockEntity.this, p);
            }
        };

        // 填充容器
        for (int i = 0; i < SLOT_COUNT; i++) {
            container.setItem(i, inv.get(i).copy());
        }
        initializing[0] = false;

        return new WoodenChestMenu(containerId, playerInventory, container,
                this::setColorSelection, getColorSelection());
    }

    // ── NBT ──

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("colorSelection", colorSelection);
        tag.putInt("lastRefreshDay", lastRefreshDay);

        ListTag playersList = new ListTag();
        for (var entry : playerInventories.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            ListTag itemsList = new ListTag();
            NonNullList<ItemStack> inv = entry.getValue();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.get(i);
                if (stack.isEmpty()) continue;
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                itemTag.put("Stack", stack.save(registries));
                itemsList.add(itemTag);
            }
            playerTag.put("Items", itemsList);
            playersList.add(playerTag);
        }
        tag.put("PlayerInventories", playersList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colorSelection = tag.contains("colorSelection")
                ? WoodenChestColorPalette.clampIndex(tag.getInt("colorSelection")) : -1;
        lastRefreshDay = tag.contains("lastRefreshDay") ? tag.getInt("lastRefreshDay") : -1;

        playerInventories.clear();
        if (tag.contains("PlayerInventories")) {
            ListTag playersList = tag.getList("PlayerInventories", Tag.TAG_COMPOUND);
            for (int p = 0; p < playersList.size(); p++) {
                CompoundTag playerTag = playersList.getCompound(p);
                UUID uuid = playerTag.getUUID("UUID");
                NonNullList<ItemStack> inv = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
                ListTag itemsList = playerTag.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < itemsList.size(); i++) {
                    CompoundTag itemTag = itemsList.getCompound(i);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < SLOT_COUNT) {
                        inv.set(slot, ItemStack.parse(registries, itemTag.getCompound("Stack"))
                                .orElse(ItemStack.EMPTY));
                    }
                }
                playerInventories.put(uuid, inv);
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // 客户端只需要颜色信息，不需要玩家库存
        tag.putInt("colorSelection", colorSelection);
        return tag;
    }

    // ── GeckoLib ──

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            BlockState blockState = getBlockState();
            boolean openNow = blockState.hasProperty(MineChestBlock.OPEN) && blockState.getValue(MineChestBlock.OPEN);
            if (openNow != lastAnimatedOpen) {
                state.setAndContinue(openNow ? OPEN_ANIM : CLOSE_ANIM);
                lastAnimatedOpen = openNow;
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
