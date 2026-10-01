package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Keeps client placement prediction consistent with the server's public-area rule. */
public record PublicBuildingRuleSyncPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<PublicBuildingRuleSyncPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "public_building_rule_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PublicBuildingRuleSyncPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PublicBuildingRuleSyncPayload::enabled,
                    PublicBuildingRuleSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PublicBuildingRuleSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> context.player().level().getGameRules()
                .getRule(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING).set(payload.enabled(), null));
    }
}
