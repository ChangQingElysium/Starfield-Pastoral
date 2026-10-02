package com.stardew.craft.port;

import com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity;
import com.stardew.craft.entity.seat.CushionEntity;
import com.stardew.craft.entity.seat.DoubleSwingSeatEntity;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * The Y coordinate of 1.21.1 getPassengerRidingPosition, used when the mod's melee rule clips a riding target's
 * hitbox. The old getPassengersRidingOffset is not that coordinate: 1.21 uses type passenger attachments and the
 * mod's seat overrides, and a player's feet lie 0.6 below its attachment. Computing the point must not call
 * positionRider, which also changes passenger position/rotation.
 */
public final class PortPassengerAttachments {
    private PortPassengerAttachments() {}

    public static double ridingY(Entity vehicle, Entity passenger) {
        if (vehicle instanceof CushionEntity seat) return seat.getPassengerRidingPosition(passenger).y;
        if (vehicle instanceof SofaSeatEntity seat) return seat.getPassengerRidingPosition(passenger).y;
        if (vehicle instanceof DoubleSwingSeatEntity seat) return seat.getPassengerRidingPosition(passenger).y;
        if (vehicle instanceof BirdSpringRiderSeatEntity seat) return seat.getPassengerRidingPosition(passenger).y;
        if (vehicle instanceof Boat boat) {
            float height = boat.getDimensions(boat.getPose()).height;
            return boat.getY() + (boat.getVariant() == Boat.Type.BAMBOO ? height * 0.8888889F : height / 3.0F);
        }
        if (vehicle instanceof AbstractMinecart) {
            return vehicle.getY() + (passenger instanceof Villager || passenger instanceof WanderingTrader ? 0.0 : 0.1875F);
        }
        if (vehicle instanceof Camel camel) return camel.getY() + camelAttachmentY(camel, passenger);

        float scale = vehicle instanceof LivingEntity living
                ? (living.isBaby() ? 0.5F : 1.0F) * PortAttributes.scale(living) : 1.0F;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        // Unknown 1.20.1 third-party vehicles have no 1.21 type definition to reproduce; preserve their own seat.
        if (!id.getNamespace().equals("minecraft") && !PortInheritance.isModEntity(vehicle)) {
            return vehicle.getY() + vehicle.getPassengersRidingOffset();
        }
        double attachment = typeAttachmentY(id.getPath(), vehicle.getType().getDimensions().height) * scale;
        if (vehicle instanceof Llama llama) {
            // 1.21 Llama.BABY_DIMENSIONS overrides its adult attachment before scaling by 0.5.
            if (llama.isBaby()) attachment = (vehicle.getType().getDimensions().height - 0.8125F) * scale;
        } else if (vehicle instanceof AbstractHorse horse) {
            attachment += 0.15 * horse.getStandAnim(0.0F) * scale;
        } else if (vehicle instanceof Strider strider) {
            float speed = Math.min(0.25F, strider.walkAnimation.speed());
            float offset = 0.12F * Mth.cos(strider.walkAnimation.position() * 1.5F) * 2.0F * speed;
            attachment += offset * scale;
        } else if (vehicle instanceof Slime slime) {
            attachment = slime.getDimensions(slime.getPose()).height - 0.015625 * slime.getSize() * scale;
        }
        return vehicle.getY() + attachment;
    }

    /** Explicit passenger attachment Y values from 1.21.1 EntityType; absent attachments fall back to type height. */
    private static double typeAttachmentY(String type, float height) {
        return switch (type) {
            case "cat" -> 0.5125F;
            case "chicken" -> 0.7;
            case "cow", "mooshroom" -> 1.36875F;
            case "donkey", "goat" -> 1.1125F;
            case "drowned", "piglin", "piglin_brute", "zombie" -> 2.0125F;
            case "elder_guardian" -> 2.350625F;
            case "ender_dragon" -> 3.0F;
            case "enderman" -> 2.80625F;
            case "endermite", "silverfish" -> 0.2375F;
            case "evoker", "illusioner", "pillager", "vindicator", "zombified_piglin" -> 2.0F;
            case "fox" -> 0.6375;
            case "frog" -> 0.375;
            case "ghast" -> 4.0625F;
            case "guardian" -> 0.975F;
            case "hoglin", "zoglin" -> 1.49375F;
            case "horse" -> 1.44375F;
            case "husk" -> 2.075F;
            case "llama", "trader_llama" -> 1.37;
            case "mule" -> 1.2125F;
            case "ocelot" -> 0.6375F;
            case "parrot" -> 0.4625F;
            case "phantom" -> 0.3375F;
            case "pig" -> 0.86875F;
            case "ravager" -> 2.2625;
            case "sheep" -> 1.2375F;
            case "skeleton_horse", "zombie_horse" -> 1.31875F;
            case "sniffer" -> 2.09375F;
            case "spider" -> 0.765F;
            case "turtle" -> 0.55625;
            case "vex" -> 0.7375F;
            case "warden" -> 3.15F;
            case "witch" -> 2.2625F;
            case "wolf" -> 0.81875;
            case "zombie_villager" -> 2.125F;
            default -> height;
        };
    }

    /** 1.21 Camel#getPassengerAttachmentPoint/getBodyAnchorAnimationYOffset at partialTick=0. */
    private static double camelAttachmentY(Camel camel, Entity passenger) {
        if (camel.isRemoved()) return 0.01F;
        float scale = camel.isBaby() ? 0.45F : 1.0F;
        float height = (camel.getType().getDimensions().height - (camel.getPose() == Pose.SITTING ? 1.43F : 0.0F)) * scale;
        double offset = height - 0.375F * scale;
        float standing = scale * 1.43F;
        float sitting = standing - scale * 0.2F;
        float difference = standing - sitting;
        boolean transition = camel.isInPoseTransition();
        boolean isSitting = camel.isCamelSitting();
        if (transition) {
            boolean first = Math.max(camel.getPassengers().indexOf(passenger), 0) == 0;
            int duration = isSitting ? 40 : 52;
            int split = isSitting ? 28 : first ? 24 : 32;
            float factor = isSitting ? first ? 0.5F : 0.1F : first ? 0.6F : 0.35F;
            float ticks = Mth.clamp((float) camel.getPoseTime(), 0.0F, (float) duration);
            boolean early = ticks < split;
            float progress = early ? ticks / split : (ticks - split) / (duration - split);
            float middle = standing - factor * sitting;
            offset += isSitting ? Mth.lerp(progress, early ? standing : middle, early ? middle : difference)
                    : Mth.lerp(progress, early ? difference - standing : difference - middle, early ? difference - middle : 0.0F);
        }
        if (isSitting && !transition) offset += difference;
        return (float) offset;
    }
}
