package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EntityTeleportEvent extends EntityEvent {
    private double x, y, z;
    public EntityTeleportEvent(Entity entity, double x, double y, double z) {
        super(entity, true);
        this.x = x; this.y = y; this.z = z;
    }
    public double getTargetX() { return x; }
    public double getTargetY() { return y; }
    public double getTargetZ() { return z; }
    public void setTargetX(double x) { this.x = x; }
    public void setTargetY(double y) { this.y = y; }
    public void setTargetZ(double z) { this.z = z; }
    public Vec3 getTarget() { return new Vec3(x, y, z); }
    public double getPrevX() { return getEntity().getX(); }
    public double getPrevY() { return getEntity().getY(); }
    public double getPrevZ() { return getEntity().getZ(); }
    public Vec3 getPrev() { return getEntity().position(); }
    public static final class TeleportCommand extends EntityTeleportEvent {
        public TeleportCommand(Entity entity, double x, double y, double z) { super(entity, x, y, z); }
    }
    public static final class SpreadPlayersCommand extends EntityTeleportEvent {
        public SpreadPlayersCommand(Entity entity, double x, double y, double z) { super(entity, x, y, z); }
    }
    public static final class EnderEntity extends EntityTeleportEvent {
        public EnderEntity(LivingEntity entity, double x, double y, double z) { super(entity, x, y, z); }
        public LivingEntity getEntityLiving() { return (LivingEntity) getEntity(); }
    }
    public static final class ChorusFruit extends EntityTeleportEvent {
        public ChorusFruit(LivingEntity entity, double x, double y, double z) { super(entity, x, y, z); }
        public LivingEntity getEntityLiving() { return (LivingEntity) getEntity(); }
    }
    public static final class EnderPearl extends EntityTeleportEvent {
        private final ThrownEnderpearl pearl;
        private final HitResult result;
        private float damage;
        public EnderPearl(ServerPlayer player, double x, double y, double z, ThrownEnderpearl pearl, float damage, HitResult result) {
            super(player, x, y, z); this.pearl = pearl; this.damage = damage; this.result = result;
        }
        public ThrownEnderpearl getPearlEntity() { return pearl; }
        public ServerPlayer getPlayer() { return (ServerPlayer) getEntity(); }
        public HitResult getHitResult() { return result; }
        public float getAttackDamage() { return damage; }
        public void setAttackDamage(float damage) { this.damage = damage; }
    }
}
