package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: the result of cracking a geode (the treasure item ID).
 * Also sends the geode type so the client can play the correct destruction animation.
 */
@SuppressWarnings("null")
public record GeodeCrackResultPayload(
    String treasureItemId,
    String geodeType,
    int newMoney
) implements CustomPacketPayload {

    public static final Type<GeodeCrackResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "geode_crack_result"));

    public static final StreamCodec<FriendlyByteBuf, GeodeCrackResultPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.treasureItemId());
            buf.writeUtf(payload.geodeType());
            buf.writeVarInt(payload.newMoney());
        },
        buf -> new GeodeCrackResultPayload(buf.readUtf(), buf.readUtf(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeodeCrackResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(GeodeCrackResultPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.gui.GeodeMenuScreen screen) {
            screen.onCrackResult(payload.treasureItemId(), payload.geodeType(), payload.newMoney());
        }
    }
}
