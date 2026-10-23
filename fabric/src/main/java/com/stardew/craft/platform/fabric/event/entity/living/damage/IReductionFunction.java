package com.stardew.craft.platform.fabric.event.entity.living.damage;

@FunctionalInterface
public interface IReductionFunction {
    float modify(DamageContainer container, float reductionIn);
}
