package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CloseNpcDialoguePayload(String npcId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CloseNpcDialoguePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "close_npc_dialogue"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, CloseNpcDialoguePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.npcId() == null ? "" : payload.npcId(), 64),
        buf -> new CloseNpcDialoguePayload(buf.readUtf(64))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloseNpcDialoguePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                NpcInteractionService.handleDialogueClosed(serverPlayer, payload.npcId());
                com.stardew.craft.sewer.KrobusUnsealEffectService.onDialogueClosed(serverPlayer, payload.npcId());
                com.stardew.craft.world.HenchmanService.onDialogueClosed(serverPlayer, payload.npcId());
            }
        });
    }
}
