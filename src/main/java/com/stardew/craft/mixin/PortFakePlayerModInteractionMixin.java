package com.stardew.craft.mixin;

import com.stardew.craft.port.PortFakePlayers;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 {@code FakePlayer#openMenu}/{@code startRiding} results for StardewCraft menus and
 * seats (see {@link PortFakePlayers}). {@code openHorseInventory} is not needed: the mod has no horses and never
 * calls it.
 */
@Mixin(ServerPlayer.class)
public abstract class PortFakePlayerModInteractionMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$fakePlayerMenu121(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (PortFakePlayers.refusesMenu((ServerPlayer) (Object) this, provider)) cir.setReturnValue(OptionalInt.empty());
    }

    @Inject(method = "startRiding", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$fakePlayerRiding121(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (PortFakePlayers.refusesRiding((ServerPlayer) (Object) this, vehicle)) cir.setReturnValue(false);
    }
}
