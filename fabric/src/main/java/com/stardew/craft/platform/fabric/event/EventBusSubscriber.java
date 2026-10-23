package com.stardew.craft.platform.fabric.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.fabricmc.api.EnvType;

/** Metadata for the generated subscriber index; this annotation does not scan classes. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "";
    EnvType[] value() default {EnvType.CLIENT, EnvType.SERVER};
    /** Legacy source metadata. Automatic routing uses each event parameter's ModBusEvent classification. */
    Bus bus() default Bus.GAME;

    enum Bus { GAME, MOD }
}
