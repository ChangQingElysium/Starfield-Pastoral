package com.stardew.craft.platform.fabric.capability;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.fluid.IFluidHandler;
import com.stardew.craft.platform.fabric.fluid.transfer.FluidHandlerStorage;
import com.stardew.craft.platform.fabric.item.IItemHandler;
import com.stardew.craft.platform.fabric.item.transfer.ItemHandlerStorage;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Registration installs real sided Transfer providers, including multi-provider block chains. */
public final class RegisterCapabilitiesEvent extends Event implements ModBusEvent {
    private final BlockApiLookup<Storage<ItemVariant>, Direction> items;
    private final BlockApiLookup<Storage<FluidVariant>, Direction> fluids;

    public RegisterCapabilitiesEvent() {
        this(ItemStorage.SIDED, FluidStorage.SIDED);
    }

    RegisterCapabilitiesEvent(BlockApiLookup<Storage<ItemVariant>, Direction> items,
            BlockApiLookup<Storage<FluidVariant>, Direction> fluids) {
        this.items = Objects.requireNonNull(items, "items");
        this.fluids = Objects.requireNonNull(fluids, "fluids");
    }

    public <T, B extends BlockEntity> void registerBlockEntity(BlockCapability<T, Direction> capability,
            BlockEntityType<B> type, BiFunction<? super B, Direction, ? extends T> provider) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        // Vanilla exposes isValid publicly. Iterating the completed registry
        // avoids a private field accessor and registers each block only once.
        boolean foundBlock = false;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (type.isValid(block.defaultBlockState())) {
                foundBlock = true;
                registerBlock(capability, (level, position, state, blockEntity, side) -> {
                    if (blockEntity == null || blockEntity.getType() != type) return null;
                    @SuppressWarnings("unchecked") B typed = (B) blockEntity;
                    return provider.apply(typed, side);
                }, block);
            }
        }
        if (!foundBlock) {
            throw new IllegalArgumentException("Capability block entity type has no registered blocks: "
                    + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type));
        }
    }

    public <T> void registerBlock(BlockCapability<T, Direction> capability,
            IBlockCapabilityProvider<T, Direction> provider, Block... blocks) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(provider, "provider");
        if (blocks.length == 0) throw new IllegalArgumentException("At least one capability block is required");
        for (Block block : blocks) {
            Objects.requireNonNull(block, "block");
            if (capability.register(block, provider)) install(capability, block);
        }
    }

    private <T> void install(BlockCapability<T, Direction> capability, Block block) {
        if (capability.kind == BlockCapability.Kind.ITEM) {
            items.registerForBlocks((level, position, state, blockEntity, side) -> new ResolvedStorage<>(() ->
                    ItemHandlerStorage.of((IItemHandler) capability.getCapability(level, position,
                            level.getBlockState(position), level.getBlockEntity(position), side))), block);
        } else {
            fluids.registerForBlocks((level, position, state, blockEntity, side) -> new ResolvedStorage<>(() ->
                    FluidHandlerStorage.of((IFluidHandler) capability.getCapability(level, position,
                            level.getBlockState(position), level.getBlockEntity(position), side))), block);
        }
    }

    /**
     * Re-resolve on use, so binding a shared chest owner cannot leave a cached
     * empty/unguarded storage. A denied provider stays non-null and prevents the
     * generic FAPI Container fallback from exposing an unbound Junimo chest.
     */
    private record ResolvedStorage<V>(Supplier<Storage<V>> resolve) implements Storage<V> {
        @Override public boolean supportsInsertion() { return resolve.get().supportsInsertion(); }
        @Override public boolean supportsExtraction() { return resolve.get().supportsExtraction(); }
        @Override public long insert(V variant, long amount, TransactionContext transaction) {
            return resolve.get().insert(variant, amount, transaction);
        }
        @Override public long extract(V variant, long amount, TransactionContext transaction) {
            return resolve.get().extract(variant, amount, transaction);
        }
        @Override public Iterator<StorageView<V>> iterator() { return resolve.get().iterator(); }
    }
}
