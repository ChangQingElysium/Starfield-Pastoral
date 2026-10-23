package com.stardew.craft.platform.fabric.light;

import com.stardew.craft.platform.fabric.network.FabricFragmentation;
import com.stardew.craft.platform.fabric.network.PacketDistributor;
import com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/** Common-only cache, packet and lighting access. No light worker reads a block entity. */
public final class AuxiliaryLights {
    private AuxiliaryLights() {}

    public static AuxiliaryLightManager getManager(BlockGetter view, BlockPos pos) {
        if (view instanceof AuxiliaryLightView holder) return holder.stardewcraft$auxiliaryLights(pos);
        if (view instanceof ImposterProtoChunk imposter) return getManager(imposter.getWrapped(), pos);
        if (view instanceof Level level) {
            // Never generate a chunk from a lighting/render thread.
            var chunk = level.getChunkSource().getChunkForLighting(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk instanceof AuxiliaryLightView holder) return holder.stardewcraft$auxiliaryLights(pos);
        }
        return null;
    }

    public static ChunkAuxiliaryLightManager owned(LevelChunk chunk) {
        var manager = getManager(chunk, chunk.getPos().getWorldPosition());
        if (!(manager instanceof ChunkAuxiliaryLightManager owned)) throw new IllegalStateException("Chunk auxiliary-light mixin is required");
        return owned;
    }

    public static void changed(LevelChunk chunk, ChunkAuxiliaryLightManager.Change change) {
        var level = chunk.getLevel();
        level.getChunkSource().getLightEngine().checkBlock(change.pos());
        chunk.setUnsaved(true);
        if (level instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersTrackingChunk(server, chunk.getPos(), new LightSyncPayload(level.dimension().location(),
                    chunk.getPos(), false, Map.of(change.pos().asLong(), (byte) change.value())));
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("fabric-auxiliary-light/1").playToClient(LightSyncPayload.TYPE, LightSyncPayload.CODEC, (payload, context) -> {
            if (context.flow() != PacketFlow.CLIENTBOUND) throw new IllegalStateException("Auxiliary light must be clientbound");
            context.enqueueWork(() -> {
                Level level = context.player().level();
                if (!level.isClientSide() || !level.dimension().location().equals(payload.dimension())) return;
                var chunk = level.getChunkSource().getChunkNow(payload.chunk().x, payload.chunk().z);
                if (chunk != null) owned(chunk).apply(payload.values(), payload.full());
            });
        });
    }

    /** The native chunk must reach the client before its cache, in the same ordered packet bundle. */
    public static Packet<?> bundle(LevelChunk chunk, ClientboundLevelChunkWithLightPacket original) {
        var payload = new LightSyncPayload(chunk.getLevel().dimension().location(), chunk.getPos(), true, owned(chunk).snapshot());
        return bundlePackets(original, FabricFragmentation.prepare(payload, chunk.getLevel().registryAccess(), PacketFlow.CLIENTBOUND));
    }

    static ClientboundBundlePacket bundlePackets(ClientboundLevelChunkWithLightPacket original, List<? extends CustomPacketPayload> frames) {
        var packets = new ArrayList<Packet<? super ClientGamePacketListener>>(); packets.add(original);
        for (var frame : frames) {
            packets.add(new ClientboundCustomPayloadPacket(frame));
        }
        return new ClientboundBundlePacket(packets);
    }

    public static AuxiliaryLightSnapshot snapshotRegion(Level level, int minChunkX, int minChunkZ, int size) {
        var lights = new HashMap<Long, Byte>();
        for (int x = minChunkX; x < minChunkX + size; x++) for (int z = minChunkZ; z < minChunkZ + size; z++) {
            var chunk = level.getChunkSource().getChunkForLighting(x, z);
            if (chunk instanceof LevelChunk levelChunk) lights.putAll(owned(levelChunk).snapshot());
        }
        return new AuxiliaryLightSnapshot(lights);
    }
}
