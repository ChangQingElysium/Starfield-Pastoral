package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: authoritative Prize Machine claim result for UI animation/state updates. */
@SuppressWarnings("null")
public record PrizeTicketClaimResultPayload(
    boolean success,
    String rewardItemId,
    int rewardCount,
    int previousTicketPrizesClaimed,
    int newTicketPrizesClaimed,
    int claimedPrizeLevel,
    List<PrizeTicketRewardPreview> previews
) implements CustomPacketPayload {
    public static final Type<PrizeTicketClaimResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "prize_ticket_claim_result"));

    public static final StreamCodec<FriendlyByteBuf, PrizeTicketClaimResultPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.success());
            buf.writeUtf(payload.rewardItemId());
            buf.writeVarInt(payload.rewardCount());
            buf.writeVarInt(payload.previousTicketPrizesClaimed());
            buf.writeVarInt(payload.newTicketPrizesClaimed());
            buf.writeVarInt(payload.claimedPrizeLevel());
            buf.writeVarInt(payload.previews().size());
            for (PrizeTicketRewardPreview preview : payload.previews()) {
                PrizeTicketRewardPreview.write(buf, preview);
            }
        },
        buf -> {
            boolean success = buf.readBoolean();
            String rewardItemId = buf.readUtf();
            int rewardCount = buf.readVarInt();
            int previousClaimed = buf.readVarInt();
            int newClaimed = buf.readVarInt();
            int prizeLevel = buf.readVarInt();
            int previewCount = buf.readVarInt();
            List<PrizeTicketRewardPreview> previews = new ArrayList<>(previewCount);
            for (int index = 0; index < previewCount; index++) {
                previews.add(PrizeTicketRewardPreview.read(buf));
            }
            return new PrizeTicketClaimResultPayload(success, rewardItemId, rewardCount,
                previousClaimed, newClaimed, prizeLevel, previews);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrizeTicketClaimResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(PrizeTicketClaimResultPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen instanceof com.stardew.craft.client.gui.PrizeTicketMachineScreen screen) {
            screen.onClaimResult(payload);
        }
    }
}