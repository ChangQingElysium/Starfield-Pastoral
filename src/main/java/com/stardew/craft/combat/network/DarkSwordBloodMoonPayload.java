package com.stardew.craft.combat.network;
import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
public record DarkSwordBloodMoonPayload(int casterId,long castTick,boolean active,int durationTicks) implements CustomPacketPayload {
    public static final Type<DarkSwordBloodMoonPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID,"dark_sword_blood_moon_state"));
    public static final StreamCodec<ByteBuf,DarkSwordBloodMoonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,DarkSwordBloodMoonPayload::casterId,ByteBufCodecs.VAR_LONG,DarkSwordBloodMoonPayload::castTick,
            ByteBufCodecs.BOOL,DarkSwordBloodMoonPayload::active,ByteBufCodecs.VAR_INT,DarkSwordBloodMoonPayload::durationTicks,DarkSwordBloodMoonPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DarkSwordBloodMoonPayload p,IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.BloodForgeVisuals.darkState(p.casterId(),p.castTick(),p.active(),p.durationTicks(),true));
    }
}
