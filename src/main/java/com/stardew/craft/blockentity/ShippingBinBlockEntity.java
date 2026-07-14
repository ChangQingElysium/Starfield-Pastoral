package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.ShippingBinBlock;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.menu.ShippingBinMenu;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

@SuppressWarnings("null")
public class ShippingBinBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements Container, MenuProvider, GeoBlockEntity {
    private static final String TAG_ITEMS = "items";
    private static final String TAG_BUFFER_DAY = "bufferDay";
    private static final int SLOT_COUNT = 1;
    private static final int PROXIMITY_CHECK_INTERVAL = 10;

    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("close");

    /** 所有已加载的出货箱实例，用于夜间结算时统一 flush buffer */
    private static final java.util.Set<ShippingBinBlockEntity> LOADED_BINS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int openCount;
    private boolean nearbyOpen;
    private boolean lastAnimatedOpen;
    private int pendingCloseStepTicks;
    private int pendingShipSoundTicks;
    private int bufferAbsoluteDay = -1;
    @Nullable
    private UUID lastInteractorId;

    public ShippingBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPPING_BIN.get(), pos, state);
        LOADED_BINS.add(this);
    }

    /**
     * 夜间结算时调用：将所有出货箱 buffer 中剩余物品记录到出货追踪器。
     */
    public static void flushAllForOvernight() {
        for (ShippingBinBlockEntity bin : new java.util.ArrayList<>(LOADED_BINS)) {
            bin.flushBufferForOvernight();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShippingBinBlockEntity be) {
        be.flushExpiredBufferIfNeeded();

        if (be.pendingCloseStepTicks > 0) {
            be.pendingCloseStepTicks--;
            if (be.pendingCloseStepTicks == 0 && !be.nearbyOpen && be.openCount <= 0) {
                level.playSound(null, pos, ModSounds.WOODY_STEP.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            }
        }

        if (be.pendingShipSoundTicks > 0) {
            be.pendingShipSoundTicks--;
            if (be.pendingShipSoundTicks == 0) {
                level.playSound(null, pos, ModSounds.SHIP.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            }
        }

        if (level.getGameTime() % PROXIMITY_CHECK_INTERVAL != 0) {
            return;
        }
        AABB lidOpenArea = new AABB(
            pos.getX() - 1.0D,
            pos.getY(),
            pos.getZ() - 1.0D,
            pos.getX() + 3.0D,
            pos.getY() + 2.5D,
            pos.getZ() + 3.0D
        );
        boolean shouldOpenByProximity = !level.getEntitiesOfClass(Player.class, lidOpenArea, p -> !p.isSpectator()).isEmpty();
        if (be.nearbyOpen != shouldOpenByProximity) {
            be.nearbyOpen = shouldOpenByProximity;
            be.refreshOpenState();
        }

        boolean isOpen = state.hasProperty(ShippingBinBlock.OPEN) && state.getValue(ShippingBinBlock.OPEN);
        if (isOpen) {
            // Pixel-perfect inner void of the bin: X/Z 1 to 15 pixels, Y 1 to 10 pixels.
            AABB swallowArea = new AABB(
                pos.getX() + (1.0D / 16.0D), pos.getY() + (1.0D / 16.0D), pos.getZ() + (1.0D / 16.0D),
                pos.getX() + (15.0D / 16.0D), pos.getY() + (10.0D / 16.0D), pos.getZ() + (15.0D / 16.0D)
            );
            java.util.List<net.minecraft.world.entity.item.ItemEntity> itemEntities = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, swallowArea, net.minecraft.world.entity.Entity::isAlive
            );
            for (net.minecraft.world.entity.item.ItemEntity itemEntity : itemEntities) {
                ItemStack stack = itemEntity.getItem();
                if (canShip(stack)) {
                    be.swallowItemEntity(itemEntity);
                }
            }
        }
    }

    public void swallowItemEntity(net.minecraft.world.entity.item.ItemEntity entity) {
        if (level == null || level.isClientSide) return;
        Player nearest = level.getNearestPlayer(entity, 8.0D);
        if (nearest instanceof ServerPlayer serverPlayer) {
            lastInteractorId = serverPlayer.getUUID();
        }
        ItemStack incoming = entity.getItem().copy();
        pushToBufferSlot(incoming);
        entity.discard();
        level.playSound(null, worldPosition, ModSounds.BACKPACK_IN.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        pendingShipSoundTicks = 5;
    }

    public void pushToBufferSlot(ItemStack newStack) {
        if (level == null || level.isClientSide) return;
        ItemStack oldStack = items.get(0);
        if (!oldStack.isEmpty()) {
            // SDV parity: 不立即结算，只记录到夜间结算追踪器
            if (!recordShippingForOwner(oldStack, availableDayForCurrentBuffer())) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), oldStack);
            }
        }
        items.set(0, newStack);
        bufferAbsoluteDay = newStack.isEmpty() ? -1 : currentAbsoluteDay();
        setChanged();
        syncToClient();
    }

    public static boolean canShip(ItemStack stack) {
        return !stack.isEmpty() && StardewItemDataApi.getSellPrice(stack) > 0;
    }

    /**
     * 夜间结算前将 buffer 中剩余的物品记录到出货追踪器。
     * 由 StardewTimeManager 在 advanceDay 时调用。
     */
    public void flushBufferForOvernight() {
        if (level == null || level.isClientSide) return;
        ItemStack remaining = items.get(0);
        if (remaining.isEmpty()) return;
        if (recordShippingForOwner(remaining, Math.max(currentAbsoluteDay(), availableDayForCurrentBuffer()))) {
            items.set(0, ItemStack.EMPTY);
            bufferAbsoluteDay = -1;
            setChanged();
            syncToClient();
        }
    }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        clearContent();
    }

    public boolean depositFromPlayer(Player player, ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) {
            return false;
        }

        flushExpiredBufferIfNeeded();
        ItemStack incoming = stack.copy();
        if (player instanceof ServerPlayer serverPlayer) {
            lastInteractorId = serverPlayer.getUUID();
        }
        pushToBufferSlot(incoming);
        level.playSound(null, worldPosition, ModSounds.BACKPACK_IN.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        pendingShipSoundTicks = 5;
        return true;
    }

    private void refreshOpenState() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(ShippingBinBlock.OPEN)) {
            return;
        }

        boolean shouldOpen = openCount > 0 || nearbyOpen;
        boolean wasOpen = state.getValue(ShippingBinBlock.OPEN);
        if (wasOpen == shouldOpen) {
            return;
        }

        currentLevel.setBlock(worldPosition, state.setValue(ShippingBinBlock.OPEN, shouldOpen), 3);
        if (shouldOpen) {
            currentLevel.playSound(null, worldPosition, ModSounds.DOOR_CREAK.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            pendingCloseStepTicks = 0;
        } else {
            currentLevel.playSound(null, worldPosition, ModSounds.DOOR_CREAK_REVERSE.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            pendingCloseStepTicks = 5;
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
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }
        return items.get(0);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = items.get(0);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int removed = Math.min(amount, stack.getCount());
        ItemStack out = stack.copy();
        out.setCount(removed);

        if (removed >= stack.getCount()) {
            items.set(0, ItemStack.EMPTY);
            bufferAbsoluteDay = -1;
        } else {
            stack.shrink(removed);
        }

        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = items.get(0);
        items.set(0, ItemStack.EMPTY);
        bufferAbsoluteDay = -1;
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }

        ItemStack sanitized = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!sanitized.isEmpty()) {
            sanitized.setCount(Math.min(sanitized.getCount(), sanitized.getMaxStackSize()));
        }

        items.set(0, sanitized);
        bufferAbsoluteDay = sanitized.isEmpty() ? -1 : currentAbsoluteDay();
        setChanged();
        syncToClient();
    }

    private void flushExpiredBufferIfNeeded() {
        if (level == null || level.isClientSide) return;
        ItemStack remaining = items.get(0);
        if (remaining.isEmpty()) {
            if (bufferAbsoluteDay != -1) {
                bufferAbsoluteDay = -1;
                setChanged();
            }
            return;
        }

        int today = currentAbsoluteDay();
        if (bufferAbsoluteDay < 0) {
            bufferAbsoluteDay = today;
            setChanged();
            return;
        }
        if (today <= bufferAbsoluteDay) {
            return;
        }

        if (recordShippingForOwner(remaining, Math.max(today, bufferAbsoluteDay + 1))) {
            items.set(0, ItemStack.EMPTY);
            bufferAbsoluteDay = -1;
            setChanged();
            syncToClient();
            settleAvailableShippingForOnlineOwner();
        }
    }

    private boolean recordShippingForOwner(ItemStack stack, int availableDay) {
        if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
            return false;
        }

        ServerPlayer payer = resolvePayoutPlayer();
        UUID payerId = payer != null ? payer.getUUID() : lastInteractorId;
        if (payerId == null) {
            return false;
        }
        lastInteractorId = payerId;

        SellQuote quote;
        if (payer != null) {
            quote = ProfessionSellPriceService.quoteItem(payer, stack, SellSource.SHIPPING_BIN);
        } else {
            PlayerStardewData data = PlayerDataManager.getPlayerData(payerId);
            quote = ProfessionSellPriceService.quoteItem(data, stack, SellSource.SHIPPING_BIN);
        }
        if (!quote.sellable() || quote.finalUnitPrice() <= 0) {
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        OvernightSettlementTracker.recordShipping(server, payerId, stack, quote.finalUnitPrice(), availableDay);
        return true;
    }

    private void settleAvailableShippingForOnlineOwner() {
        if (!(level instanceof ServerLevel serverLevel) || lastInteractorId == null) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(lastInteractorId);
        if (player == null) {
            return;
        }
        com.stardew.craft.network.overnight.OvernightSettlementPayload payload =
            OvernightSettlementTracker.consumePayload(player);
        com.stardew.craft.player.PlayerStardewDataAPI.recordOvernightShippedItems(player, payload.shippedItems());
    }

    private int availableDayForCurrentBuffer() {
        return bufferAbsoluteDay >= 0 ? bufferAbsoluteDay + 1 : currentAbsoluteDay() + 1;
    }

    private static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return time == null ? 1 : time.getAbsoluteDay();
    }

    @Nullable
    private ServerPlayer resolvePayoutPlayer() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (lastInteractorId != null) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(lastInteractorId);
            if (owner != null) {
                return owner;
            }
        }
        Player nearest = serverLevel.getNearestPlayer(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 8.0D, false);
        return nearest instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
        syncToClient();
    }

    @Override
    public void startOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount++;
        if (player instanceof ServerPlayer serverPlayer) {
            lastInteractorId = serverPlayer.getUUID();
        }
        refreshOpenState();
    }

    @Override
    public void stopOpen(Player player) {
        if (player.isSpectator()) {
            return;
        }
        openCount = Math.max(0, openCount - 1);
        refreshOpenState();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.stardew_craft.shipping_bin");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ShippingBinMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();
        ItemStack stack = items.get(0);
        if (!stack.isEmpty()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", 0);
            entry.put("Stack", stack.save(registries));
            list.add(entry);
        }
        tag.put(TAG_ITEMS, list);
        if (bufferAbsoluteDay >= 0) {
            tag.putInt(TAG_BUFFER_DAY, bufferAbsoluteDay);
        }
        if (lastInteractorId != null) {
            tag.putUUID("lastInteractorId", lastInteractorId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.set(0, ItemStack.EMPTY);

        if (tag.contains(TAG_ITEMS, 9)) {
            ListTag list = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.getInt("Slot") == 0) {
                    ItemStack parsed = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                    items.set(0, parsed);
                }
            }
        }
        bufferAbsoluteDay = tag.contains(TAG_BUFFER_DAY, Tag.TAG_INT) ? tag.getInt(TAG_BUFFER_DAY) : -1;

        if (tag.hasUUID("lastInteractorId")) {
            lastInteractorId = tag.getUUID("lastInteractorId");
        } else {
            lastInteractorId = null;
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
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            BlockState blockState = getBlockState();
            boolean openNow = blockState.hasProperty(ShippingBinBlock.OPEN) && blockState.getValue(ShippingBinBlock.OPEN);
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
