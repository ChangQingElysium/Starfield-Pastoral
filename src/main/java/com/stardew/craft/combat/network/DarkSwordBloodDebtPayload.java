package com.stardew.craft.combat.network;
import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
public record DarkSwordBloodDebtPayload(int casterId,long castTick,boolean active,int durationTicks) implements CustomPacketPayload {
    public static final Type<DarkSwordBloodDebtPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID,"dark_sword_blood_debt_state"));
    public static final StreamCodec<ByteBuf,DarkSwordBloodDebtPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,DarkSwordBloodDebtPayload::casterId,ByteBufCodecs.VAR_LONG,DarkSwordBloodDebtPayload::castTick,
            ByteBufCodecs.BOOL,DarkSwordBloodDebtPayload::active,ByteBufCodecs.VAR_INT,DarkSwordBloodDebtPayload::durationTicks,DarkSwordBloodDebtPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DarkSwordBloodDebtPayload p,IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.BloodForgeVisuals.darkState(p.casterId(),p.castTick(),p.active(),p.durationTicks(),false));
    }
}
