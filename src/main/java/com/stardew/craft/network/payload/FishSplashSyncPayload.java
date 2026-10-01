package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishing.ClientFishSplashState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server → Client: sync fish splash points (气泡).
 * <p>
 * If {@code fullSnapshot=true}, the client replaces its entire splash map with
 * the supplied entries. Empty optionals are skipped on snapshot apply.
 * <p>
 * If {@code fullSnapshot=false}, each entry is a diff:
 * {@code Optional.empty()} = remove that locationKey, present = add/replace.
 */
public record FishSplashSyncPayload(boolean fullSnapshot, Map<String, Optional<BlockPos>> entries)
		implements CustomPacketPayload {

	public static final Type<FishSplashSyncPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fish_splash_sync"));

	public static final StreamCodec<ByteBuf, FishSplashSyncPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FishSplashSyncPayload::fullSnapshot,
			ByteBufCodecs.<ByteBuf, String, Optional<BlockPos>, Map<String, Optional<BlockPos>>>map(
					LinkedHashMap::new,
					ByteBufCodecs.STRING_UTF8,
					com.stardew.craft.port.PortCodecs.BLOCK_POS.apply(ByteBufCodecs::optional)),
			FishSplashSyncPayload::entries,
			FishSplashSyncPayload::new);

	/** Convenience: build a snapshot payload from a present-only map. */
	public static FishSplashSyncPayload snapshot(Map<String, BlockPos> snapshot) {
		LinkedHashMap<String, Optional<BlockPos>> wrapped = new LinkedHashMap<>(snapshot.size());
		for (Map.Entry<String, BlockPos> e : snapshot.entrySet()) {
			wrapped.put(e.getKey(), Optional.of(e.getValue()));
		}
		return new FishSplashSyncPayload(true, wrapped);
	}

	/** Convenience: build a diff payload (additions + removals). */
	public static FishSplashSyncPayload diff(Map<String, BlockPos> additions, java.util.Set<String> removals) {
		LinkedHashMap<String, Optional<BlockPos>> wrapped = new LinkedHashMap<>(additions.size() + removals.size());
		for (Map.Entry<String, BlockPos> e : additions.entrySet()) {
			wrapped.put(e.getKey(), Optional.of(e.getValue()));
		}
		for (String key : removals) {
			wrapped.put(key, Optional.empty());
		}
		return new FishSplashSyncPayload(false, wrapped);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(FishSplashSyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (payload.fullSnapshot) {
				LinkedHashMap<String, BlockPos> flat = new LinkedHashMap<>(payload.entries.size());
				for (Map.Entry<String, Optional<BlockPos>> e : payload.entries.entrySet()) {
					e.getValue().ifPresent(v -> flat.put(e.getKey(), v));
				}
				ClientFishSplashState.replaceAll(flat);
			} else {
				for (Map.Entry<String, Optional<BlockPos>> e : payload.entries.entrySet()) {
					if (e.getValue().isEmpty()) {
						ClientFishSplashState.remove(e.getKey());
					} else {
						ClientFishSplashState.put(e.getKey(), e.getValue().get());
					}
				}
			}
		});
	}
}
