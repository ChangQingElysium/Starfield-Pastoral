package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Small, combat-critical player vitals update.
 *
 * <p>The complete player-data packet contains progression, collections, mail,
 * equipment and other persistent state. Sending all of it for every accepted
 * hit delays the one value the HUD needs immediately.</p>
 */
public record PlayerVitalsSyncPayload(int health, int maxHealth)
        implements CustomPacketPayload {

    public static final Type<PlayerVitalsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "player_vitals_sync")
    );

    public static final StreamCodec<ByteBuf, PlayerVitalsSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PlayerVitalsSyncPayload::health,
                    ByteBufCodecs.VAR_INT,
                    PlayerVitalsSyncPayload::maxHealth,
                    PlayerVitalsSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerVitalsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.stardew.craft.client.ClientPlayerDataCache.updateVitals(
                        payload.health(),
                        payload.maxHealth()
                )
        );
    }
}
