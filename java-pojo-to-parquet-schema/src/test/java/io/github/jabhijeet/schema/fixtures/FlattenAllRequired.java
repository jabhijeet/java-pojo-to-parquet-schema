package io.github.jabhijeet.schema.fixtures;

import io.github.jabhijeet.schema.annotation.SchemaField;

public class FlattenAllRequired {
    @SchemaField(nullability = SchemaField.Nullability.REQUIRED)
    public FlattenRequiredLeaf inner;
}
