package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ObsidianResonanceSyncPayload(boolean active, int remainingTicks, int totalTicks)
    implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<ObsidianResonanceSyncPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "obsidian_resonance_sync")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ObsidianResonanceSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        ObsidianResonanceSyncPayload::active,
        ByteBufCodecs.VAR_INT,
        ObsidianResonanceSyncPayload::remainingTicks,
        ByteBufCodecs.VAR_INT,
        ObsidianResonanceSyncPayload::totalTicks,
        ObsidianResonanceSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ObsidianResonanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(ObsidianResonanceSyncPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        long nowTick = mc.level.getGameTime();
        if (payload.active()) {
            com.stardew.craft.client.weapon.ObsidianResonanceClientState.sync(nowTick, payload.remainingTicks(), payload.totalTicks());
        } else {
            com.stardew.craft.client.weapon.ObsidianResonanceClientState.clear();
        }
    }
}
