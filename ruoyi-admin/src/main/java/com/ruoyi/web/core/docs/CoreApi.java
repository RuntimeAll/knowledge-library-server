package com.ruoyi.web.core.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Explicit publication and Java response binding; never changes runtime authorization. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoreApi {
    String value();
    Class<?> response();
    /** Complete internal contracts may be maintained without publishing their route. */
    boolean published() default true;
    Shape shape() default Shape.DATA;
    String[] queryFields() default {};
    String mediaType() default "application/json";

    enum Shape { DATA, PAGE, ARRAY, BINARY }
}
