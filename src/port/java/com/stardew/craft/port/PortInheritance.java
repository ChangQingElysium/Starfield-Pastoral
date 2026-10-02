package com.stardew.craft.port;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * PORT(1.20.1): 1.21.1 default implementations that StardewCraft classes inherit from vanilla/NeoForge and that differ
 * from what the same classes inherit on Forge 1.20.1. The mixins that use these helpers only apply them to
 * StardewCraft objects (registry namespace {@code stardewcraft}); vanilla and other mods keep 1.20.1 behaviour.
 * See {@code docs/porting/bulk-port-gaps.md}, section "继承的原版默认实现".
 */
public final class PortInheritance {
    /** 1.21 {@code Mob.DEFAULT_ATTACK_REACH}. */
    public static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;

    private PortInheritance() {
    }

    public static boolean isModBlock(Block block) {
        return StardewCraft.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
    }

    public static boolean isModEntity(Entity entity) {
        return StardewCraft.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    /** Classes compiled from the mod's sources (screens, widgets, renderers, ...). */
    public static boolean isModClass(Object object) {
        return object.getClass().getName().startsWith("com.stardew.craft.");
    }

    /**
     * Projectiles whose {@code shoot}/{@code shootFromRotation} follow 1.21: the mod's own projectiles and the
     * vanilla fishing hook (vanilla never calls {@code shootFromRotation} on a {@link FishingHook}; the mod's fishing
     * cast does).
     */
    public static boolean usesModProjectileRules(Entity projectile) {
        return projectile instanceof FishingHook || isModEntity(projectile);
    }

    /** 1.21 {@code LivingEntity#getScale()}: only the SCALE attribute (age scaling is separate). */
    public static float scale(LivingEntity entity) {
        return PortAttributes.scale(entity);
    }

    /** 1.21 {@code LivingEntity#getDefaultDimensions(pose).scale(getScale())} for a non-sleeping pose. */
    public static EntityDimensions dimensions(LivingEntity entity) {
        return defaultDimensions(entity).scale(scale(entity));
    }

    /** 1.21 LivingEntity's base default dimensions, before the separate attribute scale. */
    public static EntityDimensions defaultDimensions(LivingEntity entity) {
        float ageScale = entity.isBaby() ? 0.5F : 1.0F;
        return entity.getType().getDimensions().scale(ageScale);
    }

    /** 1.21 {@code Mob#getAttackBoundingBox()}. */
    public static AABB attackBoundingBox(Mob mob) {
        Entity vehicle = mob.getVehicle();
        AABB box;
        if (vehicle != null) {
            AABB vehicleBox = vehicle.getBoundingBox();
            AABB own = mob.getBoundingBox();
            box = new AABB(
                    Math.min(own.minX, vehicleBox.minX), own.minY, Math.min(own.minZ, vehicleBox.minZ),
                    Math.max(own.maxX, vehicleBox.maxX), own.maxY, Math.max(own.maxZ, vehicleBox.maxZ));
        } else {
            box = mob.getBoundingBox();
        }
        return box.inflate(DEFAULT_ATTACK_REACH, 0.0, DEFAULT_ATTACK_REACH);
    }

    /**
     * 1.21 {@code LivingEntity#getHitbox()}. Clip at the vehicle's 1.21 passenger attachment point, not the 1.20.1
     * riding offset or the passenger's feet (players have a separate 0.6-high vehicle attachment).
     */
    public static AABB hitbox(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        Entity vehicle = entity.getVehicle();
        if (vehicle == null) return box;
        double ridingY = PortPassengerAttachments.ridingY(vehicle, entity);
        return new AABB(box.minX, Math.max(ridingY, box.minY), box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    /** 1.21 {@code Mob#isWithinMeleeAttackRange(target)}. */
    public static boolean isWithinMeleeAttackRange(Mob mob, LivingEntity target) {
        return attackBoundingBox(mob).intersects(hitbox(target));
    }

    /** 1.21 {@code Projectile#shootFromRotation}: the shooter's motion contribution is its known movement. */
    public static Vec3 shooterMovement(Entity shooter) {
        return PortEntities.getKnownMovement(shooter);
    }
}
