package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.runtime.CombatRescueCutsceneContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client readiness probe for a combat-rescue destination.
 *
 * <p>The client ACKs only after both the target dimension and target chunk are
 * present locally. The server resends this probe while waiting, so receiving
 * the first copy before a cross-dimension respawn packet is harmless.</p>
 */
public record CombatRescuePreparePayload(
        long token,
        ResourceLocation dimension,
        BlockPos target,
        String rescuerNpcId,
        String dialogueKey
) implements CustomPacketPayload {
    public static final Type<CombatRescuePreparePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "combat_rescue_prepare"));

    public static final StreamCodec<ByteBuf, CombatRescuePreparePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, CombatRescuePreparePayload::token,
                    ResourceLocation.STREAM_CODEC, CombatRescuePreparePayload::dimension,
                    BlockPos.STREAM_CODEC, CombatRescuePreparePayload::target,
                    ByteBufCodecs.STRING_UTF8, CombatRescuePreparePayload::rescuerNpcId,
                    ByteBufCodecs.STRING_UTF8, CombatRescuePreparePayload::dialogueKey,
                    CombatRescuePreparePayload::new);

    public CombatRescuePreparePayload {
        rescuerNpcId = rescuerNpcId == null ? "" : rescuerNpcId;
        dialogueKey = dialogueKey == null ? "" : dialogueKey;
    }

    public static void handle(CombatRescuePreparePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(CombatRescuePreparePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(payload.dimension)
                || !minecraft.level.hasChunkAt(payload.target)) {
            return;
        }

        int targetChunkX = payload.target.getX() >> 4;
        int targetChunkZ = payload.target.getZ() >> 4;
        if (minecraft.player.chunkPosition().x != targetChunkX
                || minecraft.player.chunkPosition().z != targetChunkZ) {
            return;
        }

        CombatRescueCutsceneContext.set(payload.rescuerNpcId, payload.dialogueKey);
        com.stardew.craft.client.gui.overnight.PassOutOverlayScreen.destinationReady();
        PacketDistributor.sendToServer(new CombatRescueReadyPayload(payload.token));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
