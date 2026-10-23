package com.stardew.craft.platform.fabric.event;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

public abstract class ServerTickEvent extends ServerEvent {
    private final BooleanSupplier haveTime;

    protected ServerTickEvent(BooleanSupplier haveTime, MinecraftServer server) {
        super(server);
        this.haveTime = haveTime;
    }

    public final boolean hasTime() { return haveTime.getAsBoolean(); }

    public static final class Pre extends ServerTickEvent {
        public Pre(BooleanSupplier haveTime, MinecraftServer server) { super(haveTime, server); }
    }

    public static final class Post extends ServerTickEvent {
        public Post(BooleanSupplier haveTime, MinecraftServer server) { super(haveTime, server); }
    }
}
