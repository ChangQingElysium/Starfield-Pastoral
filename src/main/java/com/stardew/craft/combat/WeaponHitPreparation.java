package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.handler.DragontoothShivBreathSkillHandler;
import com.stardew.craft.combat.skill.handler.InsectEyeStanceSkillHandler;
import com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzySkillHandler;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ordered context transformation for one weapon hit before damage assembly.
 *
 * <p>The order is authored behavior: insect stance consumes first, steel
 * spine only sees a still-normal attack, then weapon passives and remaining
 * runtime stances amend critical state. Do not replace this sequence with an
 * unordered registry.</p>
 */
public record WeaponHitPreparation(
        SkillContext skillContext,
        SteelSpineFurySkillHandler.AttackBoost steelSpineBoost,
        Reservation reservation
) {
    public WeaponHitPreparation {
        Objects.requireNonNull(skillContext, "skillContext");
        Objects.requireNonNull(reservation, "reservation");
    }

    public static WeaponHitPreparation prepare(
            Player player,
            LivingEntity target,
            ItemStack weapon,
            SkillContext initialContext,
            long gameTick
    ) {
        return prepareInternal(
                player,
                target,
                weapon,
                initialContext,
                gameTick,
                false
        );
    }

    /**
     * Prepares damage before native iframe/protection while reserving, rather
     * than consuming, one-shot stance resources.
     */
    public static WeaponHitPreparation reserve(
            Player player,
            LivingEntity target,
            ItemStack weapon,
            SkillContext initialContext,
            long gameTick
    ) {
        return prepareInternal(
                player,
                target,
                weapon,
                initialContext,
                gameTick,
                true
        );
    }

    private static WeaponHitPreparation prepareInternal(
            Player player,
            LivingEntity target,
            ItemStack weapon,
            SkillContext initialContext,
            long gameTick,
            boolean reserveOneShotResources
    ) {
        SkillContext context = Objects.requireNonNull(
                initialContext,
                "initialContext"
        );
        SteelSpineFurySkillHandler.AttackBoost spineBoost = null;
        List<Runnable> commits = new ArrayList<>();
        List<Runnable> releases = new ArrayList<>();

        if (isNormal(context) && player instanceof ServerPlayer serverPlayer) {
            SkillContext stanceContext;
            if (reserveOneShotResources) {
                InsectEyeStanceSkillHandler.AttackReservation reserved =
                        InsectEyeStanceSkillHandler.reserveAttack(
                                serverPlayer,
                                gameTick
                        );
                stanceContext = reserved == null
                        ? null
                        : reserved.skillContext();
                if (reserved != null) {
                    commits.add(reserved.commit());
                    releases.add(reserved.release());
                }
            } else {
                stanceContext = InsectEyeStanceSkillHandler.consumeAttack(
                        serverPlayer,
                        gameTick
                );
            }
            if (stanceContext != null) {
                context = stanceContext;
            }
        }

        if (isNormal(context) && player instanceof ServerPlayer serverPlayer) {
            if (reserveOneShotResources) {
                SteelSpineFurySkillHandler.AttackReservation reserved =
                        SteelSpineFurySkillHandler.reserveAttack(
                                serverPlayer,
                                gameTick
                        );
                spineBoost = reserved == null ? null : reserved.boost();
                if (reserved != null) {
                    commits.add(reserved.commit());
                    releases.add(reserved.release());
                }
            } else {
                spineBoost = SteelSpineFurySkillHandler.consumeAttack(
                        serverPlayer,
                        gameTick
                );
            }
            if (spineBoost != null) {
                context = SkillContext.builder()
                        .skillId(spineBoost.strong()
                                ? "steel_spine_fury"
                                : "steel_spine_fury_weak")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(spineBoost.damageMultiplier())
                        .build();
            }
        }

        IStardewWeapon builtIn = weapon.getItem() instanceof IStardewWeapon item
                ? item
                : null;
        String weaponId = builtIn == null ? null : builtIn.getWeaponId();

        if (player instanceof ServerPlayer serverPlayer
                && "crystal_dagger".equals(weaponId)) {
            int stacks = CrystalDaggerLayerTracker.getStacks(
                    serverPlayer,
                    gameTick
            );
            if (stacks > 0) {
                context = copy(context)
                        .critChanceBonus(
                                context.getCritChanceBonus() + stacks * 0.02F
                        )
                        .build();
            }
        }

        if (isNormal(context)
                && player instanceof ServerPlayer serverPlayer
                && "dragontooth_cutlass".equals(weaponId)) {
            int stacks = DragonBreathTracker.getStacks(serverPlayer);
            if (stacks > 0) {
                context = SkillContext.builder()
                        .skillId("normal")
                        .tier(SkillContext.SkillTier.NORMAL)
                        .damageMultiplier(1.0F + stacks * 0.01F)
                        .build();
            }
        }

        if (player instanceof ServerPlayer serverPlayer
                && "dragontooth_shiv".equals(weaponId)) {
            boolean stanceActive = DragontoothShivBreathSkillHandler.isActive(
                    serverPlayer,
                    gameTick
            );
            if (stanceActive || isBackstab(player, target)) {
                String skillId = context.getSkillId();
                if (stanceActive && "normal".equals(skillId)) {
                    skillId = "dragontooth_shiv_breath";
                }
                context = copy(context)
                        .skillId(skillId)
                        .guaranteedCrit(true)
                        .build();
            }
        }

        if (player instanceof ServerPlayer serverPlayer
                && "iridium_needle".equals(weaponId)) {
            boolean frenzyActive = IridiumNeedleFrenzySkillHandler.isActive(
                    serverPlayer,
                    gameTick
            );
            boolean forceCrit = IridiumNeedleCritTracker.shouldGuaranteeCrit(
                    serverPlayer
            );
            if (frenzyActive || forceCrit) {
                String skillId = context.getSkillId();
                if (frenzyActive && "normal".equals(skillId)) {
                    skillId = "iridium_needle_frenzy";
                }
                float critBonus = context.getCritChanceBonus()
                        + (frenzyActive
                                ? IridiumNeedleFrenzySkillHandler.CRIT_CHANCE_BONUS
                                : 0.0F);
                context = copy(context)
                        .skillId(skillId)
                        .guaranteedCrit(context.isGuaranteedCrit() || forceCrit)
                        .critChanceBonus(critBonus)
                        .build();
            }
        }

        return new WeaponHitPreparation(
                context,
                spineBoost,
                new Reservation(commits, releases)
        );
    }

    /** Idempotent commit-or-release transaction for one exact hurt call. */
    public static final class Reservation {
        private final List<Runnable> commits;
        private final List<Runnable> releases;
        private boolean settled;

        private Reservation(
                List<Runnable> commits,
                List<Runnable> releases
        ) {
            this.commits = List.copyOf(commits);
            this.releases = List.copyOf(releases);
        }

        public void commit() {
            if (settled) return;
            settled = true;
            commits.forEach(Runnable::run);
        }

        public void release() {
            if (settled) return;
            settled = true;
            for (int index = releases.size() - 1; index >= 0; index--) {
                releases.get(index).run();
            }
        }
    }

    private static boolean isNormal(SkillContext context) {
        return "normal".equals(context.getSkillId());
    }

    private static SkillContext.Builder copy(SkillContext context) {
        return SkillContext.builder()
                .skillId(context.getSkillId())
                .tier(context.getTier())
                .damageMultiplier(context.getDamageMultiplier())
                .ignoreDefense(context.isIgnoreDefense())
                .guaranteedCrit(context.isGuaranteedCrit())
                .critChanceBonus(context.getCritChanceBonus())
                .defaultKnockback(context.usesDefaultKnockback());
    }

    private static boolean isBackstab(Player player, LivingEntity target) {
        Vec3 toAttacker = player.position().subtract(target.position());
        Vec3 targetLook = target.getLookAngle();
        Vec3 toFlat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
        Vec3 lookFlat = new Vec3(targetLook.x, 0.0, targetLook.z);
        if (toFlat.lengthSqr() < 1.0E-4 || lookFlat.lengthSqr() < 1.0E-4) {
            return false;
        }
        return toFlat.normalize().dot(lookFlat.normalize()) < -0.35;
    }
}
