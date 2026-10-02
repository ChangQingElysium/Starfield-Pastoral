package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): the Moonlight Jellies festival seats a vanilla {@code ItemDisplay} lantern in a vanilla boat and
 * relies on the 1.21.1 seat height: 1.21 places the passenger's vehicle attachment point (feet for non-players) at
 * {@code boat height / 3} (bamboo raft: {@code height * 8/9}) above the boat, 1.20.1 at
 * {@code getPassengersRidingOffset() + passenger.getMyRidingOffset()} = -0.1 (raft 0.25), i.e. 0.2875 lower.
 * The 1.21.1 height is used only for boats carrying a {@code stardewcraft*} scoreboard tag (entities the mod spawns
 * and owns); every other boat keeps 1.20.1 seating. The horizontal offset is the same in both versions.
 */
@Mixin(Boat.class)
public abstract class PortBoatPassengerHeightMixin {
    @WrapOperation(method = "positionRider", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/Boat;getPassengersRidingOffset()D"))
    private double stardewcraft$seatHeight121(Boat boat, Operation<Double> original, @Local(argsOnly = true) Entity passenger) {
        if (boat.getTags().stream().noneMatch(tag -> tag.startsWith("stardewcraft"))) return original.call(boat);
        float height = boat.getDimensions(boat.getPose()).height;
        double attachment = boat.getVariant() == Boat.Type.BAMBOO ? height * 0.8888889F : height / 3.0F;
        // 1.20.1 adds passenger.getMyRidingOffset() to this value; 1.21 subtracts the passenger's vehicle attachment.
        return attachment - PortEntities.vehicleAttachmentPoint(passenger).y - passenger.getMyRidingOffset();
    }
}
