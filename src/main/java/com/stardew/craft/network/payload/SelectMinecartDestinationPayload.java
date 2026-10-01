package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → Server：玩家选定了一个目的地。 */
@SuppressWarnings("null")
public record SelectMinecartDestinationPayload(String currentStationId, String chosenDestinationId)
        implements CustomPacketPayload {

    public static final Type<SelectMinecartDestinationPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "select_minecart_destination"));

    public static final StreamCodec<FriendlyByteBuf, SelectMinecartDestinationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.currentStationId(), 32);
                buf.writeUtf(p.chosenDestinationId(), 32);
            },
            buf -> new SelectMinecartDestinationPayload(buf.readUtf(32), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectMinecartDestinationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            com.stardew.craft.minecart.MinecartMenuService.handleSelection(
                    player, payload.currentStationId(), payload.chosenDestinationId());
        });
    }
}
