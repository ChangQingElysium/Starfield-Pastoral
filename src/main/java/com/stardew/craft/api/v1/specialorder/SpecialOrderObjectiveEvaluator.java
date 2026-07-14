package com.stardew.craft.api.v1.specialorder;

/** Returns the amount of progress to add for one event. */
@FunctionalInterface
public interface SpecialOrderObjectiveEvaluator<T> {
    int progress(SpecialOrderObjectiveContext context, T data, SpecialOrderProgressEvent event);
}
