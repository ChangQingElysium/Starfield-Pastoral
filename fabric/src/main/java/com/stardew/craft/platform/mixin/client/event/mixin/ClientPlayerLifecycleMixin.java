package com.stardew.craft.platform.mixin.client.event.mixin;


import com.stardew.craft.platform.fabric.client.event.FabricClientPlayerEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class ClientPlayerLifecycleMixin {
    // Match NeoForge's point before the game mode and player are cleared. This
    // includes nullable teardown while creating another integrated server.
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;gameMode:Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void stardewcraft$loggingOut(Screen screen, boolean transferring, CallbackInfo callback) {
        FabricClientPlayerEvents.loggingOut((Minecraft) (Object) this);
    }
}
