package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.FishingRodCastAnimationState;
import com.stardew.craft.fishing.network.FishingUsePayload;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Owns input and late-message rejection for one charge, cast and presentation. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingInteractionState {
	private static UUID useId;
	private static int slot = -1;
	private static Item rod;
	private static net.minecraft.client.multiplayer.ClientLevel level;

	private FishingInteractionState() { }

	public static void begin() {
		cancel(true);
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		useId = UUID.randomUUID();
		slot = mc.player.getInventory().selected;
		rod = mc.player.getMainHandItem().getItem();
		level = mc.level;
		// Item.use executes inside startPrediction, before the vanilla use packet is sent.
		PacketDistributor.sendToServer(new FishingUsePayload(useId, true));
		FishingPresentationClient.charge(useId);
	}


	public static boolean valid() {
		var mc = Minecraft.getInstance();
		return useId != null && mc.level == level && mc.player != null && mc.player.isAlive()
				&& !mc.player.isSpectator() && mc.player.getInventory().selected == slot
				&& mc.player.getMainHandItem().getItem() == rod && mc.player.getOffhandItem().isEmpty();
	}

	public static boolean accepts(UUID id) { return id != null && id.equals(useId) && valid(); }

	public static void cancelFromServer(UUID id) {
		if (id.equals(useId)) cancel(false);
	}

	public static void cancel(boolean notifyServer) {
		UUID previous = useId;
		if (previous == null) return;
		useId = null;
		slot = -1;
		rod = null;
		level = null;
		var mc = Minecraft.getInstance();
		if (mc.player != null) {
			if (mc.player.getUseItem().getItem() instanceof FishingRodItem) mc.player.stopUsingItem();
			for (var stack : mc.player.getInventory().items) if (stack.getItem() instanceof FishingRodItem) FishingRodItem.setCastActive(stack, false);
			for (var stack : mc.player.getInventory().offhand) if (stack.getItem() instanceof FishingRodItem) FishingRodItem.setCastActive(stack, false);
			if (mc.player.fishing != null) {
				mc.player.fishing.discard();
				mc.player.fishing = null;
			}
		}
		if (mc.screen instanceof FishingMinigameScreen screen) screen.cancelWithoutResult();
		if (mc.screen instanceof TreasureChestScreen && mc.player != null) mc.player.closeContainer();
		FishingPresentationClient.cancelLocal();
		FishingBiteVisuals.clear();
		FishingCatchVisuals.cancel();
		FishingRodCastAnimationState.reset();
        com.stardew.craft.client.hud.FishingCastHud.reset();
		if (notifyServer && mc.getConnection() != null) PacketDistributor.sendToServer(new FishingUsePayload(previous, false));
	}

	public static void selectSlot(int next) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || next == mc.player.getInventory().selected) return;
		cancel(true);
		mc.player.getInventory().selected = next;
		mc.player.connection.send(new ServerboundSetCarriedItemPacket(next));
	}

	public static void validateEquipment() { if (useId != null && !valid()) cancel(true); }
	@SubscribeEvent
	public static void tick(ClientTickEvent.Pre event) {
		validateEquipment();
	}

	@SubscribeEvent
	public static void key(InputEvent.Key event) {
		if (useId == null || event.getAction() != GLFW.GLFW_PRESS) return;
		var mc = Minecraft.getInstance();
		if (mc.screen != null) return;
		for (int i = 0; i < 9; i++) {
			if (i != slot && mc.options.keyHotbarSlots[i].matches(event.getKey(), event.getScanCode())) {
				cancel(true);
				return;
			}
		}
		if (mc.options.keySwapOffhand.matches(event.getKey(), event.getScanCode())
				|| mc.options.keyDrop.matches(event.getKey(), event.getScanCode())) cancel(true);
	}

	@SubscribeEvent
	public static void scroll(InputEvent.MouseScrollingEvent event) {
		// PORT(1.20.1): 1.20.1 only reports vertical scrolling (getScrollDelta).
		if (useId != null && Minecraft.getInstance().screen == null && event.getScrollDelta() != 0) cancel(true);
	}
}
