package org.ajtech.schema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customises how a POJO field appears in the generated schema.
 *
 * <p>Field name, documentation, and nullability may each be overridden.
 * {@link Nullability#AUTO} defers to the generator's default rule: primitives are
 * required, reference types follow {@code SchemaOptions.nullableByDefault()}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaField {

    String name() default "";

    String doc() default "";

    Nullability nullability() default Nullability.AUTO;

    enum Nullability { AUTO, NULLABLE, REQUIRED }
}
