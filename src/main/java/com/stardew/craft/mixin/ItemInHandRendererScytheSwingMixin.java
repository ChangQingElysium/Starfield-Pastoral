package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.client.FishingRodCastAnimationState;
import com.stardew.craft.client.weapon.SkillFailShakeState;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.client.ScytheSwingAnimationState;
import com.stardew.craft.client.weapon.WeaponSkillAnimationClient;
import com.stardew.craft.client.weapon.animation.WeaponSkillAnimation;
import com.stardew.craft.client.weapon.animation.WeaponSkillAnimationRegistry;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.player.AbstractClientPlayer;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 *  -> ?
 *
 * ?
 */
@SuppressWarnings("unused")
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class ItemInHandRendererScytheSwingMixin {
	private static final int TOOL_NONE = 0;
	private static final int TOOL_SCYTHE = 1;
	private static final int TOOL_HOE = 2;
	private static final int TOOL_FISHING_ROD = 3;
	private static final int TOOL_WEAPON_SKILL = 4;

	private static final ThreadLocal<Integer> STARDEWCRAFT_TOOL_KIND = ThreadLocal.withInitial(() -> TOOL_NONE);
	private static final ThreadLocal<Float> STARDEWCRAFT_PARTIAL_TICK = ThreadLocal.withInitial(() -> 0.0F);
	private static final ThreadLocal<InteractionHand> STARDEWCRAFT_HAND = new ThreadLocal<>();
	private static final ThreadLocal<ItemStack> STARDEWCRAFT_STACK = new ThreadLocal<>();

	// p1/p2  Blockbench translation  /16?
	private static final float P1_RX = -69.04F;
	private static final float P1_RY = -32.78F;
	private static final float P1_RZ = -102.22F;
	private static final float P1_TX = 0.0F;
	private static final float P1_TY = 4.25F;
	private static final float P1_TZ = 0.0F;

	private static final float P2_RX = -72.43F;
	private static final float P2_RY = -6.48F;
	private static final float P2_RZ = 6.73F;
	private static final float P2_TX = -13.5F;
	private static final float P2_TY = 4.25F;
	private static final float P2_TZ = 0.0F;

	//  display?assets/.../models/item/scythe.json?
	private static final float BASE_RH_RX = 22.75F;
	private static final float BASE_RH_RY = -71.0F;
	private static final float BASE_RH_RZ = 0.0F;
	private static final float BASE_RH_TX = 0.0F;
	private static final float BASE_RH_TY = 4.25F;
	private static final float BASE_RH_TZ = 0.0F;

	private static final float BASE_LH_RX = -22.75F;
	private static final float BASE_LH_RY = 71.0F;
	private static final float BASE_LH_RZ = 0.0F;
	private static final float BASE_LH_TX = 0.0F;
	private static final float BASE_LH_TY = 3.25F;
	private static final float BASE_LH_TZ = 0.0F;

	// 0.5s ?..1?
	private static final float HOLD_P1_UNTIL = 0.06F;
	private static final float REACH_P2_AT = 0.58F;

	// ====== Hoe first-person keyframes (p1: , p2: ? ======
	private static final float HOE_P1_RX = -114.0F;
	private static final float HOE_P1_RY = -87.5F;
	private static final float HOE_P1_RZ = -180.0F;
	private static final float HOE_P1_TX = 2.0F;
	private static final float HOE_P1_TY = 8.0F;
	private static final float HOE_P1_TZ = 0.75F;

	private static final float HOE_P2_RX = 138.75F;
	private static final float HOE_P2_RY = -87.5F;
	private static final float HOE_P2_RZ = 180.0F;
	private static final float HOE_P2_TX = 2.0F;
	private static final float HOE_P2_TY = 3.5F;
	private static final float HOE_P2_TZ = -4.25F;

	// Hoe base display = hoe.json firstperson transforms
	private static final float HOE_BASE_RH_RX = -144.0F;
	private static final float HOE_BASE_RH_RY = -87.5F;
	private static final float HOE_BASE_RH_RZ = -180.0F;
	private static final float HOE_BASE_RH_TX = 2.0F;
	private static final float HOE_BASE_RH_TY = 4.25F;
	private static final float HOE_BASE_RH_TZ = 0.0F;

	private static final float HOE_BASE_LH_RX = 41.67F;
	private static final float HOE_BASE_LH_RY = 75.39F;
	private static final float HOE_BASE_LH_RZ = -11.11F;
	private static final float HOE_BASE_LH_TX = 2.0F;
	private static final float HOE_BASE_LH_TY = 4.25F;
	private static final float HOE_BASE_LH_TZ = 0.0F;

	// Hoe strike curve segments (0..1)
	private static final float HOE_DIRECT_RAISE_END = 0.22F;
	private static final float HOE_DIRECT_HOLD_END = 0.32F;
	private static final float HOE_DIRECT_HIT_END = 0.46F;
	private static final float HOE_CHARGED_HIT_END = 0.12F;

	// ====== Fishing rod first-person keyframes (p5: charge hold, p6: cast out hold) ======
	// base display for fishing rod = rod_base.json firstperson transforms
	private static final float ROD_BASE_RH_RX = -166.5F;
	private static final float ROD_BASE_RH_RY = 79.5F;
	private static final float ROD_BASE_RH_RZ = 180.0F;
	private static final float ROD_BASE_RH_TX = 1.0F;
	private static final float ROD_BASE_RH_TY = 1.0F;
	private static final float ROD_BASE_RH_TZ = 0.0F;

	private static final float ROD_BASE_LH_RX = 12.25F;
	private static final float ROD_BASE_LH_RY = -68.25F;
	private static final float ROD_BASE_LH_RZ = 0.0F;
	private static final float ROD_BASE_LH_TX = 2.5F;
	private static final float ROD_BASE_LH_TY = 1.0F;
	private static final float ROD_BASE_LH_TZ = 1.5F;

	// p5/p6 are authored as right-hand poses; left-hand mirrors y/z rotations and x translation.
	private static final float ROD_P5_RX = -122.25F;
	private static final float ROD_P5_RY = 79.5F;
	private static final float ROD_P5_RZ = -180.0F;
	private static final float ROD_P5_TX = 1.0F;
	private static final float ROD_P5_TY = 3.5F;
	private static final float ROD_P5_TZ = 0.0F;

	private static final float ROD_P6_RX = 152.25F;
	private static final float ROD_P6_RY = 79.5F;
	private static final float ROD_P6_RZ = 180.0F;
	private static final float ROD_P6_TX = 1.0F;
	private static final float ROD_P6_TY = 2.0F;
	private static final float ROD_P6_TZ = -2.5F;

	private static final float ROD_CHARGE_RAISE_TICKS = 3.0F;
	private static final float ROD_CHARGE_SHAKE_DEG = 1.25F;
	private static final float ROD_CHARGE_SHAKE_SPEED = 6.0F;

	// Fish rod animation should rotate around the grip/handle, not the model origin.
	// Values are in "pixels" (Blockbench units) and converted to blocks by /16.
	private static final float ROD_GRIP_PIVOT_PX_X = 0.0F;
	private static final float ROD_GRIP_PIVOT_PX_Y = -6.0F;
	private static final float ROD_GRIP_PIVOT_PX_Z = 0.0F;

	@Shadow
	private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
		throw new IllegalStateException("Mixin failed to shadow applyItemArmAttackTransform");
	}

	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD")
	)
	private void stardewcraft$scytheFirstPersonSwing(
			AbstractClientPlayer player,
			float partialTick,
			float pitch,
			InteractionHand hand,
			float swingProgress,
			ItemStack stack,
			float equipProgress,
			PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci
	) {
		int kind = TOOL_NONE;
		if ((hand == InteractionHand.MAIN_HAND) && (stack.getItem() instanceof ScytheItem
				|| (stack.getItem() instanceof IStardewWeapon && ScytheSwingAnimationState.isActive()))) {
			kind = TOOL_SCYTHE;
		} else if (stack.getItem() instanceof HoeItem) {
			kind = TOOL_HOE;
		} else if (stack.getItem() instanceof FishingRodItem) {
			kind = TOOL_FISHING_ROD;
		} else if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof IStardewWeapon) {
			kind = TOOL_WEAPON_SKILL;
		}
		STARDEWCRAFT_TOOL_KIND.set(kind);
		STARDEWCRAFT_PARTIAL_TICK.set(partialTick);
		STARDEWCRAFT_HAND.set(hand);
		if (kind == TOOL_WEAPON_SKILL) {
			STARDEWCRAFT_STACK.set(stack);
		}
	}

	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN")
	)
	private void stardewcraft$scytheFirstPersonSwingEnd(
			AbstractClientPlayer player,
			float partialTick,
			float pitch,
			InteractionHand hand,
			float swingProgress,
			ItemStack stack,
			float equipProgress,
			PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci
	) {
		STARDEWCRAFT_TOOL_KIND.set(TOOL_NONE);
		STARDEWCRAFT_PARTIAL_TICK.set(0.0F);
		STARDEWCRAFT_HAND.remove();
		STARDEWCRAFT_STACK.remove();
	}

	@Redirect(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"
			)
	)
	private void stardewcraft$redirectAttackTransform(
			net.minecraft.client.renderer.ItemInHandRenderer instance,
			PoseStack poseStack,
			HumanoidArm arm,
			float swingProgress
	) {
		int kind = STARDEWCRAFT_TOOL_KIND.get();
		if (kind == TOOL_NONE) {
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}

		if (kind == TOOL_WEAPON_SKILL) {
			float partialTick = STARDEWCRAFT_PARTIAL_TICK.get();
			float t = WeaponSkillAnimationClient.getProgress(partialTick);
			if (t < 0.0f) {
				var player = net.minecraft.client.Minecraft.getInstance().player;
				var action = player == null ? null
						: com.stardew.craft.client.weapon.LavaKatanaVisuals.action(player, partialTick);
				if (action != null) {
					com.stardew.craft.client.weapon.animation.LavaKatanaSlashAnimation.INSTANCE
							.apply(poseStack, arm, action.progress());
					return;
				}
				applyItemArmAttackTransform(poseStack, arm, swingProgress);
				return;
			}

			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.player instanceof LivingEntitySwingAccessor swingAccessor) {
				swingAccessor.stardewcraft$setSwingTime(0);
				swingAccessor.stardewcraft$setSwinging(false);
			}

			ItemStack stack = STARDEWCRAFT_STACK.get();
			if (stack == null || !(stack.getItem() instanceof IStardewWeapon weaponItem)) {
				applyItemArmAttackTransform(poseStack, arm, swingProgress);
				return;
			}

			String activeWeaponId = WeaponSkillAnimationClient.getWeaponId();
			String activeSkillId = WeaponSkillAnimationClient.getSkillId();
			if (activeWeaponId != null && !activeWeaponId.equals(weaponItem.getWeaponId())) {
				applyItemArmAttackTransform(poseStack, arm, swingProgress);
				return;
			}
			String resolvedWeaponId = activeWeaponId != null ? activeWeaponId : weaponItem.getWeaponId();
			WeaponSkillAnimation animation = WeaponSkillAnimationRegistry.getAnimation(resolvedWeaponId, activeSkillId);
			if (!animation.apply(poseStack, arm, t)) {
				applyItemArmAttackTransform(poseStack, arm, swingProgress);
			}
			return;
		}

		if (kind == TOOL_HOE) {
			if (applyHoeStrikeIfActive(poseStack, arm)) {
				return;
			}
			if (applyHoeChargeHoldIfActive(poseStack, arm)) {
				return;
			}
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}

		// Fishing rod pose is applied in a separate inject before renderItem() to ensure it runs
		// even while the item is being used (charging).
		if (kind == TOOL_FISHING_ROD) {
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}

		float effectiveProgress;
		boolean timed = ScytheSwingAnimationState.isActive();
		if (timed) {
			float partialTick = STARDEWCRAFT_PARTIAL_TICK.get();
			effectiveProgress = ScytheSwingAnimationState.getProgress(partialTick);
		} else {
			effectiveProgress = swingProgress;
		}

		// ?
		if (!timed && effectiveProgress <= 1.0E-4F) {
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}
		applyScytheKeyframedSwing(poseStack, arm, effectiveProgress);
	}

	@SuppressWarnings("null")
	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
					shift = At.Shift.BEFORE
			)
	)
	private void stardewcraft$applySkillFailShakeBeforeRenderItem(
			AbstractClientPlayer player,
			float partialTick,
			float pitch,
			InteractionHand hand,
			float swingProgress,
			ItemStack stack,
			float equipProgress,
			PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci
	) {
		if (!(stack.getItem() instanceof IStardewWeapon)) {
			return;
		}
		if (!SkillFailShakeState.isActive(hand)) {
			return;
		}

		float t = SkillFailShakeState.getProgress01(hand, partialTick);
		float fade = 1.0F - t;
		float time = (player.tickCount + partialTick) / 20.0F;
		float shake = (float) Math.sin(time * (float) (Math.PI * 2.0) * 9.0F);
		float ampDeg = 2.2F * (0.35F + 0.65F * fade);
		float ry = shake * (ampDeg * 0.35F);
		float rz = shake * (ampDeg * 0.9F);
		poseStack.mulPose(eulerXYZDegreesToQuat(0.0F, ry, rz));
	}

	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
					shift = At.Shift.BEFORE
			)
	)
	private void stardewcraft$applyFishingRodPoseBeforeRenderItem(
			AbstractClientPlayer player,
			float partialTick,
			float pitch,
			InteractionHand hand,
			float swingProgress,
			ItemStack stack,
			float equipProgress,
			PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci
	) {
		if (STARDEWCRAFT_TOOL_KIND.get() != TOOL_FISHING_ROD) {
			return;
		}
		applyFishingRodPoseIfActive(poseStack, player, hand, partialTick);
	}

	private static boolean applyFishingRodPoseIfActive(PoseStack poseStack, AbstractClientPlayer player, InteractionHand hand, float partialTick) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		@SuppressWarnings("null")
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof FishingRodItem)) {
			return false;
		}

		boolean castActive = FishingRodItem.isCastActive(stack);
		boolean isUsingThisHand = player.isUsingItem() && player.getUsedItemHand() == hand;
		var phase = FishingRodCastAnimationState.getPhase(hand, isUsingThisHand, castActive);
		if (phase == FishingRodCastAnimationState.Phase.IDLE) {
			return false;
		}

		HumanoidArm renderedArm = (hand == InteractionHand.MAIN_HAND) ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean right = renderedArm == HumanoidArm.RIGHT;
		float bRx = right ? ROD_BASE_RH_RX : ROD_BASE_LH_RX;
		float bRy = right ? ROD_BASE_RH_RY : ROD_BASE_LH_RY;
		float bRz = right ? ROD_BASE_RH_RZ : ROD_BASE_LH_RZ;
		float bTx = right ? ROD_BASE_RH_TX : ROD_BASE_LH_TX;
		float bTy = right ? ROD_BASE_RH_TY : ROD_BASE_LH_TY;
		float bTz = right ? ROD_BASE_RH_TZ : ROD_BASE_LH_TZ;

		float p5Ry = right ? ROD_P5_RY : -ROD_P5_RY;
		float p5Rz = right ? ROD_P5_RZ : -ROD_P5_RZ;
		float p5Tx = right ? ROD_P5_TX : -ROD_P5_TX;

		float p6Ry = right ? ROD_P6_RY : -ROD_P6_RY;
		float p6Rz = right ? ROD_P6_RZ : -ROD_P6_RZ;
		float p6Tx = right ? ROD_P6_TX : -ROD_P6_TX;

		float tRx;
		float tRy;
		float tRz;
		float tTx;
		float tTy;
		float tTz;

		if (phase == FishingRodCastAnimationState.Phase.CHARGING) {
			int activeTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
			float t = (activeTicks + partialTick) / ROD_CHARGE_RAISE_TICKS;
			t = Mth.clamp(t, 0.0F, 1.0F);
			float inv = 1.0F - t;
			float easeOutCubic = 1.0F - (inv * inv * inv);

			tRx = Mth.lerp(easeOutCubic, bRx, ROD_P5_RX);
			tRy = Mth.lerp(easeOutCubic, bRy, p5Ry);
			tRz = Mth.lerp(easeOutCubic, bRz, p5Rz);
			tTx = Mth.lerp(easeOutCubic, bTx, p5Tx);
			tTy = Mth.lerp(easeOutCubic, bTy, ROD_P5_TY);
			tTz = Mth.lerp(easeOutCubic, bTz, ROD_P5_TZ);

			// Bow-like subtle shake while charging
			float time = (player.tickCount + partialTick) / 20.0F;
			float shake = (float) Math.sin(time * (float) (Math.PI * 2.0) * ROD_CHARGE_SHAKE_SPEED);
			float amp = ROD_CHARGE_SHAKE_DEG * (0.35F + 0.65F * t);
			tRy += shake * (amp * 0.35F);
			tRz += shake * (amp * 0.55F);
		} else if (phase == FishingRodCastAnimationState.Phase.CAST_OUT) {
			float s = FishingRodCastAnimationState.getCastOutProgress01(hand, partialTick);
			// No overshoot: keep motion monotonic (prevents "down then up" bounce).
			float inv = 1.0F - s;
			float u = 1.0F - (inv * inv * inv);
			tRx = Mth.lerp(u, ROD_P5_RX, ROD_P6_RX);
			tRy = Mth.lerp(u, p5Ry, p6Ry);
			tRz = Mth.lerp(u, p5Rz, p6Rz);
			tTx = Mth.lerp(u, p5Tx, p6Tx);
			tTy = Mth.lerp(u, ROD_P5_TY, ROD_P6_TY);
			tTz = Mth.lerp(u, ROD_P5_TZ, ROD_P6_TZ);
		} else if (phase == FishingRodCastAnimationState.Phase.REEL_IN) {
			float s = FishingRodCastAnimationState.getReelInProgress01(hand, partialTick);
			float inv = 1.0F - s;
			float easeOutCubic = 1.0F - (inv * inv * inv);
			tRx = Mth.lerp(easeOutCubic, ROD_P6_RX, bRx);
			tRy = Mth.lerp(easeOutCubic, p6Ry, bRy);
			tRz = Mth.lerp(easeOutCubic, p6Rz, bRz);
			tTx = Mth.lerp(easeOutCubic, p6Tx, bTx);
			tTy = Mth.lerp(easeOutCubic, ROD_P6_TY, bTy);
			tTz = Mth.lerp(easeOutCubic, ROD_P6_TZ, bTz);
		} else {
			// CAST_HELD: hold p6 while bobber is out
			tRx = ROD_P6_RX;
			tRy = p6Ry;
			tRz = p6Rz;
			tTx = p6Tx;
			tTy = ROD_P6_TY;
			tTz = ROD_P6_TZ;
		}

		Vector3f pivot = new Vector3f(ROD_GRIP_PIVOT_PX_X / 16.0F, ROD_GRIP_PIVOT_PX_Y / 16.0F, ROD_GRIP_PIVOT_PX_Z / 16.0F);
		applyDeltaFromBaseDisplayWithPivot(poseStack, pivot, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
		return true;
	}

	private static float easeOutBack(float t, float overshoot) {
		float x = t - 1.0F;
		return 1.0F + (overshoot + 1.0F) * x * x * x + overshoot * x * x;
	}

	@SuppressWarnings("null")
	private static boolean applyHoeStrikeIfActive(PoseStack poseStack, HumanoidArm arm) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}

		var player = mc.player;
		ItemStack used = (arm == player.getMainArm()) ? player.getMainHandItem() : player.getOffhandItem();
		if (!(used.getItem() instanceof HoeItem)) {
			return false;
		}

		@SuppressWarnings("null")
		CustomData data = used.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag();
		if (!tag.contains(HoeItem.NBT_STRIKE_START_TICK)) {
			return false;
		}

		int startTick = tag.getInt(HoeItem.NBT_STRIKE_START_TICK);
		int duration = tag.contains(HoeItem.NBT_STRIKE_DURATION_TICKS) ? tag.getInt(HoeItem.NBT_STRIKE_DURATION_TICKS) : HoeItem.STRIKE_TOTAL_TICKS;
		if (duration <= 0) {
			duration = HoeItem.STRIKE_TOTAL_TICKS;
		}
		boolean fromP1 = tag.contains(HoeItem.NBT_STRIKE_FROM_P1) && tag.getBoolean(HoeItem.NBT_STRIKE_FROM_P1);

		float partialTick = STARDEWCRAFT_PARTIAL_TICK.get();
		float elapsed = (player.tickCount + partialTick) - (float) startTick;
		float s = elapsed / (float) duration;
		s = Mth.clamp(s, 0.0F, 1.0F);

		if (s >= 1.0F) {
			// ?
			tag.remove(HoeItem.NBT_STRIKE_START_TICK);
			tag.remove(HoeItem.NBT_STRIKE_DURATION_TICKS);
			tag.remove(HoeItem.NBT_STRIKE_FROM_P1);
			if (tag.isEmpty()) {
				used.remove(DataComponents.CUSTOM_DATA);
			} else {
				used.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		}

		applyHoeStrike(poseStack, arm, s, fromP1);
		return true;
	}

	private static boolean applyHoeChargeHoldIfActive(PoseStack poseStack, HumanoidArm arm) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		var player = mc.player;
		if (!player.isUsingItem()) {
			return false;
		}
		InteractionHand hand = player.getUsedItemHand();
		@SuppressWarnings("null")
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof HoeItem hoe)) {
			return false;
		}
		if (hoe.getTier().getMaxChargeLevel() <= 0) {
			return false;
		}
		HumanoidArm usedArm = (hand == InteractionHand.MAIN_HAND) ? player.getMainArm() : player.getMainArm().getOpposite();
		if (arm != usedArm) {
			return false;
		}

		int activeTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
		// ?
		if (activeTicks < HoeItem.TAP_THRESHOLD_TICKS) {
			return false;
		}
		float partialTick = STARDEWCRAFT_PARTIAL_TICK.get();
		float t = (activeTicks + partialTick - HoeItem.TAP_THRESHOLD_TICKS) / (float) HoeItem.RAISE_TO_P1_TICKS;
		t = Mth.clamp(t, 0.0F, 1.0F);
		float easeOutCubic = 1.0F - (float) Math.pow(1.0F - t, 3);

		boolean right = arm == HumanoidArm.RIGHT;
		float bRx = right ? HOE_BASE_RH_RX : HOE_BASE_LH_RX;
		float bRy = right ? HOE_BASE_RH_RY : HOE_BASE_LH_RY;
		float bRz = right ? HOE_BASE_RH_RZ : HOE_BASE_LH_RZ;
		float bTx = right ? HOE_BASE_RH_TX : HOE_BASE_LH_TX;
		float bTy = right ? HOE_BASE_RH_TY : HOE_BASE_LH_TY;
		float bTz = right ? HOE_BASE_RH_TZ : HOE_BASE_LH_TZ;

		float p1Ry = right ? HOE_P1_RY : -HOE_P1_RY;
		float p1Rz = right ? HOE_P1_RZ : -HOE_P1_RZ;
		float p1Tx = right ? HOE_P1_TX : -HOE_P1_TX;

		float tRx = Mth.lerp(easeOutCubic, bRx, HOE_P1_RX);
		float tRy = Mth.lerp(easeOutCubic, bRy, p1Ry);
		float tRz = Mth.lerp(easeOutCubic, bRz, p1Rz);
		float tTx = Mth.lerp(easeOutCubic, bTx, p1Tx);
		float tTy = Mth.lerp(easeOutCubic, bTy, HOE_P1_TY);
		float tTz = Mth.lerp(easeOutCubic, bTz, HOE_P1_TZ);

		applyDeltaFromBaseDisplay(poseStack, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
		return true;
	}

	private static void applyHoeStrike(PoseStack poseStack, HumanoidArm arm, float progress, boolean fromP1) {
		float s = Mth.clamp(progress, 0.0F, 1.0F);
		boolean right = arm == HumanoidArm.RIGHT;

		float bRx = right ? HOE_BASE_RH_RX : HOE_BASE_LH_RX;
		float bRy = right ? HOE_BASE_RH_RY : HOE_BASE_LH_RY;
		float bRz = right ? HOE_BASE_RH_RZ : HOE_BASE_LH_RZ;
		float bTx = right ? HOE_BASE_RH_TX : HOE_BASE_LH_TX;
		float bTy = right ? HOE_BASE_RH_TY : HOE_BASE_LH_TY;
		float bTz = right ? HOE_BASE_RH_TZ : HOE_BASE_LH_TZ;

		float p1Ry = right ? HOE_P1_RY : -HOE_P1_RY;
		float p1Rz = right ? HOE_P1_RZ : -HOE_P1_RZ;
		float p1Tx = right ? HOE_P1_TX : -HOE_P1_TX;

		float p2Ry = right ? HOE_P2_RY : -HOE_P2_RY;
		float p2Rz = right ? HOE_P2_RZ : -HOE_P2_RZ;
		float p2Tx = right ? HOE_P2_TX : -HOE_P2_TX;

		float tRx;
		float tRy;
		float tRz;
		float tTx;
		float tTy;
		float tTz;

		if (fromP1) {
			if (s <= HOE_CHARGED_HIT_END) {
				float u = s / HOE_CHARGED_HIT_END;
				float inv = 1.0F - u;
				float easeOutCubic = 1.0F - (inv * inv * inv);
				float alpha = easeOutCubic * 1.12F;
				tRx = Mth.lerp(alpha, HOE_P1_RX, HOE_P2_RX);
				tRy = Mth.lerp(alpha, p1Ry, p2Ry);
				tRz = Mth.lerp(alpha, p1Rz, p2Rz);
				tTx = Mth.lerp(alpha, p1Tx, p2Tx);
				tTy = Mth.lerp(alpha, HOE_P1_TY, HOE_P2_TY);
				tTz = Mth.lerp(alpha, HOE_P1_TZ, HOE_P2_TZ);
			} else {
				float v = (s - HOE_CHARGED_HIT_END) / (1.0F - HOE_CHARGED_HIT_END);
				float inv2 = 1.0F - v;
				float easeOutCubic = 1.0F - (inv2 * inv2 * inv2);
				tRx = Mth.lerp(easeOutCubic, HOE_P2_RX, bRx);
				tRy = Mth.lerp(easeOutCubic, p2Ry, bRy);
				tRz = Mth.lerp(easeOutCubic, p2Rz, bRz);
				tTx = Mth.lerp(easeOutCubic, p2Tx, bTx);
				tTy = Mth.lerp(easeOutCubic, HOE_P2_TY, bTy);
				tTz = Mth.lerp(easeOutCubic, HOE_P2_TZ, bTz);
			}
		} else {
			if (s <= HOE_DIRECT_RAISE_END) {
				float u = s / HOE_DIRECT_RAISE_END;
				float inv = 1.0F - u;
				float easeOutQuad = 1.0F - (inv * inv);
				tRx = Mth.lerp(easeOutQuad, bRx, HOE_P1_RX);
				tRy = Mth.lerp(easeOutQuad, bRy, p1Ry);
				tRz = Mth.lerp(easeOutQuad, bRz, p1Rz);
				tTx = Mth.lerp(easeOutQuad, bTx, p1Tx);
				tTy = Mth.lerp(easeOutQuad, bTy, HOE_P1_TY);
				tTz = Mth.lerp(easeOutQuad, bTz, HOE_P1_TZ);
			} else if (s <= HOE_DIRECT_HOLD_END) {
				tRx = HOE_P1_RX;
				tRy = p1Ry;
				tRz = p1Rz;
				tTx = p1Tx;
				tTy = HOE_P1_TY;
				tTz = HOE_P1_TZ;
			} else if (s <= HOE_DIRECT_HIT_END) {
				float u = (s - HOE_DIRECT_HOLD_END) / (HOE_DIRECT_HIT_END - HOE_DIRECT_HOLD_END);
				float easeInCubic = u * u * u;
				float alpha = easeInCubic * 1.10F;
				tRx = Mth.lerp(alpha, HOE_P1_RX, HOE_P2_RX);
				tRy = Mth.lerp(alpha, p1Ry, p2Ry);
				tRz = Mth.lerp(alpha, p1Rz, p2Rz);
				tTx = Mth.lerp(alpha, p1Tx, p2Tx);
				tTy = Mth.lerp(alpha, HOE_P1_TY, HOE_P2_TY);
				tTz = Mth.lerp(alpha, HOE_P1_TZ, HOE_P2_TZ);
			} else {
				float v = (s - HOE_DIRECT_HIT_END) / (1.0F - HOE_DIRECT_HIT_END);
				float inv2 = 1.0F - v;
				float easeOutCubic = 1.0F - (inv2 * inv2 * inv2);
				tRx = Mth.lerp(easeOutCubic, HOE_P2_RX, bRx);
				tRy = Mth.lerp(easeOutCubic, p2Ry, bRy);
				tRz = Mth.lerp(easeOutCubic, p2Rz, bRz);
				tTx = Mth.lerp(easeOutCubic, p2Tx, bTx);
				tTy = Mth.lerp(easeOutCubic, HOE_P2_TY, bTy);
				tTz = Mth.lerp(easeOutCubic, HOE_P2_TZ, bTz);
			}
		}

		applyDeltaFromBaseDisplay(poseStack, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
	}

	private static void applyScytheKeyframedSwing(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
		// 
		// -  p1
		// - p1 -> p2?
		// - p2 -> (base)
		float s = Mth.clamp(swingProgress, 0.0F, 1.0F);

		boolean right = arm == HumanoidArm.RIGHT;
		float bRx = right ? BASE_RH_RX : BASE_LH_RX;
		float bRy = right ? BASE_RH_RY : BASE_LH_RY;
		float bRz = right ? BASE_RH_RZ : BASE_LH_RZ;
		float bTx = right ? BASE_RH_TX : BASE_LH_TX;
		float bTy = right ? BASE_RH_TY : BASE_LH_TY;
		float bTz = right ? BASE_RH_TZ : BASE_LH_TZ;

		float tRx;
		float tRy;
		float tRz;
		float tTx;
		float tTy;
		float tTz;

		if (s <= HOLD_P1_UNTIL) {
			//  p1
			tRx = P1_RX;
			tRy = right ? P1_RY : -P1_RY;
			tRz = right ? P1_RZ : -P1_RZ;
			tTx = right ? P1_TX : -P1_TX;
			tTy = P1_TY;
			tTz = P1_TZ;
		} else if (s <= REACH_P2_AT) {
			// p1 -> p2
			float u = (s - HOLD_P1_UNTIL) / (REACH_P2_AT - HOLD_P1_UNTIL);
			float inv = 1.0F - u;
			float easeOutCubic = 1.0F - (inv * inv * inv);
			// 
			float alpha = easeOutCubic * 1.08F;
			tRx = Mth.lerp(alpha, P1_RX, P2_RX);
			tRy = Mth.lerp(alpha, right ? P1_RY : -P1_RY, right ? P2_RY : -P2_RY);
			tRz = Mth.lerp(alpha, right ? P1_RZ : -P1_RZ, right ? P2_RZ : -P2_RZ);
			tTx = Mth.lerp(alpha, right ? P1_TX : -P1_TX, right ? P2_TX : -P2_TX);
			tTy = Mth.lerp(alpha, P1_TY, P2_TY);
			tTz = Mth.lerp(alpha, P1_TZ, P2_TZ);
		} else {
			// p2 -> base ease-out
			float v = (s - REACH_P2_AT) / (1.0F - REACH_P2_AT);
			float inv2 = 1.0F - v;
			float easeOutQuad = 1.0F - (inv2 * inv2);
			tRx = Mth.lerp(easeOutQuad, P2_RX, bRx);
			tRy = Mth.lerp(easeOutQuad, right ? P2_RY : -P2_RY, bRy);
			tRz = Mth.lerp(easeOutQuad, right ? P2_RZ : -P2_RZ, bRz);
			tTx = Mth.lerp(easeOutQuad, right ? P2_TX : -P2_TX, bTx);
			tTy = Mth.lerp(easeOutQuad, P2_TY, bTy);
			tTz = Mth.lerp(easeOutQuad, P2_TZ, bTz);
		}

		applyDeltaFromBaseDisplay(poseStack, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
	}

	/**
	 * ?display  deltadelta * base = target?
	 *  target?p1/p2?
	 */
	private static void applyDeltaFromBaseDisplay(
			PoseStack poseStack,
			float baseRx,
			float baseRy,
			float baseRz,
			float baseTxPx,
			float baseTyPx,
			float baseTzPx,
			float targetRx,
			float targetRy,
			float targetRz,
			float targetTxPx,
			float targetTyPx,
			float targetTzPx
	) {
		Quaternionf rBase = eulerXYZDegreesToQuat(baseRx, baseRy, baseRz);
		Quaternionf rTarget = eulerXYZDegreesToQuat(targetRx, targetRy, targetRz);

		Quaternionf rDelta = new Quaternionf(rTarget);
		rDelta.mul(new Quaternionf(rBase).invert());

		Vector3f tBase = new Vector3f(baseTxPx / 16.0F, baseTyPx / 16.0F, baseTzPx / 16.0F);
		Vector3f tTarget = new Vector3f(targetTxPx / 16.0F, targetTyPx / 16.0F, targetTzPx / 16.0F);

		// tDelta = tTarget - (rDelta * tBase)
		Vector3f tBaseRotated = new Vector3f(tBase).rotate(rDelta);
		Vector3f tDelta = new Vector3f(tTarget).sub(tBaseRotated);

		poseStack.translate(tDelta.x, tDelta.y, tDelta.z);
		poseStack.mulPose(rDelta);
	}

	private static void applyDeltaFromBaseDisplayWithPivot(
			PoseStack poseStack,
			Vector3f pivot,
			float baseRx,
			float baseRy,
			float baseRz,
			float baseTxPx,
			float baseTyPx,
			float baseTzPx,
			float targetRx,
			float targetRy,
			float targetRz,
			float targetTxPx,
			float targetTyPx,
			float targetTzPx
	) {
		Quaternionf rBase = eulerXYZDegreesToQuat(baseRx, baseRy, baseRz);
		Quaternionf rTarget = eulerXYZDegreesToQuat(targetRx, targetRy, targetRz);

		Quaternionf rDelta = new Quaternionf(rTarget);
		rDelta.mul(new Quaternionf(rBase).invert());

		Vector3f tBase = new Vector3f(baseTxPx / 16.0F, baseTyPx / 16.0F, baseTzPx / 16.0F);
		Vector3f tTarget = new Vector3f(targetTxPx / 16.0F, targetTyPx / 16.0F, targetTzPx / 16.0F);

		// Delta' is applied as: T(tDeltaPivot) * T(pivot) * R(rDelta) * T(-pivot)
		// Solve tDeltaPivot so that Delta' * Base == Target.
		Vector3f baseMinusPivot = new Vector3f(tBase).sub(pivot);
		Vector3f rotated = baseMinusPivot.rotate(rDelta);
		Vector3f tDeltaPivot = new Vector3f(tTarget).sub(pivot).sub(rotated);

		poseStack.translate(tDeltaPivot.x, tDeltaPivot.y, tDeltaPivot.z);
		poseStack.translate(pivot.x, pivot.y, pivot.z);
		poseStack.mulPose(rDelta);
		poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	private static Quaternionf eulerXYZDegreesToQuat(float xDeg, float yDeg, float zDeg) {
		float x = xDeg * ((float) Math.PI / 180.0F);
		float y = yDeg * ((float) Math.PI / 180.0F);
		float z = zDeg * ((float) Math.PI / 180.0F);
		Quaternionf q = new Quaternionf();
		q.rotateX(x);
		q.rotateY(y);
		q.rotateZ(z);
		return q;
	}
}
