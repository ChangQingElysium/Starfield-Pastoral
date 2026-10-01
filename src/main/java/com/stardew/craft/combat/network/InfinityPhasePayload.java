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

/** Cast identity keeps late cancellation from removing a newer field. */
public record InfinityPhasePayload(int casterId, long castTick, long tick, Phase phase, Vec3 from, Vec3 to,
                                   int duration, boolean evolved) implements CustomPacketPayload {
    public enum Phase { EVOLVE, COLLAPSE, RELEASE, PULSE, FINISH, END_EVOLVE, END_COLLAPSE, LEAP }
    public static final Type<InfinityPhasePayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "infinity_phase"));
    public static final StreamCodec<ByteBuf, InfinityPhasePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public InfinityPhasePayload decode(ByteBuf b) {
            return new InfinityPhasePayload(b.readInt(), b.readLong(), b.readLong(), Phase.values()[b.readUnsignedByte()],
                    read(b), read(b), b.readInt(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, InfinityPhasePayload p) {
            b.writeInt(p.casterId); b.writeLong(p.castTick); b.writeLong(p.tick); b.writeByte(p.phase.ordinal());
            write(b, p.from); write(b, p.to); b.writeInt(p.duration); b.writeBoolean(p.evolved);
        }
        private Vec3 read(ByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
        private void write(ByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    };
    @Override public Type<InfinityPhasePayload> type() { return TYPE; }
    public static void send(ServerPlayer p, long castTick, Phase phase, Vec3 from, Vec3 to, int duration, boolean evolved) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
                new InfinityPhasePayload(p.getId(), castTick, p.level().getGameTime(), phase, from, to, duration, evolved));
    }
    public static void handle(InfinityPhasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.InfinityWeaponVisuals.phase(payload));
    }
}
