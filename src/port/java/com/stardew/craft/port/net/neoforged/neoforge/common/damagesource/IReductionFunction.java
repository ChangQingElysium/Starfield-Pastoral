package com.stardew.craft.port.net.neoforged.neoforge.common.damagesource;

/** PORT(1.20.1): NeoForge 21.1 damage reduction modifier. */
@FunctionalInterface
public interface IReductionFunction {
    /**
     * @param container   the damage container for this sequence
     * @param reductionIn the reduction computed by vanilla and preceding modifiers
     * @return the new reduction value
     */
    float modify(DamageContainer container, float reductionIn);
}
