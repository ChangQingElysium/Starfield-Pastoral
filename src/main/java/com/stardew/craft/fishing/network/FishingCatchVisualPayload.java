package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.UUIDUtil;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Complete reward appearance and the last real hook position, captured before hook removal. */
public record FishingCatchVisualPayload(UUID sessionId, ItemStack stack, boolean fish,
		double x, double y, double z) implements CustomPacketPayload {
	public static final Type<FishingCatchVisualPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fishing_catch_visual"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FishingCatchVisualPayload> STREAM_CODEC = StreamCodec.composite(
			com.stardew.craft.port.PortCodecs.UUID, FishingCatchVisualPayload::sessionId,
			com.stardew.craft.port.PortCodecs.ITEM_STACK, FishingCatchVisualPayload::stack,
			ByteBufCodecs.BOOL, FishingCatchVisualPayload::fish,
			ByteBufCodecs.DOUBLE, FishingCatchVisualPayload::x,
			ByteBufCodecs.DOUBLE, FishingCatchVisualPayload::y,
			ByteBufCodecs.DOUBLE, FishingCatchVisualPayload::z,
			FishingCatchVisualPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }

	public static void handle(FishingCatchVisualPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingCatchVisualPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId()) || payload.stack().isEmpty()) return;
		com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(
				Component.translatable("stardewcraft.fishing.caught", payload.stack().getHoverName()));
		com.stardew.craft.client.fishing.FishingCatchVisuals.start(payload.stack(), payload.fish(), new Vec3(payload.x(), payload.y(), payload.z()));
	}
}
