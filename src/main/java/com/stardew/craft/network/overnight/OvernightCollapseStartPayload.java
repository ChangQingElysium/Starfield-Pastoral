package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C notification sent as soon as an overnight pass-out is accepted.
 *
 * <p>This packet deliberately precedes the final {@link OvernightSettlementPayload}. It lets the
 * client play the collapse at the player's current location and then wait on black while the
 * server finishes the shared multiplayer new-day transaction.</p>
 */
@SuppressWarnings("null")
public record OvernightCollapseStartPayload(int settlementDay, Cause cause)
        implements CustomPacketPayload {

    public static final Type<OvernightCollapseStartPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_collapse_start")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightCollapseStartPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OvernightCollapseStartPayload::settlementDay,
            ByteBufCodecs.VAR_INT.map(Cause::fromId, Cause::id),
            OvernightCollapseStartPayload::cause,
            OvernightCollapseStartPayload::new
        );

    public OvernightCollapseStartPayload {
        settlementDay = Math.max(1, settlementDay);
        cause = cause == null ? Cause.TWO_AM : cause;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightCollapseStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OvernightCollapseStartPayload payload) {
        OvernightCollapseClientState.begin(payload.settlementDay(), payload.cause());
    }

    public enum Cause {
        TWO_AM(0),
        STAMINA(1);

        private final int id;

        Cause(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Cause fromId(int id) {
            return id == STAMINA.id ? STAMINA : TWO_AM;
        }
    }
}
