package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.block.utility.WoodenChestBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.menu.WoodenChestMenu;
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
public class WoodenChestBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, MenuProvider, GeoBlockEntity, com.stardew.craft.inventory.ChestStorage {
    private static final String TAG_ITEMS = "items";
    private static final String TAG_COLOR_SELECTION = "colorSelection";
    private static final int SLOT_COUNT = com.stardew.craft.menu.ChestMenuLayout.NORMAL_CAPACITY;

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("OPEN");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("CLOSE");

    private final NonNullList<ItemStack> localItems;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int openCount;
    private boolean lastAnimatedOpen;
    private int colorSelection = -1;

    public WoodenChestBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.WOODEN_CHEST.get(), pos, state, SLOT_COUNT);
    }

    protected WoodenChestBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                     BlockPos pos, BlockState state, int capacity) {
        super(type, pos, state);
        this.localItems = NonNullList.withSize(capacity, ItemStack.EMPTY);
    }

    protected NonNullList<ItemStack> inventoryItems() { return localItems; }

    public com.stardew.craft.block.utility.ChestVariant variant() {
        return com.stardew.craft.block.utility.ChestVariant.of(getBlockState().getBlock());
    }

    @Override public boolean isInUse() { return openCount > 0; }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SimpleContainer container = new SimpleContainer(inventoryItems().toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        clearContent();
    }

    public int getColorSelection() {
        return colorSelection;
    }

    public void setColorSelection(int selection) {
        int clamped = variant().dyeable ? WoodenChestColorPalette.clampIndex(selection) : -1;
        if (colorSelection == clamped) {
            return;
        }
        colorSelection = clamped;
        setChanged();
        syncToClient();
    }

    @Override
    public int getContainerSize() {
        return inventoryItems().size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventoryItems()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= inventoryItems().size()) {
            return ItemStack.EMPTY;
        }
        return inventoryItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= inventoryItems().size() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = inventoryItems().get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int removed = Math.min(amount, stack.getCount());
        ItemStack out = stack.copy();
        out.setCount(removed);

        if (removed >= stack.getCount()) {
            inventoryItems().set(slot, ItemStack.EMPTY);
        } else {
            stack.shrink(removed);
        }

        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= inventoryItems().size()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = inventoryItems().get(slot);
        inventoryItems().set(slot, ItemStack.EMPTY);
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventoryItems().size()) {
            return;
        }

        if (stack.isEmpty()) {
            inventoryItems().set(slot, ItemStack.EMPTY);
        } else {
            ItemStack copy = stack.copy();
            copy.setCount(Math.min(copy.getCount(), copy.getMaxStackSize()));
            inventoryItems().set(slot, copy);
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
        for (int i = 0; i < inventoryItems().size(); i++) {
            inventoryItems().set(i, ItemStack.EMPTY);
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
        if (!state.hasProperty(WoodenChestBlock.OPEN)) {
            return;
        }

        boolean openNow = openCount > 0;
        if (state.getValue(WoodenChestBlock.OPEN) != openNow) {
            currentLevel.setBlock(worldPosition, state.setValue(WoodenChestBlock.OPEN, openNow), 3);
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
        return Component.translatable("block.stardewcraft." + variant().id);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WoodenChestMenu(containerId, playerInventory, this, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);

        ListTag list = new ListTag();
        for (int i = 0; i < localItems.size(); i++) {
            ItemStack stack = localItems.get(i);
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

        for (int i = 0; i < localItems.size(); i++) {
            localItems.set(i, ItemStack.EMPTY);
        }

        if (tag.contains(TAG_ITEMS, 9)) {
            ListTag list = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= localItems.size()) {
                    continue;
                }
                ItemStack parsed = PortItemStacks.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                localItems.set(slot, parsed);
            }
        }

        if (tag.contains(TAG_COLOR_SELECTION)) {
            colorSelection = variant().dyeable ? WoodenChestColorPalette.clampIndex(tag.getInt(TAG_COLOR_SELECTION)) : -1;
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
            boolean openNow = blockState.hasProperty(WoodenChestBlock.OPEN) && blockState.getValue(WoodenChestBlock.OPEN);
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
