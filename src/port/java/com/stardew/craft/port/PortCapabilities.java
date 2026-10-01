package com.stardew.craft.port;

import com.stardew.craft.port.net.neoforged.neoforge.capabilities.BlockCapability;
import com.stardew.craft.port.net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import com.stardew.craft.port.net.neoforged.neoforge.capabilities.ICapabilityProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge block capabilities on Forge.
 * <ul>
 * <li>{@link #getCapability(Level, BlockCapability, BlockPos, Object)} replaces {@code level.getCapability(cap, pos, ctx)}:
 * registered providers first (in registration order), then the block entity's own Forge capability.</li>
 * <li>Every block entity whose type or block has a registration gets a Forge capability provider attached, so
 * hoppers, pipes and other mods see the same handlers through {@code BlockEntity#getCapability}.</li>
 * </ul>
 * Blocks without a block entity cannot carry Forge capabilities; their registrations are only visible through
 * {@link #getCapability}.
 */
public final class PortCapabilities {
    private static final ResourceLocation PROVIDER_ID = new ResourceLocation(PortBootstrap.NAMESPACE, "block_capabilities");
    private static final List<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();
    private static final Map<BlockEntity, Provider> PROVIDERS = Collections.synchronizedMap(new WeakHashMap<>());

    private PortCapabilities() {}

    private record Registration(BlockCapability<?, ?> capability, @Nullable Set<Block> blocks,
                                @Nullable BlockEntityType<?> blockEntityType, Object provider) {
        boolean applies(BlockCapability<?, ?> cap, BlockState state, @Nullable BlockEntity blockEntity) {
            if (capability != cap) return false;
            if (blocks != null) return blocks.contains(state.getBlock());
            return blockEntity != null && blockEntity.getType() == blockEntityType;
        }

        boolean appliesTo(Block block, BlockEntityType<?> type) {
            return blocks != null ? blocks.contains(block) : type == blockEntityType;
        }
    }

    public static <T, C> void registerBlock(BlockCapability<T, C> capability, IBlockCapabilityProvider<T, C> provider, Block... blocks) {
        REGISTRATIONS.add(new Registration(capability, Set.of(blocks), null, provider));
    }

    public static <T, C, BE extends BlockEntity> void registerBlockEntity(BlockCapability<T, C> capability, BlockEntityType<BE> type,
            ICapabilityProvider<? super BE, C, T> provider) {
        REGISTRATIONS.add(new Registration(capability, null, type, provider));
    }

    public static boolean isBlockRegistered(BlockCapability<?, ?> capability, Block block) {
        for (Registration registration : REGISTRATIONS) {
            if (registration.capability == capability && registration.blocks != null && registration.blocks.contains(block)) return true;
        }
        return false;
    }

    /** Call-site replacement for NeoForge {@code Level#getCapability(BlockCapability, BlockPos, C)}. */
    @Nullable
    public static <T, C> T getCapability(Level level, BlockCapability<T, C> capability, BlockPos pos, C context) {
        return getCapability(capability, level, pos, null, null, context);
    }

    /** Call-site replacement for NeoForge {@code Level#getCapability(BlockCapability, BlockPos, BlockState, BlockEntity, C)}. */
    @Nullable
    public static <T, C> T getCapability(Level level, BlockCapability<T, C> capability, BlockPos pos,
            @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context) {
        return getCapability(capability, level, pos, state, blockEntity, context);
    }

    @Nullable
    public static <T, C> T getCapability(BlockCapability<T, C> capability, Level level, BlockPos pos,
            @Nullable BlockState state, @Nullable BlockEntity blockEntity, C context) {
        if (state == null) state = level.getBlockState(pos);
        if (blockEntity == null && state.hasBlockEntity()) blockEntity = level.getBlockEntity(pos);
        T registered = resolveRegistered(capability, level, pos, state, blockEntity, context);
        if (registered != null || blockEntity == null) return registered;
        return blockEntity.getCapability(capability.forgeCapability(), context instanceof Direction side ? side : null)
                .resolve().orElse(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static <T, C> T resolveRegistered(BlockCapability<T, C> capability, Level level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, C context) {
        for (Registration registration : REGISTRATIONS) {
            if (!registration.applies(capability, state, blockEntity)) continue;
            T value;
            if (registration.blocks != null) {
                value = ((IBlockCapabilityProvider<T, C>) registration.provider).getCapability(level, pos, state, blockEntity, context);
            } else {
                value = (T) ((ICapabilityProvider) registration.provider).getCapability(blockEntity, context);
            }
            if (value != null) return value;
        }
        return null;
    }

    @SubscribeEvent
    public static void attachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity blockEntity = event.getObject();
        Block block = blockEntity.getBlockState().getBlock();
        List<BlockCapability<?, ?>> capabilities = new ArrayList<>();
        for (Registration registration : REGISTRATIONS) {
            if (registration.appliesTo(block, blockEntity.getType()) && registration.capability.contextClass() == Direction.class
                    && !capabilities.contains(registration.capability)) {
                capabilities.add(registration.capability);
            }
        }
        if (capabilities.isEmpty()) return;
        Provider provider = new Provider(blockEntity, capabilities);
        event.addCapability(PROVIDER_ID, provider);
        event.addListener(provider::invalidate);
        PROVIDERS.put(blockEntity, provider);
    }

    /**
     * Call-site replacement for NeoForge {@code Level#invalidateCapabilities(BlockPos)}: handlers previously handed out
     * for the block entity at {@code pos} are invalidated, so Forge consumers holding a {@link LazyOptional} re-query.
     */
    public static void invalidateCapabilities(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Provider provider = blockEntity == null ? null : PROVIDERS.get(blockEntity);
        if (provider != null) provider.invalidate();
    }

    /** Exposes registered block capabilities through Forge's capability system. */
    private static final class Provider implements net.minecraftforge.common.capabilities.ICapabilityProvider {
        private final BlockEntity blockEntity;
        private final List<BlockCapability<?, ?>> capabilities;
        private final Map<Capability<?>, Cached[]> cache = new HashMap<>();

        private record Cached(Object value, LazyOptional<?> optional) {}

        Provider(BlockEntity blockEntity, List<BlockCapability<?, ?>> capabilities) {
            this.blockEntity = blockEntity;
            this.capabilities = capabilities;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <X> @NotNull LazyOptional<X> getCapability(@NotNull Capability<X> cap, @Nullable Direction side) {
            Level level = blockEntity.getLevel();
            if (level == null || blockEntity.isRemoved()) return LazyOptional.empty();
            for (BlockCapability<?, ?> capability : capabilities) {
                if (capability.forgeCapability() != cap) continue;
                Object value = resolveRegistered((BlockCapability<Object, Direction>) capability, level, blockEntity.getBlockPos(),
                        blockEntity.getBlockState(), blockEntity, side);
                return (LazyOptional<X>) cached(cap, side, value);
            }
            return LazyOptional.empty();
        }

        private synchronized LazyOptional<?> cached(Capability<?> cap, @Nullable Direction side, @Nullable Object value) {
            Cached[] slots = cache.computeIfAbsent(cap, ignored -> new Cached[7]);
            int index = side == null ? 6 : side.ordinal();
            Cached current = slots[index];
            if (current != null && current.value == value) return current.optional;
            if (current != null) current.optional.invalidate();
            if (value == null) {
                slots[index] = null;
                return LazyOptional.empty();
            }
            LazyOptional<?> optional = LazyOptional.of(() -> value);
            slots[index] = new Cached(value, optional);
            return optional;
        }

        synchronized void invalidate() {
            for (Cached[] slots : cache.values()) {
                for (Cached cached : slots) if (cached != null) cached.optional.invalidate();
            }
            cache.clear();
        }
    }
}
