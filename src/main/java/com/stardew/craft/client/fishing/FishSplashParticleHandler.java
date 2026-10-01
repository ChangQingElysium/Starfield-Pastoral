package com.stardew.craft.client.fishing;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.Random;

/**
 * Renders fish splash points (气泡) using vanilla {@code BUBBLE} + {@code SPLASH}
 * particles. Reads from {@link ClientFishSplashState} every client tick.
 * <p>
 * SDV uses a 51-frame TemporaryAnimatedSprite. We approximate the visual feel
 * with a small particle burst that's recognizable as "bubbling water".
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishSplashParticleHandler {

	private FishSplashParticleHandler() {}

	private static final Random RNG = new Random();

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		net.minecraft.client.player.LocalPlayer player = mc.player;
		if (level == null || player == null) return;

		Map<String, BlockPos> splashes = ClientFishSplashState.view();
		if (splashes.isEmpty()) return;

		final double maxDistSq = 96.0 * 96.0; // visible from a distance like SDV's tile sprite
		double px = player.getX(), py = player.getY(), pz = player.getZ();

		for (BlockPos pos : splashes.values()) {
			double cx = pos.getX() + 0.5;
			double cy = pos.getY() + 1.0;
			double cz = pos.getZ() + 0.5;
			double d2 = (cx - px) * (cx - px) + (cy - py) * (cy - py) + (cz - pz) * (cz - pz);
			if (d2 > maxDistSq) continue;

			// Bubbles rising out of water — main visual cue.
			int n = 6 + RNG.nextInt(4);
			for (int i = 0; i < n; i++) {
				double ox = (RNG.nextDouble() - 0.5) * 0.9;
				double oz = (RNG.nextDouble() - 0.5) * 0.9;
				level.addParticle(ParticleTypes.BUBBLE,
						cx + ox, cy - 0.1, cz + oz,
						0.0, 0.05 + RNG.nextDouble() * 0.05, 0.0);
			}
			// Persistent splash droplets every tick — looks like SDV's animated bubble sprite.
			for (int i = 0; i < 4; i++) {
				double angle = RNG.nextDouble() * Math.PI * 2;
				double r = 0.25 + RNG.nextDouble() * 0.2;
				level.addParticle(ParticleTypes.SPLASH,
						cx + Math.cos(angle) * r, cy, cz + Math.sin(angle) * r,
						0.0, 0.0, 0.0);
			}
			// White-ish fishing surface "pop" every few ticks for additional motion.
			if ((player.tickCount + Math.floorMod(pos.hashCode(), 7)) % 8 == 0) {
				for (int i = 0; i < 6; i++) {
					double angle = RNG.nextDouble() * Math.PI * 2;
					double r = 0.35;
					level.addParticle(ParticleTypes.FISHING,
							cx + Math.cos(angle) * r, cy + 0.05, cz + Math.sin(angle) * r,
							Math.cos(angle) * 0.05, 0.05, Math.sin(angle) * 0.05);
				}
			}
		}
	}
}
