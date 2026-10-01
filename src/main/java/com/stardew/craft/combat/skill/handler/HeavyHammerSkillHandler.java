package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.*;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import static com.stardew.craft.combat.skill.handler.HeavyHammerRules.*;

/** Four authored skills share release ownership; the empowerment remains active alongside the minor skill. */
public final class HeavyHammerSkillHandler implements RuntimeWeaponSkillHandler {
    private final String id;
    public HeavyHammerSkillHandler(String id) { this.id = id; }
    @Override public SkillValidation validate(SkillExecutionContext c) {
        if (c.hand() != InteractionHand.MAIN_HAND || !supports(c.weaponId().getPath(), id)
                || c.player().isSpectator() || !c.player().isAlive()
                || WeaponSkillAnimationLock.isLocked(c.player(), c.nowTick())
                || WeaponSkillRuntime.hasActive(c.player().getUUID(), c.skillId())) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        return WeaponSkillCooldowns.isOnCooldown(c.player(), c.weaponId().getPath(), id, c.nowTick())
                ? SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN) : SkillValidation.accept();
    }
    @Override public void begin(SkillExecutionContext c, SkillInstance instance) {
        WeaponSkillRuntime.commitCooldown(c, instance, cooldown(id) * 20);
        HeavyHammerExecutionState state = new HeavyHammerExecutionState(c, id);
        instance.initializeExecutionState(state);
        instance.registerCommittedEffect(() -> {
            WeaponSkillAnimationLock.setLock(c.player(), c.nowTick(), animationTicks(id));
            WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(), c.weaponId().getPath(), id, animationTicks(id));
            if (ENDLESS.equals(id)) state.sendBuff(c.player(), true);
        });
    }
    @Override public boolean completesImmediately() { return false; }
    @Override public SkillTickResult tick(SkillExecutionContext c, SkillInstance i) {
        return i.requireExecutionState(HeavyHammerExecutionState.class).advance(c);
    }
    @Override public void finish(SkillExecutionContext c, SkillInstance i, SkillInstance.EndReason reason) {
        i.executionState(HeavyHammerExecutionState.class).ifPresent(state -> {
            state.cancel();
            if (ENDLESS.equals(id)) state.sendBuff(c.player(), false);
            if (c.nowTick() < state.startTick + animationTicks(id)) WeaponSkillAnimationLock.clear(c.player());
        });
    }
    private static HeavyHammerExecutionState buff(ServerPlayer p) {
        if (p == null || !p.isAlive() || p.isSpectator()
                || !(p.getMainHandItem().getItem() instanceof IStardewWeapon w)
                || !"infinity_gavel".equals(w.getWeaponId())) return null;
        return WeaponSkillRuntime.activeExecutionState(p.getUUID(),
                new ResourceLocation("stardewcraft", ENDLESS), HeavyHammerExecutionState.class)
                .filter(s -> s.validHeld(p) && s.buffActive(p.level().getGameTime())).orElse(null);
    }
    public static boolean isEmpowered(ServerPlayer p) { return buff(p) != null; }
    /** Input only renews a short lease. The server owns timing, aim, targets and damage. */
    public static void attackIntent(ServerPlayer p, boolean held) {
        HeavyHammerExecutionState state = buff(p);
        if (state != null) state.attackIntent(p.level().getGameTime(), held);
    }
    public static void appliedHit(ResolvedWeaponHit hit) {
        if (hit.dealtPositiveDamage() && hit.attacker() instanceof ServerPlayer p
                && isWeapon(hit.weaponIdentity().logicId())) {
            HeavyHammerExecutionState.appliedHit(p, hit);
        }
    }
}
