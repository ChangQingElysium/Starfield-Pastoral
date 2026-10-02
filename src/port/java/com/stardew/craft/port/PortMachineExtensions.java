package com.stardew.craft.port;

import com.stardew.craft.blockentity.UtilityAutomationAccess;
import com.stardew.craft.capability.UtilityAutomationCapabilities;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.HeavyTapperBlock;
import com.stardew.craft.port.net.neoforged.neoforge.capabilities.Capabilities;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge can query a block capability at an occupied extension with no block entity.
 * Forge hoppers and third-party pipes instead query the block entity at that position. This registry adds an
 * inventory-free, ticker-free bridge only to the machines with that existing ItemHandler.BLOCK registration.
 * The owner remains the only production/inventory entity; the extension exposes the owner's handler itself.
 */
public final class PortMachineExtensions {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PortBootstrap.NAMESPACE);
    public static final RegistryObject<BlockEntityType<ExtensionBlockEntity>> TYPE = TYPES.register(
            "port_machine_extension", () -> BlockEntityType.Builder.of(ExtensionBlockEntity::new,
                    automationExtensionBlocks()).build(null));
    private static boolean registered;

    private PortMachineExtensions() {}

    private static Block[] automationExtensionBlocks() {
        Block[] multiblocks = UtilityAutomationCapabilities.multiblockAutomationBlocks();
        // HeavyTapper has a separate block-capability registration and uses upper, not part.
        Block[] supported = Arrays.copyOf(multiblocks, multiblocks.length + 1);
        supported[multiblocks.length] = GingerIslandBlocks.get("ginger_heavy_tapper");
        return supported;
    }

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        TYPES.register(modBus);
    }

    /** Called only from the previously-null extension branch of machine EntityBlock factories. */
    public static @Nullable BlockEntity createExtension(BlockPos pos, BlockState state) {
        if (!isAutomationExtension(state)) return null;
        return new ExtensionBlockEntity(pos, state);
    }

    private static boolean isAutomationExtension(BlockState state) {
        if (!PortCapabilities.isBlockRegistered(Capabilities.ItemHandler.BLOCK, state.getBlock())) return false;
        if (state.getBlock() instanceof HeavyTapperBlock) return state.getValue(HeavyTapperBlock.UPPER);
        return state.getValues().entrySet().stream().anyMatch(entry -> entry.getKey().getName().equals("part")
                && entry.getValue() instanceof StringRepresentable part && part.getSerializedName().equals("extension"));
    }

    /** No Container, UtilityAutomationAccess, production state, ticker or renderer is added here. */
    public static final class ExtensionBlockEntity extends BlockEntity {
        private final Cached[] handlers = new Cached[7];
        private boolean capabilitiesValid = true;

        private record Cached(BlockEntity owner, LazyOptional<IItemHandler> source, LazyOptional<IItemHandler> exposed,
                              NonNullConsumer<LazyOptional<IItemHandler>> invalidationListener) {}

        private ExtensionBlockEntity(BlockPos pos, BlockState state) {
            super(TYPE.get(), pos, state);
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability != ForgeCapabilities.ITEM_HANDLER) return super.getCapability(capability, side);
            Level currentLevel = getLevel();
            int index = side == null ? 6 : side.ordinal();
            if (currentLevel == null || isRemoved() || !capabilitiesValid
                    || !isAutomationExtension(currentLevel.getBlockState(worldPosition))) {
                invalidateHandler(index);
                return LazyOptional.empty();
            }
            BlockState state = currentLevel.getBlockState(worldPosition);
            BlockPos mainPos = UtilityAutomationCapabilities.resolveMainPos(currentLevel, worldPosition, state);
            // Never recurse into this bridge, or accept another extension as an inventory owner.
            BlockEntity owner = mainPos.equals(worldPosition) ? null : currentLevel.getBlockEntity(mainPos);
            if (!(owner instanceof UtilityAutomationAccess) || owner.isRemoved()) {
                invalidateHandler(index);
                return LazyOptional.empty();
            }
            LazyOptional<IItemHandler> source = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            IItemHandler handler = source.resolve().orElse(null);
            if (handler == null) {
                invalidateHandler(index);
                return LazyOptional.empty();
            }
            Cached cached = handlers[index];
            if (cached != null && cached.owner == owner && cached.source == source) return cached.exposed.cast();
            invalidateHandler(index);
            LazyOptional<IItemHandler> exposed = LazyOptional.of(() -> handler);
            NonNullConsumer<LazyOptional<IItemHandler>> invalidationListener = ignored -> {
                if (handlers[index] != null && handlers[index].exposed == exposed) handlers[index] = null;
                exposed.invalidate();
            };
            handlers[index] = new Cached(owner, source, exposed, invalidationListener);
            source.addListener(invalidationListener);
            return exposed.cast();
        }

        private void invalidateHandler(int index) {
            Cached cached = handlers[index];
            handlers[index] = null;
            if (cached != null) {
                // Avoid retaining unloaded bridges while a horizontal owner lives in an adjacent loaded chunk.
                if (cached.source.isPresent()) cached.source.removeListener(cached.invalidationListener);
                cached.exposed.invalidate();
            }
        }

        void invalidateHandlers() {
            for (int index = 0; index < handlers.length; index++) invalidateHandler(index);
        }

        @Override
        public void invalidateCaps() {
            capabilitiesValid = false;
            super.invalidateCaps();
            invalidateHandlers();
        }

        @Override
        public void reviveCaps() {
            super.reviveCaps();
            capabilitiesValid = true;
        }
    }
}
