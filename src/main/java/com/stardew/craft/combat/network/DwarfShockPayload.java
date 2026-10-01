package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** One already-triggered shock; never starts a hand animation or a damage simulation. */
public record DwarfShockPayload(int caster, long tick, double x, double y, double z, float radius, boolean echo) implements CustomPacketPayload {
    public static final Type<DwarfShockPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "dwarf_shock"));
    public static final StreamCodec<ByteBuf, DwarfShockPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public DwarfShockPayload decode(ByteBuf b) {
            return new DwarfShockPayload(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_LONG.decode(b),
                    b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, DwarfShockPayload p) {
            ByteBufCodecs.VAR_INT.encode(b, p.caster); ByteBufCodecs.VAR_LONG.encode(b, p.tick);
            b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeFloat(p.radius); b.writeBoolean(p.echo);
        }
    };
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DwarfShockPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.DwarfWeaponVisuals.shock(p));
    }
}
