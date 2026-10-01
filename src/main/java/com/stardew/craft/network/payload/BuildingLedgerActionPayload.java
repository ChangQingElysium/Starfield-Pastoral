package com.stardew.craft.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingLedgerActionPayload(UUID session, String action, String name) implements CustomPacketPayload {
    public static final Type<BuildingLedgerActionPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_ledger_action"));
    public static final StreamCodec<FriendlyByteBuf, BuildingLedgerActionPayload> CODEC = StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeUtf(p.action,16);b.writeUtf(p.name,32);}, b->new BuildingLedgerActionPayload(b.readUUID(),b.readUtf(16),b.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingLedgerActionPayload p, IPayloadContext context) { context.enqueueWork(() -> {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) com.stardew.craft.building.runtime.BuildingLedgerService.action(player,p.session,p.action,p.name);
    }); }
}
