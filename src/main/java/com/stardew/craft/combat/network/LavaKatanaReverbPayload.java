package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record LavaKatanaReverbPayload(int casterId, long castTick, boolean active, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<LavaKatanaReverbPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "lava_katana_reverb_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, LavaKatanaReverbPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        LavaKatanaReverbPayload::casterId,
        ByteBufCodecs.VAR_LONG,
        LavaKatanaReverbPayload::castTick,
        ByteBufCodecs.BOOL,
        LavaKatanaReverbPayload::active,
        ByteBufCodecs.VAR_INT,
        LavaKatanaReverbPayload::durationTicks,
        LavaKatanaReverbPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LavaKatanaReverbPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(LavaKatanaReverbPayload payload) {
        if (com.stardew.craft.client.weapon.LavaKatanaReverbClientState.apply(payload) && payload.active()) {
            com.stardew.craft.client.weapon.LavaKatanaReverbVisuals.ignite(payload.casterId());
        }
    }
}
