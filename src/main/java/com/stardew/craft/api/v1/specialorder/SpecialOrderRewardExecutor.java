package com.stardew.craft.api.v1.specialorder;

@FunctionalInterface
public interface SpecialOrderRewardExecutor<T> {
    void grant(SpecialOrderRewardContext context, T data);
}
