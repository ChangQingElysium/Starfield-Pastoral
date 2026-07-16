package com.stardew.craft.network.payload;

import org.jetbrains.annotations.NotNull;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.emote.EmoteCatalog;
import com.stardew.craft.emote.EmoteType;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record EmoteUsePayload(String emoteId) implements CustomPacketPayload {

	@SuppressWarnings("null")
	public static final Type<EmoteUsePayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "emote_use")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, EmoteUsePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		EmoteUsePayload::emoteId,
		EmoteUsePayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(EmoteUsePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer serverPlayer)) {
				return;
			}

			EmoteType emote = EmoteCatalog.byId(payload.emoteId());
			if (emote == null) {
				return;
			}

			int bubbleBaseIndex = EmoteCatalog.getBubbleBaseIndex(emote);
			PacketDistributor.sendToAllPlayers(new EmoteBroadcastPayload(serverPlayer.getId(), bubbleBaseIndex));

			Component emoteDisplay = Component.empty()
				.append(EmoteCatalog.getChatIconComponent(emote))
				.append(Component.literal(" "))
				.append(Component.translatable("stardewcraft.emote." + emote.id()));
			Component chatLine = Component.translatable("stardewcraft.emote.chat",
					com.stardew.craft.player.PlayerDisplayName.get(serverPlayer), emoteDisplay);
			for (ServerPlayer target : serverPlayer.server.getPlayerList().getPlayers()) {
				target.sendSystemMessage(chatLine);
			}
		});
	}
}
