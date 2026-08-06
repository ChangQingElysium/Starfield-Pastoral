package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.FertilizerType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Server-to-client fertilizer updates, either incremental or an atomic chunk snapshot. */
public record FertilizerSyncPacket(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        boolean replaceChunk,
        List<Entry> entries
) implements CustomPacketPayload {
    public FertilizerSyncPacket {
        dimension = Objects.requireNonNull(dimension, "dimension");
        entries = List.copyOf(entries);
    }

    public static FertilizerSyncPacket update(
            ResourceLocation dimension,
            BlockPos pos,
            @Nullable FertilizerType type
    ) {
        return new FertilizerSyncPacket(
                dimension,
                pos.getX() >> 4,
                pos.getZ() >> 4,
                false,
                List.of(new Entry(pos, type == null ? null : type.getSerializedName())));
    }

    public static FertilizerSyncPacket chunkSnapshot(
            ResourceLocation dimension,
            ChunkPos chunkPos,
            List<Entry> entries
    ) {
        return new FertilizerSyncPacket(dimension, chunkPos.x, chunkPos.z, true, entries);
    }

    public ChunkPos chunkPos() {
        return new ChunkPos(chunkX, chunkZ);
    }

    public record Entry(BlockPos pos, @Nullable String fertilizerType) {
        public Entry {
            pos = Objects.requireNonNull(pos, "pos").immutable();
        }

        @Nullable
        public FertilizerType type() {
            return FertilizerType.bySerializedName(fertilizerType);
        }

        private static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                Entry::pos,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
                entry -> java.util.Optional.ofNullable(entry.fertilizerType()),
                (pos, type) -> new Entry(pos, type.orElse(null)));
    }

    @SuppressWarnings("null")
    public static final Type<FertilizerSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fertilizer_sync"));

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, FertilizerSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    FertilizerSyncPacket::dimension,
                    ByteBufCodecs.VAR_INT,
                    FertilizerSyncPacket::chunkX,
                    ByteBufCodecs.VAR_INT,
                    FertilizerSyncPacket::chunkZ,
                    ByteBufCodecs.BOOL,
                    FertilizerSyncPacket::replaceChunk,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    FertilizerSyncPacket::entries,
                    FertilizerSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
