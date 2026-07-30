package com.stardew.craft.mixin;

import com.stardew.craft.client.combat.CombatCollapseClientState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light initial camera jitter matching the original Farmer jitterStrength feedback. */
@Mixin(Camera.class)
public abstract class CameraCombatCollapseMixin {
    @Shadow
    private float yRot;
    @Shadow
    private float xRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void stardewcraft$applyCombatCollapseJitter(
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        if (!CombatCollapseClientState.isActive()) {
            return;
        }
        setRotation(
            yRot + CombatCollapseClientState.cameraYawJitter(partialTick),
            xRot + CombatCollapseClientState.cameraPitchJitter(partialTick)
        );
    }
}
