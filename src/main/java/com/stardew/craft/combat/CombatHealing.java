package com.stardew.craft.combat;

import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.server.level.ServerPlayer;

/**
 * Routes healing to the health model that is authoritative in the current dimension.
 */
public final class CombatHealing {
    private CombatHealing() {}

    public static float heal(ServerPlayer player, float amount) {
        if (player == null || amount <= 0.0F) {
            return 0.0F;
        }

        if (DimensionDamageMapper.isInStardewDimension(player)) {
            int current = PlayerStardewDataAPI.getHealth(player);
            int maximum = PlayerStardewDataAPI.getMaxHealth(player);
            int requested = Math.max(0, Math.round(amount));
            int actual = Math.round(cappedIncrease(current, maximum, requested));
            if (actual > 0) {
                PlayerStardewDataAPI.setHealth(player, current + actual);
            }
            return actual;
        }

        float before = player.getHealth();
        player.heal(amount);
        return Math.max(0.0F, player.getHealth() - before);
    }

    public static float healFraction(
            ServerPlayer player,
            float maximumHealthRatio,
            float minimumAmount
    ) {
        if (player == null || maximumHealthRatio <= 0.0F) {
            return 0.0F;
        }
        float maximum = maximumHealth(player);
        return heal(
                player,
                Math.max(minimumAmount, maximum * maximumHealthRatio)
        );
    }

    public static float maximumHealth(ServerPlayer player) {
        if (player == null) {
            return 0.0F;
        }
        return DimensionDamageMapper.isInStardewDimension(player)
                ? PlayerStardewDataAPI.getMaxHealth(player)
                : player.getMaxHealth();
    }

    public static float currentHealth(ServerPlayer player) {
        if (player == null) {
            return 0.0F;
        }
        return DimensionDamageMapper.isInStardewDimension(player)
                ? PlayerStardewDataAPI.getHealth(player)
                : player.getHealth();
    }

    /**
     * Pays a health cost against the authoritative health model without allowing
     * the player to fall below {@code minimumRemaining}.
     *
     * @return the health actually consumed
     */
    public static float spendNonlethal(
            ServerPlayer player,
            float requested,
            float minimumRemaining
    ) {
        if (player == null || requested <= 0.0F) {
            return 0.0F;
        }

        if (DimensionDamageMapper.isInStardewDimension(player)) {
            int current = PlayerStardewDataAPI.getHealth(player);
            int consumed = Math.round(nonlethalReduction(
                    current,
                    Math.round(requested),
                    Math.round(minimumRemaining)
            ));
            if (consumed > 0) {
                PlayerStardewDataAPI.setHealth(player, current - consumed);
            }
            return consumed;
        }

        float current = player.getHealth();
        float consumed = nonlethalReduction(
                current,
                requested,
                minimumRemaining
        );
        if (consumed > 0.0F) {
            player.setHealth(current - consumed);
        }
        return consumed;
    }

    static float cappedIncrease(float current, float maximum, float requested) {
        if (requested <= 0.0F || current >= maximum) {
            return 0.0F;
        }
        return Math.min(requested, maximum - current);
    }

    static float nonlethalReduction(
            float current,
            float requested,
            float minimumRemaining
    ) {
        if (requested <= 0.0F || current <= minimumRemaining) {
            return 0.0F;
        }
        return Math.min(requested, current - minimumRemaining);
    }
}
