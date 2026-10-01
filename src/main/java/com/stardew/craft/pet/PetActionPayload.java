package com.stardew.craft.pet;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record PetActionPayload(UUID nonce, UUID pet, String action, String value, BlockPos bowl) implements CustomPacketPayload {
    public static final Type<PetActionPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:pet_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetActionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.nonce); b.writeUUID(p.pet); b.writeUtf(p.action, 16); b.writeUtf(p.value, 384); b.writeBlockPos(p.bowl); },
            b -> new PetActionPayload(b.readUUID(), b.readUUID(), b.readUtf(16), b.readUtf(384), b.readBlockPos()));
    public record Selection(String variant, String name) {}
    public static String selection(String variant, String name) { return variant + "\n" + PetRecord.cleanName(name); }
    public Selection selection() {
        int separator = value.indexOf('\n');
        return separator <= 0 ? new Selection("", "") : new Selection(value.substring(0, separator), PetRecord.cleanName(value.substring(separator + 1)));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PetActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) PetManagement.submit(player, payload); });
    }
}
