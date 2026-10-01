package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ElfBladeMarkPayload(int entityId, int durationTicks, int stacks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<ElfBladeMarkPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "elf_blade_mark")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ElfBladeMarkPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ElfBladeMarkPayload::entityId,
        ByteBufCodecs.VAR_INT,
        ElfBladeMarkPayload::durationTicks,
        ByteBufCodecs.VAR_INT,
        ElfBladeMarkPayload::stacks,
        ElfBladeMarkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ElfBladeMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.ElfBladeMarkClientState.apply(
            payload.entityId(), payload.durationTicks(), payload.stacks()
        ));
    }
}
