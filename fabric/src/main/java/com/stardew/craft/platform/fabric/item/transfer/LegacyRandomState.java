package com.stardew.craft.platform.fabric.item.transfer;

import java.util.concurrent.atomic.AtomicLong;

/** Narrow state access for the native, server-thread LegacyRandomSource only. */
public interface LegacyRandomState {
    AtomicLong stardewcraft$randomSeed();
}
