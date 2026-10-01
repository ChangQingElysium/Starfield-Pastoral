package com.stardew.craft.mixin;

import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adjust fishing line origin so it visually connects to StardewCraft rod tip,
 * instead of vanilla rod tip.
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererLineOriginMixin {
	// Vanilla first-person uses getPointOnPlane((float)i * 0.525F, -0.1F)
	// Tune these two values to match StardewCraft rod model tip in first-person.
	private static final float STARDEWCRAFT_FP_PLANE_X = 0.25F;
	private static final float STARDEWCRAFT_FP_PLANE_Y = -0.16F;

	@SuppressWarnings("null")
	@Inject(
			method = "getPlayerHandPos(Lnet/minecraft/world/entity/player/Player;FF)Lnet/minecraft/world/phys/Vec3;",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void stardewcraft$adjustFirstPersonLineOrigin(Player player, float attackAnimFactor, float partialTick, CallbackInfoReturnable<Vec3> cir) {
		if (player == null) {
			return;
		}

		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean rodInMain = main.getItem() instanceof FishingRodItem;
		boolean rodInOff = off.getItem() instanceof FishingRodItem;
		boolean stardewRod = rodInMain || rodInOff;
		if (!stardewRod) {
			return;
		}

		// Only adjust the local player's first-person view.
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != player) {
			return;
		}
		if (!mc.options.getCameraType().isFirstPerson()) {
			return;
		}

		int i = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		// Keep vanilla handedness flip behavior.
		if (!main.canPerformAction(com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST)) {
			i = -i;
		}

		// Follow vanilla getPlayerHandPos math exactly; only plane X/Y are changed.
		double fovScale = 960.0 / (double) mc.options.fov().get().intValue();
		Vec3 offset = mc.gameRenderer
				.getMainCamera()
				.getNearPlane()
				.getPointOnPlane((float) i * STARDEWCRAFT_FP_PLANE_X, STARDEWCRAFT_FP_PLANE_Y)
				.scale(fovScale)
				.yRot(attackAnimFactor * 0.5F)
				.xRot(-attackAnimFactor * 0.7F);

		cir.setReturnValue(player.getEyePosition(partialTick).add(offset));
	}
}
