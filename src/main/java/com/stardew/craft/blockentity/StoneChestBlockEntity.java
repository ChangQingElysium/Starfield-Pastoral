package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.block.utility.StoneChestBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.menu.StoneChestMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class StoneChestBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, MenuProvider, GeoBlockEntity, com.stardew.craft.inventory.ChestStorage {
    private static final String TAG_ITEMS = "items";
    private static final String TAG_COLOR_SELECTION = "colorSelection";
    private static final int SLOT_COUNT = com.stardew.craft.menu.ChestMenuLayout.NORMAL_CAPACITY;
    // Old saves had 54 slots. Retain the last 18 for withdrawal, never accept new deposits.
    private static final int LEGACY_SLOT_COUNT = 54;

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("OPEN");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("CLOSE");

    private final NonNullList<ItemStack> items = NonNullList.withSize(LEGACY_SLOT_COUNT, ItemStack.EMPTY);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int openCount;
    private boolean lastAnimatedOpen;
    private int colorSelection = -1;

    public StoneChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_CHEST.get(), pos, state);
    }

    @Override public boolean isInUse() { return openCount > 0; }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        clearContent();
    }

    public int getColorSelection() {
        return colorSelection;
    }

    public void setColorSelection(int selection) {
        int clamped = WoodenChestColorPalette.clampIndex(selection);
        if (colorSelection == clamped) {
            return;
        }
        colorSelection = clamped;
        setChanged();
        syncToClient();
    }

    @Override
    public int getContainerSize() {
        for (int slot = SLOT_COUNT; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty()) return LEGACY_SLOT_COUNT;
        }
        return SLOT_COUNT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= items.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int removed = Math.min(amount, stack.getCount());
        ItemStack out = stack.copy();
        out.setCount(removed);

        if (removed >= stack.getCount()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            stack.shrink(removed);
        }

        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }

        if (slot >= SLOT_COUNT && !stack.isEmpty()) {
            ItemStack previous = items.get(slot);
            if (!ItemStack.isSameItemSameTags(previous, stack) || stack.getCount() > previous.getCount()) return;
        }

        if (stack.isEmpty()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            ItemStack copy = stack.copy();
            copy.setCount(Math.min(copy.getCount(), copy.getMaxStackSize()));
            items.set(slot, copy);
        }

        setChanged();
        syncToClient();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
        syncToClient();
    }

    @Override
    public void startOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount++;
        if (openCount == 1 && level != null) {
            level.playSound(null, worldPosition, ModSounds.OPEN_CHEST.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        updateOpenState();
    }

    @Override
    public void stopOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount = Math.max(0, openCount - 1);
        if (openCount == 0 && level != null) {
            level.playSound(null, worldPosition, ModSounds.DOOR_CREAK_REVERSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        updateOpenState();
    }

    @Override public void onLoad() {
        super.onLoad();
        updateOpenState();
    }

    private void updateOpenState() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(StoneChestBlock.OPEN)) {
            return;
        }

        boolean openNow = openCount > 0;
        if (state.getValue(StoneChestBlock.OPEN) != openNow) {
            currentLevel.setBlock(worldPosition, state.setValue(StoneChestBlock.OPEN, openNow), 3);
        }
    }

    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 11);
        if (currentLevel instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.stardew_craft.stone_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StoneChestMenu(containerId, playerInventory, this, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);

        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Stack", PortItemStacks.save(stack, registries));
            list.add(entry);
        }
        tag.put(TAG_ITEMS, list);
        tag.putInt(TAG_COLOR_SELECTION, colorSelection);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);

        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        if (tag.contains(TAG_ITEMS, 9)) {
            ListTag list = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= items.size()) {
                    continue;
                }
                ItemStack parsed = PortItemStacks.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                items.set(slot, parsed);
            }
        }

        if (tag.contains(TAG_COLOR_SELECTION)) {
            colorSelection = WoodenChestColorPalette.clampIndex(tag.getInt(TAG_COLOR_SELECTION));
        } else {
            colorSelection = -1;
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
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            BlockState blockState = getBlockState();
            boolean openNow = blockState.hasProperty(StoneChestBlock.OPEN) && blockState.getValue(StoneChestBlock.OPEN);
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

    @SuppressWarnings("null")
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
