package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Actual teleport endpoints or the confirmed start of a continuous wind dash. No simulated damage path. */
public record IronWindMovePayload(int caster,long tick,Mode mode,Vec3 from,Vec3 to) implements CustomPacketPayload {
    public enum Mode { IRON_BLINK, WIND_BLINK, WIND_DASH }
    public static final Type<IronWindMovePayload> TYPE=new Type<>(new ResourceLocation(StardewCraft.MODID,"iron_wind_move"));
    public static final StreamCodec<ByteBuf,IronWindMovePayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public IronWindMovePayload decode(ByteBuf b) {
            return new IronWindMovePayload(ByteBufCodecs.VAR_INT.decode(b),ByteBufCodecs.VAR_LONG.decode(b),Mode.values()[b.readUnsignedByte()],
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()));
        }
        @Override public void encode(ByteBuf b,IronWindMovePayload p) {
            ByteBufCodecs.VAR_INT.encode(b,p.caster);ByteBufCodecs.VAR_LONG.encode(b,p.tick);b.writeByte(p.mode.ordinal());
            b.writeDouble(p.from.x).writeDouble(p.from.y).writeDouble(p.from.z);
            b.writeDouble(p.to.x).writeDouble(p.to.y).writeDouble(p.to.z);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(IronWindMovePayload p,IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.IronWindVisuals.move(p));
    }
}
