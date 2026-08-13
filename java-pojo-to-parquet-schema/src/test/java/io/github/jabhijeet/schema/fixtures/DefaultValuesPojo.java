package io.github.jabhijeet.schema.fixtures;

/**
 * Fixture for exercising {@code SchemaOptions.preserveDefaultValues()}. Every
 * field has a Java-level initializer so the prototype instance exposes a
 * non-null default value.
 */
public class DefaultValuesPojo {
    public int count = 7;
    public long total = 42L;
    public float ratio = 1.5f;
    public double average = 2.25;
    public boolean enabled = true;
    public String label = "hello";
    public Integer boxedCount = 11;
    public String nullableLabel = "world";
}

