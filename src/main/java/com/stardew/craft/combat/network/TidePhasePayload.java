package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Exact contact and displacement endpoints; short effects expire against server time. */
public record TidePhasePayload(int casterId, long tick, Phase phase, Vec3 from, Vec3 to,
                                   int duration, boolean empowered) implements CustomPacketPayload {
    public enum Phase { ANCHOR, TRANSFER, REEL }
    public static final Type<TidePhasePayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "tide_phase"));
    public static final StreamCodec<ByteBuf, TidePhasePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TidePhasePayload decode(ByteBuf b) {
            return new TidePhasePayload(b.readInt(), b.readLong(), Phase.values()[b.readUnsignedByte()],
                    read(b), read(b), b.readInt(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, TidePhasePayload p) {
            b.writeInt(p.casterId); b.writeLong(p.tick); b.writeByte(p.phase.ordinal());
            write(b, p.from); write(b, p.to); b.writeInt(p.duration); b.writeBoolean(p.empowered);
        }
        private Vec3 read(ByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
        private void write(ByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    };
    @Override public Type<TidePhasePayload> type() { return TYPE; }
    public static void send(ServerPlayer p, Phase phase, Vec3 from, Vec3 to, int duration, boolean empowered) {
        var payload = new TidePhasePayload(p.getId(), p.level().getGameTime(), phase, from, to, duration, empowered);
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, to.x, to.y, to.z, 48, payload);
        if (from.distanceToSqr(to) > 0.01)
            PacketDistributor.sendToPlayersNear(p.serverLevel(), null, from.x, from.y, from.z, 48, payload);
    }
    public static void handle(TidePhasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.TideWeaponVisuals.phase(payload));
    }
}
