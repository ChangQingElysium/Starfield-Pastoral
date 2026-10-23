package com.stardew.craft.platform.mixin.client.event.registration.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.client.event.registration.FabricSystemChatEvents;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public abstract class SystemChatReceivedMixin {
    // IFEQ #0/#1 are the hide-matched-names/blocked-user early return. #2 selects overlay
    // versus chat, after the filter but before display, logging, or narration uses message.
    @Inject(method = "handleSystemMessage", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, ordinal = 2), cancellable = true)
    private void stardewcraft$system(Component original, boolean overlay, CallbackInfo callback, @Local(argsOnly = true) LocalRef<Component> message) {
        var received = FabricSystemChatEvents.receive(message.get(), overlay);
        if (received == null) callback.cancel(); else message.set(received);
    }
}
