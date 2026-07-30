package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ForestBlessingPayload(
        int casterEntityId,
        boolean active,
        int durationTicks,
        boolean completedCycle
) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<ForestBlessingPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "forest_blessing_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ForestBlessingPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ForestBlessingPayload::casterEntityId,
        ByteBufCodecs.BOOL,
        ForestBlessingPayload::active,
        ByteBufCodecs.VAR_INT,
        ForestBlessingPayload::durationTicks,
        ByteBufCodecs.BOOL,
        ForestBlessingPayload::completedCycle,
        ForestBlessingPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ForestBlessingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(ForestBlessingPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        com.stardew.craft.client.weapon.presentation.SkillPresentationClient
                .setForestBlessingState(
                        payload.casterEntityId(),
                        payload.active(),
                        payload.durationTicks(),
                        payload.completedCycle()
                );
        if (mc.player == null || mc.player.getId() != payload.casterEntityId()) {
            return;
        }
        if (payload.active()) {
            long nowTick = mc.level != null ? mc.level.getGameTime() : 0L;
            com.stardew.craft.client.weapon.ForestBlessingClientState.start(nowTick, payload.durationTicks());
        } else {
            com.stardew.craft.client.weapon.ForestBlessingClientState.clear();
        }
    }
}
