package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adjust fishing line origin so it visually connects to StardewCraft rod tip,
 * instead of vanilla rod tip.
 *
 * <p>PORT(1.20.1): 1.21 computes the hand position in {@code getPlayerHandPos} (replaced at HEAD there, only the
 * near-plane point X/Y differ from vanilla). 1.20.1 inlines the same first-person math into {@code render}, so
 * the single {@code NearPlane#getPointOnPlane(i * 0.525F, -0.1F)} call of that branch is redirected instead; the
 * fov scale, swing rotations and eye-position offset that follow are the vanilla ones the 1.21 handler copied.</p>
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererLineOriginMixin {
	// Vanilla first-person uses getPointOnPlane((float)i * 0.525F, -0.1F)
	// Tune these two values to match StardewCraft rod model tip in first-person.
	private static final float STARDEWCRAFT_FP_PLANE_X = 0.25F;
	private static final float STARDEWCRAFT_FP_PLANE_Y = -0.16F;

	@SuppressWarnings("null")
	@Redirect(
			method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera$NearPlane;getPointOnPlane(FF)Lnet/minecraft/world/phys/Vec3;"),
			require = 0
	)
	private Vec3 stardewcraft$adjustFirstPersonLineOrigin(Camera.NearPlane plane, float planeX, float planeY,
														   FishingHook hook, float entityYaw, float partialTick,
														   PoseStack poseStack, MultiBufferSource buffers, int light) {
		Player player = hook.getPlayerOwner();
		if (player == null) {
			return plane.getPointOnPlane(planeX, planeY);
		}

		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean rodInMain = main.getItem() instanceof FishingRodItem;
		boolean rodInOff = off.getItem() instanceof FishingRodItem;
		boolean stardewRod = rodInMain || rodInOff;
		if (!stardewRod) {
			return plane.getPointOnPlane(planeX, planeY);
		}

		// Only adjust the local player's first-person view.
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != player || !mc.options.getCameraType().isFirstPerson()) {
			return plane.getPointOnPlane(planeX, planeY);
		}

		// Vanilla passes (float) i * 0.525F with the handedness/FISHING_ROD_CAST flip already applied.
		int i = planeX < 0.0F ? -1 : 1;
		return plane.getPointOnPlane((float) i * STARDEWCRAFT_FP_PLANE_X, STARDEWCRAFT_FP_PLANE_Y);
	}
}
