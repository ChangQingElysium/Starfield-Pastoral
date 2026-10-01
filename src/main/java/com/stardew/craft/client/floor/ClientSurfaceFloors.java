package com.stardew.craft.client.floor;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.floor.SurfaceFloorData;
import com.stardew.craft.floor.SurfaceFloorPacket;
import com.stardew.craft.floor.SurfaceFloorType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Immutable chunk snapshots can be read safely by terrain meshing workers. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ClientSurfaceFloors {
    private static final Map<Long, Map<Long, SurfaceFloorData.Cover>> CHUNKS = new ConcurrentHashMap<>();
    private ClientSurfaceFloors() {}

    public static SurfaceFloorData.Cover at(BlockPos pos) {
        var chunk = CHUNKS.get(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.get(pos.asLong());
    }

    public static void apply(SurfaceFloorPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(packet.dimension())) return;
        var previous = CHUNKS.getOrDefault(packet.chunk(), Map.of());
        var next = new HashMap<Long, SurfaceFloorData.Cover>(packet.replace() ? Map.of() : previous);
        var changed = new HashSet<Long>();
        if (packet.replace()) changed.addAll(previous.keySet());
        for (var entry : packet.entries()) {
            if (ChunkPos.asLong(entry.pos()) != packet.chunk()) continue;
            long pos = entry.pos().asLong();
            SurfaceFloorType type = SurfaceFloorType.byNetworkId(entry.type());
            if (type == null) next.remove(pos);
            else next.put(pos, new SurfaceFloorData.Cover(type, entry.variant()));
            changed.add(pos);
        }
        if (next.isEmpty()) CHUNKS.remove(packet.chunk()); else CHUNKS.put(packet.chunk(), Map.copyOf(next));
        // A changed tile affects straw centers one cell away and their tips a second cell away.
        var sections = new HashSet<net.minecraft.core.SectionPos>();
        for (long key : changed) {
            BlockPos pos = BlockPos.of(key);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                sections.add(net.minecraft.core.SectionPos.of(pos.offset(x, 0, z)));
        }
        for (var section : sections) mc.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { CHUNKS.clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) CHUNKS.clear();
    }
}
