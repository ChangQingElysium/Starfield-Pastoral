package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.item.ModItems;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): the Moonlight Jellies festival seats a water-lantern {@code ItemDisplay} on a vanilla oak boat. 1.21.1
 * places a boat passenger at {@code position + (0, height / 3 (raft: height * 0.8888889), xOffset).yRot(-yRot)} minus
 * the passenger's vehicle attachment (feet for a display entity), i.e. 0.1875 above the boat; 1.20.1 uses
 * {@code getPassengersRidingOffset() + getMyRidingOffset()} = -0.1. Only the lantern display is moved to its 1.21.1
 * position (both logical sides run {@code positionRider}); every other boat passenger keeps the 1.20.1 placement and
 * the rotation code after the move is unchanged (1.21 applies it to all passengers outside
 * {@code #can_turn_in_boats}, which display entities are not in).
 */
@Mixin(Boat.class)
public abstract class PortBoatLanternRiderMixin {
    @Shadow
    protected abstract float getSinglePassengerXOffset();

    @WrapOperation(method = "positionRider", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity$MoveFunction;accept(Lnet/minecraft/world/entity/Entity;DDD)V"))
    private void stardewcraft$lanternRidingPosition(Entity.MoveFunction callback, Entity passenger, double x, double y,
            double z, Operation<Void> original) {
        if (!(passenger instanceof Display.ItemDisplay display)
                || !display.getSlot(0).get().is(ModItems.WATER_LANTERN.get())) {
            original.call(callback, passenger, x, y, z);
            return;
        }
        Boat boat = (Boat) (Object) this;
        // 1.21.1 Boat#getPassengerAttachmentPoint(passenger, dimensions, 1.0F).
        float offset = this.getSinglePassengerXOffset();
        if (boat.getPassengers().size() > 1) {
            int index = boat.getPassengers().indexOf(passenger);
            offset = index == 0 ? 0.2F : -0.6F;
            if (passenger instanceof Animal) offset += 0.2F;
        }
        float height = boat.getDimensions(boat.getPose()).height;
        Vec3 attachment = new Vec3(0.0,
                boat.getVariant() == Boat.Type.BAMBOO ? (double) (height * 0.8888889F) : (double) (height / 3.0F),
                (double) offset).yRot(-boat.getYRot() * (float) (Math.PI / 180.0));
        // 1.21.1 Entity#positionRider: riding position minus the passenger's vehicle attachment point (feet = zero).
        Vec3 riding = boat.position().add(attachment);
        original.call(callback, passenger, riding.x, riding.y, riding.z);
    }
}
