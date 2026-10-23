package com.stardew.craft.platform.fabric.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Method settings take precedence over their declaring class, as in NeoForge. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PrefixGameTestTemplate {
    boolean value() default true;
}
