package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C→S: saves the dialogue profile collected for an existing save. */
@SuppressWarnings("null")
public record PlayerProfileSubmitPayload(
        String preferredName,
        String favoriteThing,
        boolean male
) implements CustomPacketPayload {
    public static final Type<PlayerProfileSubmitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "player_profile_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProfileSubmitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PlayerProfileSubmitPayload::preferredName,
                    ByteBufCodecs.STRING_UTF8, PlayerProfileSubmitPayload::favoriteThing,
                    ByteBufCodecs.BOOL, PlayerProfileSubmitPayload::male,
                    PlayerProfileSubmitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerProfileSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String name = sanitize(payload.preferredName(), 48);
            String favorite = sanitize(payload.favoriteThing(), 64);
            if (name.isBlank() || favorite.isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.player_profile.validation.required"));
                return;
            }
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            data.setProfile(name, favorite, payload.male() ? 0 : 1);
            PlayerDataManager.get().setDirty();
            com.stardew.craft.farm.FarmInstanceRegistry.get().updateOwnerName(player.getUUID(), name);
            PlayerDataEventHandler.syncPlayerData(player, data);
        });
    }

    private static String sanitize(String value, int maxLength) {
        String sanitized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength).trim();
    }
}
