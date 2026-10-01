package com.stardew.craft.combat.network;

import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authored ground phases and exact positive target contacts are separate events. */
public record HeavyHammerFxPayload(int caster, long tick, String skill, int phase,
                                   double x, double y, double z, float radius, int target, float yaw) implements CustomPacketPayload {
    public static final int BUFF_START=0, BUFF_END=1, SWEEP=2, QUAKE=3, FINAL=4, PRESS=5, ECHO=6, POUND=7, HIT=8, HIT_ECHO=9;
    public static final Type<HeavyHammerFxPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft", "heavy_hammer_fx"));
    public static final StreamCodec<ByteBuf, HeavyHammerFxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HeavyHammerFxPayload decode(ByteBuf b) {
            return new HeavyHammerFxPayload(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_LONG.decode(b),
                    ByteBufCodecs.STRING_UTF8.decode(b), b.readUnsignedByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), ByteBufCodecs.VAR_INT.decode(b), b.readFloat());
        }
        @Override public void encode(ByteBuf b, HeavyHammerFxPayload p) {
            ByteBufCodecs.VAR_INT.encode(b,p.caster); ByteBufCodecs.VAR_LONG.encode(b,p.tick);
            ByteBufCodecs.STRING_UTF8.encode(b,p.skill); b.writeByte(p.phase);
            b.writeDouble(p.x).writeDouble(p.y).writeDouble(p.z).writeFloat(p.radius); ByteBufCodecs.VAR_INT.encode(b,p.target); b.writeFloat(p.yaw);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(HeavyHammerFxPayload p, IPayloadContext c) {
        c.enqueueWork(() -> com.stardew.craft.client.weapon.HeavyHammerVisuals.receive(p));
    }
    public static void send(ServerPlayer p, String skill, int phase, Vec3 point, float radius, int target) {
        send(p, skill, phase, point, radius, target, p.getYRot());
    }
    public static void send(ServerPlayer p, String skill, int phase, Vec3 point, float radius, int target, float yaw) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
                new HeavyHammerFxPayload(p.getId(), p.level().getGameTime(), skill, phase, point.x,point.y,point.z,radius,target,yaw));
    }
}
