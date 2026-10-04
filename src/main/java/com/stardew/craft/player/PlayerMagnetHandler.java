package com.stardew.craft.player;

import com.stardew.craft.combat.equipment.EquipmentResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative attraction. Pickup delay, ownership and collision remain vanilla. */
public final class PlayerMagnetHandler {
    private PlayerMagnetHandler() {}

    /** 原版磁力半径以像素计，64 像素 = 1 格；食物 Buff（Bean Hotpot +32、Crispy Bass +64）按像素存储。 */
    private static final double PIXELS_PER_TILE = 64.0;
    /** 原版玩家无装备时的基础磁力半径 128 像素 = 2 格；戒指数值（RingType）已是含基础半径的总格数。 */
    private static final double BASE_RADIUS_TILES = 2.0;

    /** 总吸附半径（格）：戒指总半径（已含基础）+ 食物 Buff 像素加成换算；无戒指时 Buff 叠加在原版基础半径上。 */
    private static double attractRadius(ServerPlayer player) {
        double food = PlayerDataManager.getPlayerData(player).getTempMagneticRadiusBonus() / PIXELS_PER_TILE;
        int ring = EquipmentResolver.getMergedStats(player).getMagneticRadius();
        if (ring > 0) return ring + food;
        return food > 0 ? BASE_RADIUS_TILES + food : 0.0;
    }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        double radius = attractRadius(player);
        if (radius <= 0) return;

        Vec3 target = player.position().add(0.0, 0.35, 0.0);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(radius), ItemEntity::isAlive)) {
            if (!canAttract(player, item)) continue;
            Vec3 origin = item.getBoundingBox().getCenter();
            Vec3 delta = target.subtract(origin);
            double distance = delta.length();
            if (distance < 0.05 || distance > radius) continue;
            if (player.level().clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, item)).getType() != HitResult.Type.MISS) continue;

            // Let the closest eligible wearer pull, rather than applying competing forces.
            boolean closerWearer = false;
            for (ServerPlayer other : player.serverLevel().players()) {
                if (other == player || !other.isAlive() || other.isSpectator() || !canAttract(other, item)) continue;
                double otherDistance = other.position().add(0.0, 0.35, 0.0).distanceTo(origin);
                if (otherDistance >= distance) continue;
                double otherRadius = attractRadius(other);
                if (otherDistance <= otherRadius && player.level().clip(new ClipContext(origin,
                        other.position().add(0.0, 0.35, 0.0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, item)).getType() == HitResult.Type.MISS) {
                    closerWearer = true;
                    break;
                }
            }
            if (closerWearer) continue;

            // Ease towards a bounded target speed; slow down near the pickup box.
            double speed = Math.min(0.38, distance * 0.22);
            Vec3 travel = player.getKnownMovement().multiply(1, 0, 1);
            if (travel.lengthSqr() > 0.35 * 0.35) travel = travel.normalize().scale(0.35);
            Vec3 desired = delta.scale(speed / distance).add(travel);
            Vec3 motion = item.getDeltaMovement().lerp(desired, 0.3);
            // Counter the upcoming item gravity, without changing its gravity/physics flags.
            // Otherwise ground friction makes attracted items lag behind a walking wearer.
            if (item.getMaxHeightFluidType().isAir()) motion = motion.add(0, item.getGravity(), 0);
            item.setDeltaMovement(motion);
            item.hasImpulse = true;
            // Normal entity tracking synchronizes velocity. Forcing hurtMarked every tick
            // sends redundant correction packets and makes the client motion look abrupt.
        }
    }

    private static boolean canAttract(ServerPlayer player, ItemEntity item) {
        if (item.getItem().isEmpty() || item.hasPickUpDelay()) return false;
        if (item.getTarget() != null && !item.getTarget().equals(player.getUUID())) return false;
        return player.getInventory().getFreeSlot() >= 0
                || player.getInventory().getSlotWithRemainingSpace(item.getItem()) >= 0;
    }
}
