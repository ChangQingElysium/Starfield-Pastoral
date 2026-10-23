package com.stardew.craft.platform.fabric.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata consumed by the real TestFunction generator, including NeoForge's namespace field. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameTest {
    int timeoutTicks() default 100;
    String batch() default "defaultBatch";
    boolean skyAccess() default false;
    int rotationSteps() default 0;
    boolean required() default true;
    boolean manualOnly() default false;
    String templateNamespace() default "";
    String template() default "";
    long setupTicks() default 0L;
    int attempts() default 1;
    int requiredSuccesses() default 1;
}
