package com.stardew.craft.port;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** 1.20.2+ level query helpers missing from 1.20.1. */
public final class PortLevels {
    private PortLevels() {}

    /** 1.21 {@code CollisionGetter#noBlockCollision}: block shapes only, unlike {@code noCollision}. */
    public static boolean noBlockCollision(CollisionGetter level, @Nullable Entity entity, AABB box) {
        for (VoxelShape shape : level.getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static java.lang.reflect.Method lightAddTask;
    private static Object lightPostUpdate;

    /**
     * 1.21 {@code ThreadedLevelLightEngine#waitForPendingTasks(x, z)}: a future completed by an empty task queued as
     * {@code POST_UPDATE} for the chunk, i.e. after the light work already queued for it. 1.20.1 has the same private
     * {@code addTask(int, int, TaskType, Runnable)} (SRG {@code m_9312_}) but no public wrapper.
     */
    public static java.util.concurrent.CompletableFuture<?> waitForPendingTasks(
            net.minecraft.server.level.ThreadedLevelLightEngine engine, int x, int z) {
        java.lang.reflect.Method addTask = lightAddTask();
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
        }, task -> {
            try {
                addTask.invoke(engine, x, z, lightPostUpdate, task);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("ThreadedLevelLightEngine#addTask", exception);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized java.lang.reflect.Method lightAddTask() {
        if (lightAddTask == null) {
            try {
                Class<? extends Enum> taskType = (Class<? extends Enum>) Class.forName(
                        "net.minecraft.server.level.ThreadedLevelLightEngine$TaskType");
                lightPostUpdate = Enum.valueOf(taskType, "POST_UPDATE");
                lightAddTask = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        net.minecraft.server.level.ThreadedLevelLightEngine.class, "m_9312_",
                        int.class, int.class, taskType, Runnable.class);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("ThreadedLevelLightEngine$TaskType", exception);
            }
        }
        return lightAddTask;
    }

    /**
     * 1.21 {@code MinecraftServer#isPaused()}: false except for an integrated server paused by its client (the
     * 1.20.1 {@code IntegratedServer.paused} field, exposed by the client-only {@code PortIntegratedServerAccessor}).
     */
    public static boolean isPaused(net.minecraft.server.MinecraftServer server) {
        // PORT(1.20.1): the accessor lives in the mixin package and is only registered by the client mixin list;
        // on a dedicated/game test server, merely resolving it throws Mixin's IllegalClassLoadError (an Error).
        return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && ClientPause.isPaused(server);
    }

    private static final class ClientPause {
        private ClientPause() {}

        static boolean isPaused(net.minecraft.server.MinecraftServer server) {
            return server instanceof com.stardew.craft.mixin.PortIntegratedServerAccessor integrated
                    && integrated.stardewcraft$isPaused();
        }
    }
}
