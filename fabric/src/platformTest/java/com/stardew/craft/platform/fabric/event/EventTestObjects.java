package com.stardew.craft.platform.fabric.event;

/** Identity-only payload fixtures: no constructors, worlds, servers, or gameplay are run. */
final class EventTestObjects {
    private EventTestObjects() {}

    static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        return type.cast(allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, type));
    }
}
