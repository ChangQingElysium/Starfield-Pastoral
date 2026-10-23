package com.stardew.craft.platform.mixin.event.mixin;


import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.OnDatapackSyncEvent;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerDatapackSyncMixin {
    @Inject(method = "placeNewPlayer", at = @At(value = "NEW",
            target = "net/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket"))
    private void stardewcraft$login(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        FabricEvents.GAME.post(new OnDatapackSyncEvent((PlayerList) (Object) this, player));
    }

    @Inject(method = "reloadResources", at = @At(value = "NEW",
            target = "net/minecraft/network/protocol/common/ClientboundUpdateTagsPacket"))
    private void stardewcraft$reload(CallbackInfo ci) {
        FabricEvents.GAME.post(new OnDatapackSyncEvent((PlayerList) (Object) this, null));
    }
}
