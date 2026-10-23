package com.stardew.craft.platform.fabric.gametest;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.level.ServerLevel;

/** Reuses each test actor until its world unloads, retaining its original profile and inventory. */
public final class FakePlayerFactory {
    private static final GameProfile DEFAULT = new GameProfile(
            net.fabricmc.fabric.api.entity.FakePlayer.DEFAULT_UUID, "[Minecraft]");
    private static final Map<Key, FakePlayer> PLAYERS = new HashMap<>();

    static {
        ServerWorldEvents.UNLOAD.register((server, level) -> unloadLevel(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (PLAYERS) {
                PLAYERS.keySet().removeIf(key -> key.level().getServer() == server);
            }
        });
    }

    public static FakePlayer getMinecraft(ServerLevel level) { return get(level, DEFAULT); }

    public static FakePlayer get(ServerLevel level, GameProfile profile) {
        java.util.Objects.requireNonNull(level, "level");
        java.util.Objects.requireNonNull(profile, "profile");
        synchronized (PLAYERS) {
            return PLAYERS.computeIfAbsent(new Key(level, profile), key -> new FakePlayer(key.level(), key.profile()));
        }
    }

    public static void unloadLevel(ServerLevel level) {
        synchronized (PLAYERS) { PLAYERS.keySet().removeIf(key -> key.level() == level); }
    }

    private record Key(ServerLevel level, GameProfile profile) {}
    private FakePlayerFactory() {}
}
