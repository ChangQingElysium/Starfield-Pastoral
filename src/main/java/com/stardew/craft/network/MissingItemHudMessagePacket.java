package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record MissingItemHudMessagePacket(@NotNull String itemId, int requiredCount) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<MissingItemHudMessagePacket> TYPE = new Type<>(
		new ResourceLocation(StardewCraft.MODID, "missing_item_hud")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, MissingItemHudMessagePacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		MissingItemHudMessagePacket::itemId,
		ByteBufCodecs.INT,
		MissingItemHudMessagePacket::requiredCount,
		MissingItemHudMessagePacket::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(MissingItemHudMessagePacket packet, IPayloadContext context) {
		context.enqueueWork(() -> {
			String itemId = Objects.requireNonNull(packet.itemId(), "itemId");
			Item item = null;
			ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
			if (itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey)) {
				item = BuiltInRegistries.ITEM.get(itemKey);
			}
			com.stardew.craft.client.hud.StardewHudMessageManager.showMissingItem(item, itemId, packet.requiredCount());
		});
	}

	public static void sendTo(Player player, Item item, int requiredCount) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (item == null) {
			return;
		}
		ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item);
		PacketDistributor.sendToPlayer(serverPlayer, new MissingItemHudMessagePacket(itemKey.toString(), requiredCount));
	}
}
