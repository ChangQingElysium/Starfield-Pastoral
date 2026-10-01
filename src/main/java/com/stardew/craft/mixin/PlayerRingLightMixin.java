package com.stardew.craft.mixin;

import com.stardew.craft.player.PlayerGlowState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerRingLightMixin implements PlayerGlowState {
    @Unique
    private static final EntityDataAccessor<Integer> STARDEWCRAFT_RING_LIGHT =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void stardewcraft$defineRingLight(CallbackInfo ci) {
        // PORT(1.20.1): defineSynchedData() takes no builder; entries are defined on the entity data directly.
        ((Player) (Object) this).getEntityData().define(STARDEWCRAFT_RING_LIGHT, 0);
    }

    @Override
    public int stardewcraft$getRingLight() {
        return ((Player) (Object) this).getEntityData().get(STARDEWCRAFT_RING_LIGHT);
    }

    @Override
    public void stardewcraft$setRingLight(int light) {
        ((Player) (Object) this).getEntityData().set(STARDEWCRAFT_RING_LIGHT, light);
    }
}
