package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cast identity keeps late cancellation from removing a newer field. */
public record ForestBlessingPayload(int casterId, long castTick, long tick, Phase phase, int duration, boolean empowered) implements CustomPacketPayload {
    public enum Phase { START, HEAL, END }
    public static final Type<ForestBlessingPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "forest_blessing_state"));
    public static final StreamCodec<ByteBuf, ForestBlessingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public ForestBlessingPayload decode(ByteBuf b) {
            return new ForestBlessingPayload(b.readInt(), b.readLong(), b.readLong(), Phase.values()[b.readUnsignedByte()],
                    b.readInt(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, ForestBlessingPayload p) {
            b.writeInt(p.casterId); b.writeLong(p.castTick); b.writeLong(p.tick); b.writeByte(p.phase.ordinal());
            b.writeInt(p.duration); b.writeBoolean(p.empowered);
        }
    };
    @Override public Type<ForestBlessingPayload> type() { return TYPE; }
    public static void send(ServerPlayer p, long castTick, Phase phase, int duration, boolean empowered) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
                new ForestBlessingPayload(p.getId(), castTick, p.level().getGameTime(), phase, duration, empowered));
    }
    public static void handle(ForestBlessingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.GroveWeaponVisuals.phase(payload));
    }
}
