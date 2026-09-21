package com.stardew.craft.combat;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Injectable random source used by damage calculations.
 */
@FunctionalInterface
public interface DamageRandomSource {
    DamageRandomSource THREAD_LOCAL = () -> ThreadLocalRandom.current().nextFloat();

    float nextFloat();

    static DamageRandomSource threadLocal() {
        return THREAD_LOCAL;
    }
}
