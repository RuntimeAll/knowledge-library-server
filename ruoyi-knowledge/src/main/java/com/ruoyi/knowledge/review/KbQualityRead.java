package com.ruoyi.knowledge.review;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Pins a review projection and database read view across count, rows and page enrichment. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface KbQualityRead { }
