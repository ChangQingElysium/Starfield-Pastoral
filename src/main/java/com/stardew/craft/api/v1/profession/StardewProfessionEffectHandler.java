package com.stardew.craft.api.v1.profession;

@FunctionalInterface
public interface StardewProfessionEffectHandler {
    double apply(StardewProfessionEffectContext context);
}
