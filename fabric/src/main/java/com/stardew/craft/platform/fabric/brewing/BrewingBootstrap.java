package com.stardew.craft.platform.fabric.brewing;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;

public final class BrewingBootstrap {
    private static final ThreadLocal<RegistryAccess> REGISTRIES = new ThreadLocal<>();
    private BrewingBootstrap() {}

    public static <T> T withRegistries(RegistryAccess registries, Supplier<T> bootstrap) {
        Objects.requireNonNull(registries);
        RegistryAccess previous = REGISTRIES.get();
        REGISTRIES.set(registries);
        try { return bootstrap.get(); }
        finally {
            if (previous == null) REGISTRIES.remove(); else REGISTRIES.set(previous);
        }
    }

    public static RegistryAccess registries() {
        RegistryAccess access = REGISTRIES.get();
        return access == null ? RegistryAccess.EMPTY : access;
    }

    public static BrewingRecipes collect() { return collect(FabricEvents.GAME); }

    public static BrewingRecipes collect(IEventBus bus) {
        BrewingRecipes.Builder builder = new BrewingRecipes.Builder();
        bus.post(new RegisterBrewingRecipesEvent(builder, registries()));
        return builder.build();
    }
}
