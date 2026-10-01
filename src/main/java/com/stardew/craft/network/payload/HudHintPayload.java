package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S → C：在 HUD 上弹一个错误/警告样式的 messagebox（带取消音，和「好感度不够」等提示一致）。
 * 携带一个翻译 key，客户端用 {@code Component.translatable(key)} 显示。
 *
 * <p>{@link #send} 自带每玩家+key 的节流（30 tick），避免长按砍受保护方块时刷屏。
 */
public record HudHintPayload(String translationKey) implements CustomPacketPayload {

	@SuppressWarnings("null")
	public static final Type<HudHintPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "hud_hint"));

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, HudHintPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			HudHintPayload::translationKey,
			HudHintPayload::new);

	private static final long THROTTLE_TICKS = 30L;
	private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(HudHintPayload payload, IPayloadContext context) {
		context.enqueueWork(() ->
				com.stardew.craft.client.hud.StardewHudMessageManager.showError(
						net.minecraft.network.chat.Component.translatable(payload.translationKey())));
	}

	/** 向玩家发送一条提示（带节流）。 */
	public static void send(ServerPlayer player, String translationKey) {
		long now = player.serverLevel().getGameTime();
		UUID id = player.getUUID();
		String mapKey = id + "|" + translationKey;
		Long last = LAST_SENT.get(mapKey);
		if (last != null && now - last < THROTTLE_TICKS) {
			return;
		}
		LAST_SENT.put(mapKey, now);
		PacketDistributor.sendToPlayer(player, new HudHintPayload(translationKey));
	}
}
