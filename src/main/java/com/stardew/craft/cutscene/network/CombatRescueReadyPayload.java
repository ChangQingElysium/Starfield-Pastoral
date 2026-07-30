package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.server.CombatRescueCutsceneCoordinator;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server confirmation that the authored rescue location is loaded. */
public record CombatRescueReadyPayload(long token) implements CustomPacketPayload {
    public static final Type<CombatRescueReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "combat_rescue_ready"));

    public static final StreamCodec<ByteBuf, CombatRescueReadyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, CombatRescueReadyPayload::token,
                    CombatRescueReadyPayload::new);

    public static void handle(CombatRescueReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CombatRescueCutsceneCoordinator.onClientReady(player, payload.token);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
