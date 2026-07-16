package com.stardew.craft.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** Resolves the Stardew character name shown by mod-owned user interfaces and messages. */
public final class PlayerDisplayName {
    private static final String DEFAULT_FALLBACK = "Player";

    private PlayerDisplayName() {
    }

    public static String get(ServerPlayer player) {
        return resolve(PlayerDataManager.get().getData(player.getUUID()), player.getGameProfile().getName());
    }

    public static String get(MinecraftServer server, UUID playerId) {
        PlayerStardewData data = PlayerDataManager.get().getData(playerId);
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return resolve(data, online.getGameProfile().getName());
        }
        if (data != null && !data.getLastKnownName().isBlank()) {
            return resolve(data, data.getLastKnownName());
        }
        var profileCache = server.getProfileCache();
        String fallback = profileCache == null ? "" : profileCache.get(playerId)
                .map(GameProfile::getName)
                .orElse("");
        if (fallback.isBlank()) {
            fallback = "Player-" + playerId.toString().substring(0, 8);
        }
        return resolve(data, fallback);
    }

    static String resolve(@Nullable PlayerStardewData data, @Nullable String fallback) {
        String preferredName = data == null ? "" : data.getPreferredName().trim();
        if (!preferredName.isBlank()) {
            return preferredName;
        }
        String normalizedFallback = fallback == null ? "" : fallback.trim();
        return normalizedFallback.isBlank() ? DEFAULT_FALLBACK : normalizedFallback;
    }
}
