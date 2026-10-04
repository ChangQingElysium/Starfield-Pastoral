package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes palette growth and bit-storage access with slot readers.
 * Keeps the vanilla checked-writer detector; callback traversal methods are not locked.
 * Vanilla acquire/release can span an asynchronous world-generation handoff, so the
 * thread-owned lock covers individual operations, never that detector's lifetime.
 */
@Mixin(PalettedContainer.class)
public abstract class PalettedContainerStateAccessMixin<T> {
    @Unique
    private final ReentrantLock stardewcraft$stateAccessLock = new ReentrantLock();

    // Checked writes must take this lock before the vanilla detector, just like read/write/pack.
    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private T stardewcraft$getAndSetCheckedState(int x, int y, int z, T state, Operation<T> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call(x, y, z, state);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private void stardewcraft$setCheckedState(int x, int y, int z, T state, Operation<Void> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            original.call(x, y, z, state);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "pack(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)Lnet/minecraft/world/level/chunk/PalettedContainerRO$PackedData;")
    private PalettedContainerRO.PackedData<T> stardewcraft$packState(IdMap<T> registry,
            PalettedContainer.Strategy strategy, Operation<PalettedContainerRO.PackedData<T>> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call(registry, strategy);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "getSerializedSize()I")
    private int stardewcraft$serializedStateSize(Operation<Integer> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            return original.call();
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V")
    private void stardewcraft$readState(FriendlyByteBuf buffer, Operation<Void> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            original.call(buffer);
        } finally {
            stardewcraft$stateAccessLock.unlock();
        }
    }

    @WrapMethod(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V")
    private void stardewcraft$writeState(FriendlyByteBuf buffer, Operation<Void> original) {
        stardewcraft$stateAccessLock.lock();
        try {
            original.call(buffer);
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
