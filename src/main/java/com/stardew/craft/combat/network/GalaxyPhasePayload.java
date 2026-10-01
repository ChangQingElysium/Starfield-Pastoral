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

/** Exact starfall execution or completed teleport endpoints; never a schedule of future hits. */
public record GalaxyPhasePayload(int casterId, long tick, boolean leap, Vec3 from, Vec3 to) implements CustomPacketPayload {
    public static final Type<GalaxyPhasePayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "galaxy_phase"));
    public static final StreamCodec<ByteBuf, GalaxyPhasePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public GalaxyPhasePayload decode(ByteBuf b) {
            return new GalaxyPhasePayload(b.readInt(), b.readLong(), b.readBoolean(), read(b), read(b));
        }
        @Override public void encode(ByteBuf b, GalaxyPhasePayload p) {
            b.writeInt(p.casterId); b.writeLong(p.tick); b.writeBoolean(p.leap); write(b, p.from); write(b, p.to);
        }
        private Vec3 read(ByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
        private void write(ByteBuf b, Vec3 p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    };
    @Override public Type<GalaxyPhasePayload> type() { return TYPE; }
    public static void send(ServerPlayer player, boolean leap, Vec3 from, Vec3 to) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new GalaxyPhasePayload(player.getId(), player.level().getGameTime(), leap, from, to));
    }
    public static void handle(GalaxyPhasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.GalaxyWeaponVisuals.phase(payload));
    }
}
