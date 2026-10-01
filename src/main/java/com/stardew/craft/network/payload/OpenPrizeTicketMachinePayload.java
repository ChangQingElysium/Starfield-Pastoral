package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: opens the Prize Machine menu with server-authored reward previews. */
@SuppressWarnings("null")
public record OpenPrizeTicketMachinePayload(int ticketPrizesClaimed, List<PrizeTicketRewardPreview> previews)
    implements CustomPacketPayload {
    public static final Type<OpenPrizeTicketMachinePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_prize_ticket_machine"));

    public static final StreamCodec<FriendlyByteBuf, OpenPrizeTicketMachinePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.ticketPrizesClaimed());
            buf.writeVarInt(payload.previews().size());
            for (PrizeTicketRewardPreview preview : payload.previews()) {
                PrizeTicketRewardPreview.write(buf, preview);
            }
        },
        buf -> {
            int claimed = buf.readVarInt();
            int size = buf.readVarInt();
            List<PrizeTicketRewardPreview> previews = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                previews.add(PrizeTicketRewardPreview.read(buf));
            }
            return new OpenPrizeTicketMachinePayload(claimed, previews);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPrizeTicketMachinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenPrizeTicketMachinePayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new com.stardew.craft.client.gui.PrizeTicketMachineScreen(
            payload.ticketPrizesClaimed(), payload.previews()));
    }
}