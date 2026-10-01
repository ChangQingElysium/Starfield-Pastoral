package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.combat.skill.YetiFreezeTracker;
import com.stardew.craft.combat.skill.handler.HeavyHammerSkillHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid=StardewCraft.MODID)
public final class HeavyHammerCombatEvents {
    private static final String PUSH = "stardewcraft_hammer_push";
    private HeavyHammerCombatEvents() {}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void attack(AttackEntityEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && HeavyHammerSkillHandler.isEmpowered(p)
                && !WeaponSkillContextStore.hasPending(p,p.level().getGameTime())) {
            // The normal primary hit and its area children must not run alongside a pound.
            e.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void block(PlayerInteractEvent.LeftClickBlock e) {
        if(e.getEntity() instanceof ServerPlayer p && HeavyHammerSkillHandler.isEmpowered(p)) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void knockback(LivingKnockBackEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && HeavyHammerSkillHandler.isEmpowered(p)) e.setStrength(e.getStrength()*.2f);
    }

    /** Shove after the brief stagger, otherwise its zero-velocity lock erases the impact. */
    public static void queuePush(LivingEntity target, Vec3 source, float strength, long tick) {
        Vec3 direction = source.subtract(target.position()).multiply(1, 0, 1);
        if (direction.lengthSqr() < 1.0E-6) return;
        var push = new net.minecraft.nbt.CompoundTag();
        push.putLong("tick", tick);
        push.putDouble("x", direction.x);
        push.putDouble("z", direction.z);
        push.putFloat("strength", strength);
        target.getPersistentData().put(PUSH, push);
    }

    public static void queueLift(LivingEntity target,long tick,double velocity) {
        var reaction=new net.minecraft.nbt.CompoundTag();
        reaction.putLong("tick",tick);reaction.putDouble("lift",velocity);
        target.getPersistentData().put(PUSH,reaction);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof LivingEntity target) || target.level().isClientSide
                || !target.getPersistentData().contains(PUSH)) return;
        var push = target.getPersistentData().getCompound(PUSH);
        long now = target.level().getGameTime();
        if (now < push.getLong("tick")) return;
        // A stronger subsequent control effect owns movement; do not replay an old shove later.
        target.getPersistentData().remove(PUSH);
        if (!target.isAlive() || now > push.getLong("tick") + 1 || YetiFreezeTracker.isMovementLocked(target, now)) return;
        if(push.getFloat("strength")>0)target.knockback(push.getFloat("strength"), push.getDouble("x"), push.getDouble("z"));
        if(push.getDouble("lift")>0&&target.onGround())target.setDeltaMovement(target.getDeltaMovement().add(0,push.getDouble("lift"),0));
        target.hurtMarked = true;
    }
}
