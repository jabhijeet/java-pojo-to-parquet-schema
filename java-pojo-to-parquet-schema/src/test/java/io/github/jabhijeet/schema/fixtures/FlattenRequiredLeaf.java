package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaField;

public class FlattenRequiredLeaf {
    @SchemaField(nullability = SchemaField.Nullability.REQUIRED)
    public String c;
}
