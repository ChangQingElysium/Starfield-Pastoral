package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record OpenFarmJoinInvitePayload(
        UUID requesterUUID,
        String requesterName,
        String farmName
) implements CustomPacketPayload {

    public static final Type<OpenFarmJoinInvitePayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_farm_join_invite"));

    public static final StreamCodec<FriendlyByteBuf, OpenFarmJoinInvitePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.requesterUUID());
                buf.writeUtf(payload.requesterName());
                buf.writeUtf(payload.farmName());
            },
            buf -> new OpenFarmJoinInvitePayload(buf.readUUID(), buf.readUtf(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFarmJoinInvitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenFarmJoinInvitePayload payload) {
        com.stardew.craft.client.farm.FarmJoinInviteClient.enqueue(
                payload.requesterUUID(), payload.requesterName(), payload.farmName());
    }
}