package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DamageNumberPayload(float x, float y, float z, int damage, boolean crit, String skillId) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<DamageNumberPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "damage_number")
    );

        @SuppressWarnings("null")
        public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            DamageNumberPayload::x,
            ByteBufCodecs.FLOAT,
            DamageNumberPayload::y,
            ByteBufCodecs.FLOAT,
            DamageNumberPayload::z,
            ByteBufCodecs.VAR_INT,
            DamageNumberPayload::damage,
            ByteBufCodecs.BOOL,
            DamageNumberPayload::crit,
            ByteBufCodecs.STRING_UTF8,
            payload -> payload.skillId() == null ? "" : payload.skillId(),
            DamageNumberPayload::new
        );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DamageNumberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            String skill = payload.skillId();
            if (skill != null && skill.isEmpty()) {
                skill = null;
            }
            com.stardew.craft.client.combat.DamageNumberClient.add(payload.x(), payload.y(), payload.z(), payload.damage(), payload.crit(), skill);
        });
    }
}
