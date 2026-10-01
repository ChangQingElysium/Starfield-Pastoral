package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.block.mine.MineChestBlock;
import com.stardew.craft.block.mine.MineChestLidMotion;
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
        implements MenuProvider {

    private static final int SLOT_COUNT = 27;

    /** 每个玩家独立的库存 */
    private final Map<UUID, NonNullList<ItemStack>> playerInventories = new HashMap<>();

    private final MineChestLidMotion lidMotion = new MineChestLidMotion();
    private int openCount;
    private boolean lidInitialized;
    private int colorSelection = -1;
    /** Negative identity of this generated Skull Cavern chest; ordinary rewards use their floor. */
    private int rewardKey;

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
        claimKeyForFloor(floor);
        return playerInventories.computeIfAbsent(playerId, id -> {
            NonNullList<ItemStack> inv = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
            // 根据宝箱所在层数生成奖励，但如果玩家已在本存档领过该层，就不再给
            if (!hasClaimedReward(playerId, floor)) {
                ItemStack reward;
                if (floor>120) {
                    // Random and forced Skull Cavern treasure rooms share the 26-slot source pool.
                    // Include the generated chest identity: revisiting this instance preserves loot; new runs reroll.
                    long seed = ((long) floor * 341873128712L)
                            ^ playerId.getMostSignificantBits()
                            ^ playerId.getLeastSignificantBits()
                            ^ (worldPosition.asLong() * 132897987541L) ^ rewardKey;
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

    /** Each generated Skull chest has its own persisted identity, independent of other
     * chests, players, days and runs. Normal mine rewards remain once per floor. */
    private int claimKeyForFloor(int floor) {
        if(floor<=120)return floor;
        if(rewardKey==0 && level instanceof ServerLevel serverLevel) {
            rewardKey=MineRewardClaimManager.get(serverLevel).allocateTemporaryKey();
            var floors=com.stardew.craft.mining.MineFloorDataManager.get(serverLevel);
            var data=floors.getFloorData(floor);
            if(data!=null){data.addTreasureKey(rewardKey);floors.setFloorData(floor,data);}
            setChanged();syncToClient();
        }
        return rewardKey;
    }

    /**
     * 根据方块坐标反推矿井层数。
     */
    private int getFloorNumber() {
        return com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(worldPosition);
    }

    public void consumeStoryReward(net.minecraft.server.level.ServerPlayer player) {
        markRewardClaimed(player.getUUID(),getFloorNumber());
        playerInventories.remove(player.getUUID());setChanged();
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

    private boolean personalRewardChest() {
        return level != null && level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING
                && getFloorNumber() > 0;
    }

    public void startOpen(Player player) {
        if (player.isSpectator()) return;
        if (personalRewardChest() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var manager=MineRewardClaimManager.get(serverPlayer.serverLevel());
            if (!manager.hasOpened(player.getUUID(),claimKeyForFloor(getFloorNumber()))) {
                manager.markOpened(player.getUUID(),claimKeyForFloor(getFloorNumber()));
                serverPlayer.playNotifySound(ModSounds.OPEN_CHEST.get(),SoundSource.BLOCKS,.7f,1f);
            }
            manager.sync(serverPlayer);
            return;
        }
        openCount++;
        if (openCount == 1 && level != null) {
            level.playSound(null, worldPosition, ModSounds.OPEN_CHEST.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        updateOpenState();
    }

    public void stopOpen(Player player) {
        if (player.isSpectator() || personalRewardChest()) return;
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
                this::setColorSelection, getColorSelection(), personalRewardChest());
    }

    // ── NBT ──

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putInt("colorSelection", colorSelection);
        tag.putInt("rewardKey",rewardKey);

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
                itemTag.put("Stack", PortItemStacks.save(stack, registries));
                itemsList.add(itemTag);
            }
            playerTag.put("Items", itemsList);
            playersList.add(playerTag);
        }
        tag.put("PlayerInventories", playersList);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        colorSelection = tag.contains("colorSelection")
                ? WoodenChestColorPalette.clampIndex(tag.getInt("colorSelection")) : -1;
        rewardKey = tag.getInt("rewardKey");

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
                        inv.set(slot, PortItemStacks.parse(registries, itemTag.getCompound("Stack"))
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
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        // 客户端只需要颜色信息，不需要玩家库存
        tag.putInt("colorSelection", colorSelection);
        tag.putInt("rewardKey",rewardKey);
        return tag;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MineChestBlockEntity chest) {
        boolean open = chest.personalRewardChest()
                ? com.stardew.craft.client.mining.ClientMineRewardState.isOpen(chest.claimKeyForFloor(chest.getFloorNumber()))
                : state.getValue(MineChestBlock.OPEN);
        if (!chest.lidInitialized) { chest.lidMotion.snap(open); chest.lidInitialized=true; }
        chest.lidMotion.tick(open);
    }

    public float getLidAngle(float partialTick) {
        return lidMotion.angle(partialTick);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
