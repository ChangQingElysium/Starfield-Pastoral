package com.stardew.craft.mixin;

import com.stardew.craft.fishing.server.FishingSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disable vanilla FishingHook bite/lure logic when StardewCraft is controlling fishing.
 *
 * We still spawn a vanilla FishingHook for rendering (line + bobber), but we don't want vanilla to
 * randomly trigger its own bite dip / sounds on top of our SV-like timing.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookDisableVanillaBiteMixin {
	/** 让鱼钩在岩浆中不受伤害，支持岩浆钓鱼 */
	// PORT(1.20.1): FishingHook declares no lavaHurt in 1.20.1 or 1.21.1 (it is inherited from Entity), so this
	// optional injector matches nothing on either version; kept unchanged for parity (see bulk-port-gaps.md).
	// The explicit descriptor only lets the AP report the (intentional) miss as a suppressible TARGET warning.
	@SuppressWarnings("target")
	@Inject(method = "lavaHurt()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void stardewcraft$preventLavaDamage(CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true, require = 0)
	private void stardewcraft$disableVanillaCatchingFish(BlockPos pos, CallbackInfo ci) {
		FishingHook self = (FishingHook) (Object) this;
		if (self.level() == null || self.level().isClientSide) {
			return;
		}
		Entity owner = self.getOwner();
		if (!(owner instanceof ServerPlayer player)) {
			return;
		}
		FishingSessionManager mgr = FishingSessionManager.get(player.server);
		if (mgr.getState(player) != null) {
			ci.cancel();
		}
	}
}
