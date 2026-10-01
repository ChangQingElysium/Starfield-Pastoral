package com.stardew.craft.interior.door;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TownDoorNetwork {
    // Installed only by client setup. Common payload classes never resolve client-only types.
    public static Consumer<State> receiveState = ignored -> {};
    public static Consumer<Ack> receiveAck = ignored -> {};

    private TownDoorNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("town_native_2");
        // The registrar already dispatches on the game thread. A second enqueue reorders moves.
        registrar.playToServer(Cross.TYPE, Cross.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TownDoorRuntime.cross(player, payload);
        });
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> receiveState.accept(payload));
        registrar.playToClient(Ack.TYPE, Ack.CODEC, (payload, context) -> receiveAck.accept(payload));
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(new ResourceLocation(StardewCraft.MODID, name));
    }

    public record DoorState(int id, int revision, boolean open, boolean enter, boolean exit,
                            DoorConnection connection) {
    }

    public record State(List<DoorState> doors) implements CustomPacketPayload {
        public State {
            doors = List.copyOf(doors);
        }

        public static final Type<State> TYPE = TownDoorNetwork.type("town_door_state");
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.doors.size());
            for (DoorState door : p.doors) {
                buf.writeVarInt(door.id); buf.writeVarInt(door.revision); buf.writeBoolean(door.open);
                buf.writeBoolean(door.enter); buf.writeBoolean(door.exit);
                writeConnection(buf, door.connection);
            }
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > 64) throw new IllegalArgumentException("Invalid town door count " + size);
            List<DoorState> doors = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                doors.add(new DoorState(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBoolean(), buf.readBoolean(), readConnection(buf)));
            }
            return new State(doors);
        });
        @Override public Type<State> type() { return TYPE; }
    }

    public record Cross(int sequence, int doorId, int revision, boolean entering,
                        Vec3 before, Vec3 after) implements CustomPacketPayload {
        public static final Type<Cross> TYPE = TownDoorNetwork.type("town_door_cross");
        public static final StreamCodec<FriendlyByteBuf, Cross> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.sequence); buf.writeVarInt(p.doorId); buf.writeVarInt(p.revision); buf.writeBoolean(p.entering);
            com.stardew.craft.port.PortCodecs.writeVec3(buf, p.before); com.stardew.craft.port.PortCodecs.writeVec3(buf, p.after);
        }, buf -> new Cross(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                com.stardew.craft.port.PortCodecs.readVec3(buf), com.stardew.craft.port.PortCodecs.readVec3(buf)));
        @Override public Type<Cross> type() { return TYPE; }
    }

    public record Ack(int sequence, boolean accepted) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = TownDoorNetwork.type("town_door_ack");
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.sequence); buf.writeBoolean(p.accepted);
        }, buf -> new Ack(buf.readVarInt(), buf.readBoolean()));
        @Override public Type<Ack> type() { return TYPE; }
    }

    private static void writeConnection(FriendlyByteBuf buf, DoorConnection connection) {
        com.stardew.craft.port.PortCodecs.writeVec3(buf, connection.outside());
        com.stardew.craft.port.PortCodecs.writeVec3(buf, connection.inside());
        buf.writeDouble(connection.width());
        buf.writeByte(connection.outsideDirectionZ());
        buf.writeByte(connection.insideDirectionZ());
    }

    private static DoorConnection readConnection(FriendlyByteBuf buf) {
        return new DoorConnection(com.stardew.craft.port.PortCodecs.readVec3(buf), com.stardew.craft.port.PortCodecs.readVec3(buf), buf.readDouble(), buf.readByte(), buf.readByte());
    }
}
