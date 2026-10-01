package com.stardew.craft.floor;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public record SurfaceFloorPacket(ResourceLocation dimension, long chunk, boolean replace, List<Entry> entries)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 2048;
    public SurfaceFloorPacket { entries = List.copyOf(entries); }
    public record Entry(BlockPos pos, int type, int variant) {
        public static Entry of(BlockPos pos, SurfaceFloorData.Cover cover) {
            return new Entry(pos.immutable(), cover == null ? -1 : cover.type().ordinal(), cover == null ? 0 : cover.variant());
        }
        private static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                com.stardew.craft.port.PortCodecs.BLOCK_POS, Entry::pos, ByteBufCodecs.VAR_INT, Entry::type,
                ByteBufCodecs.VAR_INT, Entry::variant, Entry::new);
    }
    public static final Type<SurfaceFloorPacket> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "surface_floors"));
    public static final StreamCodec<ByteBuf, SurfaceFloorPacket> CODEC = StreamCodec.composite(
            com.stardew.craft.port.PortCodecs.RESOURCE_LOCATION, SurfaceFloorPacket::dimension,
            ByteBufCodecs.VAR_LONG, SurfaceFloorPacket::chunk,
            ByteBufCodecs.BOOL, SurfaceFloorPacket::replace,
            Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), SurfaceFloorPacket::entries, SurfaceFloorPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (packet, context) ->
                context.enqueueWork(() -> com.stardew.craft.client.floor.ClientSurfaceFloors.apply(packet)));
    }
}
