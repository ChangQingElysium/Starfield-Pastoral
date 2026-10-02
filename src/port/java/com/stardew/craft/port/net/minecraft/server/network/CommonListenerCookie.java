package com.stardew.craft.port.net.minecraft.server.network;

import com.mojang.authlib.GameProfile;

/**
 * 1.21.1 {@code CommonListenerCookie}. 1.20.1 packet listeners take no cookie, so this only keeps
 * the data callers build; ServerGamePacketListenerImpl is constructed without it on 1.20.1.
 */
public record CommonListenerCookie(GameProfile gameProfile, int latency, boolean transferred) {
    public static CommonListenerCookie createInitial(GameProfile gameProfile, boolean transferred) {
        return new CommonListenerCookie(gameProfile, 0, transferred);
    }
}
