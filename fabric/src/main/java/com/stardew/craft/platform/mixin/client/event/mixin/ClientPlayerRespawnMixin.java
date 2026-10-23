package com.stardew.craft.platform.mixin.client.event.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.client.event.FabricClientPlayerEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayerRespawnMixin extends ClientCommonPacketListenerImpl {
    protected ClientPlayerRespawnMixin(Minecraft client, Connection connection, CommonListenerCookie cookie) {
        super(client, connection, cookie);
    }

    @WrapMethod(method = "handleRespawn")
    private void stardewcraft$respawn(ClientboundRespawnPacket packet, Operation<Void> original) {
        LocalPlayer oldPlayer = minecraft.player;
        original.call(packet);
        FabricClientPlayerEvents.cloned(minecraft, oldPlayer);
    }
}
