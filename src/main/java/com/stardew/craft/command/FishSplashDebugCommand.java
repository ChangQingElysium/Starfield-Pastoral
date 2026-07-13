package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.splash.FishSplashState;
import com.stardew.craft.network.payload.FishSplashSyncPayload;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug helper for the fish splash (气泡) system.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code /stardew splash here} — force-spawn a splash at the player's looking-at water tile</li>
 *   <li>{@code /stardew splash list} — print all current splash entries on the server</li>
 *   <li>{@code /stardew splash clear} — remove every splash entry</li>
 *   <li>{@code /stardew splash resync} — re-send the full snapshot to the calling player</li>
 *   <li>{@code /stardew splash keys} — print the vanilla-aligned location keys at the player's biome</li>
 * </ul>
 */
public final class FishSplashDebugCommand {

	private FishSplashDebugCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("stardew")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("splash")
					.then(Commands.literal("here").executes(FishSplashDebugCommand::spawnHere))
					.then(Commands.literal("list").executes(FishSplashDebugCommand::list))
					.then(Commands.literal("clear").executes(FishSplashDebugCommand::clear))
					.then(Commands.literal("resync").executes(FishSplashDebugCommand::resync))
					.then(Commands.literal("keys").executes(FishSplashDebugCommand::printKeys))
				);
		dispatcher.register(root);
	}

	private static int spawnHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		ServerLevel stardew = level.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
		if (stardew == null) {
			ctx.getSource().sendFailure(Component.literal("[splash] Stardew Valley dim not loaded"));
			return 0;
		}
		if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) {
			ctx.getSource().sendFailure(Component.literal("[splash] You must be inside the Stardew Valley dim"));
			return 0;
		}

		// Find a water tile near the player (8x8 around) with air above.
		BlockPos anchor = player.blockPosition();
		BlockPos chosen = null;
		outer:
		for (int radius = 0; radius <= 8; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
					for (int dy = 4; dy >= -4; dy--) {
						BlockPos p = anchor.offset(dx, dy, dz);
						if (!level.getFluidState(p).is(Fluids.WATER)) continue;
						if (!level.getBlockState(p.above()).isAir()) continue;
						chosen = p;
						break outer;
					}
				}
			}
		}
		if (chosen == null) {
			ctx.getSource().sendFailure(Component.literal("[splash] No water tile found within 8 blocks"));
			return 0;
		}

		Holder<Biome> bh = level.getBiome(chosen);
		List<String> keys = FishingDataManager.resolveVanillaAlignedLocationKeysStatic(level, bh, chosen);
		String key = keys.stream().filter(k -> !"Default".equals(k)).findFirst().orElse("Default");

		FishSplashState state = FishSplashState.get(stardew);
		int nowMin = absoluteMinutes();
		state.put(key, new FishSplashState.Entry(chosen, nowMin));
		FishSplashState.broadcastChange(stardew, key, chosen);

		final BlockPos chosenF = chosen;
		final String keyF = key;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"[splash] Spawned at " + chosenF.toShortString() + " key=" + keyF
				+ " biomeKeys=" + keys), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		ServerLevel stardew = ctx.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
		if (stardew == null) {
			ctx.getSource().sendFailure(Component.literal("[splash] Stardew dim not loaded"));
			return 0;
		}
		FishSplashState state = FishSplashState.get(stardew);
		Map<String, FishSplashState.Entry> view = state.view();
		if (view.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("[splash] (no entries)"), false);
			return 0;
		}
		int now = absoluteMinutes();
		for (Map.Entry<String, FishSplashState.Entry> e : view.entrySet()) {
			int age = now - e.getValue().createdGameMinutes();
			ctx.getSource().sendSuccess(() -> Component.literal(
					"[splash] " + e.getKey() + " @ " + e.getValue().pos().toShortString()
					+ " age=" + age + "min"), false);
		}
		return view.size();
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		ServerLevel stardew = ctx.getSource().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
		if (stardew == null) return 0;
		FishSplashState state = FishSplashState.get(stardew);
		java.util.List<String> keys = new java.util.ArrayList<>(state.view().keySet());
		for (String k : keys) {
			state.remove(k);
			FishSplashState.broadcastChange(stardew, k, null);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("[splash] Cleared " + keys.size() + " entries"), true);
		return keys.size();
	}

	private static int resync(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel stardew = player.serverLevel().getServer().getLevel(ModDimensions.STARDEW_VALLEY);
		if (stardew == null) return 0;
		FishSplashState state = FishSplashState.get(stardew);
		Map<String, BlockPos> snap = new LinkedHashMap<>();
		state.view().forEach((k, v) -> snap.put(k, v.pos()));
		PacketDistributor.sendToPlayer(player, FishSplashSyncPayload.snapshot(snap));
		ctx.getSource().sendSuccess(() -> Component.literal("[splash] Resynced " + snap.size() + " entries"), false);
		return 1;
	}

	private static int printKeys(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Holder<Biome> bh = level.getBiome(player.blockPosition());
		List<String> keys = FishingDataManager.resolveVanillaAlignedLocationKeysStatic(
			level, bh, player.blockPosition());
		ctx.getSource().sendSuccess(() -> Component.literal("[splash] biome keys = " + keys), false);
		return 1;
	}

	private static int absoluteMinutes() {
		StardewTimeManager tm = StardewTimeManager.get();
		if (tm == null) return 0;
		int year = Math.max(1, tm.getCurrentYear());
		int season = tm.getCurrentSeason();
		int day = tm.getCurrentDay();
		int time = tm.getCurrentTime();
		return ((year - 1) * 4 + season) * 28 * 1440 + (day - 1) * 1440 + time;
	}
}
