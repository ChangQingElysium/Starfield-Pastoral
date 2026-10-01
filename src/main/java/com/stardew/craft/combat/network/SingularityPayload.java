package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SingularityPayload(int stacks) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SingularityPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "singularity")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SingularityPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SingularityPayload::stacks,
        SingularityPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SingularityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SingularityClientState.setStacks(payload.stacks()));
    }
}
