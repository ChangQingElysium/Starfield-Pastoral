package com.stardew.craft.port;

import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.function.Function;

/**
 * DFU 8 (1.21) {@code DataResult#getOrThrow} / {@code getPartialOrThrow}. DFU 6 (1.20.1) only has
 * {@code getOrThrow(boolean allowPartial, Consumer<String> onError)}, which throws a bare
 * {@code RuntimeException} after calling the consumer. These reproduce DFU 8 exactly:
 * a success returns its value, an error throws {@code exceptionSupplier.apply(message)}
 * ({@link IllegalStateException} for the no-arg forms); {@code getPartialOrThrow} returns the
 * partial value of an error when present.
 */
public final class PortDataResults {
    private PortDataResults() {
    }

    public static <R> R getOrThrow(DataResult<R> result) {
        return getOrThrow(result, IllegalStateException::new);
    }

    public static <R, E extends Throwable> R getOrThrow(DataResult<R> result, Function<String, E> exceptionSupplier)
            throws E {
        Optional<DataResult.PartialResult<R>> error = result.error();
        if (error.isPresent()) {
            throw exceptionSupplier.apply(error.get().message());
        }
        return result.result().orElseThrow();
    }

    public static <R> R getPartialOrThrow(DataResult<R> result) {
        return getPartialOrThrow(result, IllegalStateException::new);
    }

    public static <R, E extends Throwable> R getPartialOrThrow(DataResult<R> result,
            Function<String, E> exceptionSupplier) throws E {
        Optional<DataResult.PartialResult<R>> error = result.error();
        if (error.isEmpty()) {
            return result.result().orElseThrow();
        }
        Optional<R> partial = result.resultOrPartial(message -> {
        });
        if (partial.isPresent()) {
            return partial.get();
        }
        throw exceptionSupplier.apply(error.get().message());
    }
}
