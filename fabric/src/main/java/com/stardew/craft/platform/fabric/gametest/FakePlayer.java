package com.stardew.craft.platform.fabric.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Public construction for the existing tests, backed by Fabric's real dummy connection. */
public class FakePlayer extends net.fabricmc.fabric.api.entity.FakePlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override public void displayClientMessage(Component message, boolean actionBar) {}
    @Override public boolean canHarmPlayer(Player other) { return false; }
    @Override public void die(DamageSource source) {}
}
