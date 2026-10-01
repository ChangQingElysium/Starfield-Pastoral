package com.stardew.craft.mixin;

import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): Forge 1.20.1 reads its per-connection handshake data straight from the Netty channel, so any
 * channel-less connection (a never-connected {@code new Connection(flow)}, e.g. Forge's own {@code FakePlayer} network
 * handler) throws inside Forge listeners such as {@code TierSortingRegistry.playerLoggedIn}
 * ({@code SimpleChannel#isRemotePresent}). NeoForge 1.21.1 has no such login listener and the same mod login path
 * runs to completion there. A connection without a channel never completed a handshake, so it simply has no
 * handshake data: return {@code null} (callers already treat that as "remote channel not present").
 */
@Mixin(value = NetworkHooks.class, remap = false)
public abstract class PortNetworkHooksChannelMixin {
    @Inject(method = {"getConnectionData", "getModMismatchData", "getChannelList"}, at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$noChannelNoData(Connection connection, CallbackInfoReturnable<Object> cir) {
        if (connection.channel() == null) {
            cir.setReturnValue(null);
        }
    }
}
