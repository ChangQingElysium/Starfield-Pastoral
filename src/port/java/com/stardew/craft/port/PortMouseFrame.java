package com.stardew.craft.port;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): implemented on {@code MouseHandler} by {@code PortMouseHandlerScreenInputMixin}; the per-frame 1.21.1
 * {@code handleAccumulatedMovement} screen dispatch for StardewCraft screens.
 */
@OnlyIn(Dist.CLIENT)
public interface PortMouseFrame {
    void port$handleAccumulatedMovement();
}
