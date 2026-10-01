package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.event.ScytheHarvestEvents;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.network.ScytheSwingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/**
 * 让“镰刀左键=挥舞并触发收割”在客户端侧可靠生效：
 * - 对空气：依然能检测到攻击键并发包
 * - 对方块：仍然能触发（同时挖掘会被拦截）
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ScytheClientInputHandler {
	private static boolean wasAttackDown = false;

	private ScytheClientInputHandler() {
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			ScytheSwingAnimationState.reset();
			return;
		}
		ScytheSwingAnimationState.tick();

		var player = mc.player;
		@SuppressWarnings("null")
		var mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean scythe = mainHandStack.getItem() instanceof ScytheItem;
		boolean weaponGrassCut = !scythe
				&& mainHandStack.getItem() instanceof IStardewWeapon
				&& isLookingAtWeaponCuttableGrass(mc);
		if (!scythe && !weaponGrassCut) {
			wasAttackDown = false;
			ScytheSwingAnimationState.reset();
			return;
		}

		boolean isAttackDown = mc.options.keyAttack.isDown();
		if (isAttackDown && !wasAttackDown) {
			// 冷却期间：不应进入下一段动画，也不应产生任何效果（不挥舞、不发包）。
			if (player.getCooldowns().isOnCooldown(mainHandStack.getItem())) {
				wasAttackDown = true;
				return;
			}

			ScytheSwingAnimationState.start();
			player.swing(InteractionHand.MAIN_HAND);
			PacketDistributor.sendToServer(new ScytheSwingPayload());
		}
		wasAttackDown = isAttackDown;

		// 额外保险：持镰刀时不允许进入挖掘流程。
		if (isAttackDown && mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
		}
	}

	private static boolean isLookingAtWeaponCuttableGrass(Minecraft mc) {
		var level = mc.level;
		if (level == null || !(mc.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		return ScytheHarvestEvents.isWeaponGrassCuttingState(level.getBlockState(hit.getBlockPos()));
	}
}
