package com.stardew.craft.item;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public class MonsterMuskItem extends SimpleStardewItem {
    private static final int DURATION_TICKS = 10 * 60 * 20;
    private static final int APPLY_DELAY_TICKS = 35;
    private static final DustParticleOptions PURPLE_MIST =
            new DustParticleOptions(new Vector3f(0.55F, 0.05F, 0.60F), 1.0F);

    public MonsterMuskItem(Properties properties) {
        super("stardewcraft.type.monster_loot", 50, properties);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), ModSounds.STEAM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, APPLY_DELAY_TICKS, 255, false, false, false));
                for (int i = 0; i < 3; i++) {
                    int delay = i * 2;
                    com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(
                            serverLevel, delay, () -> spawnPurpleMist(serverLevel, serverPlayer));
                }
                com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(
                        serverLevel, APPLY_DELAY_TICKS, () -> applyMonsterMusk(serverLevel, serverPlayer));
            }
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCooldowns().addCooldown(this, 20);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void spawnPurpleMist(ServerLevel level, Player player) {
        if (player.isRemoved()) {
            return;
        }
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        level.sendParticles(PURPLE_MIST, x, y, z, 12, 0.35D, 0.6D, 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.WITCH, x, y, z, 4, 0.25D, 0.4D, 0.25D, 0.01D);
    }

    private static void applyMonsterMusk(ServerLevel level, Player player) {
        if (player.isRemoved()) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModMobEffects.MONSTER_MUSK, DURATION_TICKS, 0, false, true, true));
        level.playSound(null, player.blockPosition(), ModSounds.CROAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
