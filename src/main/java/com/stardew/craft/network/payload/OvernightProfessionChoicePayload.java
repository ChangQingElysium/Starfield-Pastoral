package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record OvernightProfessionChoicePayload(int professionId) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<OvernightProfessionChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "overnight_profession_choice"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, OvernightProfessionChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.professionId()),
        buf -> new OvernightProfessionChoicePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(OvernightProfessionChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ProfessionType profession = ProfessionType.fromId(payload.professionId());
            if (profession == null) {
                return;
            }
            PlayerStardewDataAPI.choosePendingProfession(player, profession);
        });
    }
}