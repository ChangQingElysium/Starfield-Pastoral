package com.stardew.craft.combat;

import com.stardew.craft.combat.network.SteelSpineFuryStrikePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pre-application effects owned by active authored skill execution state. */
final class BuiltinSkillEvaluatedHitRules {
    private BuiltinSkillEvaluatedHitRules() {
    }

    static void emitSteelSpineStrike(EvaluatedWeaponHit hit) {
        if (hit.steelSpineBoost() != null
                && hit.successful()
                && hit.attacker() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(
                    player,
                    new SteelSpineFuryStrikePayload(
                            hit.steelSpineBoost().strong()
                    )
            );
        }
    }

}
