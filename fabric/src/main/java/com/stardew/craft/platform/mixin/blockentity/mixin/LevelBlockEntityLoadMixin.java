package com.stardew.craft.platform.mixin.blockentity.mixin;


import com.stardew.craft.platform.fabric.blockentity.BlockEntityLoadQueue;
import com.stardew.craft.platform.fabric.blockentity.LevelBlockEntityLoads;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelBlockEntityLoadMixin implements LevelBlockEntityLoads {
    @Unique
    private final BlockEntityLoadQueue stardewcraft$pendingBlockEntityLoads = new BlockEntityLoadQueue();

    @Override
    public BlockEntityLoadQueue stardewcraft$blockEntityLoads() {
        return stardewcraft$pendingBlockEntityLoads;
    }

    @Inject(method = "tickBlockEntities", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;tickingBlockEntities:Z",
            opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void stardewcraft$runLoadedCallbacks(CallbackInfo callback) {
        stardewcraft$pendingBlockEntityLoads.drain((Level) (Object) this);
    }
}
