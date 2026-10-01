package com.stardew.craft.port;

import com.stardew.craft.port.net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PORT(1.20.1): NeoForge's auxiliary light manager ({@code BlockGetter#getAuxLightManager}) on Forge.
 * Light values live in a chunk capability saved with the chunk; a chunk's values are sent to a player right after
 * the chunk itself (NeoForge bundles them with the chunk packet).
 */
public final class PortAuxLight {
    private static final ResourceLocation CAPABILITY_ID = new ResourceLocation(PortBootstrap.NAMESPACE, "aux_lights");
    public static final Capability<ChunkLights> LIGHTS = CapabilityManager.get(new CapabilityToken<>() {});

    private PortAuxLight() {}

    /** Call-site replacement for NeoForge {@code BlockGetter#getAuxLightManager(BlockPos)}. */
    @Nullable
    public static AuxiliaryLightManager getAuxLightManager(BlockGetter level, BlockPos pos) {
        return getAuxLightManager(level, new ChunkPos(pos));
    }

    /** Call-site replacement for NeoForge {@code BlockGetter#getAuxLightManager(ChunkPos)}. */
    @Nullable
    public static AuxiliaryLightManager getAuxLightManager(BlockGetter level, ChunkPos pos) {
        if (level instanceof LevelChunk chunk) return of(chunk);
        if (level instanceof ImposterProtoChunk chunk) return of(chunk.getWrapped());
        if (level instanceof LevelAccessor accessor) {
            LightChunk chunk = accessor.getChunkSource().getChunkForLighting(pos.x, pos.z);
            if (chunk instanceof LevelChunk levelChunk) return of(levelChunk);
            if (chunk instanceof ImposterProtoChunk imposter) return of(imposter.getWrapped());
        }
        return null;
    }

    @Nullable
    private static ChunkLights of(LevelChunk chunk) {
        return chunk.getCapability(LIGHTS).resolve().orElse(null);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        ChunkLights lights = new ChunkLights(event.getObject());
        LazyOptional<ChunkLights> optional = LazyOptional.of(() -> lights);
        event.addCapability(CAPABILITY_ID, new ICapabilitySerializable<ListTag>() {
            @Override
            public <C> @NotNull LazyOptional<C> getCapability(@NotNull Capability<C> cap, @Nullable Direction side) {
                return LIGHTS.orEmpty(cap, optional);
            }

            @Override
            public ListTag serializeNBT() {
                return lights.serialize();
            }

            @Override
            public void deserializeNBT(ListTag tag) {
                lights.deserialize(tag);
            }
        });
        event.addListener(optional::invalidate);
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        ChunkLights lights = of(event.getChunk());
        if (lights != null && !lights.lights.isEmpty()) {
            PortNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer),
                    new SyncMessage(event.getPos(), Map.copyOf(lights.lights)));
        }
    }

    /** A chunk packet replaces the client chunk's contents; its light data follows in a separate message. */
    @SubscribeEvent
    public static void clientChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            ChunkLights lights = of(chunk);
            if (lights != null) lights.lights.clear();
        }
    }

    static void applySync(Level level, SyncMessage message) {
        if (getAuxLightManager(level, message.pos) instanceof ChunkLights lights) {
            lights.lights.clear();
            lights.lights.putAll(message.lights);
        }
    }

    @AutoRegisterCapability
    public static final class ChunkLights implements AuxiliaryLightManager {
        private final LevelChunk owner;
        private final Map<BlockPos, Byte> lights = new ConcurrentHashMap<>();

        ChunkLights(LevelChunk owner) {
            this.owner = owner;
        }

        @Override
        public void setLightAt(BlockPos pos, int value) {
            pos = pos.immutable();
            value = Mth.clamp(value, 0, LightEngine.MAX_LEVEL);
            Byte oldValue = value > 0 ? lights.put(pos, (byte) value) : lights.remove(pos);
            if (Objects.requireNonNullElse(oldValue, (byte) 0) != value) {
                owner.getLevel().getChunkSource().getLightEngine().checkBlock(pos);
                owner.setUnsaved(true);
            }
        }

        @Override
        public int getLightAt(BlockPos pos) {
            return lights.getOrDefault(pos, (byte) 0);
        }

        ListTag serialize() {
            ListTag list = new ListTag();
            lights.forEach((pos, light) -> {
                CompoundTag tag = new CompoundTag();
                tag.putLong("pos", pos.asLong());
                tag.putByte("level", light);
                list.add(tag);
            });
            return list;
        }

        void deserialize(ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                lights.put(BlockPos.of(tag.getLong("pos")), tag.getByte("level"));
            }
        }
    }

    public record SyncMessage(ChunkPos pos, Map<BlockPos, Byte> lights) {
        void encode(FriendlyByteBuf buf) {
            buf.writeChunkPos(pos);
            buf.writeVarInt(lights.size());
            lights.forEach((blockPos, light) -> {
                buf.writeBlockPos(blockPos);
                buf.writeByte(light);
            });
        }

        static SyncMessage decode(FriendlyByteBuf buf) {
            ChunkPos pos = buf.readChunkPos();
            int size = buf.readVarInt();
            Map<BlockPos, Byte> lights = new HashMap<>(size);
            for (int i = 0; i < size; i++) lights.put(buf.readBlockPos(), buf.readByte());
            return new SyncMessage(pos, lights);
        }
    }

}
