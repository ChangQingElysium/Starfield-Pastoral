package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.tool.FishingRodItem;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record FishingRodCastStatePayload(java.util.UUID sessionId, boolean active) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingRodCastStatePayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fishing_cast_state")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingRodCastStatePayload> STREAM_CODEC = StreamCodec.composite(
			com.stardew.craft.port.PortCodecs.UUID, FishingRodCastStatePayload::sessionId,
			ByteBufCodecs.BOOL,
			FishingRodCastStatePayload::active,
			FishingRodCastStatePayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingRodCastStatePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingRodCastStatePayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		boolean active = payload.active();
		@SuppressWarnings("null")
		var main = mc.player.getMainHandItem();
		if (!main.isEmpty() && main.getItem() instanceof FishingRodItem) {
			FishingRodItem.setCastActive(main, active);
		}
		@SuppressWarnings("null")
		var off = mc.player.getOffhandItem();
		if (!off.isEmpty() && off.getItem() instanceof FishingRodItem) {
			FishingRodItem.setCastActive(off, active);
		}
	}
}
