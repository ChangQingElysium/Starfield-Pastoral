package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes palette growth and bit-storage access with slot readers.
 * Keeps the vanilla checked-writer detector; callback traversal methods are not locked.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerStateAccessMixin<T> {
    @Unique
    private final ReentrantLock stardewcraft$stateAccessLock = new ReentrantLock();

    @WrapMethod(method = "acquire()V")
    private void stardewcraft$acquireStateAccess(Operation<Void> original) {
        original.call();
        stardewcraft$stateAccessLock.lock();
    }

    @WrapMethod(method = "release()V")
    private void stardewcraft$releaseStateAccess(Operation<Void> original) {
        try {
            original.call();
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "get(I)Ljava/lang/Object;")
    private T stardewcraft$getState(int index, Operation<T> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call(index);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "getAndSet(ILjava/lang/Object;)Ljava/lang/Object;")
    private T stardewcraft$getAndSetState(int index, T state, Operation<T> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call(index, state);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "set(ILjava/lang/Object;)V")
    private void stardewcraft$setState(int index, T state, Operation<Void> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            original.call(index, state);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "onResize(ILjava/lang/Object;)I")
    private int stardewcraft$resizeState(int bits, T state, Operation<Integer> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call(bits, state);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "copy()Lnet/minecraft/world/level/chunk/PalettedContainer;")
    private PalettedContainer<T> stardewcraft$copyState(Operation<PalettedContainer<T>> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call();
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }
}
