package com.stardew.craft.animal.runtime;

import java.util.UUID;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record LivestockPurchasePayload(UUID nonce, UUID home, long revision, String name, String species) implements CustomPacketPayload {
    public static final Type<LivestockPurchasePayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:livestock_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LivestockPurchasePayload> CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.nonce); b.writeUUID(p.home); b.writeLong(p.revision); b.writeUtf(p.name,32); b.writeUtf(p.species,32); },
            b -> new LivestockPurchasePayload(b.readUUID(), b.readUUID(), b.readLong(), b.readUtf(32), b.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(LivestockPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) LivestockShop.submit(player, payload); });
    }
}
