package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.BloodForgeEffectPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Quiet state cues; recovery is dispatched only after positive applied healing. */
public final class DarkSwordEffects {
    private DarkSwordEffects() {}
    public static void playBloodDebtCast(ServerPlayer player) {
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.45f,.75f);
    }
    public static void playBloodMoonStart(ServerPlayer player) {
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.BEACON_POWER_SELECT,SoundSource.PLAYERS,.3f,.6f);
    }
    public static void playBloodMoonBurn(ServerPlayer player) {
        // Only emitted when health was actually spent; no constant smoke hiding the weapon.
        player.serverLevel().sendParticles(ParticleTypes.ASH,player.getX(),player.getY()+.8,player.getZ(),2,.15,.2,.15,.005);
    }
    public static void playLifeSteal(ServerPlayer player,LivingEntity target) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new BloodForgeEffectPayload(player.getId(),target.getId(),
                player.level().getGameTime(),BloodForgeEffectPayload.RECOVERY,8,
                target.getX(),target.getY()+target.getBbHeight()*.6,target.getZ()));
    }
    public static void playBloodMoonBurst(ServerPlayer player) {
        // The conditional release has a sound, while target contact is still gated by actual damage.
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.6f,.55f);
    }
}
