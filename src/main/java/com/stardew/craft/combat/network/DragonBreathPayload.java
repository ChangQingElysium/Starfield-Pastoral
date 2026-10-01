package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DragonBreathPayload(int stacks) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<DragonBreathPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "dragon_breath")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, DragonBreathPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        DragonBreathPayload::stacks,
        DragonBreathPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DragonBreathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.DragonBreathClientState.setStacks(payload.stacks()));
    }
}
