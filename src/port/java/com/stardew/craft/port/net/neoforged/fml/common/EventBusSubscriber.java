package com.stardew.craft.port.net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraftforge.api.distmarker.Dist;

/**
 * PORT(1.20.1): NeoForge 21.1 {@code @EventBusSubscriber}.
 * <p>
 * NeoForge (FML 4) ignores {@link #bus()} and routes every {@code @SubscribeEvent} static method by its event type:
 * {@code IModBusEvent} listeners go to the mod bus, everything else to the game bus, and one class may mix both.
 * It also accepts non-public listener methods. Forge 1.20.1's {@code Mod.EventBusSubscriber} registers a whole
 * class on the single bus named by {@code bus} (default FORGE) and silently skips non-public methods, so the mod's
 * subscriber classes are routed by {@link com.stardew.craft.port.event.PortEventSubscribers} instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    /** Physical sides on which the subscriber class is loaded and registered. */
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    /** Owning mod id; empty means the mod whose file contains the class. */
    String modid() default "";

    /** Ignored, exactly as in NeoForge 21.1: listeners are routed by event type. */
    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        /** Alias produced by the mechanical rewrite of {@code Bus.GAME}; identical to {@link #GAME}. */
        FORGE,
        MOD
    }
}
