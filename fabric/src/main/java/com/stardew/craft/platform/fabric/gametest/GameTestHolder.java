package com.stardew.craft.platform.fabric.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Default structure namespace for the test methods declared by this class. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameTestHolder {
    String value() default "minecraft";
}
