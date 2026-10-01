package com.stardew.craft.integration.jade;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import snownee.jade.Jade;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.HierarchyLookup;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.util.CommonProxy;
import snownee.jade.util.WailaExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * PORT(1.20.1): Jade 15 (1.21.1) keys block server-data providers by {@link Block} class and requests
 * server data for blocks without a block entity; Jade 11 (1.20.1) only keys them by block entity class
 * and never requests data without one. The mod's providers rely on the Jade 15 behaviour (crops,
 * saplings, upper halves of two-block machines), so they are registered here and merged into Jade 11's
 * request path by {@code PortJadeBlockAccessorClientHandlerMixin} / {@code PortJadeBlockAccessorImplMixin}
 * exactly like Jade 15's {@code PairHierarchyLookup#getMerged(block, blockEntity)}.
 */
public final class JadeBlockDataProviders {
    private static final HierarchyLookup<IServerDataProvider<BlockAccessor>> BLOCK_PROVIDERS =
            new HierarchyLookup<>(Block.class);
    private static final Comparator<IServerDataProvider<BlockAccessor>> PRIORITY =
            Comparator.comparingInt(WailaCommonRegistration.INSTANCE.priorities::byValue);

    private JadeBlockDataProviders() {
    }

    /** Jade 15 {@code registerBlockDataProvider(provider, Class<? extends Block>)}. */
    public static void register(IWailaCommonRegistration registration,
                                IServerDataProvider<BlockAccessor> provider,
                                Class<? extends Block> blockClass) {
        BLOCK_PROVIDERS.register(blockClass, provider);
    }

    public static boolean hasBlockProviders(Block block) {
        return !BLOCK_PROVIDERS.get(block).isEmpty();
    }

    /** Jade 15 {@code WailaCommonRegistration#getBlockNBTProviders(block, blockEntity)}. */
    public static List<IServerDataProvider<BlockAccessor>> merged(Block block, @Nullable BlockEntity blockEntity) {
        List<IServerDataProvider<BlockAccessor>> first = BLOCK_PROVIDERS.get(block);
        if (blockEntity == null) {
            return first;
        }
        List<IServerDataProvider<BlockAccessor>> second =
                WailaCommonRegistration.INSTANCE.getBlockNBTProviders(blockEntity);
        if (first.isEmpty()) {
            return second;
        }
        return second.isEmpty() ? first : ImmutableList.sortedCopyOf(PRIORITY, Iterables.concat(first, second));
    }

    /**
     * Jade 11 {@code BlockAccessorImpl#handleRequest} with Jade 15's provider lookup: block-keyed providers run
     * with or without a block entity. Distance/loaded checks and the response format stay Jade 11's.
     */
    public static void handleRequest(FriendlyByteBuf buf, ServerPlayer player, Consumer<Runnable> executor,
                                     Consumer<CompoundTag> responseSender) {
        BlockAccessor accessor;
        try {
            accessor = BlockAccessorImpl.fromNetwork(buf, player);
        } catch (Exception e) {
            WailaExceptionHandler.handleErr(e, null, null);
            return;
        }
        executor.accept(() -> {
            BlockPos pos = accessor.getPosition();
            ServerLevel world = player.serverLevel();
            if (pos.distSqr(player.blockPosition()) > Jade.MAX_DISTANCE_SQR || !world.isLoaded(pos)) {
                return;
            }
            BlockEntity tile = accessor.getBlockEntity();
            List<IServerDataProvider<BlockAccessor>> providers = merged(accessor.getBlock(), tile);
            if (providers.isEmpty()) {
                return;
            }
            CompoundTag tag = accessor.getServerData();
            for (IServerDataProvider<BlockAccessor> provider : providers) {
                try {
                    provider.appendServerData(tag, accessor);
                } catch (Exception e) {
                    WailaExceptionHandler.handleErr(e, provider, null);
                }
            }
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            if (tile != null) {
                tag.putString("id", CommonProxy.getId(tile.getType()).toString());
            }
            tag.putString("BlockId", CommonProxy.getId(accessor.getBlock()).toString());
            responseSender.accept(tag);
        });
    }
}
