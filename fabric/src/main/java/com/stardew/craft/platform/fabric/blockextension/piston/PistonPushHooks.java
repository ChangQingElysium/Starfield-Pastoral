package com.stardew.craft.platform.fabric.blockextension.piston;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

public final class PistonPushHooks {
    private PistonPushHooks() {}
    public static PushReaction reaction(BlockState state, PushReaction declared) {
        if (state.getBlock() instanceof PistonPushProvider provider) {
            PushReaction reaction = provider.getPistonPushReaction(state);
            if (reaction != null) return reaction;
        }
        return declared;
    }
}
