package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record StartrailPayload(int stacks) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<StartrailPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "startrail")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, StartrailPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        StartrailPayload::stacks,
        StartrailPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.StartrailClientState.setStacks(payload.stacks()));
    }
}
