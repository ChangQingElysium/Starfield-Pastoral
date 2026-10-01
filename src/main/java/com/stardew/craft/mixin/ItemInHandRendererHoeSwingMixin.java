
package com.stardew.craft.mixin;

import com.stardew.craft.port.PortItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.tool.HoeItem;
import net.minecraft.client.player.AbstractClientPlayer;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称锄头动画：
 * - 点按：p0 -> p1(抬手) -> p2(砸下) -> p0
 * - 蓄力：p0 -> p1(保持)；松手：p1 -> p2(瞬砸) -> p0
 *
 * 注意：这是纯客户端视觉，不参与服务端判定。
 */
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public class ItemInHandRendererHoeSwingMixin {
	private static final ThreadLocal<Boolean> STARDEWCRAFT_HOE_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<Float> STARDEWCRAFT_PARTIAL_TICK = ThreadLocal.withInitial(() -> 0.0F);
	private static final ThreadLocal<InteractionHand> STARDEWCRAFT_HAND = new ThreadLocal<>();

	// p1/p2 来自你给的 Blockbench 关键帧（translation 单位为“像素”，渲染时需 /16）。
	private static final float P1_RX = -114.0F;
	private static final float P1_RY = -87.5F;
	private static final float P1_RZ = -180.0F;
	private static final float P1_TX = 2.0F;
	private static final float P1_TY = 8.0F;
	private static final float P1_TZ = 0.75F;

	private static final float P2_RX = 138.75F;
	private static final float P2_RY = -87.5F;
	private static final float P2_RZ = 180.0F;
	private static final float P2_TX = 2.0F;
	private static final float P2_TY = 3.5F;
	private static final float P2_TZ = -4.25F;

	// 当前锄头模型的第一人称基础 display（来自 assets/.../models/item/hoe*.json）。
	private static final float BASE_RH_RX = -144.0F;
	private static final float BASE_RH_RY = -87.5F;
	private static final float BASE_RH_RZ = -180.0F;
	private static final float BASE_RH_TX = 2.0F;
	private static final float BASE_RH_TY = 4.25F;
	private static final float BASE_RH_TZ = 0.0F;

	private static final float BASE_LH_RX = 41.67F;
	private static final float BASE_LH_RY = 75.39F;
	private static final float BASE_LH_RZ = -11.11F;
	private static final float BASE_LH_TX = 2.0F;
	private static final float BASE_LH_TY = 4.25F;
	private static final float BASE_LH_TZ = 0.0F;

	// 分段曲线参数（0..1）
	private static final float DIRECT_RAISE_END = 0.22F;
	private static final float DIRECT_HOLD_END = 0.32F;
	private static final float DIRECT_HIT_END = 0.46F;

	private static final float CHARGED_HIT_END = 0.12F;

	@Shadow
	private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress) {
		throw new IllegalStateException("Mixin failed to shadow applyItemArmAttackTransform");
	}

	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD")
	)
	private void stardewcraft$hoeFirstPersonBegin(
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
		boolean active = (stack.getItem() instanceof HoeItem);
		STARDEWCRAFT_HOE_ACTIVE.set(active);
		STARDEWCRAFT_PARTIAL_TICK.set(partialTick);
		STARDEWCRAFT_HAND.set(hand);
	}

	@Inject(
			method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN")
	)
	private void stardewcraft$hoeFirstPersonEnd(
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
		STARDEWCRAFT_HOE_ACTIVE.set(Boolean.FALSE);
		STARDEWCRAFT_PARTIAL_TICK.set(0.0F);
		STARDEWCRAFT_HAND.remove();
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
		if (!Boolean.TRUE.equals(STARDEWCRAFT_HOE_ACTIVE.get())) {
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}

		// 只改“使用中的那只手”的渲染，避免污染另一只手。
		InteractionHand hand = STARDEWCRAFT_HAND.get();
		if (hand == null) {
			applyItemArmAttackTransform(poseStack, arm, swingProgress);
			return;
		}

		// 注意：renderArmWithItem 里 arm 与 hand 的对应关系：
		// - MAIN_HAND 使用玩家主手 (player.getMainArm())
		// - OFF_HAND 使用相反手
		// 这里我们只看 "arm"，不再额外推断。

		// 1) 如果有“砸下去”的计时器：优先播放 strike。
		if (applyStrikeIfActive(poseStack, arm)) {
			return;
		}

		// 2) 蓄力期间：p0 -> p1，然后保持在 p1。
		if (applyChargeHoldIfActive(poseStack, arm)) {
			return;
		}

		// 3) 默认：不改。
		applyItemArmAttackTransform(poseStack, arm, swingProgress);
	}

	@SuppressWarnings("null")
	private static boolean applyStrikeIfActive(PoseStack poseStack, HumanoidArm arm) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}

		ItemStack stack = mc.player.getMainHandItem();
		// main hand 渲染时 stack 就是 mainHand；off hand 同理，但这里拿不到传参。
		// 保险：两只手都检查一下，优先用正在渲染的那只。
		@SuppressWarnings("null")
		ItemStack off = mc.player.getOffhandItem();

		@SuppressWarnings("null")
		ItemStack used = (arm == mc.player.getMainArm()) ? stack : off;
		if (!(used.getItem() instanceof HoeItem)) {
			return false;
		}

		@SuppressWarnings("null")
		CustomData data = PortItemData.get(used, DataComponents.CUSTOM_DATA);
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
		@SuppressWarnings("null")
		float elapsed = (mc.player.tickCount + partialTick) - (float) startTick;
		float s = elapsed / (float) duration;
		if (s <= 0.0F) {
			s = 0.0F;
		}
		if (s >= 1.0F) {
			s = 1.0F;
			// 动画结束：清理 CustomData，避免一直占着。
			tag.remove(HoeItem.NBT_STRIKE_START_TICK);
			tag.remove(HoeItem.NBT_STRIKE_DURATION_TICKS);
			tag.remove(HoeItem.NBT_STRIKE_FROM_P1);
			if (tag.isEmpty()) {
				PortItemData.remove(used, DataComponents.CUSTOM_DATA);
			} else {
				PortItemData.set(used, DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		}

		applyHoeStrike(poseStack, arm, s, fromP1);
		return true;
	}

	private static boolean applyChargeHoldIfActive(PoseStack poseStack, HumanoidArm arm) {
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
		// 普通锄头不蓄力：不做 hold。
		if (hoe.getTier().getMaxChargeLevel() <= 0) {
			return false;
		}

		// 只对“正在使用的那只手”生效
		HumanoidArm usedArm = (hand == InteractionHand.MAIN_HAND) ? player.getMainArm() : player.getMainArm().getOpposite();
		if (arm != usedArm) {
			return false;
		}

		int activeTicks = stack.getUseDuration() - player.getUseItemRemainingTicks();
		float partialTick = STARDEWCRAFT_PARTIAL_TICK.get();
		float t = (activeTicks + partialTick) / (float) HoeItem.RAISE_TO_P1_TICKS;
		t = Mth.clamp(t, 0.0F, 1.0F);
		float easeOutCubic = 1.0F - (float) Math.pow(1.0F - t, 3);

		boolean right = arm == HumanoidArm.RIGHT;
		float bRx = right ? BASE_RH_RX : BASE_LH_RX;
		float bRy = right ? BASE_RH_RY : BASE_LH_RY;
		float bRz = right ? BASE_RH_RZ : BASE_LH_RZ;
		float bTx = right ? BASE_RH_TX : BASE_LH_TX;
		float bTy = right ? BASE_RH_TY : BASE_LH_TY;
		float bTz = right ? BASE_RH_TZ : BASE_LH_TZ;

		float p1Ry = right ? P1_RY : -P1_RY;
		float p1Rz = right ? P1_RZ : -P1_RZ;
		float p1Tx = right ? P1_TX : -P1_TX;

		float tRx = Mth.lerp(easeOutCubic, bRx, P1_RX);
		float tRy = Mth.lerp(easeOutCubic, bRy, p1Ry);
		float tRz = Mth.lerp(easeOutCubic, bRz, p1Rz);
		float tTx = Mth.lerp(easeOutCubic, bTx, p1Tx);
		float tTy = Mth.lerp(easeOutCubic, bTy, P1_TY);
		float tTz = Mth.lerp(easeOutCubic, bTz, P1_TZ);

		applyDeltaFromBaseDisplay(poseStack, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
		return true;
	}

	private static void applyHoeStrike(PoseStack poseStack, HumanoidArm arm, float progress, boolean fromP1) {
		float s = Mth.clamp(progress, 0.0F, 1.0F);
		boolean right = arm == HumanoidArm.RIGHT;

		float bRx = right ? BASE_RH_RX : BASE_LH_RX;
		float bRy = right ? BASE_RH_RY : BASE_LH_RY;
		float bRz = right ? BASE_RH_RZ : BASE_LH_RZ;
		float bTx = right ? BASE_RH_TX : BASE_LH_TX;
		float bTy = right ? BASE_RH_TY : BASE_LH_TY;
		float bTz = right ? BASE_RH_TZ : BASE_LH_TZ;

		float p1Ry = right ? P1_RY : -P1_RY;
		float p1Rz = right ? P1_RZ : -P1_RZ;
		float p1Tx = right ? P1_TX : -P1_TX;

		float p2Ry = right ? P2_RY : -P2_RY;
		float p2Rz = right ? P2_RZ : -P2_RZ;
		float p2Tx = right ? P2_TX : -P2_TX;

		float tRx;
		float tRy;
		float tRz;
		float tTx;
		float tTy;
		float tTz;

		if (fromP1) {
			// 蓄力：p1 -> p2 要“啪一下”很快完成，然后回收
			if (s <= CHARGED_HIT_END) {
				float u = s / CHARGED_HIT_END;
				float inv = 1.0F - u;
				float easeOutCubic = 1.0F - (inv * inv * inv);
				float alpha = easeOutCubic * 1.12F; // 轻微过冲，增强力度
				tRx = Mth.lerp(alpha, P1_RX, P2_RX);
				tRy = Mth.lerp(alpha, p1Ry, p2Ry);
				tRz = Mth.lerp(alpha, p1Rz, p2Rz);
				tTx = Mth.lerp(alpha, p1Tx, p2Tx);
				tTy = Mth.lerp(alpha, P1_TY, P2_TY);
				tTz = Mth.lerp(alpha, P1_TZ, P2_TZ);
			} else {
				float v = (s - CHARGED_HIT_END) / (1.0F - CHARGED_HIT_END);
				float inv2 = 1.0F - v;
				float easeOutCubic = 1.0F - (inv2 * inv2 * inv2);
				tRx = Mth.lerp(easeOutCubic, P2_RX, bRx);
				tRy = Mth.lerp(easeOutCubic, p2Ry, bRy);
				tRz = Mth.lerp(easeOutCubic, p2Rz, bRz);
				tTx = Mth.lerp(easeOutCubic, p2Tx, bTx);
				tTy = Mth.lerp(easeOutCubic, P2_TY, bTy);
				tTz = Mth.lerp(easeOutCubic, P2_TZ, bTz);
			}
		} else {
			// 点按：p0->p1(很快) 小定帧 -> p2(砸下) -> p0(回收)
			if (s <= DIRECT_RAISE_END) {
				float u = s / DIRECT_RAISE_END;
				float inv = 1.0F - u;
				float easeOutQuad = 1.0F - (inv * inv);
				tRx = Mth.lerp(easeOutQuad, bRx, P1_RX);
				tRy = Mth.lerp(easeOutQuad, bRy, p1Ry);
				tRz = Mth.lerp(easeOutQuad, bRz, p1Rz);
				tTx = Mth.lerp(easeOutQuad, bTx, p1Tx);
				tTy = Mth.lerp(easeOutQuad, bTy, P1_TY);
				tTz = Mth.lerp(easeOutQuad, bTz, P1_TZ);
			} else if (s <= DIRECT_HOLD_END) {
				tRx = P1_RX;
				tRy = p1Ry;
				tRz = p1Rz;
				tTx = p1Tx;
				tTy = P1_TY;
				tTz = P1_TZ;
			} else if (s <= DIRECT_HIT_END) {
				float u = (s - DIRECT_HOLD_END) / (DIRECT_HIT_END - DIRECT_HOLD_END);
				float easeInCubic = u * u * u;
				float alpha = easeInCubic * 1.10F;
				tRx = Mth.lerp(alpha, P1_RX, P2_RX);
				tRy = Mth.lerp(alpha, p1Ry, p2Ry);
				tRz = Mth.lerp(alpha, p1Rz, p2Rz);
				tTx = Mth.lerp(alpha, p1Tx, p2Tx);
				tTy = Mth.lerp(alpha, P1_TY, P2_TY);
				tTz = Mth.lerp(alpha, P1_TZ, P2_TZ);
			} else {
				float v = (s - DIRECT_HIT_END) / (1.0F - DIRECT_HIT_END);
				float inv2 = 1.0F - v;
				float easeOutCubic = 1.0F - (inv2 * inv2 * inv2);
				tRx = Mth.lerp(easeOutCubic, P2_RX, bRx);
				tRy = Mth.lerp(easeOutCubic, p2Ry, bRy);
				tRz = Mth.lerp(easeOutCubic, p2Rz, bRz);
				tTx = Mth.lerp(easeOutCubic, p2Tx, bTx);
				tTy = Mth.lerp(easeOutCubic, P2_TY, bTy);
				tTz = Mth.lerp(easeOutCubic, P2_TZ, bTz);
			}
		}

		applyDeltaFromBaseDisplay(poseStack, bRx, bRy, bRz, bTx, bTy, bTz, tRx, tRy, tRz, tTx, tTy, tTz);
	}

	/**
	 * 在“模型 display 基础变换”生效前，先应用 delta：delta * base = target。
	 * 这样最终渲染出来的姿势才会精确等于 target（即 p0/p1/p2）。
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

		Vector3f tBaseRotated = new Vector3f(tBase).rotate(rDelta);
		Vector3f tDelta = tTarget.sub(tBaseRotated);

		poseStack.translate(tDelta.x, tDelta.y, tDelta.z);
		poseStack.mulPose(rDelta);
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
