package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.event.ScytheHarvestEvents;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.weapon.IStardewWeapon;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端左键挥舞（对空气/方块）时发到服务端，用于触发收割逻辑。
 * 不携带额外数据：服务端按玩家朝向自行做射线与范围收割。
 */
public record ScytheSwingPayload() implements CustomPacketPayload {
	private static final int DEFAULT_COOLDOWN_TICKS = 10;

	@SuppressWarnings("null")
	public static final Type<ScytheSwingPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "scythe_swing"));
	public static final StreamCodec<ByteBuf, ScytheSwingPayload> STREAM_CODEC = StreamCodec.unit(new ScytheSwingPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(ScytheSwingPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			var mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
			if (player.getCooldowns().isOnCooldown(mainHand.getItem())) {
				return;
			}
			if (!(player.level() instanceof ServerLevel level)) {
				return;
			}

			boolean scytheSwing = mainHand.getItem() instanceof ScytheItem;
			boolean weaponGrassSwing = !scytheSwing && mainHand.getItem() instanceof IStardewWeapon;
			if (!scytheSwing && !weaponGrassSwing) {
				return;
			}

			boolean didSomething = scytheSwing
					? ScytheHarvestEvents.harvestSwing(level, player, (ScytheItem) mainHand.getItem())
					: ScytheHarvestEvents.harvestWeaponGrassSwing(level, player, mainHand);
			if (weaponGrassSwing && !didSomething) {
				return;
			}
			spawnSweepParticle(level, player);

			float volume = didSomething ? 0.8F : 0.6F;
			float pitch = didSomething ? 1.0F : 1.1F;
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, volume, pitch);

			int cooldownTicks = scytheSwing ? ((ScytheItem) mainHand.getItem()).getCooldownTicks() : DEFAULT_COOLDOWN_TICKS;
			if (cooldownTicks <= 0) {
				cooldownTicks = DEFAULT_COOLDOWN_TICKS;
			}
			player.getCooldowns().addCooldown(mainHand.getItem(), cooldownTicks);
		});
	}

	@SuppressWarnings("null")
	private static void spawnSweepParticle(ServerLevel level, ServerPlayer player) {
		// Mimic the vanilla sweeping-edge visual: a quick arc in front of the player.
		Vec3 look = player.getLookAngle();
		Vec3 flatForward = new Vec3(look.x, 0.0, look.z);
		if (flatForward.lengthSqr() < 1.0E-6) {
			flatForward = new Vec3(0.0, 0.0, 1.0);
		} else {
			flatForward = flatForward.normalize();
		}

		double x = player.getX() + flatForward.x * 0.9;
		double y = player.getY() + 1.0;
		double z = player.getZ() + flatForward.z * 0.9;
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
	}
}
