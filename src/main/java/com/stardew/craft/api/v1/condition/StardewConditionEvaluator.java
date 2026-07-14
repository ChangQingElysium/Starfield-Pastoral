package com.stardew.craft.api.v1.condition;

/** Evaluates one decoded condition payload. */
@FunctionalInterface
public interface StardewConditionEvaluator<T> {
    boolean test(StardewConditionContext context, T data);
}
