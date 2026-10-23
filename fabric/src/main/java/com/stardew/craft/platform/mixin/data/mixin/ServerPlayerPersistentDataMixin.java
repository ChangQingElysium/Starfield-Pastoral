package com.stardew.craft.platform.mixin.data.mixin;


import com.stardew.craft.platform.fabric.data.PersistentEntityData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPersistentDataMixin {
    // Vanilla restoreFrom does not touch our state. Copying on entry makes the
    // persisted section available before any separately adapted Clone callback
    // at return, without depending on mixin ordering. NeoForge copies this same
    // section regardless of keepEverything; the entire root is never copied.
    @Inject(
            method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At("HEAD"),
            require = 1)
    private void stardewcraft$copyPlayerPersisted(ServerPlayer original, boolean keepEverything, CallbackInfo callback) {
        PersistentEntityData.copyPlayerPersisted(original, (ServerPlayer) (Object) this);
    }
}
