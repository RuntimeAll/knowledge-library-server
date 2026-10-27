package com.ruoyi.knowledge.review;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Encloses the outermost database transaction in the quality recovery barrier. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KbQualityWrite {
    String dependency() default "NONE";
}
