package com.stardew.craft.animal.runtime;

import java.util.UUID;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record LivestockManagePayload(UUID nonce, UUID animal, UUID home, String action, String name) implements CustomPacketPayload {
    public static final Type<LivestockManagePayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:livestock_manage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LivestockManagePayload> CODEC = StreamCodec.of(
        (b,p) -> { b.writeUUID(p.nonce); b.writeUUID(p.animal); b.writeUUID(p.home); b.writeUtf(p.action,16); b.writeUtf(p.name,32); },
        b -> new LivestockManagePayload(b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(16),b.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(LivestockManagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) LivestockManagement.submit(player, payload); });
    }
}
