package org.ajtech.schema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares precision and scale for a {@link java.math.BigDecimal} field.
 *
 * <p>Avro requires both values to be known at schema-generation time. Without
 * this annotation the generator falls back to the defaults configured on
 * {@code SchemaOptions}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaDecimal {

    int precision();

    int scale() default 0;
}
